package com.ismartcoding.plain.tunnel

import com.ismartcoding.plain.lib.logcat.LogCat
import com.ngrok.Http
import com.ngrok.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object TunnelManager {
    private var session: Session? = null
    private var listener: Http.Listener? = null
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val tunnelUrl = MutableStateFlow("")
    val isRunning = MutableStateFlow(false)

    fun start(localPort: Int, authToken: String) {
        if (authToken.isBlank()) {
            LogCat.e("Ngrok auth token is empty, cannot start tunnel")
            return
        }

        scope.launch {
            try {
                stop()

                val sess = Session.newBuilder(authToken)
                    .metadata("plain-app")
                    .build()

                session = sess

                val l = sess.httpEndpoint()
                    .metadata("plain-web")
                    .listen()

                listener = l
                tunnelUrl.value = l.url
                isRunning.value = true
                LogCat.d("Ngrok tunnel URL: ${l.url}")

                l.forwardTcp("localhost:$localPort")
            } catch (e: Exception) {
                LogCat.e("Failed to start ngrok tunnel: ${e.message}")
                isRunning.value = false
                scheduleReconnect(localPort, authToken)
            }
        }
    }

    private fun scheduleReconnect(localPort: Int, authToken: String) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(10_000)
            if (isActive) {
                start(localPort, authToken)
            }
        }
    }

    fun stop() {
        reconnectJob?.cancel()
        reconnectJob = null
        try {
            listener?.close()
        } catch (_: Exception) {}
        listener = null
        try {
            session?.close()
        } catch (_: Exception) {}
        session = null
        isRunning.value = false
        tunnelUrl.value = ""
        LogCat.d("Ngrok tunnel stopped")
    }
}