package kr.co.palank.pocketserver.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kr.co.palank.pocketserver.monitor.ResourceMonitor
import kr.co.palank.pocketserver.util.BatteryMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val cpuUsage: Int = 0,
    val ramUsedGb: Double = 0.0,
    val ramTotalGb: Double = 0.0,
    val temp: Float = 0f,
    val uptime: String = "00:00:00"
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        viewModelScope.launch {
            while (true) {
                val ram = ResourceMonitor.getRamUsage(getApplication())
                _uiState.value = _uiState.value.copy(
                    cpuUsage = ResourceMonitor.getCpuUsage(),
                    ramUsedGb = ram.first,
                    ramTotalGb = ram.second,
                    temp = BatteryMonitor.getTemperature(getApplication())
                )
                delay(1000)
            }
        }
    }
}
