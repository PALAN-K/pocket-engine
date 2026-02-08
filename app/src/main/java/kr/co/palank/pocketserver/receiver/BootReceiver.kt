package kr.co.palank.pocketserver.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kr.co.palank.pocketserver.service.ServerForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, ServerForegroundService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
