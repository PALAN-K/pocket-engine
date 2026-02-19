package kr.co.palank.pocketserver.linux

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Date
import java.util.concurrent.TimeUnit

sealed class ProotState {
    object Idle : ProotState()
    object Running : ProotState()
    object Stopped : ProotState()
    data class Error(val message: String) : ProotState()
}

class ProotManager(private val context: Context) {

    private val _state = MutableStateFlow<ProotState>(ProotState.Idle)
    val state: StateFlow<ProotState> = _state

    private var process: Process? = null

    private val rootPath: String
        get() = context.filesDir.absolutePath

    private val rootfsPath: String
        get() = File(context.filesDir, "ubuntu").absolutePath

    private val supportPath: String
        get() = ProotBinaryManager.getSupportDir(context).absolutePath

    val isRunning: Boolean
        get() = process?.isAlive == true

    /**
     * Build PRoot command with all UserLand-style bind mounts.
     * Direct invocation replaces execInProot.sh to eliminate shell argument splitting.
     * Logic ported from execInProot.sh lines 13-35 and 84.
     */
    /**
     * @param fakeRoot true = pass -0 (fake UID 0) for install commands and Dropbear auth.
     *                 false = no -0, real Android UID visible.
     * @param killOnExit true = --kill-on-exit (for one-shot commands).
     *                   false = omit (for long-running daemons like Dropbear that fork per connection).
     */
    internal fun buildProotCommand(
        vararg userCommand: String,
        fakeRoot: Boolean = true,
        killOnExit: Boolean = true
    ): List<String> {
        // Select PRoot binary variant based on .proot_version file
        val prootVersionFile = File(rootfsPath, "support/.proot_version")
        val prootSuffix = if (prootVersionFile.exists()) prootVersionFile.readText().trim() else ""
        val prootBinary = File(supportPath, "proot$prootSuffix").absolutePath

        val cmd = mutableListOf<String>()
        cmd.add(prootBinary)
        cmd.add("-r")
        cmd.add(rootfsPath)
        cmd.add("-v")
        cmd.add("-1")

        if (fakeRoot) {
            cmd.add("-0")
        }
        cmd.add("--link2symlink")
        if (killOnExit) {
            cmd.add("--kill-on-exit")
        }
        cmd.add("-w")
        cmd.add("/root")

        // Fixed bind mounts (execInProot.sh line 84)
        for (bind in listOf("/sys", "/dev", "/proc", "/data", "/mnt")) {
            cmd.add("-b")
            cmd.add(bind)
        }
        cmd.add("-b")
        cmd.add("/proc/mounts:/etc/mtab")
        cmd.add("-b")
        cmd.add("/system")

        // Conditional: /apex (may not exist on Android 9)
        if (File("/apex").exists()) {
            cmd.add("-b")
            cmd.add("/apex")
        }

        // Conditional: /linkerconfig/ld.config.txt (Android 10+)
        if (File("/linkerconfig/ld.config.txt").exists()) {
            cmd.add("-b")
            cmd.add("/linkerconfig/ld.config.txt")
        }

        cmd.add("-b")
        cmd.add("/:/host-rootfs")

        // Rootfs support bind mounts
        cmd.add("-b")
        cmd.add("$rootfsPath/support/:/support")

        val nosudo = File(rootfsPath, "support/nosudo")
        if (nosudo.exists()) {
            cmd.add("-b")
            cmd.add("${nosudo.absolutePath}:/usr/local/bin/sudo")
        }

        val userlandProfile = File(rootfsPath, "support/userland_profile.sh")
        if (userlandProfile.exists()) {
            cmd.add("-b")
            cmd.add("${userlandProfile.absolutePath}:/etc/profile.d/userland_profile.sh")
        }

        val ldSoPreload = File(rootfsPath, "support/ld.so.preload")
        ensureNetstubInLdPreload(ldSoPreload)
        if (ldSoPreload.exists()) {
            cmd.add("-b")
            cmd.add("${ldSoPreload.absolutePath}:/etc/ld.so.preload")
        }

        cmd.add("-b")
        cmd.add("$supportPath:/support/common")

        // Conditional bind mounts (execInProot.sh lines 13-35)
        if (!File("/dev/ashmem").canRead()) {
            cmd.add("-b")
            cmd.add("$rootfsPath/tmp:/dev/ashmem")
        }
        if (!File("/dev/shm").canRead()) {
            cmd.add("-b")
            cmd.add("$rootfsPath/tmp:/dev/shm")
        }
        if (!File("/proc/stat").canRead()) {
            val numProc = Runtime.getRuntime().availableProcessors()
            val statFile = if (numProc <= 4) "stat4" else "stat8"
            val statPath = File(supportPath, statFile)
            if (statPath.exists()) {
                cmd.add("-b")
                cmd.add("${statPath.absolutePath}:/proc/stat")
            }
        }
        if (!File("/proc/uptime").canRead()) {
            val uptimePath = File(supportPath, "uptime")
            if (uptimePath.exists()) {
                cmd.add("-b")
                cmd.add("${uptimePath.absolutePath}:/proc/uptime")
            }
        }
        if (!File("/proc/version").canRead()) {
            val osVersion = System.getProperty("os.version") ?: "4.0.0"
            val versionFile = File(supportPath, "version")
            versionFile.writeText("Linux version $osVersion (fake@userland) #1 ${ Date()}\n")
            cmd.add("-b")
            cmd.add("${versionFile.absolutePath}:/proc/version")
        }
        // /proc/net/ is restricted on Android 10+ (EACCES).
        // Node.js os.networkInterfaces() and many Linux tools need these files.
        if (!File("/proc/net/dev").canRead()) {
            val netDevFile = File(supportPath, "proc_net_dev")
            generateProcNetDev(netDevFile)
            cmd.add("-b")
            cmd.add("${netDevFile.absolutePath}:/proc/net/dev")
        }
        if (!File("/proc/net/if_inet6").canRead()) {
            val ifInet6File = File(supportPath, "proc_net_if_inet6")
            ifInet6File.writeText("00000000000000000000000000000001 01 80 10 80       lo\n")
            cmd.add("-b")
            cmd.add("${ifInet6File.absolutePath}:/proc/net/if_inet6")
        }

        // Layer 2: inner /usr/bin/env -i clears PRoot's own env vars
        // (LD_LIBRARY_PATH pointing to Android support dir, PROOT_* vars)
        // before running the user command — UserLand's 2-layer pattern.
        cmd.add("/usr/bin/env")
        cmd.add("-i")
        for ((key, value) in buildInnerEnvironment()) {
            cmd.add("$key=$value")
        }

        // User command
        cmd.addAll(userCommand)

        return cmd
    }

