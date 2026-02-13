package dev.haas.quickshare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.haas.quickshare.ssh.SshReverseTunnelManager
import kotlinx.coroutines.*

class TunnelService : Service() {

    private var webServer: WebServer? = null
    private var tunnelManager: SshReverseTunnelManager? = null
    private var serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var timerJob: Job? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_URIS = "EXTRA_URIS"
        const val NOTIFICATION_ID = 12345
        const val CHANNEL_ID = "QuickShareTunnelChannel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(EXTRA_URIS) ?: ArrayList()
                startSharing(uris)
            }
            ACTION_STOP -> {
                stopSharing()
            }
        }
        return START_NOT_STICKY
    }

    private fun startSharing(uris: List<Uri>) {
        if (TunnelState.isServerRunning.value) return

        TunnelState.setSelectedUris(uris)
        
        // Start Foreground Service
        startForeground(NOTIFICATION_ID, createNotification("Starting service..."), 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )

        serviceScope.launch {
            try {
                // Initialize WebServer
                webServer = WebServer(contentResolver) { msg ->
                    TunnelState.appendLog("[Server]: $msg")
                }
                
                // Initialize TunnelManager
                tunnelManager = SshReverseTunnelManager(
                    onLog = { msg ->
                        TunnelState.appendLog(msg)
                    },
                    onUrlAssigned = { url ->
                        val trimmedUrl = url.trim()
                        TunnelState.setTunnelUrl(trimmedUrl)
                        updateNotification("Sharing Active: $trimmedUrl")
                    }
                )

                // Start Server
                val token = webServer?.start(uris, 8080)
                if (token != null) {
                    TunnelState.setSessionToken(token)
                    
                    // Start Tunnel calls network, so it's already in IO scope, but method itself launches internal thread.
                    // Ideally SshReverseTunnelManager should be suspendable or use coroutines properly, but it uses Thread { }. 
                    // This is fine for now as it doesn't block this scope.
                    tunnelManager?.startTunnel()
                    
                    TunnelState.setServerRunning(true)
                    TunnelState.appendLog("Starting secure session...")
                    
                    startTimer()
                } else {
                    TunnelState.appendLog("Failed to start web server.")
                    stopSharing()
                }

            } catch (e: Exception) {
                TunnelState.appendLog("Error starting service: ${e.message}")
                e.printStackTrace()
                stopSharing()
            }
        }
    }

    private fun stopSharing() {
        try {
            webServer?.stop()
            tunnelManager?.stopTunnel()
            
            TunnelState.setServerRunning(false)
            TunnelState.setTunnelUrl(null)
            TunnelState.setSessionToken(null)
            TunnelState.setSecondsRunning(0)
            
            timerJob?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            
            TunnelState.appendLog("Service stopped.")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive && TunnelState.isServerRunning.value) {
                val seconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                TunnelState.setSecondsRunning(seconds)
                delay(1000)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "QuickShare Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("QuickShare Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
    
    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(content))
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSharing() // Ensure cleanup
        serviceScope.cancel()
    }
}
