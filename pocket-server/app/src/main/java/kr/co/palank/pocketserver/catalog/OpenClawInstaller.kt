package kr.co.palank.pocketserver.catalog

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.co.palank.pocketserver.linux.ProotManager
import org.json.JSONObject
import java.io.File

class OpenClawInstaller(private val context: Context) : ServiceInstaller {

    private val prootManager = ProotManager(context)
    private val rootfsPath get() = File(context.filesDir, "ubuntu").absolutePath
    private val configDir = "/root/.openclaw"
    private val configPath = "$configDir/config.json"
    private val pidFile = "/tmp/openclaw.pid"

    override suspend fun install(onProgress: (Int, String) -> Unit) = withContext(Dispatchers.IO) {
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

        // Step 5: OpenClaw 설치 (--no-color로 ANSI 코드 제거)
        onProgress(40, "OpenClaw 설치 중... (약 5-15분)")
        result = prootManager.exec(
            "/bin/bash", "-c",
            "npm install -g openclaw --no-color 2>&1"
        )
        if (!result.isSuccess) {
            val cleanErr = cleanOutput(result.output.takeLast(500))
            throw RuntimeException("OpenClaw 설치 실패:\n$cleanErr\n\n문제가 지속되면 SSH 접속 후 npm debug log를 확인해주세요.")
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
                })
            })
        }

        val configFile = File(rootfsPath, "root/.openclaw/openclaw.json")
        configFile.parentFile?.mkdirs()
        configFile.writeText(config.toString(2))

        // GEMINI_API_KEY 환경변수를 profile에 설정 (OpenClaw은 env var로 인증)
        val envFile = File(rootfsPath, "root/.openclaw/.env")
        envFile.writeText("GEMINI_API_KEY=$apiKey\n")

        Log.i(TAG, "OpenClaw configured (openclaw.json + .env)")
        Unit
    }

    override suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        // 기존 프로세스 정리 후 시작 (중복 방지)
        prootManager.exec(
            "/bin/bash", "-c",
            "if [ -f $pidFile ]; then kill \$(cat $pidFile) 2>/dev/null; rm -f $pidFile; fi; " +
            "pkill -f 'openclaw gateway run' 2>/dev/null; sleep 1"
        )
        // config + env 존재 확인
        val configExists = File(rootfsPath, "root/.openclaw/openclaw.json").exists()
        if (!configExists) {
            Log.w(TAG, "OpenClaw config not found, skipping start")
            return@withContext false
        }
        val result = prootManager.exec(
            "/bin/bash", "-c",
            "set -a; [ -f /root/.openclaw/.env ] && . /root/.openclaw/.env; set +a; " +
            "NODE_OPTIONS='--require /usr/local/lib/openclaw-bionic-bypass.js' nohup openclaw gateway run > /tmp/openclaw.log 2>&1 & echo \$! > $pidFile; " +
            "sleep 5; [ -f $pidFile ] && kill -0 \$(cat $pidFile) 2>/dev/null && echo OK || echo FAIL"
        )
        val started = result.output.trim().endsWith("OK")
        Log.i(TAG, "OpenClaw start: code=${result.exitCode}, verified=$started")
        started
    }

    override suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        val result = prootManager.exec(
            "/bin/bash", "-c",
            "if [ -f $pidFile ]; then kill \$(cat $pidFile) 2>/dev/null; rm -f $pidFile; fi; " +
            "pkill -f 'openclaw gateway run' 2>/dev/null"
        )
        Log.i(TAG, "OpenClaw stop: code=${result.exitCode}")
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
        val globalNodeModules = File(rootfsPath, "usr/lib/node_modules/openclaw")
        val localNodeModules = File(rootfsPath, "usr/local/lib/node_modules/openclaw")
        return globalNodeModules.exists() || localNodeModules.exists()
    }

    /**
     * ANSI 이스케이프 코드 제거 — npm/curl 출력에 포함된 색상 코드가 UI에 깨져 보이는 문제 방지
     */
    private fun cleanOutput(raw: String): String =
        raw.replace(Regex("\u001B\\[[0-9;]*m"), "").trim()

    companion object {
        private const val TAG = "OpenClawInstaller"
        private const val NODE_VERSION = "22.22.0"
        private const val NODE_TARBALL_URL =
            "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-linux-arm64.tar.gz"
    }
}
