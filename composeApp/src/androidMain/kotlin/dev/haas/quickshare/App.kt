package dev.haas.quickshare

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.haas.quickshare.ssh.SshReverseTunnelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

val CustomBlack = Color(0xFF16161D)

val LightColors = lightColorScheme(
    primary = CustomBlack,
    onPrimary = Color.White,
    primaryContainer = Color.White,
    onPrimaryContainer = CustomBlack,
    secondary = CustomBlack,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = CustomBlack,
    surface = Color.White,
    onSurface = CustomBlack,
    surfaceVariant = CustomBlack,
    onSurfaceVariant = Color.White,
    error = CustomBlack,
    onError = Color.White,
    outline = CustomBlack
)

@Composable
@Preview
fun App() {
    MaterialTheme(
        colorScheme = LightColors
    ) {
        val context = LocalContext.current
        
        var logs by remember { mutableStateOf(listOf<String>()) }
        var tunnelUrl by remember { mutableStateOf<String?>(null) }
        var sessionToken by remember { mutableStateOf<String?>(null) }
        var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
        var isServerRunning by remember { mutableStateOf(false) }
        var secondsRunning by remember { mutableStateOf(0) }
        
        LaunchedEffect(isServerRunning) {
            if (isServerRunning) {
                val startTime = System.currentTimeMillis()
                while (isServerRunning) {
                    secondsRunning = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                    delay(1000)
                }
            } else {
                secondsRunning = 0
            }
        }

        val webServer = remember { 
            WebServer(context.contentResolver) { msg ->
                logs = logs + "[Server]: $msg"
            } 
        }
        
        val tunnelManager = remember {
            val manager = SshReverseTunnelManager(
                onLog = { msg ->
                    if (logs.size > 100) logs = logs.drop(1)
                    logs = logs + msg
                },
                onUrlAssigned = { url ->
                    tunnelUrl = url.trim()
                }
            )
            manager
        }


        val pickMedia = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(50)
        ) { uris ->
            if (uris.isNotEmpty()) {
                selectedUris = selectedUris + uris
                logs = logs + "Added ${uris.size} photos."
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "QuickShare", 
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Secure peer-to-peer photo sharing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                if (isServerRunning) {
                    SessionActiveCard(
                        secondsRunning = secondsRunning,
                        tunnelUrl = tunnelUrl,
                        sessionToken = sessionToken,
                        onStop = {
                            webServer.stop()
                            tunnelManager.stopTunnel()
                            isServerRunning = false
                            tunnelUrl = null
                            sessionToken = null
                        }
                    )
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Selected Photos (${selectedUris.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = {
                                        pickMedia.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Photos",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            if (selectedUris.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { 
                                            pickMedia.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Tap + to select photos",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 100.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(selectedUris) { uri ->
                                        PhotoGridItem(
                                            uri = uri,
                                            onRemove = {
                                                selectedUris = selectedUris - uri
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (selectedUris.isNotEmpty()) {
                                try {
                                    val token = webServer.start(selectedUris, 8080)
                                    sessionToken = token
                                    tunnelManager.startTunnel()
                                    isServerRunning = true
                                    logs = listOf("Starting secure session...")
                                } catch (e: Exception) {
                                    logs = logs + "Error: ${e.message}"
                                    e.printStackTrace()
                                }
                            }
                        },
                        enabled = selectedUris.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(4.dp, RoundedCornerShape(12.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "START SHARING",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                if (logs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Activity Log",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val listState = rememberLazyListState()
                    LaunchedEffect(logs.size) {
                        listState.animateScrollToItem(logs.size - 1)
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        items(logs) { log ->
                            Text(
                                text = log, 
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = if (log.contains("Error") || log.contains("Exception")) 
                                    MaterialTheme.colorScheme.error 
                                else 
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoGridItem(uri: Uri, onRemove: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use { 
                    BitmapFactory.decodeStream(it, null, options) 
                }
                
                options.inSampleSize = calculateSampleSize(options, 200, 200)
                options.inJustDecodeBounds = false
                
                context.contentResolver.openInputStream(uri)?.use {
                    bitmap = BitmapFactory.decodeStream(it, null, options)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(CustomBlack.copy(alpha = 0.6f))
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

fun calculateSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
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

@Composable
fun SessionActiveCard(
    secondsRunning: Int,
    tunnelUrl: String?,
    sessionToken: String?,
    onStop: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "SESSION ACTIVE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                formatTime(secondsRunning),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (tunnelUrl != null && sessionToken != null) {
                val fullUrl = "$tunnelUrl/?token=$sessionToken"
                QRCodeView(fullUrl)
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = fullUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Establishing secure tunnel...", style = MaterialTheme.typography.bodyMedium)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("STOP SHARING")
            }
        }
    }
}

fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}

@Composable
fun QRCodeView(content: String, modifier: Modifier = Modifier) {
    var bitmap by remember(content) { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    LaunchedEffect(content) {
        withContext(Dispatchers.IO) {
            try {
                val bitMatrix = com.google.zxing.MultiFormatWriter().encode(
                    content, 
                    com.google.zxing.BarcodeFormat.QR_CODE, 
                    512, 
                    512
                )
                val width = bitMatrix.width
                val height = bitMatrix.height
                val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.RGB_565)
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.rgb(0x16, 0x16, 0x1d) else android.graphics.Color.WHITE)
                    }
                }
                bitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(), 
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (bitmap != null) {
            val context = LocalContext.current
            
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp))
                    .background(Color.White, shape = RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { 
                        shareQrCode(context, bitmap!!) 
                    }
                    .padding(16.dp)
            ) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "Scan with another device or share the link",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { 
                    shareQrCode(context, bitmap!!) 
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary, 
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Share Access Link")
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

fun shareQrCode(context: android.content.Context, bitmap: android.graphics.Bitmap) {
    try {
        val cachePath = java.io.File(context.cacheDir, "images")
        cachePath.mkdirs()
        val stream = java.io.FileOutputStream(java.io.File(cachePath, "qr_code.png"))
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()

        val imagePath = java.io.File(context.cacheDir, "images")
        val newFile = java.io.File(imagePath, "qr_code.png")
        
        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context, 
            "dev.haas.quickshare.fileprovider", 
            newFile
        )

        if (contentUri != null) {
            val shareIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, context.contentResolver.getType(contentUri))
                putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
                type = "image/png"
                clipData = android.content.ClipData.newRawUri(null, contentUri)
            }
            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share QR Code"))
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error sharing QR: ${e.message}", Toast.LENGTH_LONG).show()
    }
}