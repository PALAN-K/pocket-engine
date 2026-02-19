package kr.co.palank.pocketserver.linux

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class ServerState {
    object Idle : ServerState()
    object Installing : ServerState()
    object Starting : ServerState()
    object Running : ServerState()
    object Stopping : ServerState()
    object Stopped : ServerState()
    data class Error(val message: String) : ServerState()
}

class SessionManager(private val context: Context) {

    private val _state = MutableStateFlow<ServerState>(ServerState.Idle)
    val state: StateFlow<ServerState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prootManager = ProotManager(context)
    private val installManager = InstallManager(context)
    private val dropbearManager = DropbearManager(context, prootManager)

    val installState: StateFlow<InstallState> get() = installManager.state
    val isInstalled: Boolean get() = installManager.isInstalled
    val sshPassword: String? get() = installManager.sshPassword
    val sshPort: Int get() = DropbearManager.SSH_PORT

    private var startTimeMillis: Long = 0L
    private var healthCheckJob: Job? = null

    val uptimeMillis: Long
        get() = if (_state.value is ServerState.Running) {
            System.currentTimeMillis() - startTimeMillis
        } else 0L

    fun install() {
        if (_state.value is ServerState.Installing) return

        _state.value = ServerState.Installing
        scope.launch {
            try {
                installManager.startInstallation()
                val finalState = installManager.state.value
                if (finalState is InstallState.Error) {
                    _state.value = ServerState.Error("설치 실패: ${finalState.error}")
                } else {
                    _state.value = ServerState.Idle
                    Log.i(TAG, "Installation completed, server is idle")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Installation failed", e)
                _state.value = ServerState.Error("설치 실패: ${e.message}")
            }
        }
    }

    fun start() {
        if (_state.value is ServerState.Running || _state.value is ServerState.Starting) return
        if (!installManager.isInstalled) {
            _state.value = ServerState.Error("서버가 설치되지 않았습니다")
            return
        }

        _state.value = ServerState.Starting
        scope.launch {
            try {
                // SSH 비밀번호 재적용 (설치 시 chpasswd 실패 대비)
                installManager.ensureSshPassword()

                // Dropbear 시작 (PRoot 내에서 실행)
                dropbearManager.start()

                if (dropbearManager.isRunning) {
                    startTimeMillis = System.currentTimeMillis()
                    _state.value = ServerState.Running
                    startHealthCheck()
                    Log.i(TAG, "Server is running (SSH on port ${DropbearManager.SSH_PORT})")
                } else {
                    _state.value = ServerState.Error("SSH 서버 시작 실패")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
                _state.value = ServerState.Error("서버 시작 실패: ${e.message}")
            }
        }
    }

    fun stop() {
        if (_state.value is ServerState.Stopped || _state.value is ServerState.Idle) return

        _state.value = ServerState.Stopping
        stopHealthCheck()
        try {
            dropbearManager.stop()
            prootManager.stop()
            startTimeMillis = 0L
            _state.value = ServerState.Stopped
            Log.i(TAG, "Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
            _state.value = ServerState.Error("서버 중지 실패: ${e.message}")
        }
    }

    /**
     * Periodic health check: verify Dropbear is actually alive in the process tree.
     * Uses isServerInProcTree.sh (UserLand pattern) to check real Dropbear PID,
     * not just the PRoot wrapper. Auto-restarts if Dropbear crashed silently.
     */
    private fun startHealthCheck() {
        stopHealthCheck()
        healthCheckJob = scope.launch(Dispatchers.IO) {
            while (isActive && _state.value is ServerState.Running) {
                delay(HEALTH_CHECK_INTERVAL_MS)
                if (_state.value !is ServerState.Running) break

                val alive = dropbearManager.isDropbearAliveInProcTree()
                if (!alive && _state.value is ServerState.Running) {
                    Log.w(TAG, "Dropbear not found in process tree — auto-restarting")
                    scope.launch(Dispatchers.Main) {
                        try {
                            dropbearManager.restart()
                            if (dropbearManager.isRunning) {
                                Log.i(TAG, "Dropbear auto-restart successful")
                            } else {
                                _state.value = ServerState.Error("SSH 서버 자동 재시작 실패")
                                stopHealthCheck()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Dropbear auto-restart failed", e)
                            _state.value = ServerState.Error("SSH 서버 자동 재시작 실패: ${e.message}")
                            stopHealthCheck()
                        }
                    }
                }
            }
        }
    }

    private fun stopHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = null
    }

    fun restart() {
        scope.launch {
            stop()
            kotlinx.coroutines.delay(1000)
            start()
        }
    }

    fun reset() {
        stop()
        installManager.reset()
        _state.value = ServerState.Idle
        Log.i(TAG, "Server reset to factory state")
    }

    companion object {
        private const val TAG = "SessionManager"
        private const val HEALTH_CHECK_INTERVAL_MS = 15_000L // 15초 간격
    }
}
