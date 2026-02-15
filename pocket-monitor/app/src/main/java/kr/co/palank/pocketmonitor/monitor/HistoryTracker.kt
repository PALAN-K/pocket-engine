package kr.co.palank.pocketmonitor.monitor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HistoryEntry(
    val timestampMs: Long,
    val temperature: Float,
    val cpuUsage: Int,
    val ramUsagePercent: Int,
)

class HistoryTracker(private val deviceMonitor: DeviceMonitor) {

    private val entries = ArrayDeque<HistoryEntry>(MAX_ENTRIES)

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private var trackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        if (trackJob != null) return
        trackJob = scope.launch {
            while (isActive) {
                recordSnapshot()
                delay(RECORD_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        trackJob?.cancel()
        trackJob = null
    }

    private fun recordSnapshot() {
        val status = deviceMonitor.status.value
        val ramPercent = if (status.ramTotalGb > 0) {
            (status.ramUsedGb / status.ramTotalGb * 100).toInt()
        } else {
            0
        }
        val entry = HistoryEntry(
            timestampMs = System.currentTimeMillis(),
            temperature = status.temperature,
            cpuUsage = status.cpuUsage,
            ramUsagePercent = ramPercent,
        )
        synchronized(entries) {
            if (entries.size >= MAX_ENTRIES) {
                entries.removeFirst()
            }
            entries.add(entry)
            _history.value = entries.toList()
        }
    }

    companion object {
        private const val RECORD_INTERVAL_MS = 60_000L
        private const val MAX_ENTRIES = 1440
    }
}
