// ============================================================================
// SSH REVERSE TUNNEL - MINIMAL WORKING EXAMPLE
// ============================================================================
//
// This shows the exact minimal code needed to expose your local service
// to the internet via localhost.run
//
// ============================================================================

// ============================================================================
// EXAMPLE 1: THE ABSOLUTE MINIMUM (Copy & Paste)
// ============================================================================

fun main() {
    // Create tunnel
    val tunnel = SimpleTunnelManager(8080)

    // Start in background
    Thread { tunnel.start() }.start()

    // Tunnel is now running!
    // The output will show your public URL
    //
    // To stop later:
    // tunnel.stop()
}

// Output will be:
// → Connecting to localhost.run...
// → Establishing SSH connection...
// → Setting up reverse tunnel...
// ✓ Tunnel established on port: 80
//
// Your service is now at: https://[generated-id].lhr.life


// ============================================================================
// EXAMPLE 2: WITH STATUS UPDATES
// ============================================================================

fun main2() {
    val tunnel = buildReverseTunnel {
        remoteHost("localhost.run")
        remoteUser("nokey")
        localPort(8080)
        listener(SimpleTunnelListener())
    }

    Thread {
        try {
            tunnel.connect()
            println("✓ Tunnel connected!")
            tunnel.keepAlive()  // Blocks until tunnel closes
        } catch (e: SshConnectionException) {
            println("✗ Error: ${e.message}")
        } finally {
            tunnel.disconnect()
        }
    }.start()
}


// ============================================================================
// EXAMPLE 3: IN A COMPOSE/ANDROID VIEWMODEL
// ============================================================================

class QuickShareViewModel {
    private var tunnel: SshReverseTunnelManager? = null
    private var tunnelThread: Thread? = null

    fun startTunnel(localPort: Int = 8080) {
        tunnelThread = Thread {
            try {
                // Create and connect
                tunnel = ReverseTunnelExamples.createLocalhostRunTunnel(
                    localPort = localPort,
                    listener = SimpleTunnelListener()
                )

                tunnel?.connect()
                println("✓ Tunnel started!")

                // Keep running
                tunnel?.keepAlive()

            } catch (e: Exception) {
                println("✗ Tunnel error: ${e.message}")
            } finally {
                tunnel?.disconnect()
            }
        }
        tunnelThread?.start()
    }

    fun stopTunnel() {
        tunnel?.disconnect()
        tunnelThread?.interrupt()
    }

    fun getTunnelStatus(): String {
        return if (tunnel?.isConnected() == true) {
            "Tunnel active"
        } else {
            "Tunnel inactive"
        }
    }
}


// ============================================================================
// EXAMPLE 4: EXPOSE MULTIPLE SERVICES
// ============================================================================

fun main4() {
    // Web server on port 3000
    val webTunnel = SimpleTunnelManager(3000)

    // API server on port 8000
    val apiTunnel = SimpleTunnelManager(8000)

    // Start both
    Thread { webTunnel.start() }.start()
    Thread { apiTunnel.start() }.start()

    // Both are now public!
    println("Web: https://[id1].lhr.life")
    println("API: https://[id2].lhr.life")

    // Stop later:
    // webTunnel.stop()
    // apiTunnel.stop()
}


// ============================================================================
// EXAMPLE 5: WITH CUSTOM LISTENER FOR UI UPDATES
// ============================================================================

class TunnelListener : ReverseTunnelListener {
    override fun onTunnelEstablished(assignedRemotePort: Int) {
        println("✓ Tunnel ready on port: $assignedRemotePort")
        // Update UI here: show "Connected" button, hide "Connect" button
        // updateUI("Tunnel is active")
    }

    override fun onTunnelClosed(reason: String?) {
        println("✓ Tunnel closed: $reason")
        // Update UI: show "Connect" button, hide "Connected" status
        // updateUI("Tunnel stopped")
    }

    override fun onError(error: Throwable) {
        println("✗ Error: ${error.message}")
        // Show error dialog to user
        // showErrorDialog(error.message)
    }

    override fun onStatusChanged(message: String) {
        println("→ $message")
        // Update status text in UI
        // updateStatus(message)
    }
}

fun main5() {
    val tunnel = buildReverseTunnel {
        remoteHost("localhost.run")
        remoteUser("nokey")
        localPort(8080)
        listener(TunnelListener())  // Your custom listener
    }

    Thread {
        tunnel.connect()
        tunnel.keepAlive()
    }.start()
}


// ============================================================================
// KEY POINTS
// ============================================================================
//
// 1. SimpleTunnelManager is the simplest way
//    val tunnel = SimpleTunnelManager(8080)
//
// 2. ALWAYS run start()/connect() in a background thread
//    Thread { tunnel.start() }.start()
//
// 3. keepAlive() blocks - it's the main loop
//    Stops when tunnel closes or thread is interrupted
//
// 4. Call stop() or disconnect() from main thread
//    This closes the tunnel gracefully
//
// 5. Use listener to react to tunnel events
//    Update UI, log status, etc.
//
// ============================================================================

// ============================================================================
// THAT'S IT!
// ============================================================================
//
// Your local service is now publicly accessible!
//
// What happens:
// 1. SSH connection to localhost.run (no password)
// 2. Reverse port forward: 80 → localhost:8080
// 3. Your app gets a public URL like: https://abc123.lhr.life
// 4. Anyone can access it!
// 5. Stop tunnel anytime with tunnel.stop()
//
// ============================================================================

