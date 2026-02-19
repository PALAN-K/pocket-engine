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

        // GitHub Releases에서 tar.gz 아카이브 다운로드
        val downloadResult = prootManager.exec(
            "/bin/bash", "-c",
            "curl -fsSL -L '$DOWNLOAD_URL' -o /tmp/picoclaw.tar.gz"
        )
        if (!downloadResult.isSuccess) {
            throw RuntimeException("PicoClaw 다운로드 실패: ${downloadResult.output.takeLast(200)}")
        }

        onProgress(50, "PicoClaw 설치 중...")

        // tar.gz 압축 해제 → 바이너리를 /usr/local/bin/에 배치
        val extractResult = prootManager.exec(
            "/bin/bash", "-c",
            "tar -xzf /tmp/picoclaw.tar.gz -C /tmp/ && " +
            "mv /tmp/picoclaw $binaryPath && " +
            "chmod +x $binaryPath && " +
            "rm -f /tmp/picoclaw.tar.gz"
        )
        if (!extractResult.isSuccess) {
            throw RuntimeException("PicoClaw 압축 해제 실패: ${extractResult.output.takeLast(200)}")
        }

        onProgress(80, "설정 디렉토리 생성 중...")
        prootManager.exec("/bin/bash", "-c", "mkdir -p $configDir")

        // 설치 검증
        val verifyResult = prootManager.exec("/bin/bash", "-c", "picoclaw --version 2>&1 || echo 'verify_failed'")
        Log.i(TAG, "PicoClaw version: ${verifyResult.output.trim()}")

        onProgress(100, "PicoClaw 설치 완료")
        Log.i(TAG, "PicoClaw installed successfully")
        Unit
    }

    override suspend fun configure(inputs: Map<String, String>) = withContext(Dispatchers.IO) {
        val provider = inputs["provider"] ?: "gemini"
        val model = inputs["model"] ?: "gemini-2.5-flash-lite"
        val apiKey = inputs["api_key"] ?: throw IllegalArgumentException("API Key required")
        val telegramToken = inputs["telegram_token"] ?: throw IllegalArgumentException("Telegram Bot Token required")

        // Groq: provider prefix 필수 (normalizeModel()이 "groq/" 제거)
        // Gemini: prefix 없이 사용 (PicoClaw의 normalizeModel()이 "gemini/" 미지원, "google/"만 지원)
        val qualifiedModel = when (provider) {
            "groq" -> "groq/$model"
            else -> model
        }

        val config = JSONObject().apply {
            put("agents", JSONObject().apply {
                put("defaults", JSONObject().apply {
                    put("workspace", "~/.picoclaw/workspace")
                    put("restrict_to_workspace", true)
                    put("model", qualifiedModel)
                    put("max_tokens", 8192)
                    put("temperature", 0.7)
                    put("max_tool_iterations", 20)
                })
            })
            put("providers", JSONObject().apply {
                put(provider, JSONObject().apply {
                    put("api_key", apiKey)
                    // Gemini: 기본 v1beta가 /chat/completions 미지원 → v1beta/openai 필수
                    put("api_base", if (provider == "gemini")
                        "https://generativelanguage.googleapis.com/v1beta/openai" else "")
                })
            })
            put("channels", JSONObject().apply {
                put("telegram", JSONObject().apply {
                    put("enabled", true)
                    put("token", telegramToken)
                    put("proxy", "")
                    put("allow_from", org.json.JSONArray()) // 빈 배열 = 전체 허용 (PicoClaw는 와일드카드 미지원)
                })
            })
            put("tools", JSONObject())
            put("heartbeat", JSONObject().apply {
                put("enabled", true)
                put("interval", 30)
            })
            put("gateway", JSONObject().apply {
                put("host", "0.0.0.0")
                put("port", 18790)
            })
        }

        val configFile = File(rootfsPath, "root/.picoclaw/config.json")
        configFile.parentFile?.mkdirs()
        configFile.writeText(config.toString(2))

        // workspace 디렉토리 생성
        val workspaceDir = File(rootfsPath, "root/.picoclaw/workspace")
        workspaceDir.mkdirs()

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

    override fun readCurrentConfig(): Map<String, String>? {
        val configFile = File(rootfsPath, "root/.picoclaw/config.json")
        if (!configFile.exists()) return null
        return try {
            val json = JSONObject(configFile.readText())
            val rawModel = json.optJSONObject("agents")?.optJSONObject("defaults")?.optString("model", "") ?: ""
            val providers = json.optJSONObject("providers")
            val provider = providers?.keys()?.asSequence()?.firstOrNull() ?: ""
            // Strip provider prefix (e.g., "groq/llama-3.3-70b-versatile" → "llama-3.3-70b-versatile")
            val displayModel = rawModel.removePrefix("groq/").removePrefix("gemini/")

            // api_key와 telegram_token도 반환하여 재설정 시 기존 값 프리필
            val apiKey = providers?.optJSONObject(provider)?.optString("api_key", "") ?: ""
            val telegramToken = json.optJSONObject("channels")?.optJSONObject("telegram")?.optString("token", "") ?: ""

            mapOf(
                "provider" to provider,
                "model" to displayModel,
                "api_key" to apiKey,
                "telegram_token" to telegramToken,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read PicoClaw config", e)
            null
        }
    }

    companion object {
        private const val TAG = "PicoClawInstaller"
        private const val DOWNLOAD_URL =
            "https://github.com/sipeed/picoclaw/releases/latest/download/picoclaw_Linux_arm64.tar.gz"
        @Volatile
        private var picoClawProcess: Process? = null
    }
}
