package kr.co.palank.pocketserver.linux

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

sealed class DropbearState {
    object Stopped : DropbearState()
    object Starting : DropbearState()
    object Running : DropbearState()
    data class Error(val message: String) : DropbearState()
}

class DropbearManager(
    private val context: Context,
    private val prootManager: ProotManager
) {
    private val _state = MutableStateFlow<DropbearState>(DropbearState.Stopped)
    val state: StateFlow<DropbearState> = _state

    private var dropbearProcess: Process? = null

    val isRunning: Boolean
        get() = _state.value is DropbearState.Running

    suspend fun start() = withContext(Dispatchers.IO) {
        if (isRunning) {
            Log.w(TAG, "Dropbear is already running")
            return@withContext
        }

        try {
            _state.value = DropbearState.Starting

            // PRoot 내에서 Dropbear를 foreground 모드로 실행
            // -E: stderr 로깅
            // -p 2022: 포트 2022
            // -R: 호스트 키 없으면 자동 생성
            // -F: foreground 모드 (프로세스 관리용)
            // -w: root 로그인 비활성화
            val prootPath = ProotBinaryManager.getProotPath(context)
            val rootfsPath = "${context.filesDir.absolutePath}/ubuntu"

            val cmd = listOf(
                prootPath,
                "--link2symlink",
                "--kill-on-exit",
                "-0",
                "-r", rootfsPath,
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "-w", "/root",
                "/usr/sbin/dropbear",
                "-E", "-p", "2022", "-R", "-F", "-w"
            )

            Log.i(TAG, "Starting Dropbear: ${cmd.joinToString(" ")}")

            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(true)
            pb.environment().apply {
                put("PROOT_TMP_DIR", context.cacheDir.absolutePath)
                put("PROOT_NO_SECCOMP", "1")
                put("PROOT_LOADER", ProotBinaryManager.getLoaderPath(context))
                put("PROOT_LOADER_32", ProotBinaryManager.getLoader32Path(context))
            }

            dropbearProcess = pb.start()

            // stdout/stderr 로깅
            Thread {
                try {
                    dropbearProcess!!.inputStream.bufferedReader().use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            Log.d(TAG, "[Dropbear] $line")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Dropbear output stream closed", e)
                }
            }.apply {
                isDaemon = true
                name = "dropbear-stdout"
                start()
            }

            // 프로세스 종료 감시
            Thread {
                try {
                    val exitCode = dropbearProcess!!.waitFor()
                    Log.i(TAG, "Dropbear exited with code: $exitCode")
                    if (_state.value is DropbearState.Running || _state.value is DropbearState.Starting) {
                        _state.value = if (exitCode == 0) DropbearState.Stopped
                        else DropbearState.Error("Dropbear exited with code $exitCode")
                    }
                } catch (e: InterruptedException) {
                    Log.w(TAG, "Dropbear wait interrupted")
                }
            }.apply {
                isDaemon = true
                name = "dropbear-monitor"
                start()
            }

            // Dropbear 시작 확인 대기 (최대 5초)
            delay(1000)
            if (dropbearProcess?.isAlive == true) {
                _state.value = DropbearState.Running
                Log.i(TAG, "Dropbear SSH server running on port 2022")
            } else {
                _state.value = DropbearState.Error("Dropbear failed to start")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Dropbear", e)
            _state.value = DropbearState.Error(e.message ?: "Unknown error")
        }
    }

    fun stop() {
        dropbearProcess?.let { proc ->
            try {
                proc.destroy()
                if (proc.isAlive) proc.destroyForcibly()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping Dropbear", e)
            }
            Unit
        }
        dropbearProcess = null
        _state.value = DropbearState.Stopped
        Log.i(TAG, "Dropbear stopped")
    }

    suspend fun restart() {
        stop()
        delay(500)
        start()
    }

    companion object {
        private const val TAG = "DropbearManager"
        const val SSH_PORT = 2022
    }
}
