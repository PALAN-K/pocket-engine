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
        onProgress(5, "Node.js 22 설치 중...")
        var result = prootManager.exec(
            "/bin/bash", "-c",
            "curl -fsSL https://deb.nodesource.com/setup_22.x | bash - && apt-get install -y nodejs build-essential"
        )
        if (!result.isSuccess) {
            throw RuntimeException("Node.js 설치 실패: ${result.output.takeLast(200)}")
        }

        onProgress(40, "OpenClaw 설치 중...")
        result = prootManager.exec(
            "/bin/bash", "-c",
            "npm install -g openclaw"
        )
        if (!result.isSuccess) {
            throw RuntimeException("OpenClaw 설치 실패: ${result.output.takeLast(200)}")
        }

        onProgress(80, "호환성 패치 적용 중...")
        applyBionicBypass()

        onProgress(90, "설정 디렉토리 생성 중...")
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

        val configFile = File(rootfsPath, "root/.openclaw/config.json")
        configFile.parentFile?.mkdirs()
        configFile.writeText(config.toString(2))

        Log.i(TAG, "OpenClaw configured")
        Unit
    }

    override suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        val result = prootManager.exec(
            "/bin/bash", "-c",
            "NODE_OPTIONS='--require /usr/local/lib/openclaw-bionic-bypass.js' nohup openclaw start > /tmp/openclaw.log 2>&1 & echo \$! > $pidFile"
        )
        Log.i(TAG, "OpenClaw start: code=${result.exitCode}")
        result.isSuccess
    }

    override suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        val result = prootManager.exec(
            "/bin/bash", "-c",
            "if [ -f $pidFile ]; then kill \$(cat $pidFile) 2>/dev/null; rm -f $pidFile; fi"
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

    companion object {
        private const val TAG = "OpenClawInstaller"
    }
}
