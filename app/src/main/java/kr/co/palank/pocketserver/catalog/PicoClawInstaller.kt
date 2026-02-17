package kr.co.palank.pocketserver.catalog

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kr.co.palank.pocketserver.linux.ProotManager
import org.json.JSONObject
import java.io.File

class PicoClawInstaller(private val context: Context) : ServiceInstaller {

    private val prootManager = ProotManager(context)
    private val rootfsPath get() = File(context.filesDir, "ubuntu").absolutePath
    private val binaryPath = "/usr/local/bin/picoclaw"
    private val configDir = "/root/.picoclaw"

    override suspend fun install(onProgress: (Int, String) -> Unit) = withContext(Dispatchers.IO) {
        onProgress(10, "PicoClaw 다운로드 중...")

        val result = prootManager.exec(
            "/bin/bash", "-c",
            "curl -fsSL -L https://github.com/sipeed/picoclaw/releases/latest/download/picoclaw-linux-arm64 -o $binaryPath && chmod +x $binaryPath"
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
            put("agents", JSONObject().apply {
                put("defaults", JSONObject().apply {
                    put("model", "gemini-2.5-flash")
                    put("max_tokens", 8192)
                    put("temperature", 0.7)
                })
            })
            put("providers", JSONObject().apply {
                put("gemini", JSONObject().apply {
                    put("api_key", apiKey)
                    put("models", org.json.JSONArray().apply {
                        put("gemini-2.5-flash")
                        put("gemma-3-27b-it")
                    })
                })
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

    /**
     * Dropbear 패턴: killOnExit=false로 장기 실행 PRoot 세션을 생성하여
     * PicoClaw를 포그라운드 프로세스로 실행. exec()의 --kill-on-exit가
     * nohup 프로세스까지 죽이는 문제를 해결.
     */
    override suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        // 기존 프로세스 정리
        picoClawProcess?.let { proc ->
            proc.destroy()
            if (proc.isAlive) proc.destroyForcibly()
        }
        picoClawProcess = null
        prootManager.exec(
            "/bin/bash", "-c",
            "pkill -f 'picoclaw gateway' 2>/dev/null; sleep 1"
        )

        // config 존재 확인
        val configExists = File(rootfsPath, "root/.picoclaw/config.json").exists()
        if (!configExists) {
            Log.w(TAG, "PicoClaw config not found, skipping start")
            return@withContext false
        }

        // killOnExit=false: PRoot 세션이 PicoClaw와 함께 유지됨
        val cmd = prootManager.buildEnvWrappedCommand(
            "/bin/bash", "-c",
            "exec picoclaw gateway",
            killOnExit = false
        )

        val logFile = File(rootfsPath, "tmp/picoclaw.log")
        logFile.parentFile?.mkdirs()

        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.to(logFile))

        picoClawProcess = pb.start()

        // 시작 검증: 최대 10초 polling (1초 간격)
        var alive = false
        for (i in 1..10) {
            delay(1000)
            if (picoClawProcess?.isAlive != true) {
                val lastLines = try {
                    logFile.readLines().takeLast(5).joinToString("\n")
                } catch (_: Exception) { "로그 읽기 실패" }
                Log.e(TAG, "PicoClaw died after ${i}s. Last log:\n$lastLines")
                return@withContext false
            }
            if (i >= 3) {
                alive = true
                break
            }
        }
        Log.i(TAG, "PicoClaw start: alive=$alive")
        alive
    }

    override suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        picoClawProcess?.let { proc ->
            proc.destroy()
            if (proc.isAlive) proc.destroyForcibly()
        }
        picoClawProcess = null
        // Fallback: PRoot 내 잔여 프로세스 정리
        prootManager.exec(
            "/bin/bash", "-c",
            "pkill -f 'picoclaw gateway' 2>/dev/null"
        )
        Log.i(TAG, "PicoClaw stopped")
        true
    }

    override suspend fun isRunning(): Boolean = withContext(Dispatchers.IO) {
        if (picoClawProcess?.isAlive == true) return@withContext true
        // Fallback: PRoot 내부에서 확인
        val result = prootManager.exec(
            "/bin/bash", "-c",
            "pgrep -f 'picoclaw gateway' >/dev/null 2>&1 && echo running || echo stopped"
        )
        result.output.trim() == "running"
    }

    override fun isInstalled(): Boolean {
        return File(rootfsPath, "usr/local/bin/picoclaw").exists()
    }

    companion object {
        private const val TAG = "PicoClawInstaller"
        @Volatile
        private var picoClawProcess: Process? = null
    }
}
