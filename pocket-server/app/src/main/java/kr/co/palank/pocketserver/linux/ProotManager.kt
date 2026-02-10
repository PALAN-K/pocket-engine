package kr.co.palank.pocketserver.linux

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

sealed class ProotState {
    object Idle : ProotState()
    object Running : ProotState()
    object Stopped : ProotState()
    data class Error(val message: String) : ProotState()
}

class ProotManager(private val context: Context) {

    private val _state = MutableStateFlow<ProotState>(ProotState.Idle)
    val state: StateFlow<ProotState> = _state

    private var process: Process? = null

    private val prootPath: String
        get() = ProotBinaryManager.getProotPath(context)

    private val rootfsPath: String
        get() = File(context.filesDir, "ubuntu").absolutePath

    val isRunning: Boolean
        get() = process?.isAlive == true

    suspend fun start() = withContext(Dispatchers.IO) {
        if (isRunning) {
            Log.w(TAG, "PRoot is already running")
            return@withContext
        }

        try {
            ProotBinaryManager.ensureReady(context)

            val cmd = buildProotCommand("/bin/bash", "--login")
            Log.i(TAG, "Starting PRoot: ${cmd.joinToString(" ")}")

            val pb = ProcessBuilder(cmd)
            pb.directory(File(rootfsPath))
            pb.redirectErrorStream(true)
            pb.environment().apply {
                put("HOME", "/root")
                put("TERM", "xterm-256color")
                put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
                put("LANG", "en_US.UTF-8")
                put("PROOT_TMP_DIR", context.cacheDir.absolutePath)
                put("PROOT_NO_SECCOMP", "1")
                put("PROOT_LOADER", ProotBinaryManager.getLoaderPath(context))
                put("PROOT_LOADER_32", ProotBinaryManager.getLoader32Path(context))
            }

            process = pb.start()
            _state.value = ProotState.Running
            Log.i(TAG, "PRoot process started")

            // stdout/stderr 로깅 (별도 스레드)
            Thread {
                try {
                    BufferedReader(InputStreamReader(process!!.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            Log.d(TAG, "[PRoot] $line")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "PRoot output stream closed", e)
                }
            }.apply {
                isDaemon = true
                name = "proot-stdout"
                start()
            }

            // 프로세스 종료 감시
            Thread {
                try {
                    val exitCode = process!!.waitFor()
                    Log.i(TAG, "PRoot process exited with code: $exitCode")
                    if (_state.value is ProotState.Running) {
                        _state.value = if (exitCode == 0) ProotState.Stopped
                        else ProotState.Error("PRoot exited with code $exitCode")
                    }
                } catch (e: InterruptedException) {
                    Log.w(TAG, "PRoot wait interrupted")
                }
            }.apply {
                isDaemon = true
                name = "proot-monitor"
                start()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start PRoot", e)
            _state.value = ProotState.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun exec(vararg command: String): ExecResult = withContext(Dispatchers.IO) {
        ProotBinaryManager.ensureReady(context)

        val cmd = buildProotCommand("/usr/bin/env", "-i",
            "HOME=/root",
            "TERM=xterm-256color",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "LANG=en_US.UTF-8",
            *command
        )

        Log.d(TAG, "exec: ${command.joinToString(" ")}")

        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)
        pb.environment().apply {
            put("PROOT_TMP_DIR", context.cacheDir.absolutePath)
            put("PROOT_NO_SECCOMP", "1")
            put("PROOT_LOADER", ProotBinaryManager.getLoaderPath(context))
            put("PROOT_LOADER_32", ProotBinaryManager.getLoader32Path(context))
        }

        val proc = pb.start()
        val output = proc.inputStream.bufferedReader().readText()
        val exitCode = proc.waitFor()

        Log.d(TAG, "exec result (code=$exitCode): ${output.take(500)}")
        ExecResult(exitCode, output)
    }

    fun stop() {
        process?.let { proc ->
            try {
                proc.outputStream?.close()
                proc.destroy()
                if (proc.isAlive) proc.destroyForcibly()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping PRoot", e)
            }
            Unit
        }
        process = null
        _state.value = ProotState.Stopped
        Log.i(TAG, "PRoot stopped")
    }

    private fun buildProotCommand(vararg innerCommand: String): List<String> {
        return listOf(
            prootPath,
            "--link2symlink",
            "--kill-on-exit",
            "-0",
            "-r", rootfsPath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-w", "/root",
            *innerCommand
        )
    }

    data class ExecResult(val exitCode: Int, val output: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    companion object {
        private const val TAG = "ProotManager"
    }
}
