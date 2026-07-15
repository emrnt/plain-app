package com.ismartcoding.plain.tunnel

import android.content.Context
import com.ismartcoding.plain.lib.channel.sendEvent
import com.ismartcoding.plain.lib.helpers.CoroutinesHelper.coIO
import com.ismartcoding.plain.lib.logcat.LogCat
import kotlinx.coroutines.flow.collectLatest

object TunnelEventHandler {
    fun initialize(context: Context) {
        LogCat.d("Initializing TunnelEventHandler")
        TunnelNotificationManager.initialize(context)
        
        coIO {
            try {
                // Monitor tunnel status changes
                TunnelManager.connectionStatus.collectLatest { status ->
                    LogCat.d("Tunnel status changed: $status")
                    when (status) {
                        TunnelManager.ConnectionStatus.CONNECTING -> {
                            TunnelNotificationManager.showTunnelConnectingNotification(context)
                            sendEvent(TunnelStatusChangeEvent(status))
                        }
                        TunnelManager.ConnectionStatus.CONNECTED -> {
                            val url = TunnelManager.getTunnelUrl()
                            if (url.isNotEmpty()) {
                                TunnelNotificationManager.showTunnelUrlNotification(context, url)
                                sendEvent(TunnelUrlAvailableEvent(url))
                            }
                            sendEvent(TunnelStatusChangeEvent(status))
                        }
                        TunnelManager.ConnectionStatus.ERROR -> {
                            val error = TunnelManager.getError()
                            TunnelNotificationManager.showTunnelErrorNotification(context, error)
                            sendEvent(TunnelErrorEvent(error))
                            sendEvent(TunnelStatusChangeEvent(status))
                        }
                        TunnelManager.ConnectionStatus.RECONNECTING -> {
                            sendEvent(TunnelStatusChangeEvent(status))
                        }
                        TunnelManager.ConnectionStatus.DISCONNECTED -> {
                            TunnelNotificationManager.cancelNotification()
                            sendEvent(TunnelStatusChangeEvent(status))
                        }
                    }
                }
            } catch (e: Exception) {
                LogCat.e("Error in TunnelEventHandler: ${e.message}", e)
            }
        }
    }
}
