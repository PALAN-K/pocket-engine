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
        val apiKey = inputs["gemini_api_key"] ?: throw IllegalArgumentException("Gemini API Key required")
        val telegramToken = inputs["telegram_token"] ?: throw IllegalArgumentException("Telegram Bot Token required")

        // Step 1: .env 먼저 작성 (openclaw onboard가 GEMINI_API_KEY를 env에서 읽음)
        val envFile = File(rootfsPath, "root/.openclaw/.env")
        envFile.parentFile?.mkdirs()
        envFile.writeText("GEMINI_API_KEY=$apiKey\n")
        Log.i(TAG, "Wrote GEMINI_API_KEY to .env")

        // PRoot 명령 공통 프리앰블: .env 소싱 + bionic bypass
        val preamble = "set -a; [ -f /root/.openclaw/.env ] && . /root/.openclaw/.env; set +a; " +
            "export NODE_OPTIONS='--require /usr/local/lib/openclaw-bionic-bypass.js'; "

        // Step 2: openclaw onboard --non-interactive (공식 CLI로 설정)
        Log.i(TAG, "Running openclaw onboard --non-interactive")
        val onboardResult = prootManager.exec(
            "/bin/bash", "-c",
            preamble +
            "openclaw onboard --non-interactive " +
                "--auth-choice gemini-api-key " +
                "--mode local " +
                "--workspace /root/.openclaw/workspace " +
                "--skip-skills 2>&1"
        )

        if (onboardResult.isSuccess) {
            Log.i(TAG, "openclaw onboard succeeded")
        } else {
            Log.w(TAG, "openclaw onboard failed, falling back to manual config")
            Log.w(TAG, "onboard output: ${cleanOutput(onboardResult.output.takeLast(500))}")
            writeManualConfig(apiKey, telegramToken)
            return@withContext Unit
        }

        // Step 3: Telegram 채널 설정 (onboard는 채널 설정을 하지 않음)
        val telegramCommands = listOf(
            "openclaw config set channels.telegram.enabled true",
            "openclaw config set channels.telegram.botToken \"$telegramToken\"",
            "openclaw config set channels.telegram.dmPolicy open",
            "openclaw config set channels.telegram.allowFrom '[\"*\"]'"
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

        Log.i(TAG, "OpenClaw configured via CLI (onboard + config set)")
        Unit
    }

    /**
     * Fallback Tier 3: openclaw onboard 실패 시 수동 JSON 작성
     */
    private fun writeManualConfig(apiKey: String, telegramToken: String) {
        val config = JSONObject().apply {
            put("gateway", JSONObject().apply {
                put("mode", "local")
            })
            put("agents", JSONObject().apply {
                put("defaults", JSONObject().apply {
                    put("model", JSONObject().apply {
                        put("primary", "google/gemini-2.5-flash")
                        put("fallbacks", org.json.JSONArray().apply {
                            put("google/gemma-3-27b-it")
                        })
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
        Log.i(TAG, "Manual fallback: wrote openclaw.json directly")
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
            configFile.writeText(config.toString(2))
            Log.i(TAG, "Telegram config injected into existing openclaw.json")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject Telegram config", e)
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

        // 안전망: gateway 토큰 동기화 (device token mismatch 방지)
        if (alive) {
            try {
                prootManager.exec(
                    "/bin/bash", "-c",
                    "set -a; [ -f /root/.openclaw/.env ] && . /root/.openclaw/.env; set +a; " +
                    "export NODE_OPTIONS='--require /usr/local/lib/openclaw-bionic-bypass.js'; " +
                    "openclaw doctor --fix 2>/dev/null || true"
                )
                Log.i(TAG, "OpenClaw doctor --fix completed")
            } catch (e: Exception) {
                Log.w(TAG, "OpenClaw doctor --fix failed (non-critical)", e)
            }
        }

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
