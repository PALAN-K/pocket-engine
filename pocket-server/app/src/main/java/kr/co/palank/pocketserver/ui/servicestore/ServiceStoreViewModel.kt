package kr.co.palank.pocketserver.ui.servicestore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kr.co.palank.pocketserver.catalog.ServiceCatalog
import kr.co.palank.pocketserver.catalog.ServiceDefinition
import kr.co.palank.pocketserver.service.InstalledService
import kr.co.palank.pocketserver.service.ServiceManager
import kr.co.palank.pocketserver.service.ServiceStatus

enum class SetupStep {
    INSTALLING, API_KEY_INPUT, CONFIGURING, COMPLETED, ERROR
}

class ServiceStoreViewModel(application: Application) : AndroidViewModel(application) {

    val serviceManager = ServiceManager(application)

    // Service IDs currently transitioning (start/stop in progress) -> previous status
    private val _transitioningIds = MutableStateFlow<Map<String, ServiceStatus>>(emptyMap())

    // Merged view: real status overridden with CONFIGURING for transitioning services
    val services: StateFlow<List<InstalledService>> = combine(
        serviceManager.services,
        _transitioningIds,
    ) { serviceList, transitioning ->
        val completed = mutableSetOf<String>()
        val result = serviceList.map { svc ->
            val prevStatus = transitioning[svc.serviceId]
            if (prevStatus != null && svc.status != prevStatus) {
                // ServiceManager updated to a new status — transition finished
                completed.add(svc.serviceId)
                svc
            } else if (prevStatus != null) {
                // Still in the same status — show transitional state
                svc.copy(status = ServiceStatus.CONFIGURING)
            } else {
                svc
            }
        }
        if (completed.isNotEmpty()) {
            _transitioningIds.value = transitioning - completed
        }
        result
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentServiceId = MutableStateFlow<String?>(null)
    val currentServiceId: StateFlow<String?> = _currentServiceId

    private val _setupStep = MutableStateFlow(SetupStep.INSTALLING)
    val setupStep: StateFlow<SetupStep> = _setupStep

    private val _isReconfiguring = MutableStateFlow(false)
    val isReconfiguring: StateFlow<Boolean> = _isReconfiguring

    private val _reconfigureDefaults = MutableStateFlow<Map<String, String>>(emptyMap())
    val reconfigureDefaults: StateFlow<Map<String, String>> = _reconfigureDefaults

    val installProgress: StateFlow<Int> = serviceManager.installProgress
    val installMessage: StateFlow<String> = serviceManager.installMessage

    val currentServiceDef: ServiceDefinition?
        get() = _currentServiceId.value?.let { id ->
            ServiceCatalog.services.find { it.id == id }
        }

    fun startSetup(serviceId: String) {
        _currentServiceId.value = serviceId
        _setupStep.value = SetupStep.INSTALLING
        serviceManager.installService(serviceId)
    }

    fun onInstallComplete() {
        _setupStep.value = SetupStep.API_KEY_INPUT
    }

    private val _isOAuthFlow = MutableStateFlow(false)
    val isOAuthFlow: StateFlow<Boolean> = _isOAuthFlow

    fun submitInputs(inputs: Map<String, String>) {
        val serviceId = _currentServiceId.value ?: return
        _isOAuthFlow.value = inputs["is_oauth"] == "true"
        _setupStep.value = SetupStep.CONFIGURING
        serviceManager.configureAndStart(serviceId, inputs)
    }

    fun onConfigureComplete() {
        _setupStep.value = SetupStep.COMPLETED
    }

    fun onError() {
        _setupStep.value = SetupStep.ERROR
    }

    fun stopService(serviceId: String) {
        val current = serviceManager.services.value.find { it.serviceId == serviceId }
        val prevStatus = current?.status ?: ServiceStatus.RUNNING
        _transitioningIds.value = _transitioningIds.value + (serviceId to prevStatus)
        serviceManager.stopService(serviceId)
    }

    fun startService(serviceId: String) {
        val current = serviceManager.services.value.find { it.serviceId == serviceId }
        val prevStatus = current?.status ?: ServiceStatus.INSTALLED
        _transitioningIds.value = _transitioningIds.value + (serviceId to prevStatus)
        serviceManager.startService(serviceId)
    }

    fun restartService(serviceId: String) {
        val current = serviceManager.services.value.find { it.serviceId == serviceId }
        val prevStatus = current?.status ?: ServiceStatus.RUNNING
        _transitioningIds.value = _transitioningIds.value + (serviceId to prevStatus)
        serviceManager.restartService(serviceId)
    }

    fun startReconfigure(serviceId: String) {
        val config = serviceManager.getServiceConfig(serviceId) ?: emptyMap()
        _currentServiceId.value = serviceId
        _isReconfiguring.value = true
        _reconfigureDefaults.value = config
        _setupStep.value = SetupStep.API_KEY_INPUT
    }

    fun submitReconfigure(inputs: Map<String, String>) {
        val serviceId = _currentServiceId.value ?: return
        _isOAuthFlow.value = inputs["is_oauth"] == "true"
        _setupStep.value = SetupStep.CONFIGURING
        serviceManager.reconfigureService(serviceId, inputs)
    }

    fun clearReconfigure() {
        _isReconfiguring.value = false
        _reconfigureDefaults.value = emptyMap()
    }

    fun getServiceConfig(serviceId: String): Map<String, String>? {
        return serviceManager.getServiceConfig(serviceId)
    }

    fun getDeviceRamMb(): Int {
        val activityManager = getApplication<Application>()
            .getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return (memInfo.totalMem / (1024 * 1024)).toInt()
    }
}
