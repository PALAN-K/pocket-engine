package kr.co.palank.pocketmonitor.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kr.co.palank.pocketmonitor.MainActivity
import kr.co.palank.pocketmonitor.R

object AlertNotifier {

    private const val CHANNEL_ALERT = "alert"
    private const val NOTIFY_TEMP_WARNING = 1001
    private const val NOTIFY_TEMP_CRITICAL = 1002
    private const val NOTIFY_SERVER_CRASH = 1003
    private const val NOTIFY_SERVER_RESTARTED = 1004

    fun showTemperatureWarning(context: Context, temp: Float) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("온도 주의")
            .setContentText("기기 온도가 ${temp.toInt()}°C 입니다. 주의가 필요합니다.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(getLaunchPendingIntent(context))
            .setAutoCancel(true)
            .build()
        getManager(context).notify(NOTIFY_TEMP_WARNING, notification)
    }

    fun showTemperatureCritical(context: Context, temp: Float) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("온도 위험")
            .setContentText("기기 온도 ${temp.toInt()}°C — 서버가 자동 정지되었습니다.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(getLaunchPendingIntent(context))
            .setAutoCancel(true)
            .build()
        getManager(context).notify(NOTIFY_TEMP_CRITICAL, notification)
    }

    fun showServerCrash(context: Context, message: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("서버 오류")
            .setContentText(message.ifEmpty { "서버가 비정상 종료되었습니다." })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(getLaunchPendingIntent(context))
            .setAutoCancel(true)
            .build()
        getManager(context).notify(NOTIFY_SERVER_CRASH, notification)
    }

    fun showServerRestarted(context: Context) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("서버 재시작됨")
            .setContentText("서버가 자동으로 재시작되었습니다.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getLaunchPendingIntent(context))
            .setAutoCancel(true)
            .build()
        getManager(context).notify(NOTIFY_SERVER_RESTARTED, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ALERT,
                "알림",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "온도 경고 및 서버 상태 알림"
            }
            getManager(context).createNotificationChannel(channel)
        }
    }

    private fun getManager(context: Context): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun getLaunchPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
