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
                _uiState.value = _uiState.value.copy(device = device)
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
