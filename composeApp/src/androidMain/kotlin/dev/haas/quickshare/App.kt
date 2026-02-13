package dev.haas.quickshare

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.haas.quickshare.ssh.SshReverseTunnelManager

@Composable
@Preview
fun App() {
    MaterialTheme {
        var logs by remember { mutableStateOf(listOf<String>()) }
        // Use a wrapper to keep the manager across recompositions
        val manager = remember {
            SshReverseTunnelManager { message ->
                // Simple append, in a real app consider thread safety if issues arise
                logs = logs + message
            }
        }
        var isRunning by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = {
                    if (isRunning) {
                        manager.stopTunnel()
                        isRunning = false
                    } else {
                        // clear logs on start
                        logs = listOf("Initializing...")
                        manager.startTunnel()
                        isRunning = true
                    }
                }
            ) {
                Text(if (isRunning) "Stop Tunnel" else "Start Tunnel")
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 16.dp)
            ) {
                items(logs) { log ->
                    Text(text = log)
                }
            }
        }
    }
}