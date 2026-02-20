package kr.co.palank.pocketserver.catalog

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kr.co.palank.pocketserver.linux.ProotManager
import org.json.JSONObject
import java.io.File

class OpenClawInstaller(private val context: Context) : ServiceInstaller {

    private val prootManager = ProotManager(context)
    private val rootfsPath get() = File(context.filesDir, "ubuntu").absolutePath
    private val configDir = "/root/.openclaw"

    override suspend fun install(onProgress: (Int, String) -> Unit) = withContext(Dispatchers.IO) {
        // Step 0: 디스크 공간 사전 검증 (Node.js + npm + OpenClaw ≈ 800MB, 최소 1GB 필요)
        onProgress(2, "저장 공간 확인 중...")
        val freeMb = getFreeDiskMb()
        if (freeMb < MIN_FREE_DISK_MB) {
            throw RuntimeException(
                "저장 공간이 부족합니다.\n" +
                "필요: ${MIN_FREE_DISK_MB}MB 이상\n" +
                "현재 여유: ${freeMb}MB\n\n" +
                "불필요한 앱이나 파일을 삭제한 후 다시 시도해주세요."
            )
        }
        Log.i(TAG, "Disk space check passed: ${freeMb}MB free")

        // Step 1: Node.js tarball 직접 다운로드 (nodesource 스크립트는 PRoot에서 gpg/systemctl 실패)
        onProgress(5, "Node.js $NODE_VERSION 다운로드 중... (약 1-3분)")
        var result = prootManager.exec(
            "/bin/bash", "-c",
            "curl -fsSL --retry 3 --connect-timeout 30 -o /tmp/node.tar.gz '$NODE_TARBALL_URL'"
        )
        if (!result.isSuccess) {
            throw RuntimeException("Node.js 다운로드 실패. 네트워크 연결을 확인해주세요.\n${cleanOutput(result.output.takeLast(500))}")
        }

        // Step 2: tarball 압축 해제 → /usr/local/
        onProgress(20, "Node.js $NODE_VERSION 설치 중...")
        result = prootManager.exec(
            "/bin/bash", "-c",
            "tar -xzf /tmp/node.tar.gz -C /usr/local/ --strip-components=1 && rm -f /tmp/node.tar.gz"
        )
        if (!result.isSuccess) {
            throw RuntimeException("Node.js 압축 해제 실패: ${cleanOutput(result.output.takeLast(500))}")
        }

        // Step 3: node/npm 설치 검증
        onProgress(30, "Node.js 설치 검증 중...")
        result = prootManager.exec(
            "/bin/bash", "-c",
            "node --version && npm --version"
        )
        val versionOutput = cleanOutput(result.output)
        if (!result.isSuccess || !versionOutput.contains("v22")) {
            throw RuntimeException("Node.js 설치 검증 실패. node --version 결과: $versionOutput")
        }
        Log.i(TAG, "Node.js verified: $versionOutput")

        // Step 4: npm 전역 경로 보장 + git + build-essential (git: npm의 GitHub URL 의존성 해소 필수)
        onProgress(35, "빌드 도구 설치 중...")
        prootManager.exec(
            "/bin/bash", "-c",
            "mkdir -p /usr/local/lib/node_modules && apt-get update -qq 2>/dev/null; apt-get install -y git build-essential 2>/dev/null || true"
        )

        // Step 5: OpenClaw 설치 (--jobs=1로 병렬 컴파일 방지 → 3GB RAM OOM 방지)
        onProgress(40, "OpenClaw 설치 중... (약 5-15분)")
        result = prootManager.exec(
            "/bin/bash", "-c",
            "npm install -g openclaw --no-color --jobs=1 2>&1"
        )
        if (!result.isSuccess) {
            // 1회 재시도: npm cache 정리 후 다시 설치
            Log.w(TAG, "OpenClaw 1차 설치 실패, npm cache 정리 후 재시도")
            onProgress(45, "설치 재시도 중... (캐시 정리 후)")
            prootManager.exec("/bin/bash", "-c", "npm cache clean --force 2>/dev/null")
            result = prootManager.exec(
                "/bin/bash", "-c",
                "npm install -g openclaw --no-color --jobs=1 2>&1"
            )
            if (!result.isSuccess) {
                val cleanErr = cleanOutput(result.output.takeLast(500))
                throw RuntimeException("OpenClaw 설치 실패:\n$cleanErr\n\n문제가 지속되면 SSH 접속 후 npm debug log를 확인해주세요.")
            }
        }

        // Step 6: openclaw 바이너리 존재 검증
        onProgress(75, "OpenClaw 설치 검증 중...")
        result = prootManager.exec("/bin/bash", "-c", "which openclaw")
        if (!result.isSuccess) {
            throw RuntimeException("OpenClaw 바이너리를 찾을 수 없습니다. 설치가 불완전합니다.")
        }
        Log.i(TAG, "OpenClaw binary found at: ${cleanOutput(result.output)}")

        // Step 7: Bionic bypass (os.networkInterfaces() PRoot 크래시 방지)
        onProgress(85, "호환성 패치 적용 중...")
        applyBionicBypass()

        // Step 8: 설정 디렉토리 생성 + 심링크 (identity 통합)
        onProgress(95, "설정 디렉토리 생성 중...")
        prootManager.exec("/bin/bash", "-c", "mkdir -p $configDir")
        ensureOpenclawSymlink()

        onProgress(100, "OpenClaw 설치 완료")
        Log.i(TAG, "OpenClaw installed successfully")
        Unit
    }

