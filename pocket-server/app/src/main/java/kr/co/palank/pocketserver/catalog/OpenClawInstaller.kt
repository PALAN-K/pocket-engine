package kr.co.palank.pocketserver.catalog

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

        // Step 8: 설정 디렉토리 생성
        onProgress(95, "설정 디렉토리 생성 중...")
        prootManager.exec("/bin/bash", "-c", "mkdir -p $configDir")

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

        val envVarName = when (provider) {
            "groq" -> "GROQ_API_KEY"
            else -> "GEMINI_API_KEY"
        }

        val authChoice = when (provider) {
            "groq" -> "groq-api-key"
            else -> "gemini-api-key"
        }

        val qualifiedModel = when (provider) {
            "groq" -> "groq/$model"
            else -> "google/$model"
        }

        // Step 1: .env 먼저 작성 (openclaw onboard가 env에서 API key를 읽음)
        val envFile = File(rootfsPath, "root/.openclaw/.env")
        envFile.parentFile?.mkdirs()
        envFile.writeText("$envVarName=$apiKey\n")
        Log.i(TAG, "Wrote $envVarName to .env")

        // PRoot 명령 공통 프리앰블: .env 소싱 + bionic bypass
        val preamble = "set -a; [ -f /root/.openclaw/.env ] && . /root/.openclaw/.env; set +a; " +
            "export NODE_OPTIONS='--require /usr/local/lib/openclaw-bionic-bypass.js'; "

        // Step 2: openclaw onboard --non-interactive (공식 CLI로 설정)
        Log.i(TAG, "Running openclaw onboard --non-interactive")
        val onboardResult = prootManager.exec(
            "/bin/bash", "-c",
            preamble +
            "openclaw onboard --non-interactive " +
                "--accept-risk " +
                "--auth-choice $authChoice " +
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

        Log.i(TAG, "OpenClaw configured via CLI")
        Unit
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
    override suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        // 기존 프로세스 정리
        openclawProcess?.let { proc ->
            proc.destroy()
            if (proc.isAlive) proc.destroyForcibly()
        }
        openclawProcess = null
        prootManager.exec(
            "/bin/bash", "-c",
            "pkill -f 'openclaw gateway run' 2>/dev/null; sleep 1"
        )

        // config 존재 확인
        val configExists = File(rootfsPath, "root/.openclaw/openclaw.json").exists()
        if (!configExists) {
            Log.w(TAG, "OpenClaw config not found, skipping start")
            return@withContext false
        }

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
                return@withContext false
            }
            // 3초 이상 생존하면 성공으로 판단 (초기 크래시 아님)
            if (i >= 3) {
                alive = true
                break
            }
        }
        Log.i(TAG, "OpenClaw start: alive=$alive")

        alive
    }

    override suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        openclawProcess?.let { proc ->
            proc.destroy()
            if (proc.isAlive) proc.destroyForcibly()
        }
        openclawProcess = null
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
            val provider = when {
                model.startsWith("groq/") -> "groq"
                model.startsWith("google/") -> "gemini"
                else -> ""
            }
            val displayModel = model.removePrefix("groq/").removePrefix("google/")

            // api_key: .env 파일에서 읽기
            val apiKey = try {
                val envFile = File(rootfsPath, "root/.openclaw/.env")
                if (envFile.exists()) {
                    envFile.readLines()
                        .firstOrNull { it.contains("API_KEY=") }
                        ?.substringAfter("=")
                        ?.trim()
                        ?: ""
                } else ""
            } catch (_: Exception) { "" }

            // telegram_token: openclaw.json에서 읽기
            val telegramToken = json.optJSONObject("channels")
                ?.optJSONObject("telegram")
                ?.optString("botToken", "") ?: ""

            mapOf(
                "provider" to provider,
                "model" to displayModel,
                "api_key" to apiKey,
                "telegram_token" to telegramToken,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read OpenClaw config", e)
            null
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

    companion object {
        private const val TAG = "OpenClawInstaller"
        private const val NODE_VERSION = "22.22.0"
        private const val NODE_TARBALL_URL =
            "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-linux-arm64.tar.gz"
        private const val MIN_FREE_DISK_MB = 1024L // 1GB
        @Volatile
        private var openclawProcess: Process? = null
    }
}
