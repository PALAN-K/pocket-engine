package kr.co.palank.pocketmonitor.notification

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

class WeeklyReportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val temp = BatteryMonitor.getTemperature(applicationContext)
            val level = BatteryMonitor.getBatteryLevel(applicationContext)
            showWeeklyReport(applicationContext, temp, level)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        } finally {
            scheduleNext(applicationContext)
        }
    }

    private fun showWeeklyReport(context: Context, temp: Float, battery: Int) {
        DailyReportWorker.run {
            // Reuse report channel from DailyReportWorker
        }
        val text = "현재 온도: ${temp.toInt()}°C | 배터리: ${battery}%"
        val notification = NotificationCompat.Builder(context, CHANNEL_REPORT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("PocketMonitor 주간 요약")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getLaunchPendingIntent(context))
            .setAutoCancel(true)
            .build()
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFY_WEEKLY, notification)
    }

    companion object {
        private const val WORK_NAME = "weekly_report"
        private const val CHANNEL_REPORT = "report"
        private const val NOTIFY_WEEKLY = 2002

        fun schedule(context: Context) {
            scheduleNext(context)
        }

        private fun scheduleNext(context: Context) {
            val delay = calculateDelayUntilNextSunday()
            val request = OneTimeWorkRequestBuilder<WeeklyReportWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        private fun calculateDelayUntilNextSunday(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                val daysUntilSunday = (Calendar.SUNDAY - get(Calendar.DAY_OF_WEEK) + 7) % 7
                if (daysUntilSunday == 0 && timeInMillis <= now.timeInMillis) {
                    add(Calendar.DAY_OF_YEAR, 7)
                } else {
                    add(Calendar.DAY_OF_YEAR, if (daysUntilSunday == 0) 7 else daysUntilSunday)
                }
            }
            return target.timeInMillis - now.timeInMillis
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
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
