package kr.co.palank.pocketserver.linux

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

sealed class InstallState {
    object Idle : InstallState()
    data class Progress(val percentage: Int, val message: String) : InstallState()
    object Completed : InstallState()
    data class Error(val error: String) : InstallState()
}

class InstallManager(private val context: Context) {

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state

    private val prootManager = ProotManager(context)
    private val swapManager = SwapManager(context, prootManager)
    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val isInstalled: Boolean
        get() = prefs.getBoolean(KEY_INSTALLED, false)

    val sshPassword: String?
        get() = prefs.getString(KEY_SSH_PASSWORD, null)

    suspend fun startInstallation() {
        if (isInstalled) {
            _state.value = InstallState.Completed
            return
        }

        try {
            // Step 1: PRoot 바이너리 추출 (0-5%)
            _state.value = InstallState.Progress(0, "PRoot 환경 초기화 중...")
            withContext(Dispatchers.IO) {
                ProotBinaryManager.ensureReady(context)
            }
            _state.value = InstallState.Progress(5, "PRoot 준비 완료")

            // Step 2: rootfs 다운로드 (5-40%)
            val rootfsDir = File(context.filesDir, "ubuntu")
            if (!rootfsDir.exists() || rootfsDir.list()?.isEmpty() != false) {
                _state.value = InstallState.Progress(5, "Ubuntu 24.04 다운로드 중...")
                val tarball = downloadRootfs()

                // Step 3: 압축 해제 (40-60%)
                _state.value = InstallState.Progress(40, "시스템 압축 해제 중...")
                extractRootfs(tarball, rootfsDir)
                tarball.delete()
            } else {
                _state.value = InstallState.Progress(60, "기존 rootfs 감지됨, 건너뜀")
            }

            // Step 4: 시스템 설정 (60-72%)
            _state.value = InstallState.Progress(60, "시스템 설정 중...")
            configureSystem()

            // Step 5: 스왑 메모리 설정 (72-78%)
            _state.value = InstallState.Progress(72, "스왑 메모리 설정 중...")
            setupSwap()

            // Step 6: Dropbear 설치 (78-92%)
            _state.value = InstallState.Progress(78, "SSH 서버 설치 중...")
            installDropbear()

            // Step 7: 최종 검증 (92-100%)
            _state.value = InstallState.Progress(92, "설치 검증 중...")
            verifyInstallation()

            prefs.edit().putBoolean(KEY_INSTALLED, true).apply()
            _state.value = InstallState.Progress(100, "설치 완료!")
            _state.value = InstallState.Completed
            Log.i(TAG, "Installation completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Installation failed", e)
            _state.value = InstallState.Error(e.message ?: "알 수 없는 오류 발생")
        }
    }

