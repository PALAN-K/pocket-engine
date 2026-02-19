package kr.co.palank.pocketserver.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val changelog: String,
    val hasUpdate: Boolean,
)

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val VERSION_URL = "https://pocket-server-palank.web.app/version.json"
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 5000

    suspend fun check(context: Context): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(VERSION_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.requestMethod = "GET"

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "Version check failed: HTTP $responseCode")
                    return@withContext null
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val json = JSONObject(body)
                val latestVersion = json.getString("version")
                val downloadUrl = json.optString("url", "")
                val changelog = json.optString("changelog", "")

                val currentVersion = getCurrentVersion(context)
                val hasUpdate = isNewerVersion(latestVersion, currentVersion)

                UpdateInfo(
                    latestVersion = latestVersion,
                    downloadUrl = if (downloadUrl.startsWith("http")) downloadUrl
                    else "https://pocket-server-palank.web.app/$downloadUrl",
                    changelog = changelog,
                    hasUpdate = hasUpdate,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
                null
            }
        }
    }

    private fun getCurrentVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = local.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
