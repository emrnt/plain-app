package com.ismartcoding.plain.tunnel

import com.ismartcoding.plain.lib.logcat.LogCat
import com.ngrok.Forwarder
import com.ngrok.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URL

object TunnelManager {
    private var session: Session? = null
    private var forwarder: Forwarder.Endpoint? = null
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tunnelUrl = MutableStateFlow("")
    val tunnelUrl: StateFlow<String> = _tunnelUrl

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    enum class ConnectionStatus {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR, RECONNECTING
    }

    fun start(localPort: Int, authToken: String) {
        if (authToken.isBlank()) {
            LogCat.e("Ngrok auth token is empty, cannot start tunnel")
            _errorMessage.value = "Auth token is empty"
            _connectionStatus.value = ConnectionStatus.ERROR
            return
        }

        if (localPort <= 0 || localPort > 65535) {
            LogCat.e("Invalid port number: $localPort")
            _errorMessage.value = "Invalid port number"
            _connectionStatus.value = ConnectionStatus.ERROR
            return
        }

        scope.launch {
            try {
                _connectionStatus.value = ConnectionStatus.CONNECTING
                _errorMessage.value = ""
                
                stop()

                LogCat.d("Starting ngrok tunnel on port $localPort")

                // Create session with auth token
                val sess = Session.withAuthtoken(authToken)
                    .metadata("plain-app")
                    .connect()

                session = sess

                // Create HTTP endpoint and forward to local port
                val fwd = sess.httpEndpoint()
                    .metadata("plain-web")
                    .forward(URL("http://localhost:$localPort"))

                forwarder = fwd
                _tunnelUrl.value = fwd.url
                _isRunning.value = true
                _connectionStatus.value = ConnectionStatus.CONNECTED
                LogCat.d("Ngrok tunnel URL: ${fwd.url}")
            } catch (e: Exception) {
                LogCat.e("Failed to start ngrok tunnel: ${e.message}", e)
                _errorMessage.value = "Failed to start tunnel: ${e.message}"
                _isRunning.value = false
                _connectionStatus.value = ConnectionStatus.ERROR
                scheduleReconnect(localPort, authToken)
            }
        }
    }

    private fun scheduleReconnect(localPort: Int, authToken: String) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _connectionStatus.value = ConnectionStatus.RECONNECTING
            delay(10_000) // Wait 10 seconds before reconnecting
            if (isActive) {
                LogCat.d("Attempting to reconnect to ngrok")
                start(localPort, authToken)
            }
        }
    }

    fun stop() {
        reconnectJob?.cancel()
        reconnectJob = null
        try {
            forwarder?.close()
        } catch (e: Exception) {
            LogCat.w("Error closing forwarder: ${e.message}")
        }
        forwarder = null
        try {
            session?.close()
        } catch (e: Exception) {
            LogCat.w("Error closing session: ${e.message}")
        }
        session = null
        _isRunning.value = false
        _tunnelUrl.value = ""
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _errorMessage.value = ""
        LogCat.d("Ngrok tunnel stopped")
    }

    fun getTunnelUrl(): String = _tunnelUrl.value

    fun isConnected(): Boolean = _isRunning.value && _tunnelUrl.value.isNotEmpty()

    fun getStatus(): ConnectionStatus = _connectionStatus.value

    fun getError(): String = _errorMessage.value
}
