package kr.co.palank.pocketserver.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.*
import kr.co.palank.pocketserver.linux.ServerState
import kr.co.palank.pocketserver.util.BatteryMonitor
import java.util.concurrent.TimeUnit

class WatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val context = applicationContext
        val tempC = BatteryMonitor.getTemperature(context)
        Log.d(TAG, "Watchdog check: temp=${tempC}°C")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val shouldBeRunning = prefs.getBoolean(KEY_SERVER_SHOULD_RUN, false)

        // Temperature critical - stop server
        if (tempC >= 50f) {
            Log.w(TAG, "Temperature critical (${tempC}°C >= 50°C), ensuring server stopped")
            prefs.edit().putBoolean(KEY_THERMAL_STOPPED, true).apply()
            ServerForegroundService.stop(context)
            return Result.success()
        }

        // Recovery from thermal stop
        val wasThermalStopped = prefs.getBoolean(KEY_THERMAL_STOPPED, false)
        if (wasThermalStopped && tempC < 45f) {
            Log.i(TAG, "Temperature recovered (${tempC}°C < 45°C), clearing thermal stop flag")
            prefs.edit().putBoolean(KEY_THERMAL_STOPPED, false).apply()
            // Allow restart on next check or if shouldBeRunning
        }

        // Server should be running but isn't - restart
        if (shouldBeRunning && !wasThermalStopped) {
            val sessionManager = ServerForegroundService.sessionManager
            val currentState = sessionManager?.state?.value

            if (currentState !is ServerState.Running &&
                currentState !is ServerState.Starting &&
                currentState !is ServerState.Installing) {
                Log.i(TAG, "Server should be running but state=$currentState, restarting service")
                val intent = Intent(context, ServerForegroundService::class.java)
                ContextCompat.startForegroundService(context, intent)
            }
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "WatchdogWorker"
        private const val WORK_NAME = "PocketServerWatchdog"
        private const val PREFS_NAME = "server_prefs"
        private const val KEY_SERVER_SHOULD_RUN = "server_should_run"
        private const val KEY_THERMAL_STOPPED = "thermal_stopped"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WatchdogWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.i(TAG, "Watchdog scheduled (15 min interval)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Watchdog cancelled")
        }
    }
}
