package dev.haas.quickshare

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.haas.quickshare.ssh.SshReverseTunnelManager

import kotlinx.coroutines.delay

@Composable
@Preview
fun App() {
    MaterialTheme {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        
        // State
        var logs by remember { mutableStateOf(listOf<String>()) }
        var tunnelUrl by remember { mutableStateOf<String?>(null) }
        var sessionToken by remember { mutableStateOf<String?>(null) }
        var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
        var isServerRunning by remember { mutableStateOf(false) }
        var secondsRunning by remember { mutableStateOf(0) }
        
        // Timer Logic
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

        // Managers
        val webServer = remember { 
            WebServer(context.contentResolver) { msg ->
                logs = logs + "[Server]: $msg"
            } 
        }
        
        val tunnelManager = remember {
            SshReverseTunnelManager(
                onLog = { msg ->
                    // Limit log size to prevent UI lag
                    if (logs.size > 100) logs = logs.drop(1)
                    logs = logs + msg
                },
                onUrlAssigned = { url ->
                    tunnelUrl = url.trim()
                }
            )
        }

        // Photo Picker
        val pickMedia = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(50)
        ) { uris ->
            if (uris.isNotEmpty()) {
                selectedUris = uris
                logs = logs + "Selected ${uris.size} photos."
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Secure Photo Share", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            // -- Selection Area --
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("1. Select Photos", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { 
                                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            enabled = !isServerRunning
                        ) {
                            Text(if (selectedUris.isEmpty()) "Choose Photos" else "Change Photos")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = if (selectedUris.isEmpty()) "No photos selected" else "${selectedUris.size} photos ready",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // -- Server Control --
            if (selectedUris.isNotEmpty()) {
                Button(
                    onClick = {
                        if (isServerRunning) {
                            webServer.stop()
                            tunnelManager.stopTunnel()
                            isServerRunning = false
                            tunnelUrl = null
                            sessionToken = null
                        } else {
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServerRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(if (isServerRunning) "STOP SHARING" else "START SECURE SESSION")
                }
            }

            // -- Active Session Info --
            if (isServerRunning) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Session Active: ${formatTime(secondsRunning)}", style = MaterialTheme.typography.labelLarge)
                        
                        if (tunnelUrl != null && sessionToken != null) {
                            val fullUrl = "$tunnelUrl/photos?token=$sessionToken"
                            Spacer(modifier = Modifier.height(16.dp))
                            QRCodeView(fullUrl, modifier = Modifier.size(200.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = fullUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "Token: $sessionToken", 
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(top = 16.dp))
                            Text("Establishing secure tunnel...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // -- Logs --
            Text("Activity Log:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.Start))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp)
            ) {
                items(logs) { log ->
                    Text(
                        text = log, 
                        style = MaterialTheme.typography.bodySmall, 
                        fontSize = 10.sp,
                        color = if (log.contains("Error") || log.contains("Exception")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
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
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
                        bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                    }
                }
                bitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "QR Code",
            modifier = modifier
                .background(Color.White)
                .padding(2.dp)
        )
    } else {
        Box(modifier = modifier.background(Color.LightGray)) {
            Text("Generating...", modifier = Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodySmall)
        }
    }
}