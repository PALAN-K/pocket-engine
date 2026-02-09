package kr.co.palank.pocketmonitor.ipc

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class EngineStatus(
    val connected: Boolean = false,
    val state: String = "unknown",
    val cpu: Int = 0,
    val ram: Int = 0,
    val temp: Float = 0f,
    val uptimeMs: Long = 0,
    val ip: String? = null,
    val port: Int = 2022,
    val user: String = "pocketserver",
    val diskUsedMb: Long = 0,
    val diskTotalMb: Long = 0,
    val errorMessage: String? = null,
)

class EngineConnector {

    private val _status = MutableStateFlow<EngineStatus?>(null)
    val status: StateFlow<EngineStatus?> = _status.asStateFlow()

    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private var consecutiveFailures = 0
    private var handshakeDone = false

    fun start() {
        if (pollJob != null) return
        pollJob = scope.launch {
            while (isActive) {
                try {
                    if (!handshakeDone) {
                        val hsResponse = sendRequest("""{"cmd":"handshake","protocol_version":"1.0"}""")
                        if (hsResponse != null) {
                            val json = JSONObject(hsResponse)
                            if (json.optBoolean("ok", false)) {
                                handshakeDone = true
                                consecutiveFailures = 0
                            }
                        }
                    }

                    if (handshakeDone) {
                        val response = sendRequest("""{"cmd":"status"}""")
                        if (response != null) {
                            val json = JSONObject(response)
                            _status.value = EngineStatus(
                                connected = true,
                                state = json.optString("state", "unknown"),
                                cpu = json.optInt("cpu", 0),
                                ram = json.optInt("ram", 0),
                                temp = json.optDouble("temp", 0.0).toFloat(),
                                uptimeMs = json.optLong("uptime", 0),
                                ip = json.optString("ip", "").takeIf { it.isNotEmpty() && it != "null" },
                                port = json.optInt("port", 2022),
                                user = json.optString("user", "pocketserver"),
                                diskUsedMb = json.optLong("disk_used", 0),
                                diskTotalMb = json.optLong("disk_total", 0),
                                errorMessage = json.optString("error_message", "").takeIf { it.isNotEmpty() },
                            )
                            consecutiveFailures = 0
                        } else {
                            onConnectionFailed()
                        }
                    } else {
                        onConnectionFailed()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Poll error", e)
                    onConnectionFailed()
                }

                val delayMs = getBackoffDelay()
                delay(delayMs)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        handshakeDone = false
        consecutiveFailures = 0
    }

    suspend fun sendCommand(cmd: String) {
        withContext(Dispatchers.IO) {
            try {
                val response = sendRequest("""{"cmd":"$cmd"}""")
                if (response != null) {
                    val json = JSONObject(response)
                    if (!json.optBoolean("ok", false)) {
                        Log.w(TAG, "Command '$cmd' failed: ${json.optString("error")}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send command '$cmd'", e)
            }
            Unit
        }
    }

    private fun sendRequest(jsonRequest: String): String? {
        return try {
            val socket = LocalSocket()
            socket.connect(LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT))
            socket.use { s ->
                val writer = BufferedWriter(OutputStreamWriter(s.outputStream))
                val reader = BufferedReader(InputStreamReader(s.inputStream))
                writer.write(jsonRequest)
                writer.newLine()
                writer.flush()
                reader.readLine()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun onConnectionFailed() {
        consecutiveFailures++
        if (consecutiveFailures >= MAX_FAILURES_BEFORE_DISCONNECT) {
            _status.value = null
            handshakeDone = false
        }
    }

    private fun getBackoffDelay(): Long {
        if (consecutiveFailures == 0) return POLL_INTERVAL_MS
        val backoff = POLL_INTERVAL_MS * (1 shl consecutiveFailures.coerceAtMost(4))
        return backoff.coerceAtMost(MAX_BACKOFF_MS)
    }

    companion object {
        private const val TAG = "EngineConnector"
        private const val SOCKET_NAME = "pocketserver_ipc"
        private const val POLL_INTERVAL_MS = 2000L
        private const val MAX_BACKOFF_MS = 30_000L
        private const val MAX_FAILURES_BEFORE_DISCONNECT = 10
    }
}
