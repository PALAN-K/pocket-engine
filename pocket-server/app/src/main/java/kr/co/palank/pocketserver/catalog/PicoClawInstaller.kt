package kr.co.palank.pocketserver.catalog

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.co.palank.pocketserver.linux.ProotManager
import org.json.JSONObject
import java.io.File

class PicoClawInstaller(private val context: Context) : ServiceInstaller {

    private val prootManager = ProotManager(context)
    private val rootfsPath get() = File(context.filesDir, "ubuntu").absolutePath
    private val binaryPath = "/usr/local/bin/picoclaw"
    private val configDir = "/root/.picoclaw"
    private val configPath = "$configDir/config.json"
    private val pidFile = "/tmp/picoclaw.pid"

    override suspend fun install(onProgress: (Int, String) -> Unit) = withContext(Dispatchers.IO) {
        onProgress(10, "PicoClaw 다운로드 중...")

        val result = prootManager.exec(
            "/bin/bash", "-c",
            "curl -fsSL https://github.com/ArcadeLabsInc/picoclaw/releases/latest/download/picoclaw-linux-arm64 -o $binaryPath && chmod +x $binaryPath"
        )

        if (!result.isSuccess) {
            throw RuntimeException("PicoClaw 다운로드 실패: ${result.output.takeLast(200)}")
        }

        onProgress(80, "설정 디렉토리 생성 중...")
        prootManager.exec("/bin/bash", "-c", "mkdir -p $configDir")

        onProgress(100, "PicoClaw 설치 완료")
        Log.i(TAG, "PicoClaw installed successfully")
        Unit
    }

    override suspend fun configure(inputs: Map<String, String>) = withContext(Dispatchers.IO) {
        val apiKey = inputs["gemini_api_key"] ?: throw IllegalArgumentException("Gemini API Key required")
        val telegramToken = inputs["telegram_token"] ?: throw IllegalArgumentException("Telegram Bot Token required")

        val config = JSONObject().apply {
            put("llm", JSONObject().apply {
                put("provider", "gemini")
                put("api_key", apiKey)
                put("model", "gemini-2.0-flash")
            })
            put("channels", JSONObject().apply {
                put("telegram", JSONObject().apply {
                    put("enabled", true)
                    put("token", telegramToken)
                })
            })
        }

        val configFile = File(rootfsPath, "root/.picoclaw/config.json")
        configFile.parentFile?.mkdirs()
        configFile.writeText(config.toString(2))

        Log.i(TAG, "PicoClaw configured")
        Unit
    }

    override suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        val result = prootManager.exec(
            "/bin/bash", "-c",
            "nohup picoclaw gateway > /tmp/picoclaw.log 2>&1 & echo \$! > $pidFile"
        )
        Log.i(TAG, "PicoClaw start: code=${result.exitCode}")
        result.isSuccess
    }

    override suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        val result = prootManager.exec(
            "/bin/bash", "-c",
            "if [ -f $pidFile ]; then kill \$(cat $pidFile) 2>/dev/null; rm -f $pidFile; fi"
        )
        Log.i(TAG, "PicoClaw stop: code=${result.exitCode}")
        true
    }

    override suspend fun isRunning(): Boolean = withContext(Dispatchers.IO) {
        val result = prootManager.exec(
            "/bin/bash", "-c",
            "[ -f $pidFile ] && kill -0 \$(cat $pidFile) 2>/dev/null && echo running || echo stopped"
        )
        result.output.trim() == "running"
    }

    override fun isInstalled(): Boolean {
        return File(rootfsPath, "usr/local/bin/picoclaw").exists()
    }

    companion object {
        private const val TAG = "PicoClawInstaller"
    }
}
