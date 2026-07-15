package com.ismartcoding.plain.tunnel

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.ismartcoding.plain.lib.logcat.LogCat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TunnelService : Service() {
    private val binder = LocalBinder()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main)
    private var statusCollectionJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): TunnelService = this@TunnelService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogCat.d("TunnelService started")
        
        statusCollectionJob = scope.launch {
            try {
                // Monitor tunnel status and handle auto-start/stop
                TunnelManager.isRunning.collectLatest { isRunning ->
                    if (isRunning) {
                        LogCat.d("Tunnel is running")
                    } else {
                        LogCat.d("Tunnel is stopped")
                    }
                }
            } catch (e: Exception) {
                LogCat.e("Error monitoring tunnel status: ${e.message}", e)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        LogCat.d("TunnelService destroyed")
        TunnelManager.stop()
        statusCollectionJob?.cancel()
        job.cancel()
    }

    fun startTunnel(localPort: Int, authToken: String) {
        LogCat.d("TunnelService starting tunnel on port $localPort")
        TunnelManager.start(localPort, authToken)
    }

    fun stopTunnel() {
        LogCat.d("TunnelService stopping tunnel")
        TunnelManager.stop()
    }

    fun getTunnelUrl(): String = TunnelManager.getTunnelUrl()

    fun isConnected(): Boolean = TunnelManager.isConnected()

    companion object {
        const val TAG = "TunnelService"
    }
}