    /**
     * Outer environment: variables needed by the PRoot binary itself.
     * These run on the Android side via "busybox env -i" (Layer 1).
     * They must NOT leak into user processes inside PRoot.
     */
    internal fun buildOuterEnvironment(): Map<String, String> = mapOf(
        "LD_LIBRARY_PATH" to supportPath,
        "PROOT_TMP_DIR" to "$rootfsPath/support/",
        "PROOT_LOADER" to ProotBinaryManager.getLoaderPath(context),
        "PROOT_LOADER_32" to ProotBinaryManager.getLoader32Path(context)
    )

    /**
     * Inner environment: clean user-facing variables for processes inside PRoot.
     * Applied via "/usr/bin/env -i" inside PRoot (Layer 2).
     * This prevents PRoot's LD_LIBRARY_PATH (Android support dir) and PROOT_*
     * from leaking into Dropbear child processes, which would break glibc
     * getpwnam()/getspnam() → chdir("/") failed.
     */
    internal fun buildInnerEnvironment(): Map<String, String> {
        ensureNetFixScript()
        ensureBrowserBridgeScript()
        return mapOf(
            "HOME" to "/root",
            "TERM" to "xterm-256color",
            "LANG" to "en_US.UTF-8",
            "BROWSER" to "/usr/local/bin/xdg-open",
            "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        )
    }

    /**
     * Android restricts netlink sockets in PRoot, so Node.js os.networkInterfaces()
     * throws EACCES. This creates:
     * 1. /usr/local/lib/net-fix.js — patches os.networkInterfaces() with fallback
     *    that reads the real WiFi IP from /support/wifi_ip (written by NetworkMonitor)
     * 2. NODE_OPTIONS set in MULTIPLE locations to cover both interactive and
     *    non-interactive sessions (daemon mode, nohup, cron, etc.):
     *    - /etc/profile.d/pocketserver.sh (interactive login shells)
     *    - /etc/environment (PAM/login, ssh non-interactive)
     *    - /etc/bash.bashrc (system-wide bash, including non-login)
     *    - /root/.bashrc (root user's shell)
     *
     * Always re-writes the files to pick up any improvements to the script.
     */
    internal fun ensureNetFixScript() {
        val nodeOptions = "--require /usr/local/lib/net-fix.js"

        // 1. net-fix.js — reads /support/wifi_ip for real WiFi IP
        val netFixFile = File(rootfsPath, "usr/local/lib/net-fix.js")
        netFixFile.parentFile?.mkdirs()
        netFixFile.writeText("""
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
""".trimIndent() + "\n")

        // 2a. /etc/profile.d/pocketserver.sh (interactive login shells via SSH)
        val profileDir = File(rootfsPath, "etc/profile.d")
        val profileScript = File(profileDir, "pocketserver.sh")
        profileDir.mkdirs()
        profileScript.writeText("export NODE_OPTIONS=\"$nodeOptions\"\n")
        profileScript.setExecutable(true, false)

        // 2b. /etc/environment (read by PAM/login, ssh ForceCommand, systemd services)
        val envFile = File(rootfsPath, "etc/environment")
        ensureLineInFile(envFile, "NODE_OPTIONS=", "NODE_OPTIONS=\"$nodeOptions\"")

        // 2c. /etc/bash.bashrc (system-wide bash — non-login interactive + non-interactive)
        val bashrcSystem = File(rootfsPath, "etc/bash.bashrc")
        ensureLineInFile(bashrcSystem, "NODE_OPTIONS=", "export NODE_OPTIONS=\"$nodeOptions\"")

        // 2d. /root/.bashrc (root user's shell)
        val bashrcRoot = File(rootfsPath, "root/.bashrc")
        ensureLineInFile(bashrcRoot, "NODE_OPTIONS=", "export NODE_OPTIONS=\"$nodeOptions\"")
    }

    /**
     * PRoot → Android 브라우저 브리지 스크립트.
     *
     * OAuth 로그인 등 브라우저가 필요한 CLI 도구(Codex CLI, OpenClaw openai-codex 등)가
     * xdg-open 또는 $BROWSER를 호출하면, URL을 /support/open_url 파일에 기록.
     * Engine 앱의 BrowserBridge(FileObserver)가 이를 감지하여 Android 브라우저를 실행.
     *
     * localhost 콜백은 PRoot이 Android와 네트워크 네임스페이스를 공유하므로 직접 동작.
     */
    internal fun ensureBrowserBridgeScript() {
        val xdgOpen = File(rootfsPath, "usr/local/bin/xdg-open")
        xdgOpen.parentFile?.mkdirs()
        xdgOpen.writeText("""
#!/bin/bash
# PocketServer Browser Bridge — PRoot에서 Android 브라우저 열기
# BrowserBridge(FileObserver)가 /support/open_url 감지 → Intent(ACTION_VIEW) 실행
URL="${'$'}1"
if [ -z "${'$'}URL" ]; then
  echo "Usage: xdg-open <URL>" >&2
  exit 1
fi
echo "${'$'}URL" > /support/open_url
# 브라우저가 열릴 시간을 확보
sleep 2
exit 0
""".trimIndent() + "\n")
        xdgOpen.setExecutable(true, false)

        // $BROWSER 환경변수도 .bashrc에 설정 (interactive shell 대응)
        val bashrcRoot = File(rootfsPath, "root/.bashrc")
        ensureLineInFile(bashrcRoot, "export BROWSER=", "export BROWSER=/usr/local/bin/xdg-open")
    }

    /**
     * Ensure a line with the given prefix exists in a file.
     * If a line starting with [prefix] already exists, replace it.
     * If not, append the [fullLine] at the end.
     */
    private fun ensureLineInFile(file: File, prefix: String, fullLine: String) {
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            file.writeText("$fullLine\n")
            return
        }
        val lines = file.readLines().toMutableList()
        val idx = lines.indexOfFirst { it.trimStart().startsWith(prefix) }
        if (idx >= 0) {
            if (lines[idx].trim() == fullLine) return // already correct
            lines[idx] = fullLine
        } else {
            lines.add(fullLine)
        }
        file.writeText(lines.joinToString("\n") + "\n")
    }

