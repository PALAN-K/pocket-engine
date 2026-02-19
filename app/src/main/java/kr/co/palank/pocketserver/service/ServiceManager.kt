package kr.co.palank.pocketserver.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kr.co.palank.pocketserver.catalog.ServiceCatalog
import kr.co.palank.pocketserver.catalog.ServiceInstaller

enum class ServiceStatus {
    NOT_INSTALLED, INSTALLED, RUNNING, STOPPED, INSTALLING, CONFIGURING, ERROR
}

data class InstalledService(
    val serviceId: String,
    val name: String,
    val status: ServiceStatus,
    val errorMessage: String? = null,
)

class ServiceManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val installers = mutableMapOf<String, ServiceInstaller>()

    private val _services = MutableStateFlow<List<InstalledService>>(emptyList())
    val services: StateFlow<List<InstalledService>> = _services

    private val _installProgress = MutableStateFlow(0)
    val installProgress: StateFlow<Int> = _installProgress

    private val _installMessage = MutableStateFlow("")
    val installMessage: StateFlow<String> = _installMessage

    init {
        refreshServiceStatuses()
    }

    private fun refreshServiceStatuses() {
        val list = ServiceCatalog.services.map { def ->
            val installer = getOrCreateInstaller(def.id)
            val status = if (installer.isInstalled()) ServiceStatus.INSTALLED else ServiceStatus.NOT_INSTALLED
            InstalledService(serviceId = def.id, name = def.name, status = status)
        }
        _services.value = list
    }

    private fun getOrCreateInstaller(serviceId: String): ServiceInstaller {
        return installers.getOrPut(serviceId) {
            val def = ServiceCatalog.services.first { it.id == serviceId }
            def.installer(context)
        }
    }

    fun installService(serviceId: String) {
        val installer = getOrCreateInstaller(serviceId)
        updateServiceStatus(serviceId, ServiceStatus.INSTALLING)

        scope.launch(Dispatchers.IO) {
            try {
                installer.install { progress, message ->
                    _installProgress.value = progress
                    _installMessage.value = message
                }
                updateServiceStatus(serviceId, ServiceStatus.INSTALLED)
                Log.i(TAG, "Service $serviceId installed")
            } catch (e: Exception) {
                Log.e(TAG, "Install failed for $serviceId", e)
                updateServiceStatus(serviceId, ServiceStatus.ERROR, e.message)
            }
        }
    }

    fun configureAndStart(serviceId: String, inputs: Map<String, String>) {
        val installer = getOrCreateInstaller(serviceId)
        updateServiceStatus(serviceId, ServiceStatus.CONFIGURING)

        scope.launch(Dispatchers.IO) {
            try {
                installer.configure(inputs)
                val started = installer.start()
                if (started) {
                    updateServiceStatus(serviceId, ServiceStatus.RUNNING)
                    Log.i(TAG, "Service $serviceId configured and started")
                } else {
                    updateServiceStatus(serviceId, ServiceStatus.ERROR, "시작 실패")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Configure/start failed for $serviceId", e)
                updateServiceStatus(serviceId, ServiceStatus.ERROR, e.message)
            }
        }
    }

    fun stopService(serviceId: String) {
        val installer = getOrCreateInstaller(serviceId)
        scope.launch(Dispatchers.IO) {
            try {
                installer.stop()
                updateServiceStatus(serviceId, ServiceStatus.STOPPED)
                Log.i(TAG, "Service $serviceId stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Stop failed for $serviceId", e)
            }
        }
    }

    fun startService(serviceId: String) {
        val installer = getOrCreateInstaller(serviceId)
        scope.launch(Dispatchers.IO) {
            try {
                val started = installer.start()
                if (started) {
                    updateServiceStatus(serviceId, ServiceStatus.RUNNING)
                } else {
                    updateServiceStatus(serviceId, ServiceStatus.ERROR, "시작 실패")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Start failed for $serviceId", e)
                updateServiceStatus(serviceId, ServiceStatus.ERROR, e.message)
            }
        }
    }

    fun startAllInstalled() {
        scope.launch(Dispatchers.IO) {
            _services.value.filter { it.status == ServiceStatus.INSTALLED || it.status == ServiceStatus.STOPPED }
                .forEach { service ->
                    try {
                        val installer = getOrCreateInstaller(service.serviceId)
                        if (installer.isInstalled()) {
                            val running = installer.isRunning()
                            if (!running) {
                                installer.start()
                                updateServiceStatus(service.serviceId, ServiceStatus.RUNNING)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Auto-start failed for ${service.serviceId}", e)
                    }
                }
        }
    }

    private fun updateServiceStatus(serviceId: String, status: ServiceStatus, error: String? = null) {
        _services.value = _services.value.map {
            if (it.serviceId == serviceId) it.copy(status = status, errorMessage = error) else it
        }
    }

    companion object {
        private const val TAG = "ServiceManager"
    }
}
