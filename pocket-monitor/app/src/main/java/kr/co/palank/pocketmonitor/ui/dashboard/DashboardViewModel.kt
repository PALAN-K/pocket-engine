package kr.co.palank.pocketmonitor.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.co.palank.pocketmonitor.ipc.EngineConnector
import kr.co.palank.pocketmonitor.ipc.EngineStatus
import kr.co.palank.pocketmonitor.monitor.DeviceMonitor
import kr.co.palank.pocketmonitor.monitor.DeviceStatus
import kr.co.palank.pocketmonitor.monitor.HistoryEntry
import kr.co.palank.pocketmonitor.monitor.HistoryTracker

data class DashboardUiState(
    val device: DeviceStatus = DeviceStatus(),
    val engine: EngineStatus? = null,
    val history: List<HistoryEntry> = emptyList(),
    val healthScore: Int = 100,
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val deviceMonitor = DeviceMonitor(application)
    private val historyTracker = HistoryTracker(deviceMonitor)
    private val engineConnector = EngineConnector()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        deviceMonitor.start()
        historyTracker.start()
        engineConnector.start()

        viewModelScope.launch {
            deviceMonitor.status.collect { device ->
                _uiState.value = _uiState.value.copy(
                    device = device,
                    healthScore = calculateHealthScore(device),
                )
            }
        }
        viewModelScope.launch {
            historyTracker.history.collect { history ->
                _uiState.value = _uiState.value.copy(history = history)
            }
        }
        viewModelScope.launch {
            engineConnector.status.collect { engine ->
                _uiState.value = _uiState.value.copy(engine = engine)
            }
        }
    }

    private fun calculateHealthScore(device: DeviceStatus): Int {
        val tempScore = ((45f - device.temperature.coerceIn(30f, 45f)) / 15f * 100).toInt()
        val cpuScore = 100 - device.cpuUsage.coerceIn(0, 100)
        val ramPercent = if (device.ramTotalGb > 0) (device.ramUsedGb / device.ramTotalGb * 100).toInt() else 0
        val ramScore = 100 - ramPercent.coerceIn(0, 100)
        val storagePercent = if (device.storageTotalGb > 0) (device.storageUsedGb * 100 / device.storageTotalGb).toInt() else 0
        val storageScore = 100 - storagePercent.coerceIn(0, 100)
        val batteryScore = if (device.isCharging) 100 else device.batteryLevel.coerceIn(0, 100)
        return (tempScore * 0.4 + cpuScore * 0.2 + ramScore * 0.2 + storageScore * 0.1 + batteryScore * 0.1).toInt().coerceIn(0, 100)
    }

    fun sendCommand(cmd: String) {
        viewModelScope.launch {
            engineConnector.sendCommand(cmd)
        }
    }

    override fun onCleared() {
        super.onCleared()
        deviceMonitor.stop()
        historyTracker.stop()
        engineConnector.stop()
    }
}
