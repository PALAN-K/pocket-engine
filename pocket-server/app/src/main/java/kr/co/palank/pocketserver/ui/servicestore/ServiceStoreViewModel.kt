package kr.co.palank.pocketserver.ui.servicestore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val services: StateFlow<List<InstalledService>> = serviceManager.services

    private val _currentServiceId = MutableStateFlow<String?>(null)
    val currentServiceId: StateFlow<String?> = _currentServiceId

    private val _setupStep = MutableStateFlow(SetupStep.INSTALLING)
    val setupStep: StateFlow<SetupStep> = _setupStep

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

    fun submitInputs(inputs: Map<String, String>) {
        val serviceId = _currentServiceId.value ?: return
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
        serviceManager.stopService(serviceId)
    }

    fun startService(serviceId: String) {
        serviceManager.startService(serviceId)
    }

    fun getDeviceRamMb(): Int {
        val activityManager = getApplication<Application>()
            .getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return (memInfo.totalMem / (1024 * 1024)).toInt()
    }
}