    private suspend fun applyBionicBypass() {
        val bypassScript = """
const os = require('os');
const fs = require('fs');
const _ni = os.networkInterfaces;
os.networkInterfaces = function() {
  try { return _ni.call(this); } catch (e) {
    const result = {
      lo: [{ address: '127.0.0.1', netmask: '255.0.0.0', family: 'IPv4', mac: '00:00:00:00:00:00', internal: true, cidr: '127.0.0.1/8' }]
    };
    try {
      const ip = fs.readFileSync('/support/wifi_ip', 'utf8').trim();
      if (ip) {
        result.wlan0 = [{ address: ip, netmask: '255.255.255.0', family: 'IPv4', mac: '00:00:00:00:00:00', internal: false, cidr: ip + '/24' }];
      }
    } catch (_) {}
    return result;
  }
};
""".trimIndent()

        val bypassFile = File(rootfsPath, "usr/local/lib/openclaw-bionic-bypass.js")
        bypassFile.parentFile?.mkdirs()
        bypassFile.writeText(bypassScript + "\n")

        Log.i(TAG, "Bionic bypass applied")
    }

    override suspend fun configure(inputs: Map<String, String>) = withContext(Dispatchers.IO) {
        val provider = inputs["provider"] ?: "gemini"
        val model = inputs["model"] ?: "gemini-2.5-flash-lite"
        val apiKey = inputs["api_key"] ?: throw IllegalArgumentException("API Key required")
        val telegramToken = inputs["telegram_token"] ?: throw IllegalArgumentException("Telegram Bot Token required")
        val isOAuth = inputs["is_oauth"] == "true"

        // ChatGPT OAuth 프로바이더는 별도 플로우
        if (isOAuth && provider == "chatgpt") {
            configureWithOAuth(model, telegramToken)
            return@withContext Unit
        }

        val envVarName = when (provider) {
            "groq" -> "GROQ_API_KEY"
            "openai" -> "OPENAI_API_KEY"
            "openrouter" -> "OPENROUTER_API_KEY"
            else -> "GEMINI_API_KEY"
        }

        val authChoice = when (provider) {
            "groq" -> "groq-api-key"
            "openai" -> "openai-api-key"
            "openrouter" -> "apiKey"
            else -> "gemini-api-key"
        }

        val qualifiedModel = when (provider) {
            "groq" -> "groq/$model"
            "openai" -> "openai/$model"
            "openrouter" -> "openrouter/$model"
            else -> "google/$model"
        }

        // Step 1: .env에 API 키 기록 (기존 프로바이더 키 보존)
        val envFile = File(rootfsPath, "root/.openclaw/.env")
        envFile.parentFile?.mkdirs()
        val existingEnv = readExistingEnvKeys(envFile)
        existingEnv[envVarName] = apiKey
        envFile.writeText(existingEnv.entries.joinToString("\n") { "${it.key}=${it.value}" } + "\n")
        Log.i(TAG, "Wrote $envVarName to .env (preserved ${existingEnv.size} keys total)")

        // PRoot 명령 공통 프리앰블: .env 소싱 + bionic bypass
        val preamble = "set -a; [ -f /root/.openclaw/.env ] && . /root/.openclaw/.env; set +a; " +
            "export NODE_OPTIONS='--require /usr/local/lib/openclaw-bionic-bypass.js'; "

        // Step 2: openclaw onboard --non-interactive (공식 CLI로 설정)
        val tokenProviderFlag = if (provider == "openrouter") "--token-provider openrouter " else ""
        Log.i(TAG, "Running openclaw onboard --non-interactive (provider=$provider)")
        val onboardResult = prootManager.exec(
            "/bin/bash", "-c",
            preamble +
            "openclaw onboard --non-interactive " +
                "--accept-risk " +
                "--auth-choice $authChoice " +
                "$tokenProviderFlag" +
                "--mode local " +
                "--workspace /root/.openclaw/workspace " +
                "--skip-skills " +
                "--skip-channels " +
                "--skip-health " +
                "--skip-daemon 2>&1"
        )

        // onboard는 config를 성공적으로 작성해도 daemon 단계에서 exit 1을 반환할 수 있음 (PRoot에서 systemd 없음)
        // exit code 대신 config 파일 존재 + 크기로 성공 판단
        val configFile = File(rootfsPath, "root/.openclaw/openclaw.json")
        val onboardSuccess = configFile.exists() && configFile.length() > 100

        if (onboardSuccess) {
            Log.i(TAG, "openclaw onboard succeeded (config file exists, ${configFile.length()} bytes)")
        } else {
            Log.w(TAG, "openclaw onboard failed (config file missing or too small), falling back to manual config")
            Log.w(TAG, "onboard output: ${cleanOutput(onboardResult.output.takeLast(500))}")
            writeManualConfig(provider, qualifiedModel, telegramToken)
            return@withContext Unit
        }

        // Step 3: 모델 설정 (onboard는 자체 기본 모델을 사용하므로 사용자 선택 모델로 덮어쓰기)
        val modelResult = prootManager.exec(
            "/bin/bash", "-c",
            preamble + "openclaw config set agents.defaults.model.primary \"$qualifiedModel\" 2>&1"
        )
        if (!modelResult.isSuccess) {
            Log.w(TAG, "config set model failed, injecting directly")
            injectModelConfig(qualifiedModel)
        }

        // Step 4: compaction 설정 (reserveTokensFloor 필수 — Groq 모델 context overflow 방지)
        val compactionResult = prootManager.exec(
            "/bin/bash", "-c",
            preamble + "openclaw config set agents.defaults.compaction.reserveTokensFloor 4000 2>&1"
        )
        if (!compactionResult.isSuccess) {
            Log.w(TAG, "config set reserveTokensFloor failed, injecting directly")
            injectCompactionConfig()
        }

        // Step 5: Telegram 채널 설정 (onboard는 채널 설정을 하지 않음)
        val telegramCommands = listOf(
            "openclaw config set channels.telegram.enabled true",
            "openclaw config set channels.telegram.botToken \"$telegramToken\"",
            "openclaw config set channels.telegram.dmPolicy open",
            "openclaw config set channels.telegram.allowFrom '[\"*\"]'",
        )

        for (cmd in telegramCommands) {
            val result = prootManager.exec("/bin/bash", "-c", preamble + cmd + " 2>&1")
            if (!result.isSuccess) {
                Log.w(TAG, "config set failed: ${cleanOutput(result.output.takeLast(300))}")
                Log.w(TAG, "Falling back to manual Telegram config injection")
                injectTelegramConfig(telegramToken)
                break
            }
        }

        // 심링크 보장 — SSH 로그인 시 HOME=/home/pocketserver에서도 동일 config 참조
        ensureOpenclawSymlink()

        Log.i(TAG, "OpenClaw configured via CLI")
        Unit
    }

