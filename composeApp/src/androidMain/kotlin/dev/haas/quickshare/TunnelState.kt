package dev.haas.quickshare

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.posthog.PostHog

object TunnelState {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _tunnelUrl = MutableStateFlow<String?>(null)
    val tunnelUrl: StateFlow<String?> = _tunnelUrl.asStateFlow()

    private val _sessionToken = MutableStateFlow<String?>(null)
    val sessionToken: StateFlow<String?> = _sessionToken.asStateFlow()

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _secondsRunning = MutableStateFlow(0)
    val secondsRunning: StateFlow<Int> = _secondsRunning.asStateFlow()

    private val _selectedUris = MutableStateFlow<List<Uri>>(emptyList())
    val selectedUris: StateFlow<List<Uri>> = _selectedUris.asStateFlow()

    fun appendLog(msg: String) {
        val current = _logs.value
        if (current.size > 100) {
            _logs.value = current.drop(1) + msg
        } else {
            _logs.value = current + msg
        }
        
        // Also capture the log message in PostHog for remote debugging
        PostHog.capture(
            event = "app_log",
            properties = mapOf("message" to msg)
        )
    }

    fun setTunnelUrl(url: String?) {
        _tunnelUrl.value = url
    }

    fun setSessionToken(token: String?) {
        _sessionToken.value = token
    }

    fun setServerRunning(running: Boolean) {
        _isServerRunning.value = running
    }

    fun setSecondsRunning(seconds: Int) {
        _secondsRunning.value = seconds
    }
    
    fun setSelectedUris(uris: List<Uri>) {
        _selectedUris.value = uris
    }
}
