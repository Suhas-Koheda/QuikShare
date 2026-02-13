package dev.haas.quickshare.ssh

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.BufferedReader
import java.io.InputStreamReader

class SshReverseTunnelManager(
    private val onLog: (String) -> Unit,
    private val onUrlAssigned: (String) -> Unit
) {
    private var session: Session? = null
    private var channel: ChannelShell? = null
    private val jsch = JSch()
    private var isRunning = false

    fun startTunnel() {
        if (isRunning) return
        isRunning = true
        
        Thread {
            try {
                onLog("Starting tunnel setup...")
                onLog("Target: ssh -R 80:localhost:8080 nokey@localhost.run")

                // 1. Configure Session
                // User: nokey, Host: localhost.run, Port: 22 (default SSH)
                session = jsch.getSession("nokey", "localhost.run", 22)
                
                // effectively disable host key checking for this simple example
                session?.setConfig("StrictHostKeyChecking", "no")
                
                // localhost.run (nokey) typically doesn't require a password
                session?.setPassword("") 
                
                // 2. Connect
                onLog("Connecting to localhost.run...")
                session?.connect(30000) // 30s timeout

                if (session?.isConnected == true) {
                    onLog("SSH Session connected!")

                    // 3. Set up Reverse Port Forwarding
                    // -R 80:localhost:8080
                    session?.setPortForwardingR(80, "localhost", 8080)
                    onLog("Reverse forwarding configured (-R 80:localhost:8080)")

                    // 4. Open Shell to capture output
                    channel = session?.openChannel("shell") as ChannelShell
                    
                    // IMPORTANT: Disable PTY to prevent QR codes and ANSI coloring
                    channel?.setPty(false)
                    
                    val inputStream = channel?.inputStream
                    channel?.connect()

                    onLog("Reading server output...")
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    
                    // Read loop
                    var line: String?
                    // Regex to find the URL: https://[something].lhr.life
                    val urlRegex = Regex("https://[a-zA-Z0-9-]+\\.lhr\\.life")

                    while (isRunning) {
                        line = reader.readLine()
                        if (line == null) break
                        
                        if (line.isNotBlank() && !line.contains("\u001B")) {
                            onLog("[REMOTE]: $line") 
                            
                            // Check for URL
                            val match = urlRegex.find(line)
                            if (match != null) {
                                val url = match.value
                                onLog("→ Tunnel URL found: $url")
                                onUrlAssigned(url)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                onLog("Error: ${e.message}")
                e.printStackTrace()
            } finally {
                isRunning = false
                stopTunnel()
            }
        }.start()
    }

    fun stopTunnel() {
        isRunning = false
        try {
            channel?.disconnect()
        } catch (e: Exception) { /* ignore */ }
        
        try {
            session?.disconnect()
        } catch (e: Exception) { /* ignore */ }
        
        if (session != null) {
             onLog("Tunnel stopped.")
             session = null
        }
    }
}




