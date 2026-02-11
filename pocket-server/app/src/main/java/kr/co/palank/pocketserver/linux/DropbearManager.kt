package kr.co.palank.pocketserver.linux

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

sealed class DropbearState {
    object Stopped : DropbearState()
    object Starting : DropbearState()
    object Running : DropbearState()
    data class Error(val message: String) : DropbearState()
}

class DropbearManager(
    private val context: Context,
    private val prootManager: ProotManager
) {
    private val _state = MutableStateFlow<DropbearState>(DropbearState.Stopped)
    val state: StateFlow<DropbearState> = _state

    private var sshProcess: Process? = null

    val isRunning: Boolean
        get() = _state.value is DropbearState.Running

    /**
     * Start SSH server inside PRoot.
     *
     * Strategy:
     * 1. Try OpenSSH sshd first — handles chdir() failure as warning (non-fatal).
     *    On Samsung devices, Dropbear's forked grandchild (session handler) fails
     *    chdir() because PRoot's ptrace path translation breaks at double-fork level.
     *    Dropbear treats this as fatal → session dies. OpenSSH logs a warning and continues.
     *
     * 2. Fall back to Dropbear if sshd is not available.
     */
    suspend fun start() = withContext(Dispatchers.IO) {
        if (isRunning) {
            Log.w(TAG, "SSH server is already running")
            return@withContext
        }

        try {
            _state.value = DropbearState.Starting

            ProotBinaryManager.ensureReady(context)

            val rootfsPath = "${context.filesDir.absolutePath}/ubuntu"
            File(rootfsPath, "tmp").mkdirs()
            File(rootfsPath, "support").mkdirs()

            // Extract chdir_fix.so for Samsung PRoot compatibility (assets → rootfs, cached)
            ensureChdirFix(rootfsPath)

            val sshdExists = File(rootfsPath, "usr/sbin/sshd").exists()

            if (sshdExists) {
                Log.i(TAG, "OpenSSH sshd found, using as primary SSH server")
                ensureSshdConfig(rootfsPath)
                startSshd()
            } else {
                Log.i(TAG, "sshd not found, falling back to Dropbear")
                startDropbear()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SSH server", e)
            _state.value = DropbearState.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Ensure OpenSSH sshd prerequisites: config file, host keys, sshd user, /run/sshd dir.
     */
    private suspend fun ensureSshdConfig(rootfsPath: String) {
        val sshdConfigFile = File(rootfsPath, "etc/ssh/sshd_config_pocketserver")
        val requiredConfig =
            "Port 2022\n" +
            "PermitRootLogin yes\n" +
            "PasswordAuthentication yes\n" +
            "PubkeyAuthentication no\n" +
            "UsePAM no\n" +
            "StrictModes no\n" +
            "HostKey /etc/ssh/ssh_host_ed25519_key\n" +
            "HostKey /etc/ssh/ssh_host_rsa_key\n" +
            "PrintMotd no\n" +
            "AcceptEnv LANG LC_*\n" +
            "Subsystem sftp /usr/lib/openssh/sftp-server\n"

        if (!sshdConfigFile.exists()) {
            sshdConfigFile.writeText(requiredConfig)
            Log.i(TAG, "sshd_config_pocketserver written")
        } else {
            // Migrate existing config: ensure PubkeyAuthentication no is present
            // PRoot privsep child crashes during pubkey auth on Android 12+ (getgroups ENOSYS)
            val existing = sshdConfigFile.readText()
            if (!existing.contains("PubkeyAuthentication")) {
                sshdConfigFile.writeText(requiredConfig)
                Log.i(TAG, "sshd_config_pocketserver migrated (added PubkeyAuthentication no)")
            }
        }

        // Ensure /run/sshd exists (required for privilege separation)
        File(rootfsPath, "run/sshd").mkdirs()
        // Ensure /var/run/sshd exists (some sshd versions check this)
        File(rootfsPath, "var/run/sshd").mkdirs()

        // Generate host keys if missing
        val hasHostKeys = File(rootfsPath, "etc/ssh/ssh_host_ed25519_key").exists()
        if (!hasHostKeys) {
            Log.i(TAG, "Generating SSH host keys...")
            prootManager.exec("/bin/sh", "-c", "ssh-keygen -A 2>&1")
        }

        // Ensure sshd user exists (required for privilege separation)
        val sshdUserExists = File(rootfsPath, "etc/passwd").readText().contains("sshd:")
        if (!sshdUserExists) {
            Log.i(TAG, "Creating sshd user for privilege separation...")
            prootManager.exec("/bin/sh", "-c",
                "useradd -r -d /run/sshd -s /usr/sbin/nologin sshd 2>/dev/null || true"
            )
        }
    }

    /**
     * Ensure chdir_fix.so exists in rootfs for Samsung PRoot compatibility.
     *
     * On Samsung devices (SELinux Enforcing), PRoot's ptrace path translation breaks
     * in double-forked (grandchild) processes. SSH servers fork a child per session,
     * and chdir()/chroot() fail with ENOSYS in those children:
     *   - Dropbear: chdir(pw_dir) → fatal exit (dropbear_exit)
     *   - OpenSSH: chroot("/run/sshd") → privsep failure
     *
     * This LD_PRELOAD .so intercepts the calls and returns 0 on failure,
     * allowing SSH sessions to proceed. Safe on all devices — when chdir()
     * succeeds normally, the original return value is passed through unchanged.
     *
     * Strategy:
     * 1. Copy pre-compiled ARM64 .so from app assets (works on all devices)
     * 2. Fallback: dynamic compilation inside PRoot (if assets copy fails)
     */
    private suspend fun ensureChdirFix(rootfsPath: String) {
        val fixSo = File(rootfsPath, "usr/lib/chdir_fix.so")
        if (fixSo.exists() && fixSo.length() > 0) {
            Log.d(TAG, "chdir_fix.so already exists (${fixSo.length()} bytes)")
            return
        }

        // Strategy 1: Copy pre-compiled .so from nativeLibraryDir (via support symlink)
        try {
            Log.i(TAG, "Copying chdir_fix.so from support dir...")
            fixSo.parentFile?.mkdirs()
            val supportChdir = File(ProotBinaryManager.getSupportDir(context), "chdir_fix")
            if (supportChdir.exists()) {
                supportChdir.inputStream().use { input ->
                    fixSo.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                fixSo.setReadable(true, false)
                fixSo.setExecutable(true, false)
                Log.i(TAG, "chdir_fix.so copied from support (${fixSo.length()} bytes)")
                return
            } else {
                Log.w(TAG, "chdir_fix not found in support dir")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy chdir_fix.so from support: ${e.message}")
        }

        // Strategy 2: Dynamic compilation inside PRoot (fallback)
        Log.i(TAG, "Fallback: compiling chdir_fix.so inside PRoot...")
        val cSource = """
            #define RTLD_NEXT ((void *)-1L)
            extern void *dlsym(void *handle, const char *symbol);
            int chdir(const char *p) {
                int (*f)(const char *) = (int (*)(const char *))dlsym(RTLD_NEXT, "chdir");
                if (!f) return 0;
                int r = f(p);
                return (r < 0) ? 0 : r;
            }
            int fchdir(int fd) {
                int (*f)(int) = (int (*)(int))dlsym(RTLD_NEXT, "fchdir");
                if (!f) return 0;
                int r = f(fd);
                return (r < 0) ? 0 : r;
            }
            int chroot(const char *p) {
                int (*f)(const char *) = (int (*)(const char *))dlsym(RTLD_NEXT, "chroot");
                if (!f) return 0;
                int r = f(p);
                return (r < 0) ? 0 : r;
            }
            /* getgroups() returns ENOSYS in PRoot on some kernels/devices.
               sshd privsep child calls getgroups() during auth — return 0 (no groups) on failure. */
            int getgroups(int size, unsigned int *list) {
                int (*f)(int, unsigned int *) = (int (*)(int, unsigned int *))dlsym(RTLD_NEXT, "getgroups");
                if (!f) return 0;
                int r = f(size, list);
                return (r < 0) ? 0 : r;
            }
        """.trimIndent()

        File(rootfsPath, "tmp/chdir_fix.c").writeText(cSource)

        try {
            val result = prootManager.exec(
                "/bin/sh", "-c",
                "if command -v gcc >/dev/null 2>&1; then " +
                "  gcc -shared -fPIC -nostartfiles -o /usr/lib/chdir_fix.so /tmp/chdir_fix.c -ldl 2>&1; " +
                "elif command -v tcc >/dev/null 2>&1; then " +
                "  tcc -shared -o /usr/lib/chdir_fix.so /tmp/chdir_fix.c -ldl 2>&1; " +
                "elif command -v cc >/dev/null 2>&1; then " +
                "  cc -shared -fPIC -nostartfiles -o /usr/lib/chdir_fix.so /tmp/chdir_fix.c -ldl 2>&1; " +
                "else " +
                "  apt-get install -y -qq tcc 2>&1 && " +
                "  tcc -shared -o /usr/lib/chdir_fix.so /tmp/chdir_fix.c -ldl 2>&1; " +
                "fi"
            )

            if (fixSo.exists()) {
                Log.i(TAG, "chdir_fix.so compiled inside PRoot (${fixSo.length()} bytes)")
            } else {
                Log.w(TAG, "chdir_fix.so compilation failed: ${result.output.takeLast(500)}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compile chdir_fix.so: ${e.message}", e)
        }

        File(rootfsPath, "tmp/chdir_fix.c").delete()
    }

    /**
     * Get LD_PRELOAD prefix for SSH server commands.
     * If chdir_fix.so exists, prepend export to inherit to child processes.
     */
    private fun getLdPreloadPrefix(): String {
        val rootfsPath = "${context.filesDir.absolutePath}/ubuntu"
        val hasFixSo = File(rootfsPath, "usr/lib/chdir_fix.so").exists()
        return if (hasFixSo) "export LD_PRELOAD=/usr/lib/chdir_fix.so; " else ""
    }

    /**
     * Start OpenSSH sshd inside PRoot.
     * -D: foreground mode (don't daemonize)
     * -f: config file
     * -e: log to stderr (visible in logcat)
     *
     * With chdir_fix.so LD_PRELOAD:
     *   - chroot("/run/sshd") → wrapper returns 0 (privsep works)
     *   - chdir("/root") → wrapper returns 0 (session setup works)
     */
    private suspend fun startSshd() {
        val ldPreload = getLdPreloadPrefix()
        val cmd = prootManager.buildEnvWrappedCommand(
            "/bin/sh", "-c",
            "${ldPreload}/usr/sbin/sshd -D -e -f /etc/ssh/sshd_config_pocketserver",
            fakeRoot = true,
            killOnExit = false
        )

        Log.i(TAG, "Starting OpenSSH sshd: ${cmd.joinToString(" ")}")

        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)
        sshProcess = pb.start()

        startOutputReader("sshd")
        startProcessMonitor("sshd")

        delay(2000)
        if (sshProcess?.isAlive == true) {
            _state.value = DropbearState.Running
            Log.i(TAG, "OpenSSH sshd running on port 2022")
        } else {
            Log.w(TAG, "sshd failed to start, falling back to Dropbear")
            startDropbear()
        }
    }

    /**
     * Start Dropbear SSH server inside PRoot (fallback).
     * Shell wrapper + foreground mode (UserLand dropbearWrapper.sh pattern).
     * With chdir_fix.so: chdir(pw_dir) failure in session handler is non-fatal.
     */
    private suspend fun startDropbear() {
        val ldPreload = getLdPreloadPrefix()
        val cmd = prootManager.buildEnvWrappedCommand(
            "/bin/sh", "-c",
            "${ldPreload}/usr/sbin/dropbear -F -E -p 2022 -R",
            fakeRoot = true,
            killOnExit = false
        )

        Log.i(TAG, "Starting Dropbear: ${cmd.joinToString(" ")}")

        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)
        sshProcess = pb.start()

        startOutputReader("Dropbear")
        startProcessMonitor("Dropbear")

        delay(1500)
        if (sshProcess?.isAlive == true) {
            _state.value = DropbearState.Running
            Log.i(TAG, "Dropbear SSH server running on port 2022")
        } else {
            _state.value = DropbearState.Error("SSH server failed to start")
        }
    }

    private fun startOutputReader(serverName: String) {
        Thread {
            try {
                sshProcess!!.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.d(TAG, "[$serverName] $line")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "$serverName output stream closed", e)
            }
        }.apply {
            isDaemon = true
            name = "ssh-stdout"
            start()
        }
    }

    private fun startProcessMonitor(serverName: String) {
        Thread {
            try {
                val exitCode = sshProcess!!.waitFor()
                Log.i(TAG, "$serverName exited with code: $exitCode")
                if (_state.value is DropbearState.Running || _state.value is DropbearState.Starting) {
                    _state.value = if (exitCode == 0) DropbearState.Stopped
                    else DropbearState.Error("$serverName exited with code $exitCode")
                }
            } catch (e: InterruptedException) {
                Log.w(TAG, "$serverName wait interrupted")
            }
        }.apply {
            isDaemon = true
            name = "ssh-monitor"
            start()
        }
    }

    fun stop() {
        sshProcess?.let { proc ->
            try {
                killProcessTree(proc)
                proc.destroy()
                if (proc.isAlive) proc.destroyForcibly()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping SSH server", e)
            }
            Unit
        }
        sshProcess = null
        _state.value = DropbearState.Stopped
        Log.i(TAG, "SSH server stopped")
    }

    private fun killProcessTree(proc: Process) {
        try {
            val pid = getPid(proc) ?: return

            val supportDir = ProotBinaryManager.getSupportDir(context).absolutePath
            val busybox = ProotBinaryManager.getBusyboxPath(context)
            val killScript = "$supportDir/killProcTree.sh"

            if (!File(killScript).exists()) {
                Log.w(TAG, "killProcTree.sh not found, falling back to Process.destroy()")
                return
            }

            val pb = ProcessBuilder(busybox, "sh", killScript, pid.toString())
            pb.environment()["LIB_PATH"] = supportDir
            pb.redirectErrorStream(true)
            val killProc = pb.start()
            val output = killProc.inputStream.bufferedReader().readText()
            killProc.waitFor()
            Log.i(TAG, "killProcTree(pid=$pid): $output")
        } catch (e: Exception) {
            Log.w(TAG, "killProcessTree failed, will use Process.destroy()", e)
        }
    }

    fun isDropbearAliveInProcTree(): Boolean {
        val proc = sshProcess ?: return false
        if (!proc.isAlive) return false

        return try {
            val pid = getPid(proc) ?: return proc.isAlive

            val supportDir = ProotBinaryManager.getSupportDir(context).absolutePath
            val busybox = ProotBinaryManager.getBusyboxPath(context)
            val checkScript = "$supportDir/isServerInProcTree.sh"

            if (!File(checkScript).exists()) {
                Log.w(TAG, "isServerInProcTree.sh not found, falling back to Process.isAlive")
                return proc.isAlive
            }

            val pb = ProcessBuilder(busybox, "sh", checkScript, pid.toString())
            pb.environment()["LIB_PATH"] = supportDir
            pb.redirectErrorStream(true)
            val checkProc = pb.start()
            checkProc.inputStream.bufferedReader().readText()
            val exitCode = checkProc.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            Log.w(TAG, "isDropbearAliveInProcTree check failed", e)
            proc.isAlive
        }
    }

    private fun getPid(proc: Process): Int? {
        return try {
            val field = proc.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(proc)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot get PID via reflection", e)
            null
        }
    }

    suspend fun restart() {
        stop()
        delay(500)
        start()
    }

    companion object {
        private const val TAG = "DropbearManager"
        const val SSH_PORT = 2022
    }
}
