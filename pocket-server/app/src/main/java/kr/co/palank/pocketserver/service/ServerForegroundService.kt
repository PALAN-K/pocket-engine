package kr.co.palank.pocketserver.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kr.co.palank.pocketserver.MainActivity
import kr.co.palank.pocketserver.R
import kr.co.palank.pocketserver.ipc.IpcServer
import kr.co.palank.pocketserver.linux.ServerState
import kr.co.palank.pocketserver.linux.SessionManager
import kr.co.palank.pocketserver.monitor.NetworkMonitor

class ServerForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var wifiLock: WifiManager.WifiLock
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ipcServer: IpcServer? = null

    override fun onCreate() {
        super.onCreate()
        _instance = this
        createNotificationChannel()
        acquireWakeLock()
        acquireWifiLock()

        // Ensure sessionManager and networkMonitor are available
        // (needed when started from BootReceiver without companion vars set)
        if (sessionManager == null) {
            sessionManager = SessionManager(this)
        }
        if (networkMonitor == null) {
            networkMonitor = NetworkMonitor(this).also { it.start() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = createNotification("서버 시작 중...", "PocketServer를 준비하고 있습니다.")
                startForegroundCompat(notification)
                startIpcServer()

                // Write WiFi IP file before PRoot starts so net-fix.js has it immediately
                networkMonitor?.writeCurrentIpFile()

                sessionManager?.let { manager ->
                    serviceScope.launch {
                        manager.start()
                    }
                    observeServerState(manager)
                }
            }
            ACTION_STOP -> {
                sessionManager?.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_RESTART -> {
                sessionManager?.restart()
            }
            else -> {
                val notification = createNotification("서버 실행 중", "PocketServer가 백그라운드에서 동작하고 있습니다.")
                startForegroundCompat(notification)
                startIpcServer()

                // Write WiFi IP file before PRoot starts so net-fix.js has it immediately
                networkMonitor?.writeCurrentIpFile()

                sessionManager?.let { manager ->
                    if (manager.isInstalled) {
                        serviceScope.launch { manager.start() }
                    }
                    observeServerState(manager)
                }
            }
        }
        return START_STICKY
    }

    private fun observeServerState(manager: SessionManager) {
        serviceScope.launch {
            manager.state.collect { state ->
                val (title, content) = when (state) {
                    is ServerState.Idle -> "서버 대기 중" to "시작 버튼을 눌러주세요"
                    is ServerState.Installing -> "서버 설치 중" to "잠시만 기다려주세요..."
                    is ServerState.Starting -> "서버 시작 중" to "SSH 서버를 준비하고 있습니다"
                    is ServerState.Running -> {
                        // SSH 서버가 올라온 후 설치된 서비스(PicoClaw 등) 자동 시작
                        autoStartInstalledServices()
                        "서버 실행 중" to "SSH 포트 2022 | 정상 동작"
                    }
                    is ServerState.Stopping -> "서버 중지 중" to "서버를 안전하게 종료하고 있습니다"
                    is ServerState.Stopped -> "서버 중지됨" to "서버가 중지되었습니다"
                    is ServerState.Error -> "오류 발생" to state.message
                }
                updateNotification(title, content)
            }
        }
    }

    private fun autoStartInstalledServices() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val sm = ServiceManager(this@ServerForegroundService)
                sm.startAllInstalled()
                Log.i(TAG, "Auto-started installed services")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to auto-start services", e)
            }
        }
    }

    private fun startIpcServer() {
        if (ipcServer != null) return
        val manager = sessionManager ?: return
        val network = networkMonitor ?: return
        ipcServer = IpcServer(this, manager, network).also {
            it.start(serviceScope)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PocketServer::ServiceWakeLock"
        )
        wakeLock?.acquire()
    }

    @Suppress("DEPRECATION")
    private fun acquireWifiLock() {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PocketServer::WifiLock")
        wifiLock.acquire()
    }

    private fun releaseWifiLock() {
        if (::wifiLock.isInitialized && wifiLock.isHeld) wifiLock.release()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(title: String, content: String) {
        val notification = createNotification(title, content)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(title: String, content: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ServerForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val restartIntent = PendingIntent.getService(
            this, 2,
            Intent(this, ServerForegroundService::class.java).apply { action = ACTION_RESTART },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, "중지", stopIntent)
            .addAction(0, "재시작", restartIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PocketServer 서버 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "PocketServer 백그라운드 서비스 알림"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Layer 5: 최근 앱 목록에서 제거 시 AlarmManager로 서비스 재시작
        Log.i(TAG, "Task removed, scheduling restart via AlarmManager")
        val restartIntent = Intent(this, ServerForegroundService::class.java).apply {
            setPackage(packageName)
        }
        val pi = PendingIntent.getService(
            this, RESTART_REQUEST_CODE, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + RESTART_DELAY_MS,
            pi
        )
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        ipcServer?.stop()
        ipcServer = null
        sessionManager?.stop()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        releaseWifiLock()
        serviceScope.cancel()
        _instance = null
        Log.i(TAG, "Service destroyed")
    }

    companion object {
        private const val TAG = "ServerFGService"
        const val CHANNEL_ID = "PocketServerServiceChannel"
        const val NOTIFICATION_ID = 1
        private const val RESTART_REQUEST_CODE = 99
        private const val RESTART_DELAY_MS = 3000L
        const val ACTION_START = "kr.co.palank.pocketserver.ACTION_START"
        const val ACTION_STOP = "kr.co.palank.pocketserver.ACTION_STOP"
        const val ACTION_RESTART = "kr.co.palank.pocketserver.ACTION_RESTART"

        private var _instance: ServerForegroundService? = null
        var sessionManager: SessionManager? = null
        var networkMonitor: NetworkMonitor? = null

        fun start(context: Context, sessionManager: SessionManager, networkMonitor: NetworkMonitor? = null) {
            this.sessionManager = sessionManager
            this.networkMonitor = networkMonitor
            val intent = Intent(context, ServerForegroundService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ServerForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        /** Layer 4: Request battery optimization exemption (shows system dialog) */
        fun checkAndRequestBatteryExemption(context: Context) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }

        /** Check if battery optimization is still active (true = optimized = bad for server) */
        fun isBatteryOptimized(context: Context): Boolean {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return !pm.isIgnoringBatteryOptimizations(context.packageName)
        }

        /** Check if Android 12+ phantom process killer is enabled (true = enabled = bad for server) */
        fun isPhantomProcessKillerEnabled(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < 31) return false
            val value = Settings.Global.getString(
                context.contentResolver,
                "settings_enable_monitor_phantom_procs"
            )
            return value != "false" // null (default) = enabled, "true" = enabled
        }
    }
}
