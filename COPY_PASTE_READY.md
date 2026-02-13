# Copy & Paste Ready - SSH Reverse Tunnel
## The Simplest Way (Just Copy & Paste)
```kotlin
fun exposeYourAppToInternet() {
    val tunnel = SimpleTunnelManager(8080)  // Your local port
    Thread { tunnel.start() }.start()
    // That's it! Your app is now public!
}
// When you want to stop:
fun stopExposingApp(tunnel: SimpleTunnelManager) {
    tunnel.stop()
}
```
**Output:**
```
→ Connecting to localhost.run...
→ Establishing SSH connection...
→ Setting up reverse tunnel...
✓ Tunnel established on port: 80
```
Your service is accessible at the printed URL.
---
## Example: Android App
```kotlin
class MainActivity : ComponentActivity() {
    private var tunnel: SimpleTunnelManager? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App {
                // Start button
                Button(onClick = { startTunnel() }) {
                    Text("Expose App")
                }
                // Stop button
                Button(onClick = { stopTunnel() }) {
                    Text("Stop")
                }
            }
        }
    }
    private fun startTunnel() {
        tunnel = SimpleTunnelManager(8080)
        Thread { tunnel?.start() }.start()
    }
    private fun stopTunnel() {
        tunnel?.stop()
    }
}
```
---
## Example: With Status Updates
```kotlin
fun exposeWithStatus() {
    Thread {
        val tunnel = buildReverseTunnel {
            remoteHost("localhost.run")
            remoteUser("nokey")
            localPort(8080)
            listener(SimpleTunnelListener())  // Shows status
        }
        tunnel.connect()
        tunnel.keepAlive()  // Keeps running
    }.start()
}
```
This will print:
```
→ Connecting to localhost.run...
→ Establishing SSH connection...
→ Setting up reverse tunnel...
✓ Tunnel established on port: 80
```
---
## Example: Expose Multiple Services
```kotlin
fun exposeMultipleApps() {
    // Web server (React, Vue, etc.)
    val webTunnel = SimpleTunnelManager(3000)
    Thread { webTunnel.start() }.start()
    // API server (Spring, Node, etc.)
    val apiTunnel = SimpleTunnelManager(8000)
    Thread { apiTunnel.start() }.start()
    // Both are now public!
}
```
---
## What Happens Behind The Scenes
```
Your Local Service (localhost:8080)
         ↓
SimpleTunnelManager (wrapper)
         ↓
SshReverseTunnelManager (core logic)
         ↓
SSH Connection to localhost.run (nokey user, no password)
         ↓
Port Forwarding: remote:80 → localhost:8080
         ↓
keepAlive() loop (keeps running)
         ↓
✓ Your service is now accessible at: https://\[random-id\].lhr.life
         ↓
Anyone in the world can access your app!
```
---
## Error Handling
```kotlin
fun exposeWithErrorHandling() {
    Thread {
        try {
            val tunnel = SimpleTunnelManager(8080)
            tunnel.start()  // Connects and keeps alive
        } catch (e: SshConnectionException) {
            println("Failed to start tunnel: ${e.message}")
            // Show error to user
        }
    }.start()
}
```
---
## Full ViewModel Example
```kotlin
class ShareViewModel : ViewModel() {
    private var tunnel: SshReverseTunnelManager? = null
    private var tunnelThread: Thread? = null
    private val _isShareActive = MutableState(false)
    val isShareActive: State<Boolean> = _isShareActive
    private val _shareUrl = MutableState("")
    val shareUrl: State<String> = _shareUrl
    private val _statusMessage = MutableState("")
    val statusMessage: State<String> = _statusMessage
    fun startSharing(localPort: Int = 8080) {
        _statusMessage.value = "Starting..."
        _isShareActive.value = true
        tunnelThread = Thread {
            try {
                tunnel = ReverseTunnelExamples.createLocalhostRunTunnel(
                    localPort,
                    object : ReverseTunnelListener {
                        override fun onTunnelEstablished(assignedRemotePort: Int) {
                            _statusMessage.value = "✓ Tunnel active!"
                            _shareUrl.value = "https://[your-domain].lhr.life"
                        }
                        override fun onStatusChanged(message: String) {
                            _statusMessage.value = message
                        }
                        override fun onError(error: Throwable) {
                            _statusMessage.value = "✗ Error: ${error.message}"
                            _isShareActive.value = false
                        }
                        override fun onTunnelClosed(reason: String?) {
                            _isShareActive.value = false
                            _statusMessage.value = "Tunnel closed"
                        }
                    }
                )
                tunnel?.connect()
                tunnel?.keepAlive()
            } catch (e: Exception) {
                _statusMessage.value = "✗ Failed: ${e.message}"
                _isShareActive.value = false
            }
        }
        tunnelThread?.start()
    }
    fun stopSharing() {
        tunnel?.disconnect()
        tunnelThread?.interrupt()
        _isShareActive.value = false
        _statusMessage.value = "Stopped"
    }
}
```
Then in Compose:
```kotlin
@Composable
fun ShareScreen(viewModel: ShareViewModel) {
    Column {
        Text(viewModel.statusMessage.value)
        if (viewModel.shareUrl.value.isNotEmpty()) {
            Text("Share this link: ${viewModel.shareUrl.value}")
        }
        if (viewModel.isShareActive.value) {
            Button(onClick = { viewModel.stopSharing() }) {
                Text("Stop Sharing")
            }
        } else {
            Button(onClick = { viewModel.startSharing(8080) }) {
                Text("Start Sharing")
            }
        }
    }
}
```
---
## Configuration Options
```kotlin
// All available options (all simple, no complex auth)
buildReverseTunnel {
    remoteHost("localhost.run")      // SSH server (fixed)
    remoteUser("nokey")               // Username (fixed, no password)
    localPort(8080)                   // YOUR service port (required)
    remoteBindPort(80)                // Remote port (fixed)
    listener(SimpleTunnelListener())  // See status messages
}
```
That's literally it. No authentication, no complexity.
---
## Key Points
✅ **SimpleTunnelManager** = Simplest way
✅ **Run in background thread** = Always do this
✅ **listener()** = Optional, but recommended
✅ **stop()** = Gracefully close tunnel
✅ **Multiple tunnels** = Each runs independently
✅ **No auth needed** = localhost.run doesn't require it
---
## Troubleshooting
| Problem | Solution |
|---------|----------|
| Tunnel not starting | Check `SimpleTunnelListener()` output |
| "Connection refused" | Check if localhost.run is accessible |
| URL not showing | Use `SimpleTunnelListener()` to see messages |
| Multiple tunnels needed | Create separate `SimpleTunnelManager` instances |
| Want more control | Use `SshReverseTunnelManager` directly |
---
That's everything! Pick one example above and start using it now!
