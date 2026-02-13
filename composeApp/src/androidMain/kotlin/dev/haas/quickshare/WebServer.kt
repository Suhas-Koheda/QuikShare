package dev.haas.quickshare

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.util.UUID

// Data model for API response
@Serializable
data class SharedPhoto(
    val id: String,
    val name: String,
    val size: Long,
    val thumbnailUrl: String,
    val downloadUrl: String
)

class WebServer(
    private val contentResolver: ContentResolver,
    private val onLog: (String) -> Unit
) {
    private var server: io.ktor.server.engine.EmbeddedServer<*, *>? = null
    private var currentSessionToken: String? = null
    private val sharedUris = mutableListOf<Uri>()

    fun start(uris: List<Uri>, port: Int = 8080): String {
        stop()
        sharedUris.clear()
        sharedUris.addAll(uris)
        
        // Generate new session token
        val token = UUID.randomUUID().toString().take(8)
        currentSessionToken = token
        
        onLog("Starting local HTTP server on port $port...")
        onLog("Session Token: $token")
        onLog("Shared Files: ${uris.size}")

        server = embeddedServer(CIO, port = port) {
            install(CORS) {
                anyHost()
                allowMethod(io.ktor.http.HttpMethod.Get)
            }
            install(ContentNegotiation) {
                json()
            }
            
            routing {
                // 1. List Photos Endpoint
                get("/photos") {
                    if (!isValidToken(call, token)) return@get
                    
                    val photos = sharedUris.mapIndexed { index, uri ->
                        val fileName = getFileName(uri) ?: "photo_$index.jpg"
                        val fileSize = getFileSize(uri)
                        SharedPhoto(
                            id = index.toString(),
                            name = fileName,
                            size = fileSize,
                            thumbnailUrl = "/thumbnail/$index?token=$token",
                            downloadUrl = "/download/$index?token=$token"
                        )
                    }
                    call.respond(photos)
                }

                // 2. Thumbnail Endpoint
                get("/thumbnail/{id}") {
                    if (!isValidToken(call, token)) return@get
                    val id = call.parameters["id"]?.toIntOrNull()
                    
                    if (id != null && id in sharedUris.indices) {
                        try {
                            val uri = sharedUris[id]
                            val bytes = getThumbnailBytes(uri)
                            call.respondBytes(bytes, ContentType.Image.JPEG)
                        } catch (e: Exception) {
                            call.respond(HttpStatusCode.InternalServerError, "Error generating thumbnail")
                        }
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Image not found")
                    }
                }

                // 3. Download Endpoint (Streaming)
                get("/download/{id}") {
                    if (!isValidToken(call, token)) return@get
                    val id = call.parameters["id"]?.toIntOrNull()
                    
                    if (id != null && id in sharedUris.indices) {
                        val uri = sharedUris[id]
                        try {
                            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                            val fileName = getFileName(uri) ?: "image.jpg"
                            
                            call.response.headers.append("Content-Disposition", "attachment; filename=\"$fileName\"")
                            
                            // Stream content directly to response without loading into memory
                            val stream = contentResolver.openInputStream(uri)
                            if (stream != null) {
                                call.respondOutputStream(ContentType.parse(mimeType)) {
                                    stream.use { input ->
                                        input.copyTo(this)
                                    }
                                }
                            } else {
                                call.respond(HttpStatusCode.NotFound, "File stream not available")
                            }
                        } catch (e: Exception) {
                             e.printStackTrace()
                            call.respond(HttpStatusCode.InternalServerError, "Error reading file")
                        }
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Image not found")
                    }
                }
                
                // Root: Serve HTML Gallery
                get("/") {
                    if (!isValidToken(call, token)) return@get
                    
                    val html = """
                        <!DOCTYPE html>
                        <html lang="en">
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <title>Secure Photo Share</title>
                            <style>
                                body { font-family: system-ui, -apple-system, sans-serif; background: #121212; color: #fff; margin: 0; padding: 20px; }
                                h1 { text-align: center; font-weight: 300; margin-bottom: 30px; }
                                .gallery { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 16px; max-width: 1200px; margin: 0 auto; }
                                .photo-card { background: #1e1e1e; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.3); transition: transform 0.2s; position: relative; }
                                .photo-card:hover { transform: translateY(-5px); }
                                .thumb { width: 100%; aspect-ratio: 1; object-fit: cover; display: block; cursor: pointer; }
                                .actions { padding: 10px; display: flex; justify-content: space-between; align-items: center; background: rgba(0,0,0,0.5); position: absolute; bottom: 0; width: 100%; box-sizing: border-box; }
                                .btn { background: #3d3d3d; color: white; border: none; padding: 6px 12px; border-radius: 6px; cursor: pointer; text-decoration: none; font-size: 12px; }
                                .btn:hover { background: #505050; }
                                .modal { display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.9); z-index: 1000; align-items: center; justify-content: center; }
                                .modal.active { display: flex; }
                                .modal-img { max-width: 95%; max-height: 95%; border-radius: 8px; box-shadow: 0 0 20px rgba(0,0,0,0.5); }
                                .close { position: absolute; top: 20px; right: 20px; color: white; font-size: 30px; cursor: pointer; }
                            </style>
                        </head>
                        <body>
                            <h1>Shared Photos</h1>
                            <div id="gallery" class="gallery">
                                <div style="grid-column: 1/-1; text-align: center; padding: 50px;">Loading Secure Gallery...</div>
                            </div>

                            <div id="modal" class="modal" onclick="closeModal()">
                                <span class="close">&times;</span>
                                <img id="fullImage" class="modal-img" src="">
                            </div>

                            <script>
                                const TOKEN = "$token";
                                
                                async function loadPhotos() {
                                    try {
                                        const response = await fetch(`/photos?token=${'$'}{TOKEN}`);
                                        if (!response.ok) throw new Error('Failed to load');
                                        const photos = await response.json();
                                        renderGallery(photos);
                                    } catch (e) {
                                        document.getElementById('gallery').innerHTML = '<div style="text-align:center;color:#ff6b6b">Error loading session. Token may be invalid.</div>';
                                    }
                                }

                                function renderGallery(photos) {
                                    const container = document.getElementById('gallery');
                                    container.innerHTML = photos.length ? '' : '<div style="text-align:center;grid-column:1/-1">No photos shared.</div>';
                                    
                                    photos.forEach(photo => {
                                        const card = document.createElement('div');
                                        card.className = 'photo-card';
                                        card.innerHTML = `
                                            <img src="${'$'}{photo.thumbnailUrl}" class="thumb" onclick="viewPhoto('${'$'}{photo.downloadUrl}')" loading="lazy">
                                            <div class="actions">
                                                <span style="font-size: 10px; opacity: 0.7; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; max-width: 80px;">${'$'}{photo.name}</span>
                                                <a href="${'$'}{photo.downloadUrl}" class="btn" download>↓</a>
                                            </div>
                                        `;
                                        container.appendChild(card);
                                    });
                                }

                                function viewPhoto(url) {
                                    const modal = document.getElementById('modal');
                                    const img = document.getElementById('fullImage');
                                    img.src = url;
                                    modal.classList.add('active');
                                }

                                function closeModal() {
                                    document.getElementById('modal').classList.remove('active');
                                    setTimeout(() => document.getElementById('fullImage').src = '', 200);
                                }

                                loadPhotos();
                            </script>
                        </body>
                        </html>
                    """.trimIndent()
                    
                    call.respondText(html, ContentType.Text.Html)
                }
            }
        }.start(wait = false)
        
        return token
    }

    // ... stop, isValidToken, getFileName, getFileSize implementation remains same ...
    // Note: I need to preserve them or just replace the specific sections if strict.
    // Tool `replace_file_content` replaces contiguous block.
    // I will replace from "3. Download Endpoint" to end of file,
    // copying the helper functions back but optimizing getThumbnailBytes.

    fun stop() {
        if (server != null) {
            onLog("Stopping HTTP server...")
            server?.stop(100, 1000)
            server = null
            sharedUris.clear()
            // currentSessionToken = null // keep valid for UI history if needed, but safer to clear
            currentSessionToken = null
        }
    }

    private suspend fun isValidToken(call: ApplicationCall, expectedToken: String): Boolean {
        val token = call.request.queryParameters["token"]
        if (token != expectedToken) {
            call.respond(HttpStatusCode.Unauthorized, "Invalid or missing session token")
            return false
        }
        return true
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
             contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) result = cursor.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }
    
    private fun getFileSize(uri: Uri): Long {
         var result: Long = 0
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0) result = cursor.getLong(index)
                }
            }
        }
        return result
    }

    // Compress to 20% quality thumbnail (Memory Efficient)
    private fun getThumbnailBytes(uri: Uri): ByteArray {
        // 1. Calculate dimensions
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        contentResolver.openInputStream(uri)?.use { 
            BitmapFactory.decodeStream(it, null, options) 
        }

        // 2. Calculate sample size (target 512px)
        options.inSampleSize = calculateInSampleSize(options, 512, 512)
        options.inJustDecodeBounds = false
        
        // 3. Decode with sample size
        val inputStream = contentResolver.openInputStream(uri) ?: return ByteArray(0)
        val original = BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()
        
        if (original == null) return ByteArray(0)
        
        val out = ByteArrayOutputStream()
        // 4. Compress to 20% JPEG
        original.compress(Bitmap.CompressFormat.JPEG, 20, out)
        original.recycle()
        return out.toByteArray()
    }
    
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
