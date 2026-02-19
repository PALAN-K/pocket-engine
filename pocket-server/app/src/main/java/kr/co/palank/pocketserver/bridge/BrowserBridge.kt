package kr.co.palank.pocketserver.bridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.FileObserver
import android.util.Log
import java.io.File

/**
 * PRoot ↔ Android 브라우저 브리지.
 *
 * PRoot 내부에서 OAuth 로그인 등 브라우저가 필요한 경우:
 * 1. PRoot의 xdg-open 스크립트가 URL을 /support/open_url 파일에 기록
 * 2. BrowserBridge가 FileObserver(inotify)로 파일 변경 감지
 * 3. Android Intent로 Chrome/기본 브라우저를 열어 URL 로딩
 * 4. OAuth 콜백은 localhost:PORT로 리다이렉트 → PRoot의 서버가 수신
 *    (PRoot은 Android와 네트워크 네임스페이스를 공유하므로 localhost가 동일)
 */
class BrowserBridge(private val context: Context) {

    private var observer: FileObserver? = null
    private val rootfsPath get() = File(context.filesDir, "ubuntu").absolutePath
    private val supportDir get() = File(rootfsPath, "support")
    private val signalFile get() = File(supportDir, "open_url")

    fun start() {
        supportDir.mkdirs()

        // 기존 시그널 파일 정리
        if (signalFile.exists()) signalFile.delete()

        observer = object : FileObserver(supportDir.absolutePath, CLOSE_WRITE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == "open_url") {
                    handleSignalFile()
                }
            }
        }
        observer?.startWatching()
        Log.i(TAG, "BrowserBridge started, watching ${supportDir.absolutePath}")
    }

    fun stop() {
        observer?.stopWatching()
        observer = null
        Log.i(TAG, "BrowserBridge stopped")
    }

    private fun handleSignalFile() {
        try {
            if (!signalFile.exists()) return

            val url = signalFile.readText().trim()
            signalFile.delete()

            if (url.isEmpty()) return

            // 보안: https:// 또는 http://localhost만 허용
            if (!url.startsWith("https://") && !url.startsWith("http://localhost")) {
                Log.w(TAG, "Rejected URL (not https or localhost): $url")
                return
            }

            Log.i(TAG, "Opening browser: $url")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle open_url signal", e)
        }
    }

    companion object {
        private const val TAG = "BrowserBridge"
    }
}
