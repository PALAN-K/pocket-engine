# Android 7-Layer Background Keep-Alive System

Technical reference for maintaining a persistent Linux server (PRoot) on Android 8.0+ (API 26+).
Android 14 (API 34) specific requirements noted where applicable.

## Table of Contents
1. [Layer 1: Foreground Service](#layer-1-foreground-service)
2. [Layer 2: Partial Wake Lock](#layer-2-partial-wake-lock)
3. [Layer 3: WiFi Lock](#layer-3-wifi-lock)
4. [Layer 4: Battery Optimization Exemption](#layer-4-battery-optimization-exemption)
5. [Layer 5: Auto-restart (START_STICKY + onTaskRemoved)](#layer-5-auto-restart)
6. [Layer 6: Boot Auto-start](#layer-6-boot-auto-start)
7. [Layer 7: Manufacturer Deep Links](#layer-7-manufacturer-deep-links)
8. [Complete Manifest Snippet](#complete-manifest-snippet)

---
## Layer 1: Foreground Service

A foreground service with a persistent notification is the foundation. Without it Android kills
background processes within minutes. Android 14+ requires `foregroundServiceType`. Use
`specialUse` since no standard type (camera, location, etc.) fits a server app. The `specialUse`
type requires a `<property>` element explaining the use case -- Google Play review reads this.

**Permissions:** `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`

```kotlin
class ServerService : Service() {
    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "server_channel"
    }
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PocketServer Running")
            .setContentText("Linux server is active")
            .setSmallIcon(R.drawable.ic_server)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Server Status",
            NotificationManager.IMPORTANCE_LOW) // LOW = no sound, still visible
        channel.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
```
> Ref: https://developer.android.com/develop/background-work/services/fgs/service-types

---
## Layer 2: Partial Wake Lock

Prevents the CPU from sleeping when the screen is off. Without this, PRoot processes freeze.

**Permission:** `android.permission.WAKE_LOCK`

```kotlin
private lateinit var wakeLock: PowerManager.WakeLock

@SuppressLint("WakelockTimeout")
private fun acquireWakeLock() {
    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketServer::ServerWakeLock")
    wakeLock.acquire() // No timeout -- must hold indefinitely for server use
}
private fun releaseWakeLock() {
    if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
}
```

`@SuppressLint("WakelockTimeout")` is intentional -- a server process must not sleep.

> Pattern: Termux `TermuxService.java` holds PARTIAL_WAKE_LOCK for its entire lifetime.
> https://github.com/termux/termux-app (com.termux.app.TermuxService)
> Ref: https://developer.android.com/reference/android/os/PowerManager

---
## Layer 3: WiFi Lock

WiFi radio enters low-power mode or disconnects when the screen is off. A server needs
continuous network access.

```kotlin
private lateinit var wifiLock: WifiManager.WifiLock

@Suppress("DEPRECATION")
private fun acquireWifiLock() {
    val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PocketServer::WifiLock")
    wifiLock.acquire()
}
private fun releaseWifiLock() {
    if (::wifiLock.isInitialized && wifiLock.isHeld) wifiLock.release()
}
```

`WIFI_MODE_FULL_HIGH_PERF` keeps WiFi at full bandwidth with screen off. Deprecated in API 34+
(replaced by `WIFI_MODE_FULL_LOW_LATENCY`) but still functions on older devices.

> Pattern: Termux `TermuxService.java` acquires WiFi lock alongside the wake lock.

---
## Layer 4: Battery Optimization Exemption

Doze mode defers jobs, alarms, and network access. A server app must be exempted.

**Permission:** `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`

```kotlin
fun checkAndRequestBatteryExemption(activity: Activity) {
    val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (!pm.isIgnoringBatteryOptimizations(activity.packageName)) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        activity.startActivity(intent)
    }
}
fun isBatteryOptimized(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return !pm.isIgnoringBatteryOptimizations(context.packageName)
}
```

**Play policy:** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is allowed for apps whose core function
requires persistent operation (VoIP, server hosting). Justify in Play Console listing.

> Ref: https://developer.android.com/training/monitoring-device-state/doze-standby

---
## Layer 5: Auto-restart

**START_STICKY:** System recreates the service after killing it, with a null intent.

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent == null) {
        restoreServerState() // System restart -- use persisted config
    } else {
        handleStartIntent(intent) // Normal start -- read intent extras
    }
    return START_STICKY
}
```

**onTaskRemoved fallback:** Some OEMs ignore START_STICKY after swipe-dismiss. Use AlarmManager.

```kotlin
override fun onTaskRemoved(rootIntent: Intent?) {
    val restartIntent = Intent(this, ServerService::class.java).apply {
        setPackage(packageName)
    }
    val pi = PendingIntent.getService(this, 1, restartIntent,
        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
    val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
        SystemClock.elapsedRealtime() + 3000, pi)
    super.onTaskRemoved(rootIntent)
}
```

---
## Layer 6: Boot Auto-start

**Permission:** `android.permission.RECEIVE_BOOT_COMPLETED`

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = context.getSharedPreferences("server_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("auto_start_on_boot", true)) return
            val serviceIntent = Intent(context, ServerService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
```

`ContextCompat.startForegroundService()` calls `startForegroundService()` on API 26+ and
`startService()` on older APIs. The service has ~5 seconds to call `startForeground()`.
`QUICKBOOT_POWERON` covers HTC and other OEMs that use fast reboot.

---
## Layer 7: Manufacturer Deep Links

Samsung, Xiaomi, Huawei, OPPO, Vivo, and OnePlus have proprietary battery management that
kills apps regardless of standard APIs. Users must manually whitelist the app.

**See:** `manufacturer-deeplinks.md` in this directory for programmatic Intent deep links.

> Ref: https://dontkillmyapp.com

---
## Complete Manifest Snippet

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Layer 1: Foreground Service -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <!-- Layer 2: Wake Lock -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <!-- Layer 4: Battery Optimization -->
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <!-- Layer 6: Boot -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <!-- Notifications (Android 13+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application ...>
        <service
            android:name=".ServerService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Runs a Linux server via PRoot that must remain active
                    to serve network requests continuously." />
        </service>
        <receiver
            android:name=".BootReceiver"
            android:enabled="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.QUICKBOOT_POWERON" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

---
## Official References
- Foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types
- PowerManager: https://developer.android.com/reference/android/os/PowerManager
- Doze and Standby: https://developer.android.com/training/monitoring-device-state/doze-standby
- Termux source (proven pattern): https://github.com/termux/termux-app
- OEM kill behavior database: https://dontkillmyapp.com
