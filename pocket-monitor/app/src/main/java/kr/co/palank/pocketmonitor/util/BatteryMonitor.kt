package kr.co.palank.pocketmonitor.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object BatteryMonitor {

    const val TEMP_NORMAL = 40f
    const val TEMP_WARNING = 45f
    const val TEMP_CRITICAL = 50f

    fun getTemperature(context: Context): Float {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    }

    fun getBatteryLevel(context: Context): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 0
    }

    fun getTemperatureLevel(temp: Float): TemperatureLevel {
        return when {
            temp >= TEMP_CRITICAL -> TemperatureLevel.CRITICAL
            temp >= TEMP_WARNING -> TemperatureLevel.WARNING
            else -> TemperatureLevel.NORMAL
        }
    }

    enum class TemperatureLevel {
        NORMAL,
        WARNING,
        CRITICAL,
    }
}
