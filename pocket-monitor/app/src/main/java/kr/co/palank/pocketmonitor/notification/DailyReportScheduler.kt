package kr.co.palank.pocketmonitor.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kr.co.palank.pocketmonitor.MainActivity
import kr.co.palank.pocketmonitor.R
import kr.co.palank.pocketmonitor.util.BatteryMonitor
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DailyReportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val temp = BatteryMonitor.getTemperature(applicationContext)
            val level = BatteryMonitor.getBatteryLevel(applicationContext)
            showDailyReport(applicationContext, temp, level)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        } finally {
            scheduleNext(applicationContext)
        }
    }

    private fun showDailyReport(context: Context, temp: Float, battery: Int) {
        ensureChannel(context)
        val text = "온도: ${temp.toInt()}°C | 배터리: ${battery}%"
        val notification = NotificationCompat.Builder(context, CHANNEL_REPORT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("PocketMonitor 일일 리포트")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getLaunchPendingIntent(context))
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFY_DAILY, notification)
    }

    companion object {
        private const val WORK_NAME = "daily_report"
        private const val CHANNEL_REPORT = "report"
        private const val NOTIFY_DAILY = 2001
        private const val TARGET_HOUR = 9
        private const val TARGET_MINUTE = 0

        fun schedule(context: Context) {
            scheduleNext(context)
        }

        private fun scheduleNext(context: Context) {
            val delay = calculateDelay(TARGET_HOUR, TARGET_MINUTE)
            val request = OneTimeWorkRequestBuilder<DailyReportWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        internal fun calculateDelay(hour: Int, minute: Int): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_REPORT,
                    "리포트",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "일일 및 주간 서버 리포트"
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
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
}
