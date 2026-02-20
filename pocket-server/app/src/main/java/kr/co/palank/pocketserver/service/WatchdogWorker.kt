package kr.co.palank.pocketserver.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.runBlocking
import kr.co.palank.pocketserver.catalog.ServiceCatalog
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

            // 서버가 실행 중일 때만 서비스 크래시 복구 수행
            if (currentState is ServerState.Running) {
                checkAndRecoverServices(context, prefs)
            }
        }

        return Result.success()
    }

    /**
     * 설치된 서비스(PicoClaw/OpenClaw)가 크래시되었는지 확인하고 자동 복구.
     * 최대 MAX_AUTO_RESTART_COUNT회까지 자동 재시작, 초과 시 포기.
     */
    private fun checkAndRecoverServices(context: Context, prefs: android.content.SharedPreferences) {
        try {
            for (def in ServiceCatalog.services) {
                val installer = def.installer(context)
                if (!installer.isInstalled()) continue

                val healthy = runBlocking { installer.isHealthy() }
                if (healthy) {
                    // 정상 동작 중 → 재시작 카운터 초기화
                    prefs.edit().putInt("${KEY_SERVICE_RESTART_COUNT}_${def.id}", 0).apply()
                    continue
                }

                // 비정상: 프로세스 크래시 또는 좀비 (프로세스는 살아있지만 서비스 응답 없음)
                val running = runBlocking { installer.isRunning() }
                val restartCount = prefs.getInt("${KEY_SERVICE_RESTART_COUNT}_${def.id}", 0)
                if (restartCount >= MAX_AUTO_RESTART_COUNT) {
                    Log.w(TAG, "${def.name} failed ${restartCount}+ times, skipping auto-restart")
                    continue
                }

                if (running) {
                    // 좀비 감지: 프로세스는 살아있지만 서비스가 응답하지 않음 → 강제 종료 후 재시작
                    Log.w(TAG, "${def.name} zombie detected (process alive but unhealthy), killing before restart")
                    runBlocking { installer.stop() }
                } else {
                    Log.i(TAG, "${def.name} not running, preparing auto-restart")
                }

                Log.i(TAG, "${def.name} auto-restarting (attempt ${restartCount + 1}/$MAX_AUTO_RESTART_COUNT)")
                val started = runBlocking { installer.start() }
                prefs.edit().putInt(
                    "${KEY_SERVICE_RESTART_COUNT}_${def.id}",
                    if (started) 0 else restartCount + 1
                ).apply()

                if (started) {
                    Log.i(TAG, "${def.name} auto-restarted successfully")
                } else {
                    Log.e(TAG, "${def.name} auto-restart failed (attempt ${restartCount + 1})")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Service recovery check failed", e)
        }
    }

    companion object {
        private const val TAG = "WatchdogWorker"
        private const val WORK_NAME = "PocketServerWatchdog"
        private const val PREFS_NAME = "server_prefs"
        private const val KEY_SERVER_SHOULD_RUN = "server_should_run"
        private const val KEY_THERMAL_STOPPED = "thermal_stopped"
        private const val KEY_SERVICE_RESTART_COUNT = "service_restart_count"
        private const val MAX_AUTO_RESTART_COUNT = 3

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
