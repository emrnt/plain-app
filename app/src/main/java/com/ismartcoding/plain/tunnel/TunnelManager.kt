package com.ismartcoding.plain.tunnel

import android.content.Context
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

object TunnelManager {
    private const val BINARY_NAME = "frpc"
    private var process: Process? = null
    private var monitorJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val tunnelUrl = MutableStateFlow("")
    val isRunning = MutableStateFlow(false)

    fun ensureBinary(context: Context): File {
        val binary = File(context.filesDir, BINARY_NAME)
        if (binary.exists()) return binary

        val abi = getAbi()
        val assetPath = "frp/frpc-$abi"
        context.assets.open(assetPath).use { input ->
            binary.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        binary.setExecutable(true)
        LogCat.d("Extracted frpc binary for $abi to ${binary.absolutePath}")
        return binary
    }

    private fun getAbi(): String {
        val abis = android.os.Build.SUPPORTED_ABIS
        return when {
            abis.any { it.startsWith("arm64") } -> "arm64"
            abis.any { it.startsWith("arm64") } -> "arm64"
            else -> "arm64"
        }
    }

    fun start(context: Context, localPort: Int, server: String, port: String, domain: String) {
        if (server.isBlank()) {
            LogCat.e("frp server address is empty, cannot start tunnel")
            return
        }

        try {
            val binary = ensureBinary(context)
            stop()

            val configText = StringBuilder().apply {
                appendLine("serverAddr = \"$server\"")
                appendLine("serverPort = $port")
                appendLine()
                appendLine("[[proxies]]")
                appendLine("name = \"web\"")
                appendLine("type = \"http\"")
                appendLine("localPort = $localPort")
                if (domain.isNotBlank()) {
                    appendLine("customDomains = [\"$domain\"]")
                }
            }.toString()

            val config = File(context.filesDir, "frpc.toml")
            config.writeText(configText)

            val pb = ProcessBuilder(
                binary.absolutePath, "-c", config.absolutePath
            )
            pb.directory(context.filesDir)
            pb.environment()["HOME"] = context.filesDir.absolutePath
            pb.redirectErrorStream(true)

            process = pb.start()
            isRunning.value = true

            val publicUrl = if (domain.isNotBlank()) "https://$domain" else ""

            monitorJob = scope.launch {
                val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                while (isActive) {
                    val line = reader.readLine() ?: break
                    LogCat.d("frpc: $line")
                    if (line.contains("success") || line.contains("start proxy success")) {
                        tunnelUrl.value = publicUrl
                        LogCat.d("frp tunnel connected: $publicUrl")
                    }
                }
                LogCat.d("frpc process ended, restarting in 5s...")
                if (isActive) {
                    delay(5000)
                    start(context, localPort, server, port, domain)
                }
            }
        } catch (e: Exception) {
            LogCat.e("Failed to start frp tunnel: ${e.message}")
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
        LogCat.d("frp tunnel stopped")
    }
}