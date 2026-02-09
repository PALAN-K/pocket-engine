# Android Battery & Temperature Monitoring Reference

> PocketServer context: old phones running a Linux server 24/7 via PRoot.

## Table of Contents
1. [BatteryManager API](#batterymanager-api)
2. [Temperature Monitoring Strategy](#temperature-monitoring-strategy)
3. [Android Thermal API (Android 10+)](#android-thermal-api-android-10)
4. [Swap Memory Management](#swap-memory-management)
5. [Resource Monitoring](#resource-monitoring)
6. [Safety Recommendations Display](#safety-recommendations-display)
7. [Limitations (PRoot Environment)](#limitations-proot-environment)

---

## BatteryManager API

Android `BatteryManager` provides **read-only** battery info. There is no API to control charging.

**Key extras from `Intent.ACTION_BATTERY_CHANGED`:**

| Extra                | Type | Description                                      |
|----------------------|------|--------------------------------------------------|
| `EXTRA_TEMPERATURE`  | Int  | Tenths of degrees Celsius (350 = 35.0C)         |
| `EXTRA_LEVEL`        | Int  | Current charge level                             |
| `EXTRA_SCALE`        | Int  | Max charge level (percentage = level/scale*100)  |
| `EXTRA_STATUS`       | Int  | CHARGING / DISCHARGING / FULL / NOT_CHARGING     |
| `EXTRA_HEALTH`       | Int  | GOOD / OVERHEAT / DEAD / OVER_VOLTAGE            |
| `EXTRA_PLUGGED`      | Int  | AC / USB / WIRELESS                              |
| `EXTRA_VOLTAGE`      | Int  | Current voltage in millivolts                    |

```kotlin
class BatteryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
        val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val tempC = tempRaw / 10f
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = level * 100 / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        // Forward to ThermalMonitor / UI
    }
}

// Register (sticky broadcast -- returns last value immediately)
val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
val sticky: Intent? = context.registerReceiver(null, filter)
```

Ref: https://developer.android.com/reference/android/os/BatteryManager

---

## Temperature Monitoring Strategy

Poll every **30 seconds** via `Handler` or coroutine. Thresholds:

| Range        | Level    | Color  | Action                                        |
|--------------|----------|--------|-----------------------------------------------|
| <= 40C       | NORMAL   | Green  | No action                                     |
| 41-45C       | WARNING  | Yellow | Show notification                             |
| 46-50C       | DANGER   | Orange | Persistent warning notification               |
| > 50C        | CRITICAL | Red    | Auto-stop Linux server service, show alert    |
| < 45C (post) | RECOVERY | Green  | Auto-restart service after CRITICAL cooldown   |

```kotlin
enum class ThermalLevel { NORMAL, WARNING, DANGER, CRITICAL }

class ThermalMonitor(private val context: Context) {
    private val _state = MutableStateFlow(ThermalLevel.NORMAL)
    val state: StateFlow<ThermalLevel> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var wasCritical = false

    fun start() { handler.post(pollRunnable) }
    fun stop() { handler.removeCallbacks(pollRunnable) }

    private val pollRunnable = object : Runnable {
        override fun run() {
            val tempC = readBatteryTempC()
            val level = when {
                tempC > 50f -> ThermalLevel.CRITICAL
                tempC > 45f -> ThermalLevel.DANGER
                tempC > 40f -> ThermalLevel.WARNING
                else        -> ThermalLevel.NORMAL
            }
            _state.value = level
            when (level) {
                ThermalLevel.CRITICAL -> { wasCritical = true; stopLinuxService() }
                ThermalLevel.NORMAL, ThermalLevel.WARNING -> {
                    if (wasCritical && tempC < 45f) { wasCritical = false; restartLinuxService() }
                }
                else -> {}
            }
            handler.postDelayed(this, 30_000L)
        }
    }

    private fun readBatteryTempC(): Float {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    }
}
```

---

## Android Thermal API (Android 10+)

Supplementary signal alongside battery temperature.

**`PowerManager.getThermalHeadroom(forecastSeconds)`** -- predicts thermal headroom (0.0 to 1.0+; >= 1.0 means throttling imminent).

**`PowerManager.addThermalStatusListener()`** -- callback with status levels:
- `THERMAL_STATUS_NONE` / `LIGHT` / `MODERATE` / `SEVERE` / `CRITICAL` / `EMERGENCY` / `SHUTDOWN`

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    val pm = context.getSystemService(PowerManager::class.java)
    pm.addThermalStatusListener { status ->
        if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
            // Treat as equivalent to DANGER/CRITICAL from battery temp
        }
    }
    val headroom = pm.getThermalHeadroom(30) // 30-second forecast
}
```

Ref: https://developer.android.com/games/optimize/adpf/thermal

---

## Swap Memory Management

Create swap inside the PRoot Linux filesystem. Execute via PRoot shell commands from Kotlin.

```kotlin
fun setupSwap(prootSession: ProotSession) {
    val ramMB = getDeviceRamMB()
    val swapMB = if (ramMB <= 3072) 2048 else 2048 // diminishing returns beyond 2GB
    val commands = listOf(
        "dd if=/dev/zero of=/swapfile bs=1M count=$swapMB",
        "chmod 600 /swapfile",
        "mkswap /swapfile",
        "swapon /swapfile",
        "grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab"
    )
    commands.forEach { prootSession.exec(it) }
}

fun getDeviceRamMB(): Long {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    return info.totalMem / (1024 * 1024)
}
```

- RAM <= 3GB: 2GB swap.
- RAM 4GB+: 2GB swap (diminishing returns beyond this).

---

## Resource Monitoring

**CPU usage** -- read `/proc/stat` (works inside PRoot):
```kotlin
fun getCpuUsage(): Float {
    val lines = File("/proc/stat").readLines()
    val parts = lines[0].split("\\s+".toRegex()).drop(1).map { it.toLong() }
    val idle = parts[3]; val total = parts.sum()
    // Compare two readings 1s apart for delta percentage
    return ((total - idle).toFloat() / total) * 100f
}
```

**Memory usage:**
```kotlin
fun getMemoryInfo(context: Context): Pair<Long, Long> { // (used, total) in MB
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    val totalMB = info.totalMem / (1024 * 1024)
    val availMB = info.availMem / (1024 * 1024)
    return Pair(totalMB - availMB, totalMB)
}
```

**Disk usage:**
```kotlin
fun getDiskInfo(path: File): Pair<Long, Long> { // (used, total) in MB
    val stat = StatFs(path.absolutePath)
    val totalMB = stat.totalBytes / (1024 * 1024)
    val freeMB = stat.availableBytes / (1024 * 1024)
    return Pair(totalMB - freeMB, totalMB)
}
```

---

## Safety Recommendations Display

Show on first install completion as a non-dismissable dialog or onboarding screen. These are **UI text strings only** -- no programmatic control over charging or hardware.

1. **Smart plug**: Use a smart plug with scheduling/automation to cut power at 80% and resume at 20%.
2. **Remove phone case**: Cases trap heat; bare phone dissipates better.
3. **Placement surface**: Metal or tile surface acts as a passive heatsink. Avoid wood/fabric.
4. **Ventilation**: Do not enclose the phone. Small USB fan optional for high-load servers.
5. **Monthly battery check**: Wobble test -- place phone on flat surface, press each corner. If it rocks, battery is swelling; retire the device immediately.

---

## Limitations (PRoot Environment)

| What you CANNOT do (needs real root) | What you CAN do (no root)           |
|--------------------------------------|--------------------------------------|
| Write to sysfs charging control      | Read battery status and temperature  |
| Control CPU governor / frequency     | Monitor CPU/memory/disk usage        |
| Disable charging via kernel          | Stop/restart the Linux service       |
| Access thermal zone sysfs nodes      | Use BatteryManager + Thermal API     |

**App-level response is limited to: monitor, warn, stop/restart the Linux server service.**
