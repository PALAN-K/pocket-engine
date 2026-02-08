package kr.co.palank.pocketserver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kr.co.palank.pocketserver.MainActivity
import kr.co.palank.pocketserver.R
import kr.co.palank.pocketserver.linux.ServerState
import kr.co.palank.pocketserver.linux.SessionManager

class ServerForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        _instance = this
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = createNotification("서버 시작 중...", "PocketServer를 준비하고 있습니다.")
                startForeground(NOTIFICATION_ID, notification)

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
                startForeground(NOTIFICATION_ID, notification)

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
                    is ServerState.Running -> "서버 실행 중" to "SSH 포트 2022 | 정상 동작"
                    is ServerState.Stopping -> "서버 중지 중" to "서버를 안전하게 종료하고 있습니다"
                    is ServerState.Stopped -> "서버 중지됨" to "서버가 중지되었습니다"
                    is ServerState.Error -> "오류 발생" to state.message
                }
                updateNotification(title, content)
            }
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PocketServer::ServiceWakeLock"
        )
        wakeLock?.acquire()
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
        super.onTaskRemoved(rootIntent)
        // Layer 5: 최근 앱 목록에서 제거 시 서비스 재시작
        Log.i(TAG, "Task removed, service will restart via START_STICKY")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sessionManager?.stop()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        serviceScope.cancel()
        _instance = null
        Log.i(TAG, "Service destroyed")
    }

    companion object {
        private const val TAG = "ServerFGService"
        const val CHANNEL_ID = "PocketServerServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "kr.co.palank.pocketserver.ACTION_START"
        const val ACTION_STOP = "kr.co.palank.pocketserver.ACTION_STOP"
        const val ACTION_RESTART = "kr.co.palank.pocketserver.ACTION_RESTART"

        private var _instance: ServerForegroundService? = null
        var sessionManager: SessionManager? = null

        fun start(context: Context, sessionManager: SessionManager) {
            this.sessionManager = sessionManager
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
    }
}