    private suspend fun downloadRootfs(): File = withContext(Dispatchers.IO) {
        val targetFile = File(context.cacheDir, "ubuntu-rootfs.tar.xz")
        if (targetFile.exists()) targetFile.delete()

        val url = URL(ROOTFS_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw RuntimeException("rootfs 다운로드 실패: HTTP ${connection.responseCode}")
        }

        val totalSize = connection.contentLengthLong
        var downloadedSize = 0L

        connection.inputStream.buffered().use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedSize += bytesRead
                    if (totalSize > 0) {
                        val downloadPercent = (downloadedSize * 100 / totalSize).toInt()
                        // 다운로드 진행률: 5% ~ 40%
                        val overallPercent = 5 + (downloadPercent * 35 / 100)
                        _state.value = InstallState.Progress(
                            overallPercent,
                            "Ubuntu 24.04 다운로드 중... ${downloadedSize / 1_048_576}MB / ${totalSize / 1_048_576}MB"
                        )
                    }
                }
            }
        }

        Log.i(TAG, "rootfs downloaded: ${targetFile.length()} bytes")
        targetFile
    }

    private suspend fun extractRootfs(tarball: File, targetDir: File) = withContext(Dispatchers.IO) {
        if (!targetDir.exists()) targetDir.mkdirs()

        val totalSize = tarball.length()
        var processedSize = 0L

        tarball.inputStream().buffered().use { fileInput ->
            XZInputStream(fileInput).use { xzInput ->
                TarArchiveInputStream(BufferedInputStream(xzInput)).use { tarInput ->
                    var entry = tarInput.nextEntry
                    while (entry != null) {
                        val outputFile = File(targetDir, entry.name)

                        // Path traversal 방지
                        if (!outputFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                            Log.w(TAG, "Skipping path traversal entry: ${entry.name}")
                            entry = tarInput.nextEntry
                            continue
                        }

                        if (entry.isDirectory) {
                            outputFile.mkdirs()
                        } else {
                            outputFile.parentFile?.mkdirs()
                            FileOutputStream(outputFile).use { output ->
                                tarInput.copyTo(output)
                            }
                            // 실행 권한 보존
                            if (entry.mode and 0b001_001_001 != 0) {
                                outputFile.setExecutable(true, false)
                            }
                        }

                        // 심볼릭 링크 처리 (PRoot에서 link2symlink으로 대체)
                        if (entry.isSymbolicLink) {
                            // PRoot --link2symlink 플래그로 처리
                            Log.d(TAG, "Symlink: ${entry.name} -> ${entry.linkName}")
                        }

                        processedSize += entry.size
                        // 압축 해제 진행률: 40% ~ 60%
                        val extractPercent = if (totalSize > 0)
                            (processedSize * 100 / (totalSize * 3)).toInt().coerceAtMost(100) // xz 압축비 ~3x
                        else 50
                        _state.value = InstallState.Progress(
                            40 + (extractPercent * 20 / 100),
                            "시스템 압축 해제 중..."
                        )

                        entry = tarInput.nextEntry
                    }
                }
            }
        }

        Log.i(TAG, "rootfs extracted to ${targetDir.absolutePath}")
    }

    private suspend fun configureSystem() {
        _state.value = InstallState.Progress(61, "DNS 설정 중...")
        prootManager.exec("/bin/sh", "-c",
            "echo 'nameserver 8.8.8.8' > /etc/resolv.conf && echo 'nameserver 8.8.4.4' >> /etc/resolv.conf"
        )

        _state.value = InstallState.Progress(63, "로케일 설정 중...")
        prootManager.exec("/bin/sh", "-c",
            "echo 'en_US.UTF-8 UTF-8' > /etc/locale.gen"
        )

        _state.value = InstallState.Progress(65, "타임존 설정 중...")
        prootManager.exec("/bin/sh", "-c",
            "ln -sf /usr/share/zoneinfo/Asia/Seoul /etc/localtime"
        )

        _state.value = InstallState.Progress(67, "사용자 계정 생성 중...")
        val password = generatePassword()
        prefs.edit().putString(KEY_SSH_PASSWORD, password).apply()

        // 사용자 생성
        prootManager.exec("/bin/sh", "-c",
            "id pocketserver >/dev/null 2>&1 || useradd -m -s /bin/bash pocketserver"
        )
        prootManager.exec("/bin/sh", "-c",
            "echo 'pocketserver:$password' | chpasswd"
        )

        Log.i(TAG, "System configured, user 'pocketserver' created")
    }

    private suspend fun setupSwap() {
        _state.value = InstallState.Progress(73, "스왑 파일 생성 중... (약 1-2분 소요)")
        val success = swapManager.setup()
        if (!success) {
            Log.w(TAG, "Swap setup failed, continuing without swap")
        }
        _state.value = InstallState.Progress(78, "스왑 메모리 설정 완료")
    }

    private suspend fun installDropbear() {
        _state.value = InstallState.Progress(79, "패키지 목록 업데이트 중...")
        val updateResult = prootManager.exec("/bin/sh", "-c",
            "apt-get update -qq"
        )
        if (!updateResult.isSuccess) {
            Log.w(TAG, "apt-get update warning: ${updateResult.output.takeLast(200)}")
        }

        _state.value = InstallState.Progress(83, "Dropbear SSH 서버 설치 중...")
        val installResult = prootManager.exec("/bin/sh", "-c",
            "DEBIAN_FRONTEND=noninteractive apt-get install -y -qq dropbear"
        )
        if (!installResult.isSuccess) {
            throw RuntimeException("Dropbear 설치 실패: ${installResult.output.takeLast(300)}")
        }

        _state.value = InstallState.Progress(89, "SSH 서버 설정 중...")
        // Dropbear 기본 설정: 포트 2022, 비밀번호 인증
        prootManager.exec("/bin/sh", "-c",
            "mkdir -p /etc/dropbear && " +
            "echo 'DROPBEAR_PORT=2022' > /etc/default/dropbear && " +
            "echo 'DROPBEAR_EXTRA_ARGS=\"-w\"' >> /etc/default/dropbear && " +
            "echo 'NO_START=0' >> /etc/default/dropbear"
        )

        // 호스트 키 생성
        prootManager.exec("/bin/sh", "-c",
            "dropbearkey -t rsa -f /etc/dropbear/dropbear_rsa_host_key 2>/dev/null || true"
        )
        prootManager.exec("/bin/sh", "-c",
            "dropbearkey -t ecdsa -f /etc/dropbear/dropbear_ecdsa_host_key 2>/dev/null || true"
        )

        Log.i(TAG, "Dropbear installed and configured")
    }

    private suspend fun verifyInstallation() {
        _state.value = InstallState.Progress(93, "설치 검증 중...")

        val bashCheck = prootManager.exec("/bin/bash", "-c", "echo 'ok'")
        if (!bashCheck.isSuccess || !bashCheck.output.contains("ok")) {
            throw RuntimeException("PRoot 환경 검증 실패")
        }

        val dropbearCheck = prootManager.exec("/bin/sh", "-c", "which dropbear")
        if (!dropbearCheck.isSuccess) {
            throw RuntimeException("Dropbear 설치 검증 실패")
        }

        val userCheck = prootManager.exec("/bin/sh", "-c", "id pocketserver")
        if (!userCheck.isSuccess) {
            throw RuntimeException("사용자 계정 검증 실패")
        }

        _state.value = InstallState.Progress(98, "검증 완료")
        Log.i(TAG, "Installation verification passed")
    }

    fun reset() {
        val rootfsDir = File(context.filesDir, "ubuntu")
        if (rootfsDir.exists()) rootfsDir.deleteRecursively()
        prefs.edit()
            .remove(KEY_INSTALLED)
            .remove(KEY_SSH_PASSWORD)
            .apply()
        _state.value = InstallState.Idle
        Log.i(TAG, "Installation reset")
    }

    companion object {
        private const val TAG = "InstallManager"
        private const val PREFS_NAME = "pocketserver_prefs"
        private const val KEY_INSTALLED = "server_installed"
        private const val KEY_SSH_PASSWORD = "ssh_password"

        private const val ROOTFS_URL =
            "https://cloud-images.ubuntu.com/minimal/releases/24.04/release/" +
            "ubuntu-24.04-minimal-cloudimg-arm64-root.tar.xz"

        fun generatePassword(length: Int = 12): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            val random = SecureRandom()
            return (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
        }
    }
}