    /**
     * ChatGPT 구독 OAuth 인증을 통한 OpenClaw 설정.
     *
     * OpenClaw 자체 OAuth(`openclaw models auth login`)는 PRoot에서
     * localhost:1455 콜백 서버가 동작하지 않아 실패함.
     * 대신 Codex CLI의 `codex auth login`을 사용 (PicoClaw에서 검증됨)하여
     * ~/.codex/auth.json 토큰을 OpenClaw의 auth-profiles.json에 주입.
     *
     * 1. Codex CLI 설치 확인 + codex auth login (검증된 OAuth)
     * 2. ~/.codex/auth.json에서 토큰 읽기
     * 3. auth-profiles.json 수동 생성
     * 4. 모델을 openai-codex/[model] 로 설정
     * 5. Telegram 채널 설정
     */
    private suspend fun configureWithOAuth(
        model: String,
        telegramToken: String,
    ) {
        val preamble = "export NODE_OPTIONS='--require /usr/local/lib/openclaw-bionic-bypass.js'; "

        // Step 1: Codex CLI 설치 확인 (PicoClaw가 먼저 설치했을 수 있음)
        val codexCheck = prootManager.exec("/bin/bash", "-c", "codex --version 2>&1")
        if (!codexCheck.isSuccess || !codexCheck.output.contains(".")) {
            Log.i(TAG, "Installing Codex CLI for OAuth...")
            val installResult = prootManager.exec(
                "/bin/bash", "-c",
                "npm install -g @openai/codex --no-color 2>&1"
            )
            if (!installResult.isSuccess) {
                throw RuntimeException("Codex CLI 설치 실패: ${cleanOutput(installResult.output.takeLast(300))}")
            }
            Log.i(TAG, "Codex CLI installed")
        }

        // Step 2: 기존 인증 확인 — ~/.codex/auth.json이 이미 있는지
        val authFile = File(rootfsPath, "root/.codex/auth.json")
        if (!authFile.exists()) {
            // Codex CLI OAuth 인증 — execWithStreaming으로 URL 감지 → 브라우저 열기
            Log.i(TAG, "Starting Codex CLI OAuth login for OpenClaw...")
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
                throw RuntimeException(
                    "ChatGPT 인증에 실패했습니다.\n" +
                    "브라우저에서 ChatGPT 로그인을 완료해주세요.\n" +
                    "ChatGPT Plus(\$20/월) 또는 Pro 구독이 필요합니다."
                )
            }
        } else {
            Log.i(TAG, "Existing Codex auth.json found, reusing for OpenClaw")
        }

        // Step 3: ~/.codex/auth.json에서 토큰 읽어 auth-profiles.json 생성
        injectAuthProfiles(authFile)

