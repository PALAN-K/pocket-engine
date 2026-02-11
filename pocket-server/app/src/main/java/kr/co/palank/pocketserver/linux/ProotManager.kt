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
    internal fun buildInnerEnvironment(): Map<String, String> = mapOf(
        "HOME" to "/root",
        "TERM" to "xterm-256color",
        "LANG" to "en_US.UTF-8",
        "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    )

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

    data class ExecResult(val exitCode: Int, val output: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    companion object {
        private const val TAG = "ProotManager"
    }
}
