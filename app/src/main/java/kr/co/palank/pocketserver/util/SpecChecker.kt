package kr.co.palank.pocketserver.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs

data class DeviceSpec(
    val androidVersion: String,
    val isVersionOk: Boolean,
    val ramSizeGb: Double,
    val isRamOk: Boolean,
    val storageFreeGb: Long,
    val isStorageOk: Boolean,
    val arch: String,
    val isArchOk: Boolean
)

object SpecChecker {
    fun check(context: Context): DeviceSpec {
        val sdkInt = Build.VERSION.SDK_INT
        val isVersionOk = sdkInt >= 26

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
        val isRamOk = totalRamGb >= 2.8

        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val freeGb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024 * 1024)
        val isStorageOk = freeGb >= 8

        val abis = Build.SUPPORTED_ABIS
        val isArchOk = abis.contains("arm64-v8a")
        val archName = if (isArchOk) "ARM64" else "Unsupported"

        return DeviceSpec(
            androidVersion = "Android ${Build.VERSION.RELEASE}",
            isVersionOk = isVersionOk,
            ramSizeGb = totalRamGb,
            isRamOk = isRamOk,
            storageFreeGb = freeGb,
            isStorageOk = isStorageOk,
            arch = archName,
            isArchOk = isArchOk
        )
    }
}