        // Step 4: openclaw.json 직접 생성
        // `openclaw onboard --auth-choice openai-codex`는 non-interactive 모드에서
        // "OAuth requires interactive mode." 에러 반환 → onboard 우회하고 직접 작성
        val qualifiedModel = model // 이미 "openai-codex/gpt-5.3-codex" 형태
        val profileKey = "openai-codex:default"
        val config = JSONObject().apply {
            put("gateway", JSONObject().apply {
                put("mode", "local")
            })
            put("agents", JSONObject().apply {
                put("defaults", JSONObject().apply {
                    put("model", JSONObject().apply {
                        put("primary", qualifiedModel)
                        put("fallbacks", org.json.JSONArray())
                    })
                    put("compaction", JSONObject().apply {
                        put("reserveTokensFloor", 4000)
                    })
                })
            })
            // auth 섹션: OpenClaw이 auth-profiles.json의 프로필을 인식하려면 필요
            put("auth", JSONObject().apply {
                put("profiles", JSONObject().apply {
                    put(profileKey, JSONObject().apply {
                        put("provider", "openai-codex")
                        put("mode", "oauth")
                    })
                })
                put("order", JSONObject().apply {
                    put("openai-codex", org.json.JSONArray().apply {
                        put(profileKey)
                    })
                })
            })
            put("channels", JSONObject().apply {
                put("telegram", JSONObject().apply {
                    put("enabled", true)
                    put("botToken", telegramToken)
                    put("dmPolicy", "open")
                    put("allowFrom", org.json.JSONArray().apply { put("*") })
                })
            })
            put("commands", JSONObject().apply {
                put("restart", true)
            })
        }

        val configFile = File(rootfsPath, "root/.openclaw/openclaw.json")
        configFile.parentFile?.mkdirs()
        configFile.writeText(config.toString(2))

        // workspace 디렉토리 생성
        File(rootfsPath, "root/.openclaw/workspace").mkdirs()

        // 심링크 보장
        ensureOpenclawSymlink()

