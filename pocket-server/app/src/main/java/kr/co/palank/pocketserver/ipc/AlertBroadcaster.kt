package kr.co.palank.pocketserver.ipc

import android.content.Context
import android.content.Intent
import android.util.Log

object AlertBroadcaster {

    private const val TAG = "AlertBroadcaster"
    const val ACTION_ALERT = "kr.co.palank.pocketserver.ALERT"
    private const val MONITOR_PACKAGE = "kr.co.palank.pocketmonitor"

    const val TYPE_TEMP_WARNING = "temp_warning"
    const val TYPE_TEMP_CRITICAL = "temp_critical"
    const val TYPE_SERVER_CRASH = "server_crash"
    const val TYPE_SERVER_RESTARTED = "server_restarted"

    fun sendTempWarning(context: Context, temperature: Float) {
        sendAlert(context, TYPE_TEMP_WARNING, temperature, "온도 경고: ${temperature}\u00B0C")
    }

    fun sendTempCritical(context: Context, temperature: Float) {
        sendAlert(context, TYPE_TEMP_CRITICAL, temperature, "서버가 ${temperature}\u00B0C로 자동 정지되었습니다")
    }

    fun sendServerCrash(context: Context, message: String) {
        val intent = Intent(ACTION_ALERT).apply {
            setPackage(MONITOR_PACKAGE)
            putExtra("type", TYPE_SERVER_CRASH)
            putExtra("message", message)
        }
        sendBroadcastSafe(context, intent)
    }

    fun sendServerRestarted(context: Context) {
        val intent = Intent(ACTION_ALERT).apply {
            setPackage(MONITOR_PACKAGE)
            putExtra("type", TYPE_SERVER_RESTARTED)
            putExtra("message", "서버가 자동으로 재시작되었습니다")
        }
        sendBroadcastSafe(context, intent)
    }

    private fun sendAlert(context: Context, type: String, temperature: Float, message: String) {
        val intent = Intent(ACTION_ALERT).apply {
            setPackage(MONITOR_PACKAGE)
            putExtra("type", type)
            putExtra("temp", temperature)
            putExtra("message", message)
        }
        sendBroadcastSafe(context, intent)
    }

    private fun sendBroadcastSafe(context: Context, intent: Intent) {
        try {
            context.sendBroadcast(intent)
            Log.d(TAG, "Alert broadcast sent: ${intent.getStringExtra("type")}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send alert broadcast", e)
        }
    }
}
