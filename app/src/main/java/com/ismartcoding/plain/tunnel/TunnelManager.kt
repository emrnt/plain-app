package com.ismartcoding.plain.tunnel

import android.content.Context
import com.ismartcoding.plain.lib.helpers.CoroutinesHelper.coIO
import com.ismartcoding.plain.lib.logcat.LogCat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

object TunnelManager {
    private const val BINARY_NAME = "cloudflared"
    private var process: Process? = null
    private var monitorJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val tunnelUrl = MutableStateFlow("")
    val isRunning = MutableStateFlow(false)

    fun ensureBinary(context: Context): File {
        val binary = File(context.filesDir, BINARY_NAME)
        if (binary.exists()) return binary

        val abi = getAbi()
        val assetPath = "cloudflared/cloudflared-$abi.gz"
        context.assets.open(assetPath).use { input ->
            GZIPInputStream(input).use { decompressed ->
                binary.outputStream().use { output ->
                    decompressed.copyTo(output)
                }
            }
        }
        binary.setExecutable(true)
        LogCat.d("Extracted cloudflared binary for $abi to ${binary.absolutePath}")
        return binary
    }

    private fun getAbi(): String {
        val abis = android.os.Build.SUPPORTED_ABIS
        return when {
            abis.any { it.startsWith("arm64") } -> "arm64"
            abis.any { it.startsWith("arm") } -> "arm"
            abis.any { it.startsWith("x86_64") } -> "amd64"
            abis.any { it.startsWith("x86") } -> "386"
            else -> "arm64"
        }
    }

    fun start(context: Context, localPort: Int) {
        try {
            val binary = ensureBinary(context)
            stop()

            val pb = ProcessBuilder(
                binary.absolutePath, "tunnel",
                "--url", "http://localhost:$localPort",
                "--no-autoupdate"
            )
            pb.directory(context.filesDir)
            pb.environment()["HOME"] = context.filesDir.absolutePath
            pb.redirectErrorStream(true)

            process = pb.start()
            isRunning.value = true

            monitorJob = scope.launch {
                val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                while (isActive) {
                    val line = reader.readLine() ?: break
                    LogCat.d("cloudflared: $line")
                    if (line.contains(".trycloudflare.com") || line.contains("https://")) {
                        val url = line.substringAfter("https://").substringBefore(" ").let {
                            "https://$it"
                        }.trim()
                        if (url.length > 10) {
                            tunnelUrl.value = url
                            LogCat.d("Tunnel URL: $url")
                        }
                    }
                }
                LogCat.d("cloudflared process ended, restarting in 5s...")
                if (isActive) {
                    delay(5000)
                    start(context, localPort)
                }
            }
        } catch (e: Exception) {
            LogCat.e("Failed to start tunnel: ${e.message}")
            isRunning.value = false
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        try {
            process?.destroyForcibly()
        } catch (_: Exception) {}
        process = null
        isRunning.value = false
        tunnelUrl.value = ""
        LogCat.d("Tunnel stopped")
    }
}
