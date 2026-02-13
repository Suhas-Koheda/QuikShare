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

                session = jsch.getSession("nokey", "localhost.run", 22)
                
                session?.setConfig("StrictHostKeyChecking", "no")
                
                session?.setPassword("") 
                
                onLog("Connecting to localhost.run...")
                session?.connect(30000)

                if (session?.isConnected == true) {
                    onLog("SSH Session connected!")

                    session?.setPortForwardingR(80, "localhost", 8080)
                    onLog("Reverse forwarding configured (-R 80:localhost:8080)")

                    channel = session?.openChannel("shell") as ChannelShell
                    
                    channel?.setPty(false)
                    
                    val inputStream = channel?.inputStream
                    channel?.connect()

                    onLog("Reading server output...")
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    
                    var line: String?
                    val urlRegex = Regex("https://[a-zA-Z0-9-]+\\.lhr\\.life")

                    while (isRunning) {
                        line = reader.readLine()
                        if (line == null) break
                        
                        if (line.isNotBlank() && !line.contains("\u001B")) {
                            onLog("[REMOTE]: $line") 
                            
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
        } catch (e: Exception) { }
        
        try {
            session?.disconnect()
        } catch (e: Exception) { }
        
        if (session != null) {
             onLog("Tunnel stopped.")
             session = null
        }
    }
}




