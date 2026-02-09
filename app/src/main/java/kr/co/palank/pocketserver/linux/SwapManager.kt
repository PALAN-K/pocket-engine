package kr.co.palank.pocketserver.linux

import android.app.ActivityManager
import android.content.Context
import android.util.Log

class SwapManager(
    private val context: Context,
    private val prootManager: ProotManager
) {

    suspend fun setup(): Boolean {
        Log.i(TAG, "Setting up swap memory...")

        val swapSizeMB = SWAP_SIZE_MB

        val commands = listOf(
            "dd if=/dev/zero of=/swapfile bs=1M count=$swapSizeMB",
            "chmod 600 /swapfile",
            "mkswap /swapfile",
            "swapon /swapfile",
            "grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab"
        )

        for (cmd in commands) {
            Log.d(TAG, "Executing: $cmd")
            val result = prootManager.exec("/bin/sh", "-c", cmd)
            if (!result.isSuccess) {
                Log.e(TAG, "Swap setup failed at: $cmd (exit=${result.exitCode})")
                Log.e(TAG, "Output: ${result.output}")
                return false
            }
        }

        Log.i(TAG, "Swap memory setup complete (${swapSizeMB}MB)")
        return true
    }

    fun getDeviceRamMB(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / (1024 * 1024)
    }

    companion object {
        private const val TAG = "SwapManager"
        private const val SWAP_SIZE_MB = 2048 // Fixed 2GB, diminishing returns beyond this
    }
}
