package kr.co.palank.pocketserver.ui.dashboard

import android.app.Application
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.co.palank.pocketserver.linux.ServerState
import kr.co.palank.pocketserver.linux.SessionManager
import kr.co.palank.pocketserver.monitor.NetworkMonitor
import kr.co.palank.pocketserver.monitor.ResourceMonitor
import kr.co.palank.pocketserver.service.ServerForegroundService
import kr.co.palank.pocketserver.util.BatteryMonitor

data class DashboardUiState(
    val serverState: ServerState = ServerState.Idle,
    val cpuUsage: Int = 0,
    val ramUsedGb: Double = 0.0,
    val ramTotalGb: Double = 0.0,
    val storageFreeGb: Long = 0,
    val storageTotalGb: Long = 0,
    val temp: Float = 0f,
    val uptime: String = "0\uBD84",
    val ipAddress: String? = null,
    val isWifiConnected: Boolean = false,
    val sshPassword: String? = null,
    val sshPort: Int = 2022
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val context get() = getApplication<Application>()

    init {
        collectServerState()
        collectNetworkState()
        pollResourceMonitor()
        pollBatteryMonitor()
        loadStorageInfo()
        loadSshInfo()
    }

    // --- ServerState collection ---

    private fun collectServerState() {
        val manager = sessionManager ?: return
        viewModelScope.launch {
            manager.state.collect { state ->
                _uiState.value = _uiState.value.copy(
                    serverState = state,
                    uptime = formatUptime(manager.uptimeMillis)
                )
            }
        }
    }

    // --- NetworkMonitor collection ---

    private fun collectNetworkState() {
        val monitor = networkMonitor ?: return
        viewModelScope.launch {
            monitor.state.collect { netState ->
                _uiState.value = _uiState.value.copy(
                    ipAddress = netState.ipAddress,
                    isWifiConnected = netState.isWifiConnected
                )
            }
        }
    }

    // --- ResourceMonitor polling (every 2s) ---

    private fun pollResourceMonitor() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val cpu = ResourceMonitor.getCpuUsage()
                val ram = ResourceMonitor.getRamUsage(context)

                // Also refresh uptime on each tick while running
                val uptimeText = sessionManager?.let { formatUptime(it.uptimeMillis) }
                    ?: _uiState.value.uptime

                _uiState.value = _uiState.value.copy(
                    cpuUsage = cpu,
                    ramUsedGb = ram.first,
                    ramTotalGb = ram.second,
                    uptime = uptimeText
                )
                delay(2000)
            }
        }
    }

    // --- BatteryMonitor polling (every 5s) ---

    private fun pollBatteryMonitor() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val temp = BatteryMonitor.getTemperature(context)
                _uiState.value = _uiState.value.copy(temp = temp)
                delay(5000)
            }
        }
    }

    // --- Storage info (one-shot, refreshed on demand) ---

    private fun loadStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val stat = StatFs(Environment.getDataDirectory().path)
            val freeGb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024L * 1024L * 1024L)
            val totalGb = (stat.blockCountLong * stat.blockSizeLong) / (1024L * 1024L * 1024L)
            _uiState.value = _uiState.value.copy(
                storageFreeGb = freeGb,
                storageTotalGb = totalGb
            )
        }
    }

    // --- SSH info from SessionManager ---

    private fun loadSshInfo() {
        val manager = sessionManager ?: return
        _uiState.value = _uiState.value.copy(
            sshPassword = manager.sshPassword,
            sshPort = manager.sshPort
        )
    }

    // --- Server control actions ---

    fun startServer() {
        val manager = sessionManager ?: return
        ServerForegroundService.start(context, manager)
    }

    fun stopServer() {
        ServerForegroundService.stop(context)
    }

    fun restartServer() {
        val manager = sessionManager ?: return
        manager.restart()
    }

    // --- Uptime formatting ---

    companion object {
        var sessionManager: SessionManager? = null
        var networkMonitor: NetworkMonitor? = null

        fun formatUptime(millis: Long): String {
            val seconds = millis / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            return when {
                days > 0 -> "${days}\uC77C ${hours % 24}\uC2DC\uAC04"
                hours > 0 -> "${hours}\uC2DC\uAC04 ${minutes % 60}\uBD84"
                minutes > 0 -> "${minutes}\uBD84"
                else -> "\uBC29\uAE08 \uC2DC\uC791"
            }
        }
    }
}
