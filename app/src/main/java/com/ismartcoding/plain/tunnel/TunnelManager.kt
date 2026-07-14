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
    private const val BINARY_NAME = "ngrok"
    private var process: Process? = null
    private var monitorJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val tunnelUrl = MutableStateFlow("")
    val isRunning = MutableStateFlow(false)

    fun ensureBinary(context: Context): File {
        val binary = File(context.filesDir, BINARY_NAME)
        if (binary.exists()) return binary

        val abi = getAbi()
        val assetPath = "ngrok/ngrok-$abi"
        context.assets.open(assetPath).use { input ->
                binary.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        binary.setExecutable(true)
        LogCat.d("Extracted ngrok binary for $abi to ${binary.absolutePath}")
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

    fun start(context: Context, localPort: Int, authToken: String) {
        if (authToken.isBlank()) {
            LogCat.e("Ngrok auth token is empty, cannot start tunnel")
            return
        }

        try {
            val binary = ensureBinary(context)
            stop()

            val resolvConf = File(context.filesDir, "resolv.conf")
            resolvConf.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")

            val yml = File(context.filesDir, "ngrok.yml")
            yml.writeText("version: 2\nauthtoken: $authToken\n")

            val pb = ProcessBuilder(
                binary.absolutePath, "http",
                "http://localhost:$localPort",
                "--config", yml.absolutePath,
                "--log", "stdout"
            )
            pb.directory(context.filesDir)
            pb.environment()["HOME"] = context.filesDir.absolutePath
            pb.environment()["GODEBUG"] = "netdns=go"
            pb.redirectErrorStream(true)

            process = pb.start()
            isRunning.value = true

            monitorJob = scope.launch {
                val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                while (isActive) {
                    val line = reader.readLine() ?: break
                    LogCat.d("ngrok: $line")
                    if (line.contains("msg=\"started tunnel\"") && line.contains("url=")) {
                        val url = line.substringAfter("url=").substringBefore(" ")
                            .replace("tcp://", "https://").trim()
                        if (url.length > 10) {
                            tunnelUrl.value = url
                            LogCat.d("Ngrok tunnel URL: $url")
                        }
                    }
                }
                LogCat.d("ngrok process ended, restarting in 5s...")
                if (isActive) {
                    delay(5000)
                    start(context, localPort, authToken)
                }
            }
        } catch (e: Exception) {
            LogCat.e("Failed to start ngrok tunnel: ${e.message}")
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
        LogCat.d("Ngrok tunnel stopped")
    }
}