    /**
     * Ensure libnetstub.so is registered in ld.so.preload if the .so file exists.
     * Called on every PRoot command build to handle upgrades from older installations
     * that didn't have netstub. Safe to call repeatedly (idempotent).
     */
    private fun ensureNetstubInLdPreload(ldPreloadFile: File) {
        val netstubSo = File(rootfsPath, "usr/local/lib/libnetstub.so")
        if (!netstubSo.exists() || netstubSo.length() == 0L) return

        val netstubPath = "/usr/local/lib/libnetstub.so"

        if (!ldPreloadFile.exists()) {
            ldPreloadFile.parentFile?.mkdirs()
            ldPreloadFile.writeText("$netstubPath\n")
            Log.i(TAG, "Created ld.so.preload with libnetstub.so")
            return
        }

        val content = ldPreloadFile.readText()
        if (content.contains(netstubPath)) return

        ldPreloadFile.writeText(content.trimEnd() + "\n" + netstubPath + "\n")
        Log.i(TAG, "Appended libnetstub.so to ld.so.preload")
    }

    /**
     * Wrap a PRoot command with "busybox env -i VAR=val ... proot ..."
     * This truly clears the Android Zygote-inherited environment at the OS level.
     * ProcessBuilder.environment().clear() does NOT work on Android — Zygote injects
     * env vars (BOOTCLASSPATH, ANDROID_DATA, LD_LIBRARY_PATH, etc.) that persist
     * and interfere with PRoot's glibc-based passwd/shadow lookups inside Dropbear.
     * UserLand's execInProot.sh uses "/usr/bin/env -i" for the same reason.
     */
    internal fun buildEnvWrappedCommand(
        vararg userCommand: String,
        fakeRoot: Boolean = true,
        killOnExit: Boolean = true
    ): List<String> {
        val busybox = ProotBinaryManager.getBusyboxPath(context)
        val envVars = buildOuterEnvironment()
        val prootCmd = buildProotCommand(*userCommand, fakeRoot = fakeRoot, killOnExit = killOnExit)

        val cmd = mutableListOf<String>()
        cmd.add(busybox)
        cmd.add("env")
        cmd.add("-i")
        for ((key, value) in envVars) {
            cmd.add("$key=$value")
        }
        cmd.addAll(prootCmd)
        return cmd
    }

