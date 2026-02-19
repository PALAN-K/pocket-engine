package kr.co.palank.pocketserver.catalog

import android.content.Context
import android.content.Intent
import android.net.Uri
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
        val isOAuth = inputs["is_oauth"] == "true"

        // ChatGPT OAuth → Codex CLI 서브프로세스 프로바이더
        if (isOAuth && provider == "chatgpt") {
            configureWithCodexCli(telegramToken)
            return@withContext Unit
        }

        // Provider별 모델 prefix 처리
        val qualifiedModel = when (provider) {
            "groq" -> "groq/$model"
            "openai" -> "openai/$model"
            else -> model
        }

        // Provider별 api_base 설정
        val apiBase = when (provider) {
            "gemini" -> "https://generativelanguage.googleapis.com/v1beta/openai"
            "openai" -> "https://api.openai.com/v1"
            else -> ""
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
                    put("api_base", apiBase)
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
     * ChatGPT 구독 인증을 통한 PicoClaw 설정.
     * Codex CLI를 설치하고 OAuth 인증 후 codex-cli 프로바이더로 설정.
     *
     * 1. Node.js 설치 확인/설치 (Codex CLI는 Node.js 패키지)
     * 2. Codex CLI 설치 (npm install -g @openai/codex)
     * 3. codex auth login — stdout 스트리밍으로 OAuth URL 감지 → 브라우저 열기
     * 4. PicoClaw config에 codex-cli 프로바이더 설정
     */
    private suspend fun configureWithCodexCli(telegramToken: String) {
        // Step 1: Node.js 설치 확인
        val nodeCheck = prootManager.exec("/bin/bash", "-c", "node --version 2>&1")
        val hasNode = nodeCheck.output.contains("v2") // v20, v22 등
        if (!hasNode) {
            Log.i(TAG, "Node.js not found, installing for Codex CLI...")
            val dlResult = prootManager.exec(
                "/bin/bash", "-c",
                "curl -fsSL --retry 3 --connect-timeout 30 -o /tmp/node.tar.gz " +
                "'https://nodejs.org/dist/v22.22.0/node-v22.22.0-linux-arm64.tar.gz'"
            )
            if (!dlResult.isSuccess) {
                throw RuntimeException("Node.js 다운로드 실패. 네트워크를 확인해주세요.")
            }
            val extractResult = prootManager.exec(
                "/bin/bash", "-c",
                "tar -xzf /tmp/node.tar.gz -C /usr/local/ --strip-components=1 && rm -f /tmp/node.tar.gz"
            )
            if (!extractResult.isSuccess) {
                throw RuntimeException("Node.js 설치 실패: ${extractResult.output.takeLast(200)}")
            }
            Log.i(TAG, "Node.js installed for Codex CLI")
        }

        // Step 2: Codex CLI 설치
        val codexCheck = prootManager.exec("/bin/bash", "-c", "codex --version 2>&1")
        if (!codexCheck.isSuccess || !codexCheck.output.contains(".")) {
            Log.i(TAG, "Installing Codex CLI...")
            val installResult = prootManager.exec(
                "/bin/bash", "-c",
                "npm install -g @openai/codex --no-color 2>&1"
            )
            if (!installResult.isSuccess) {
                throw RuntimeException("Codex CLI 설치 실패: ${installResult.output.takeLast(300)}")
            }
            Log.i(TAG, "Codex CLI installed")
        }

        // Step 3: 기존 인증 확인 — ~/.codex/auth.json이 있으면 재사용
        val authFile = File(rootfsPath, "root/.codex/auth.json")
        if (!authFile.exists()) {
            // 인증 파일 없음 → Codex CLI OAuth 인증 실행
            Log.i(TAG, "Starting Codex CLI OAuth login (streaming)...")
            var browserOpened = false
            val authResult = prootManager.execWithStreaming(
                "/bin/bash", "-c",
                "export BROWSER=/usr/local/bin/xdg-open; codex auth login 2>&1",
                onLine = { line ->
                    if (!browserOpened) {
                        val url = extractOAuthUrl(line)
                        if (url != null) {
                            Log.i(TAG, "Codex OAuth URL detected: $url")
                            openBrowserUrl(url)
                            browserOpened = true
                        }
                    }
                },
                timeoutMs = 180_000,
            )
            Log.i(TAG, "Codex auth result (code=${authResult.exitCode})")

            if (!authFile.exists()) {
                Log.w(TAG, "Codex auth.json not found after auth flow")
                throw RuntimeException(
                    "ChatGPT 인증에 실패했습니다.\n" +
                    "브라우저에서 ChatGPT 로그인을 완료해주세요.\n" +
                    "ChatGPT Plus(\$20/월) 또는 Pro 구독이 필요합니다."
                )
            }
            Log.i(TAG, "Codex auth.json created, auth successful")
        } else {
            Log.i(TAG, "Existing Codex auth.json found, reusing (skip browser login)")
        }

        // Step 5: PicoClaw config 작성 (codex-cli 프로바이더)
        val config = JSONObject().apply {
            put("agents", JSONObject().apply {
                put("defaults", JSONObject().apply {
                    put("workspace", "~/.picoclaw/workspace")
                    put("restrict_to_workspace", true)
                    put("provider", "codex-cli")
                    put("model", "codex-cli")
                    put("max_tokens", 8192)
                    put("temperature", 0.7)
                    put("max_tool_iterations", 20)
                })
            })
            put("providers", JSONObject())
            put("channels", JSONObject().apply {
                put("telegram", JSONObject().apply {
                    put("enabled", true)
                    put("token", telegramToken)
                    put("proxy", "")
                    put("allow_from", org.json.JSONArray())
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

        val workspaceDir = File(rootfsPath, "root/.picoclaw/workspace")
        workspaceDir.mkdirs()

        Log.i(TAG, "PicoClaw configured with codex-cli provider")
    }

    private fun extractOAuthUrl(line: String): String? {
        val match = Regex("(https://\\S+)").find(line) ?: return null
        val url = match.groupValues[1].trimEnd(',', '.', ')', ']', '"', '\'')
        if (url.contains("auth.openai.com") || url.contains("login") || url.contains("authorize")) {
            return url
        }
        return null
    }

    private fun openBrowserUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open browser for OAuth URL", e)
        }
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
            val defaults = json.optJSONObject("agents")?.optJSONObject("defaults")
            val rawModel = defaults?.optString("model", "") ?: ""
            val rawProvider = defaults?.optString("provider", "") ?: ""

            // codex-cli 프로바이더 감지
            if (rawProvider == "codex-cli" || rawModel == "codex-cli") {
                val telegramToken = json.optJSONObject("channels")?.optJSONObject("telegram")?.optString("token", "") ?: ""
                return mapOf(
                    "provider" to "chatgpt",
                    "model" to "codex-cli",
                    "api_key" to "OAUTH",
                    "telegram_token" to telegramToken,
                )
            }

            val providers = json.optJSONObject("providers")
            val provider = providers?.keys()?.asSequence()?.firstOrNull() ?: ""
            val displayModel = rawModel.removePrefix("groq/").removePrefix("gemini/").removePrefix("openai/")

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