        Log.i(TAG, "OpenClaw configured via ChatGPT OAuth (model=$qualifiedModel)")
    }

    /**
     * ~/.codex/auth.json의 토큰을 OpenClaw의 auth-profiles.json에 주입.
     *
     * Codex CLI auth.json 구조 (ChatGPT OAuth 시):
     * {
     *   "auth_mode": "chatgpt",
     *   "OPENAI_API_KEY": null,
     *   "tokens": {
     *     "id_token": "<JWT>",
     *     "access_token": "<JWT>",
     *     "refresh_token": "<opaque>",
     *     "account_id": "acc-..."
     *   },
     *   "last_refresh": "2026-..."
     * }
     *
     * 토큰은 top-level이 아닌 "tokens" 객체 안에 중첩되어 있음.
     */
    private fun injectAuthProfiles(codexAuthFile: File) {
        try {
            val codexAuth = JSONObject(codexAuthFile.readText())
            val authMode = codexAuth.optString("auth_mode", "")
            Log.i(TAG, "Codex auth.json auth_mode=$authMode")

            // ChatGPT OAuth: 토큰은 "tokens" 중첩 객체 안에 있음
            val tokens = codexAuth.optJSONObject("tokens")
            if (tokens == null) {
                Log.w(TAG, "No 'tokens' object in codex auth.json (auth_mode=$authMode)")
                return
            }

            val accessToken = tokens.optString("access_token", "")
            val refreshToken = tokens.optString("refresh_token", "")
            val accountId = tokens.optString("account_id", "")

            if (accessToken.isEmpty()) {
                Log.w(TAG, "Empty access_token in codex auth.json tokens object")
                return
            }

            // JWT에서 accountId 추출 (tokens.account_id가 없는 경우)
            val resolvedAccountId = accountId.ifEmpty { extractAccountIdFromJwt(accessToken) }

            // JWT에서 만료시간 추출
            val expiresMs = extractExpiresFromJwt(accessToken)

            // OpenClaw auth-profiles.json 정확한 형식:
            // { "version": 1, "profiles": { "openai-codex:default": { "type": "oauth", ... } } }
            val profileKey = "openai-codex:default"
            val authProfiles = JSONObject().apply {
                put("version", 1)
                put("profiles", JSONObject().apply {
                    put(profileKey, JSONObject().apply {
                        put("type", "oauth")
                        put("provider", "openai-codex")
                        put("access", accessToken)
                        put("refresh", refreshToken)
                        put("expires", expiresMs)
                        if (resolvedAccountId.isNotEmpty()) put("accountId", resolvedAccountId)
                    })
                })
            }

            // OpenClaw이 기대하는 경로: ~/.openclaw/agents/main/agent/auth-profiles.json
            val agentDir = File(rootfsPath, "root/.openclaw/agents/main/agent")
            agentDir.mkdirs()
            val authProfilesFile = File(agentDir, "auth-profiles.json")
            authProfilesFile.writeText(authProfiles.toString(2))

            Log.i(TAG, "auth-profiles.json injected (version=1, type=oauth, accountId=${resolvedAccountId.take(10)}, expires=$expiresMs)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject auth-profiles.json from Codex auth", e)
        }
    }

    /**
     * JWT access_token에서 accountId 추출.
     * JWT payload의 "https://api.openai.com/auth" → "chatgpt_account_id"
     */
    private fun extractAccountIdFromJwt(jwt: String): String {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return ""
            val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP))
            val json = JSONObject(payload)
            val authClaim = json.optJSONObject("https://api.openai.com/auth")
            authClaim?.optString("chatgpt_account_id", "") ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract accountId from JWT", e)
            ""
        }
    }

    /**
     * JWT access_token에서 만료시간(ms) 추출.
     * JWT payload의 "exp" claim (Unix seconds) → milliseconds.
     */
    private fun extractExpiresFromJwt(jwt: String): Long {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return System.currentTimeMillis() + 3600_000
            val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP))
            val json = JSONObject(payload)
            val exp = json.optLong("exp", 0)
            if (exp > 0) exp * 1000 else System.currentTimeMillis() + 3600_000
        } catch (e: Exception) {
            System.currentTimeMillis() + 3600_000 // fallback: 1시간
        }
    }

    private fun extractOAuthUrl(line: String): String? {
        val match = Regex("(https://\\S+)").find(line) ?: return null
        val url = match.groupValues[1].trimEnd(',', '.', ')', ']', '"', '\'')
        // OAuth 관련 URL만 허용 (보안)
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

    private fun writeManualConfig(
        provider: String,
        qualifiedModel: String,
        telegramToken: String,
    ) {
        val config = JSONObject().apply {
            put("gateway", JSONObject().apply {
                put("mode", "local")
            })
            put("agents", JSONObject().apply {
                put("defaults", JSONObject().apply {
                    put("model", JSONObject().apply {
                        put("primary", qualifiedModel)
                        put("fallbacks", org.json.JSONArray())
                    })
                    put("compaction", JSONObject().apply {
                        put("reserveTokensFloor", 4000)
                    })
                })
            })
            put("channels", JSONObject().apply {
                put("telegram", JSONObject().apply {
                    put("enabled", true)
                    put("botToken", telegramToken)
                    put("dmPolicy", "open")
                    put("allowFrom", org.json.JSONArray().apply {
                        put("*")
                    })
                })
            })
            put("commands", JSONObject().apply {
                put("restart", true)
            })
        }

        val configFile = File(rootfsPath, "root/.openclaw/openclaw.json")
        configFile.parentFile?.mkdirs()
        configFile.writeText(config.toString(2))
        Log.i(TAG, "Manual fallback: wrote openclaw.json directly (provider=$provider, model=$qualifiedModel)")
    }

    /**
     * Fallback Tier 2: onboard 성공했지만 config set 실패 시, 기존 JSON에 Telegram 설정 주입
     */
    private fun injectTelegramConfig(telegramToken: String) {
        val configFile = File(rootfsPath, "root/.openclaw/openclaw.json")
        try {
            val config = if (configFile.exists()) JSONObject(configFile.readText()) else JSONObject()
            val channels = if (config.has("channels")) config.getJSONObject("channels") else JSONObject()
            channels.put("telegram", JSONObject().apply {
                put("enabled", true)
                put("botToken", telegramToken)
                put("dmPolicy", "open")
                put("allowFrom", org.json.JSONArray().apply { put("*") })
            })
            config.put("channels", channels)

            // reserveTokensFloor도 함께 보장
            val agents = if (config.has("agents")) config.getJSONObject("agents") else JSONObject().also { config.put("agents", it) }
            val defaults = if (agents.has("defaults")) agents.getJSONObject("defaults") else JSONObject().also { agents.put("defaults", it) }
            val compaction = if (defaults.has("compaction")) defaults.getJSONObject("compaction") else JSONObject().also { defaults.put("compaction", it) }
            compaction.put("reserveTokensFloor", 4000)

            configFile.writeText(config.toString(2))
            Log.i(TAG, "Telegram config + reserveTokensFloor injected into existing openclaw.json")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject Telegram config", e)
        }
    }

    /**
     * Fallback: config set model 실패 시 openclaw.json에 모델 직접 주입
     */
    private fun injectModelConfig(qualifiedModel: String) {
        val configFile = File(rootfsPath, "root/.openclaw/openclaw.json")
        try {
            val config = if (configFile.exists()) JSONObject(configFile.readText()) else return
            val agents = if (config.has("agents")) config.getJSONObject("agents") else JSONObject().also { config.put("agents", it) }
            val defaults = if (agents.has("defaults")) agents.getJSONObject("defaults") else JSONObject().also { agents.put("defaults", it) }
            // model은 객체: {"primary": "...", "fallbacks": []}
            defaults.put("model", JSONObject().apply {
                put("primary", qualifiedModel)
                put("fallbacks", org.json.JSONArray())
            })
            configFile.writeText(config.toString(2))
            Log.i(TAG, "Model injected into openclaw.json: $qualifiedModel")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject model config", e)
        }
    }

    /**
     * Fallback: config set 실패 시 openclaw.json에 reserveTokensFloor 직접 주입
     */
    private fun injectCompactionConfig() {
        val configFile = File(rootfsPath, "root/.openclaw/openclaw.json")
        try {
            val config = if (configFile.exists()) JSONObject(configFile.readText()) else return
            val agents = if (config.has("agents")) config.getJSONObject("agents") else JSONObject().also { config.put("agents", it) }
            val defaults = if (agents.has("defaults")) agents.getJSONObject("defaults") else JSONObject().also { agents.put("defaults", it) }
            val compaction = if (defaults.has("compaction")) defaults.getJSONObject("compaction") else JSONObject().also { defaults.put("compaction", it) }
            compaction.put("reserveTokensFloor", 4000)
            configFile.writeText(config.toString(2))
            Log.i(TAG, "reserveTokensFloor injected into openclaw.json")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject compaction config", e)
        }
    }

    /**
     * Dropbear 패턴: killOnExit=false로 장기 실행 PRoot 세션을 생성하여
     * OpenClaw를 포그라운드 프로세스로 실행. exec()의 --kill-on-exit가
     * nohup 프로세스까지 죽이는 문제를 해결.
     */
    override suspend fun start(): Boolean = startMutex.withLock {
        withContext(Dispatchers.IO) { doStart() }
    }

    private suspend fun doStart(): Boolean {
        // 기존 프로세스 정리
        openclawProcess?.let { proc ->
            proc.destroy()
            if (proc.isAlive) proc.destroyForcibly()
        }
        openclawProcess = null
        // Android-side pkill: PRoot 내부 pkill은 다른 PRoot 세션의 프로세스를 볼 수 없음.
        // Android shell에서 실행하면 같은 UID의 모든 프로세스를 죽일 수 있음.
        killAndroidProcesses("openclaw gateway run")
        prootManager.exec(
            "/bin/bash", "-c",
            "pkill -f 'openclaw gateway run' 2>/dev/null; sleep 1"
        )

        // Stale lock 정리 — 이전 크래시로 남은 lock 파일이 gateway 시작을 방해할 수 있음
        cleanStaleLockFiles()

        // 심링크 보장 — /home/pocketserver/.openclaw → /root/.openclaw (identity 통합)
        ensureOpenclawSymlink()

        // config 존재 확인 + 마이그레이션
        val configFile = File(rootfsPath, "root/.openclaw/openclaw.json")
        if (!configFile.exists()) {
            Log.w(TAG, "OpenClaw config not found, skipping start")
            return false
        }
        patchCommandsRestart(configFile)

        // doctor --fix 제거: gateway가 자체적으로 auth token을 관리함.
        // doctor --fix가 config를 수정하면 gateway 시작 시 token mismatch 발생.

        // killOnExit=false: PRoot 세션이 OpenClaw와 함께 유지됨
        val cmd = prootManager.buildEnvWrappedCommand(
            "/bin/bash", "-c",
            "set -a; [ -f /root/.openclaw/.env ] && . /root/.openclaw/.env; set +a; " +
            "export NODE_OPTIONS='--require /usr/local/lib/openclaw-bionic-bypass.js'; " +
            "exec openclaw gateway run",
            killOnExit = false
        )

        val logFile = File(rootfsPath, "tmp/openclaw.log")
        logFile.parentFile?.mkdirs()

        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.to(logFile))

        openclawProcess = pb.start()

        // 시작 검증: 최대 15초 polling (1초 간격)
        var alive = false
        for (i in 1..15) {
            delay(1000)
            if (openclawProcess?.isAlive != true) {
                // 프로세스가 조기 종료 — 로그에서 원인 확인
                val lastLines = try {
                    logFile.readLines().takeLast(5).joinToString("\n")
                } catch (_: Exception) { "로그 읽기 실패" }
                Log.e(TAG, "OpenClaw died after ${i}s. Last log:\n$lastLines")
                return false
            }
            // 3초 이상 생존하면 성공으로 판단 (초기 크래시 아님)
            if (i >= 3) {
                alive = true
                break
            }
        }
        if (!alive) return false

        // 로그 기반 헬스체크: gateway 초기화 에러 감지 후 1회 재시도
        delay(5000)
        if (hasGatewayError(logFile)) {
            Log.w(TAG, "Gateway error detected in log, retrying once...")
            openclawProcess?.let { proc ->
                proc.destroy()
                if (proc.isAlive) proc.destroyForcibly()
            }
            openclawProcess = null
            killAndroidProcesses("openclaw")
            prootManager.exec("/bin/bash", "-c", "pkill -f 'openclaw gateway run' 2>/dev/null; sleep 1")
            delay(3000)

            logFile.writeText("")
            openclawProcess = pb.start()

            var retryAlive = false
            for (i in 1..15) {
                delay(1000)
                if (openclawProcess?.isAlive != true) {
                    Log.e(TAG, "OpenClaw retry died after ${i}s")
                    return false
                }
                if (i >= 3) {
                    retryAlive = true
                    break
                }
            }
            if (!retryAlive) return false

            delay(5000)
            if (hasGatewayError(logFile)) {
                Log.e(TAG, "OpenClaw retry also has gateway errors")
                return false
            }
            Log.i(TAG, "OpenClaw start: retry succeeded")
        } else {
            Log.i(TAG, "OpenClaw start: alive=true, no gateway errors")
        }

        return true
    }

    private fun hasGatewayError(logFile: File): Boolean {
        val errorKeywords = listOf("ECONNREFUSED", "device_token_mismatch", "pairing required", "unauthorized", "FATAL ERROR")
        return try {
            if (!logFile.exists()) false
            else {
                val tail = logFile.readLines().takeLast(20)
                tail.any { line ->
                    val lower = line.lowercase()
                    errorKeywords.any { kw -> lower.contains(kw.lowercase()) }
                }
            }
        } catch (_: Exception) { false }
    }

    override suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        openclawProcess?.let { proc ->
            proc.destroy()
            if (proc.isAlive) proc.destroyForcibly()
        }
        openclawProcess = null
        // Android-side kill (cross-PRoot-session cleanup)
        killAndroidProcesses("openclaw gateway run")
        // Fallback: PRoot 내 잔여 프로세스 정리
        prootManager.exec(
            "/bin/bash", "-c",
            "pkill -f 'openclaw gateway run' 2>/dev/null"
        )
        Log.i(TAG, "OpenClaw stopped")
        true
    }

    override suspend fun isRunning(): Boolean = withContext(Dispatchers.IO) {
        if (openclawProcess?.isAlive == true) return@withContext true
        // Fallback: PRoot 내부에서 확인
        val result = prootManager.exec(
            "/bin/bash", "-c",
            "pgrep -f 'openclaw gateway run' >/dev/null 2>&1 && echo running || echo stopped"
        )
        result.output.trim() == "running"
    }

    /**
     * Gateway 프로세스가 살아있고, HTTP 엔드포인트가 실제로 응답하는지 확인.
     * 프로세스만 살아있고 Telegram 등 채널이 죽어있는 "좀비 게이트웨이" 감지.
     */
    override suspend fun isHealthy(): Boolean = withContext(Dispatchers.IO) {
        if (!isRunning()) return@withContext false

        // Gateway HTTP 엔드포인트 응답 확인 (127.0.0.1:18789)
        val result = prootManager.exec(
            "/bin/bash", "-c",
            "curl -s -o /dev/null -w '%{http_code}' --connect-timeout 5 --max-time 10 http://127.0.0.1:18789/ 2>/dev/null"
        )
        val httpCode = result.output.trim()
        val httpOk = httpCode == "200" || httpCode == "302" || httpCode == "401"

        if (!httpOk) {
            Log.w(TAG, "Gateway unhealthy: process alive but HTTP returned '$httpCode'")
            return@withContext false
        }

        // 로그에서 최근 활동 확인 — 마지막 로그가 30분 이상 오래되면 비정상
        val logFile = File(rootfsPath, "tmp/openclaw.log")
        if (logFile.exists()) {
            val lastModified = logFile.lastModified()
            val staleMs = System.currentTimeMillis() - lastModified
            if (staleMs > 30 * 60 * 1000) {
                Log.w(TAG, "Gateway unhealthy: log file stale for ${staleMs / 60000}min")
                return@withContext false
            }
        }

        true
    }

    override fun isInstalled(): Boolean {
        val globalNodeModules = File(rootfsPath, "usr/lib/node_modules/openclaw")
        val localNodeModules = File(rootfsPath, "usr/local/lib/node_modules/openclaw")
        return globalNodeModules.exists() || localNodeModules.exists()
    }

    override fun readCurrentConfig(): Map<String, String>? {
        val configFile = File(rootfsPath, "root/.openclaw/openclaw.json")
        if (!configFile.exists()) return null
        return try {
            val json = JSONObject(configFile.readText())
            val modelObj = json.optJSONObject("agents")?.optJSONObject("defaults")?.opt("model")
            val model = when (modelObj) {
                is JSONObject -> modelObj.optString("primary", "")
                is String -> modelObj
                else -> ""
            }
            // openrouter/ 는 openai/ 보다 먼저 체크 (openrouter/openai/... 패턴 대응)
            val provider = when {
                model.startsWith("groq/") -> "groq"
                model.startsWith("google/") -> "gemini"
                model.startsWith("openai-codex/") -> "chatgpt"
                model.startsWith("openrouter/") -> "openrouter"
                model.startsWith("openai/") -> "openai"
                else -> ""
            }
            val displayModel = model.removePrefix("groq/").removePrefix("google/")
                .removePrefix("openai-codex/").removePrefix("openrouter/").removePrefix("openai/")

            // .env에서 모든 API 키 읽기
            val allEnvKeys = try {
                val envFile = File(rootfsPath, "root/.openclaw/.env")
                if (envFile.exists()) {
                    envFile.readLines()
                        .filter { it.contains("=") && it.isNotBlank() }
                        .associate { it.substringBefore("=") to it.substringAfter("=").trim() }
                } else emptyMap()
            } catch (_: Exception) { emptyMap() }

            val apiKey = if (provider == "chatgpt") {
                "OAUTH"
            } else {
                val envVarName = when (provider) {
                    "groq" -> "GROQ_API_KEY"
                    "openai" -> "OPENAI_API_KEY"
                    "openrouter" -> "OPENROUTER_API_KEY"
                    else -> "GEMINI_API_KEY"
                }
                allEnvKeys[envVarName] ?: ""
            }

            val telegramToken = json.optJSONObject("channels")
                ?.optJSONObject("telegram")
                ?.optString("botToken", "") ?: ""

            val result = mutableMapOf(
                "provider" to provider,
                "model" to displayModel,
                "api_key" to apiKey,
                "telegram_token" to telegramToken,
            )

            // 모든 저장된 프로바이더 키를 api_key_{provider} 형태로 포함 (UI 프리필용)
            val envToProvider = mapOf(
                "GEMINI_API_KEY" to "gemini",
                "GROQ_API_KEY" to "groq",
                "OPENAI_API_KEY" to "openai",
                "OPENROUTER_API_KEY" to "openrouter",
            )
            for ((envVar, providerId) in envToProvider) {
                val savedKey = allEnvKeys[envVar]
                if (!savedKey.isNullOrEmpty()) {
                    result["api_key_$providerId"] = savedKey
                }
            }

            result.toMap()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read OpenClaw config", e)
            null
        }
    }

    /** 기존 .env 파일에서 KEY=VALUE 쌍을 읽어 반환 (프로바이더 전환 시 기존 API 키 보존용) */
    private fun readExistingEnvKeys(envFile: File): MutableMap<String, String> {
        if (!envFile.exists()) return mutableMapOf()
        return try {
            envFile.readLines()
                .filter { it.contains("=") && it.isNotBlank() }
                .associate { it.substringBefore("=") to it.substringAfter("=") }
                .toMutableMap()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read existing .env for merge", e)
            mutableMapOf()
        }
    }

    /**
     * ANSI 이스케이프 코드 제거 — npm/curl 출력에 포함된 색상 코드가 UI에 깨져 보이는 문제 방지
     */
    private fun cleanOutput(raw: String): String =
        raw.replace(Regex("\u001B\\[[0-9;]*m"), "").trim()

    private fun getFreeDiskMb(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.blockSizeLong * stat.availableBlocksLong / (1024 * 1024)
        } catch (e: Exception) {
            Long.MAX_VALUE // 확인 불가 시 통과
        }
    }

    /**
     * Android 측에서 프로세스 이름 패턴으로 프로세스를 죽임.
     * PRoot 내부 pkill은 다른 PRoot 세션의 프로세스를 볼 수 없으므로,
     * 앱 재시작 후 zombie gateway를 정리하려면 Android shell에서 실행해야 함.
     */
    private fun patchCommandsRestart(configFile: File) {
        try {
            val json = JSONObject(configFile.readText())
            val commands = json.optJSONObject("commands")
            if (commands == null || !commands.optBoolean("restart", false)) {
                json.put("commands", JSONObject().apply { put("restart", true) })
                configFile.writeText(json.toString(2))
                Log.i(TAG, "Patched commands.restart=true into existing config")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to patch commands.restart", e)
        }
    }

    /**
     * /home/pocketserver/.openclaw → /root/.openclaw 심링크 생성.
     * SSH 로그인(HOME=/home/pocketserver)과 gateway(HOME=/root)에서
     * 동일한 config/identity를 참조하도록 통합. device_token_mismatch 방지.
     */
    private suspend fun ensureOpenclawSymlink() {
        val result = prootManager.exec(
            "/bin/bash", "-c",
            "if [ -d /home/pocketserver/.openclaw ] && [ ! -L /home/pocketserver/.openclaw ]; then " +
            "  rm -rf /home/pocketserver/.openclaw && " +
            "  ln -s /root/.openclaw /home/pocketserver/.openclaw && " +
            "  echo 'symlink_created'; " +
            "elif [ ! -e /home/pocketserver/.openclaw ]; then " +
            "  ln -s /root/.openclaw /home/pocketserver/.openclaw && " +
            "  echo 'symlink_created'; " +
            "else " +
            "  echo 'symlink_exists'; " +
            "fi"
        )
        Log.i(TAG, "ensureOpenclawSymlink: ${result.output.trim()}")

        // HOME=/root 강제: SSH 세션(HOME=/home/pocketserver)에서도 동일 경로 사용
        // device_token_mismatch 방지 — OpenClaw가 경로 문자열로 identity 비교
        val profileScript = File(rootfsPath, "etc/profile.d/fix-home.sh")
        if (!profileScript.exists()) {
            profileScript.parentFile?.mkdirs()
            profileScript.writeText(
                "#!/bin/sh\n" +
                "export HOME=/root\n" +
                "cd /root\n"
            )
            Log.i(TAG, "Wrote /etc/profile.d/fix-home.sh (HOME=/root)")
        }
    }

    /**
     * Stale lock 파일 정리 — 이전 크래시로 남은 lock 파일이 gateway 시작을 방해.
     * Lock 파일은 싱글 인스턴스 보장용이지만, PRoot에서 비정상 종료 시 삭제되지 않음.
     */
    private suspend fun cleanStaleLockFiles() {
        val result = prootManager.exec(
            "/bin/bash", "-c",
            "for f in /tmp/openclaw-0/*.lock 2>/dev/null; do " +
            "  [ -f \"\$f\" ] || continue; " +
            "  pid=\$(grep -oP '\"pid\"\\s*:\\s*\\K[0-9]+' \"\$f\" 2>/dev/null); " +
            "  if [ -n \"\$pid\" ] && kill -0 \"\$pid\" 2>/dev/null; then " +
            "    echo \"live:\$pid\"; " +
            "  else " +
            "    rm -f \"\$f\"; " +
            "    echo \"stale_removed\"; " +
            "  fi; " +
            "done"
        )
        Log.i(TAG, "cleanStaleLockFiles: ${result.output.trim()}")
    }

    private fun killAndroidProcesses(namePattern: String) {
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c",
                "pkill -f '$namePattern' 2>/dev/null"))
            proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            Log.i(TAG, "Android-side pkill for '$namePattern' completed")
        } catch (e: Exception) {
            Log.w(TAG, "Android-side pkill failed for '$namePattern'", e)
        }
    }

    companion object {
        private const val TAG = "OpenClawInstaller"
        private const val NODE_VERSION = "22.22.0"
        private const val NODE_TARBALL_URL =
            "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-linux-arm64.tar.gz"
        private const val MIN_FREE_DISK_MB = 1024L // 1GB
        private val startMutex = Mutex()
        @Volatile
        private var openclawProcess: Process? = null
    }
}
