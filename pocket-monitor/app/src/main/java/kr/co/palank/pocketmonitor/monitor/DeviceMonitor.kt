package kr.co.palank.pocketmonitor.monitor

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.RandomAccessFile

data class DeviceStatus(
    val cpuUsage: Int = 0,
    val ramUsedGb: Double = 0.0,
    val ramTotalGb: Double = 0.0,
    val temperature: Float = 0f,
    val batteryLevel: Int = 0,
    val storageUsedGb: Long = 0,
    val storageTotalGb: Long = 0,
)

class DeviceMonitor(private val context: Context) {

    private val _status = MutableStateFlow(DeviceStatus())
    val status: StateFlow<DeviceStatus> = _status.asStateFlow()

    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        if (monitorJob != null) return
        monitorJob = scope.launch {
            while (isActive) {
                _status.value = collectStatus()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun collectStatus(): DeviceStatus {
        val cpu = getCpuUsage()
        val (ramUsed, ramTotal) = getRamUsage()
        val temp = getTemperature()
        val battery = getBatteryLevel()
        val (storageUsed, storageTotal) = getStorageUsage()
        return DeviceStatus(
            cpuUsage = cpu,
            ramUsedGb = ramUsed,
            ramTotalGb = ramTotal,
            temperature = temp,
            batteryLevel = battery,
            storageUsedGb = storageUsed,
            storageTotalGb = storageTotal,
        )
    }

    private fun getCpuUsage(): Int {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            var line = reader.readLine()
            val toks = line.split(" +".toRegex())
            val idle1 = toks[4].toLong()
            val cpu1 = toks[1].toLong() + toks[2].toLong() + toks[3].toLong() +
                    toks[6].toLong() + toks[7].toLong() + toks[8].toLong()
            Thread.sleep(200)
            reader.seek(0)
            line = reader.readLine()
            reader.close()
            val toks2 = line.split(" +".toRegex())
            val idle2 = toks2[4].toLong()
            val cpu2 = toks2[1].toLong() + toks2[2].toLong() + toks2[3].toLong() +
                    toks2[6].toLong() + toks2[7].toLong() + toks2[8].toLong()
            ((cpu2 - cpu1).toDouble() / ((cpu2 + idle2) - (cpu1 + idle1)) * 100).toInt()
        } catch (ex: Exception) {
            0
        }
    }

    private fun getRamUsage(): Pair<Double, Double> {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val total = memInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
        val avail = memInfo.availMem.toDouble() / (1024 * 1024 * 1024)
        return Pair(total - avail, total)
    }

    private fun getTemperature(): Float {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    }

    private fun getBatteryLevel(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 0
    }

    private fun getStorageUsage(): Pair<Long, Long> {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val totalGb = (stat.totalBytes) / (1024 * 1024 * 1024)
        val freeGb = (stat.availableBytes) / (1024 * 1024 * 1024)
        return Pair(totalGb - freeGb, totalGb)
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2000L
    }
}
