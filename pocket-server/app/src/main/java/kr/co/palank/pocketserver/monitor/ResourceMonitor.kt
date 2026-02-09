package kr.co.palank.pocketserver.monitor

import android.app.ActivityManager
import android.content.Context
import java.io.RandomAccessFile

object ResourceMonitor {
    fun getCpuUsage(): Int {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            var line = reader.readLine()
            val toks = line.split(" +".toRegex())
            val idle1 = toks[4].toLong()
            val cpu1 = toks[1].toLong() + toks[2].toLong() + toks[3].toLong() + toks[6].toLong() + toks[7].toLong() + toks[8].toLong()
            Thread.sleep(200)
            reader.seek(0)
            line = reader.readLine()
            reader.close()
            val toks2 = line.split(" +".toRegex())
            val idle2 = toks2[4].toLong()
            val cpu2 = toks2[1].toLong() + toks2[2].toLong() + toks2[3].toLong() + toks2[6].toLong() + toks2[7].toLong() + toks2[8].toLong()
            ((cpu2 - cpu1).toDouble() / ((cpu2 + idle2) - (cpu1 + idle1)) * 100).toInt()
        } catch (ex: Exception) { 0 }
    }

    fun getRamUsage(context: Context): Pair<Double, Double> {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val total = memInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
        val avail = memInfo.availMem.toDouble() / (1024 * 1024 * 1024)
        return Pair(total - avail, total)
    }
}
