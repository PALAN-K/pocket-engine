package kr.co.palank.pocketmonitor.ipc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kr.co.palank.pocketmonitor.notification.AlertNotifier

class AlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALERT) return

        val type = intent.getStringExtra("type") ?: return
        val temp = intent.getFloatExtra("temp", 0f)
        val message = intent.getStringExtra("message") ?: ""

        Log.i(TAG, "Alert received: type=$type, temp=$temp, message=$message")

        when (type) {
            "temp_warning" -> {
                AlertNotifier.showTemperatureWarning(context, temp)
            }
            "temp_critical" -> {
                AlertNotifier.showTemperatureCritical(context, temp)
            }
            "server_crash" -> {
                AlertNotifier.showServerCrash(context, message)
            }
            "server_restarted" -> {
                AlertNotifier.showServerRestarted(context)
            }
        }
    }

    companion object {
        private const val TAG = "AlertReceiver"
        private const val ACTION_ALERT = "kr.co.palank.pocketserver.ALERT"
    }
}
