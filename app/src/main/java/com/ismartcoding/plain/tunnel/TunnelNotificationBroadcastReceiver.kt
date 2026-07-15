package com.ismartcoding.plain.tunnel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.ClipboardManager
import com.ismartcoding.plain.lib.logcat.LogCat

class TunnelNotificationBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra("url") ?: return
        
        when (intent.action) {
            TunnelNotificationManager.ACTION_COPY_URL -> {
                copyToClipboard(context, url)
            }
            TunnelNotificationManager.ACTION_SHARE_URL -> {
                shareUrl(context, url)
            }
        }
    }

    private fun copyToClipboard(context: Context, url: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = android.content.ClipData.newPlainText("Tunnel URL", url)
            clipboard.setPrimaryClip(clip)
            LogCat.d("Tunnel URL copied to clipboard")
        } catch (e: Exception) {
            LogCat.e("Error copying URL to clipboard: ${e.message}", e)
        }
    }

    private fun shareUrl(context: Context, url: String) {
        try {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Ngrok Tunnel URL: $url")
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Tunnel URL"))
            LogCat.d("Sharing tunnel URL")
        } catch (e: Exception) {
            LogCat.e("Error sharing URL: ${e.message}", e)
        }
    }
}
