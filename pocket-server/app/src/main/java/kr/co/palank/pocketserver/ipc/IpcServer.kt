package kr.co.palank.pocketserver.ipc

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Environment
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.palank.pocketserver.linux.ServerState
import kr.co.palank.pocketserver.linux.SessionManager
import kr.co.palank.pocketserver.monitor.NetworkMonitor
import kr.co.palank.pocketserver.monitor.ResourceMonitor
import kr.co.palank.pocketserver.util.BatteryMonitor
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class IpcServer(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val networkMonitor: NetworkMonitor,
) {
    private var serverSocket: LocalServerSocket? = null
    private var acceptJob: Job? = null

    @Volatile
    private var running = false

    fun start(scope: CoroutineScope) {
        if (running) return
        running = true

        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = LocalServerSocket(SOCKET_NAME)
                Log.i(TAG, "IPC server listening on $SOCKET_NAME")

                while (isActive && running) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        launch(Dispatchers.IO) {
                            handleClient(client)
                        }
                    } catch (e: Exception) {
                        if (running) {
                            Log.e(TAG, "Accept error", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create server socket", e)
            }
        }
    }

    fun stop() {
        running = false
        acceptJob?.cancel()
        acceptJob = null
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing server socket", e)
        }
        serverSocket = null
        Log.i(TAG, "IPC server stopped")
    }

    private suspend fun handleClient(socket: LocalSocket) {
        try {
            socket.use { client ->
                val reader = BufferedReader(InputStreamReader(client.inputStream))
                val writer = BufferedWriter(OutputStreamWriter(client.outputStream))

                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: return
                val response = processRequest(line)
                withContext(Dispatchers.IO) {
                    writer.write(response)
                    writer.newLine()
                    writer.flush()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Client handling error", e)
        }
    }

    private suspend fun processRequest(rawJson: String): String {
        return try {
            val request = JSONObject(rawJson)
            val cmd = request.optString("cmd", "")

            when (cmd) {
                "handshake" -> handleHandshake(request)
                "status" -> handleStatus()
                "start" -> handleStart()
                "stop" -> handleStop()
                "restart" -> handleRestart()
                else -> errorResponse("unknown_command")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing request: $rawJson", e)
            errorResponse("invalid_request")
        }
    }

    private fun handleHandshake(@Suppress("UNUSED_PARAMETER") request: JSONObject): String {
        return JSONObject().apply {
            put("ok", true)
            put("protocol_version", PROTOCOL_VERSION)
            put("engine_version", getEngineVersion())
        }.toString()
    }

    private suspend fun handleStatus(): String {
        val state = sessionManager.state.value
        val stateString = when (state) {
            is ServerState.Idle -> "idle"
            is ServerState.Installing -> "installing"
            is ServerState.Starting -> "starting"
            is ServerState.Running -> "running"
            is ServerState.Stopping -> "stopping"
            is ServerState.Stopped -> "stopped"
            is ServerState.Error -> "error"
        }

        val cpu = withContext(Dispatchers.IO) { ResourceMonitor.getCpuUsage() }
        val (ramUsed, ramTotal) = withContext(Dispatchers.IO) { ResourceMonitor.getRamUsage(context) }
        val temp = BatteryMonitor.getTemperature(context)
        val networkState = networkMonitor.state.value
        val (diskUsed, diskTotal) = getDiskUsage()

        return JSONObject().apply {
            put("state", stateString)
            put("cpu", cpu)
            put("ram", Math.round(ramUsed / ramTotal * 100).coerceIn(0, 100))
            put("temp", temp.toDouble())
            put("uptime", sessionManager.uptimeMillis)
            put("ip", networkState.ipAddress ?: JSONObject.NULL)
            put("port", sessionManager.sshPort)
            put("user", "pocketserver")
            put("disk_used", diskUsed)
            put("disk_total", diskTotal)
            if (state is ServerState.Error) {
                put("error_message", state.message)
            }
        }.toString()
    }

    private fun handleStart(): String {
        val state = sessionManager.state.value
        if (state is ServerState.Running) {
            return errorResponse("server_already_running")
        }
        if (!sessionManager.isInstalled) {
            return errorResponse("install_not_complete")
        }
        sessionManager.start()
        return okResponse()
    }

    private fun handleStop(): String {
        val state = sessionManager.state.value
        if (state is ServerState.Stopped || state is ServerState.Idle) {
            return errorResponse("server_not_running")
        }
        sessionManager.stop()
        return okResponse()
    }

    private fun handleRestart(): String {
        if (!sessionManager.isInstalled) {
            return errorResponse("install_not_complete")
        }
        sessionManager.restart()
        return okResponse()
    }

    private fun okResponse(): String {
        return JSONObject().apply { put("ok", true) }.toString()
    }

    private fun errorResponse(error: String): String {
        return JSONObject().apply {
            put("ok", false)
            put("error", error)
        }.toString()
    }

    private fun getDiskUsage(): Pair<Long, Long> {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.blockSizeLong * stat.blockCountLong
            val freeBytes = stat.blockSizeLong * stat.availableBlocksLong
            val usedMb = (totalBytes - freeBytes) / (1024 * 1024)
            val totalMb = totalBytes / (1024 * 1024)
            Pair(usedMb, totalMb)
        } catch (e: Exception) {
            Pair(0L, 0L)
        }
    }

    private fun getEngineVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    companion object {
        private const val TAG = "IpcServer"
        const val SOCKET_NAME = "pocketserver_ipc"
        private const val PROTOCOL_VERSION = "1.0"
    }
}