    /**
     * Start PRoot with an interactive shell (direct invocation, no execInProot.sh)
     */
    suspend fun start() = withContext(Dispatchers.IO) {
        if (isRunning) {
            Log.w(TAG, "PRoot is already running")
            return@withContext
        }

        try {
            ProotBinaryManager.ensureReady(context)
            ensureRootfsDirectories()

            val cmd = buildEnvWrappedCommand("/bin/bash", "--login")

            Log.i(TAG, "Starting PRoot: ${cmd.joinToString(" ")}")

            val pb = ProcessBuilder(cmd)
            pb.directory(File(rootfsPath))
            pb.redirectErrorStream(true)

            process = pb.start()
            _state.value = ProotState.Running
            Log.i(TAG, "PRoot process started (direct invocation)")

            Thread {
                try {
                    BufferedReader(InputStreamReader(process!!.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            Log.d(TAG, "[PRoot] $line")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "PRoot output stream closed", e)
                }
            }.apply {
                isDaemon = true
                name = "proot-stdout"
                start()
            }

            Thread {
                try {
                    val exitCode = process!!.waitFor()
                    Log.i(TAG, "PRoot process exited with code: $exitCode")
                    if (_state.value is ProotState.Running) {
                        _state.value = if (exitCode == 0) ProotState.Stopped
                        else ProotState.Error("PRoot exited with code $exitCode")
                    }
                } catch (e: InterruptedException) {
                    Log.w(TAG, "PRoot wait interrupted")
                }
            }.apply {
                isDaemon = true
                name = "proot-monitor"
                start()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start PRoot", e)
            _state.value = ProotState.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Execute a one-shot command inside PRoot (direct invocation, no execInProot.sh)
     */
    suspend fun exec(vararg command: String): ExecResult = withContext(Dispatchers.IO) {
        ProotBinaryManager.ensureReady(context)
        ensureRootfsDirectories()

        val cmd = buildEnvWrappedCommand(*command)

        Log.d(TAG, "exec: ${command.joinToString(" ")}")

        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)

        val proc = pb.start()
        val output = proc.inputStream.bufferedReader().readText()
        val exitCode = proc.waitFor()

        Log.d(TAG, "exec result (code=$exitCode): ${output.take(500)}")
        ExecResult(exitCode, output)
    }

    /**
     * Execute a command inside PRoot with line-by-line stdout streaming.
     *
     * 일반 exec()와 달리 stdout을 한 줄씩 읽으면서 [onLine] 콜백을 호출.
     * OAuth 인증 등 프로세스가 URL을 출력하면 실시간으로 감지할 수 있음.
     *
     * @param onLine 각 stdout 줄마다 호출되는 콜백
     * @param timeoutMs 최대 대기 시간 (밀리초, 기본 3분)
     */
    suspend fun execWithStreaming(
        vararg command: String,
        onLine: ((String) -> Unit)? = null,
        timeoutMs: Long = 180_000,
    ): ExecResult = withContext(Dispatchers.IO) {
        ProotBinaryManager.ensureReady(context)
        ensureRootfsDirectories()

        val cmd = buildEnvWrappedCommand(*command)

        Log.d(TAG, "execWithStreaming: ${command.joinToString(" ")}")

        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)

        val proc = pb.start()
        val output = StringBuilder()

        val readerThread = Thread {
            try {
                proc.inputStream.bufferedReader().forEachLine { line ->
                    output.appendLine(line)
                    Log.d(TAG, "[stream] $line")
                    try { onLine?.invoke(line) } catch (e: Exception) {
                        Log.w(TAG, "onLine callback error", e)
                    }
                }
            } catch (_: Exception) {}
        }.apply { isDaemon = true; name = "proot-stream"; start() }

        val completed = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!completed) {
            Log.w(TAG, "execWithStreaming timed out after ${timeoutMs}ms, killing process")
            proc.destroyForcibly()
        }
        readerThread.join(5000)

        val exitCode = if (completed) proc.exitValue() else -1
        Log.d(TAG, "execWithStreaming result (code=$exitCode): ${output.toString().take(500)}")
        ExecResult(exitCode, output.toString())
    }

    fun stop() {
        process?.let { proc ->
            try {
                // Kill entire process tree first (UserLand killProcTree.sh pattern)
                killProcessTree(proc)
                proc.outputStream?.close()
                proc.destroy()
                if (proc.isAlive) proc.destroyForcibly()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping PRoot", e)
            }
            Unit
        }
        process = null
        _state.value = ProotState.Stopped
        Log.i(TAG, "PRoot stopped")
    }

    private fun killProcessTree(proc: Process) {
        try {
            val pid = try {
                val field = proc.javaClass.getDeclaredField("pid")
                field.isAccessible = true
                field.getInt(proc)
            } catch (e: Exception) {
                return
            }

            val busybox = ProotBinaryManager.getBusyboxPath(context)
            val killScript = File(supportPath, "killProcTree.sh").absolutePath

            if (!File(killScript).exists()) return

            val pb = ProcessBuilder(busybox, "sh", killScript, pid.toString())
            pb.environment()["LIB_PATH"] = supportPath
            pb.redirectErrorStream(true)
            val killProc = pb.start()
            killProc.inputStream.bufferedReader().readText()
            killProc.waitFor()
            Log.i(TAG, "Process tree killed for PID $pid")
        } catch (e: Exception) {
            Log.w(TAG, "killProcessTree failed", e)
        }
    }

    private fun ensureRootfsDirectories() {
        File(rootfsPath, "tmp").mkdirs()
        File(rootfsPath, "support").mkdirs()
    }

    private fun generateProcNetDev(file: File) {
        val header = """Inter-|   Receive                                                |  Transmit
 face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed"""
        val lo = "    lo:       0       0    0    0    0     0          0         0        0       0    0    0    0     0       0          0"
        val wlan = " wlan0:       0       0    0    0    0     0          0         0        0       0    0    0    0     0       0          0"
        file.writeText("$header\n$lo\n$wlan\n")
    }

    data class ExecResult(val exitCode: Int, val output: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    companion object {
        private const val TAG = "ProotManager"
    }
}
