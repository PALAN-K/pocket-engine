package kr.co.palank.pocketserver.linux

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.security.SecureRandom
import java.util.zip.GZIPInputStream

sealed class InstallState {
    object Idle : InstallState()
    data class Progress(val percentage: Int, val message: String) : InstallState()
    object Completed : InstallState()
    data class Error(val error: String) : InstallState()
}

data class DebPackage(
    val name: String,
    val primaryUrl: String,
    val fallbackUrl: String? = null
)

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

                        when {
                            entry.isSymbolicLink -> {
                                outputFile.parentFile?.mkdirs()
                                try {
                                    val linkTarget = Paths.get(entry.linkName)
                                    val linkPath = outputFile.toPath()
                                    if (Files.exists(linkPath) || Files.isSymbolicLink(linkPath)) {
                                        Files.delete(linkPath)
                                    }
                                    Files.createSymbolicLink(linkPath, linkTarget)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Symlink failed: ${entry.name} -> ${entry.linkName}: ${e.message}")
                                }
                            }
                            entry.isDirectory -> {
                                outputFile.mkdirs()
                            }
                            else -> {
                                outputFile.parentFile?.mkdirs()
                                FileOutputStream(outputFile).use { output ->
                                    tarInput.copyTo(output)
                                }
                                if (entry.mode and 0b001_001_001 != 0) {
                                    outputFile.setExecutable(true, false)
                                }
                            }
                        }

                        processedSize += entry.size
                        val extractPercent = if (totalSize > 0)
                            (processedSize * 100 / (totalSize * 3)).toInt().coerceAtMost(100)
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
        val rootfsDir = File(context.filesDir, "ubuntu")

        _state.value = InstallState.Progress(61, "DNS 설정 중...")
        // resolv.conf가 심볼릭 링크일 수 있으므로 먼저 삭제
        prootManager.exec("/bin/sh", "-c",
            "rm -f /etc/resolv.conf && echo 'nameserver 8.8.8.8' > /etc/resolv.conf && echo 'nameserver 8.8.4.4' >> /etc/resolv.conf"
        )

        _state.value = InstallState.Progress(63, "로케일 설정 중...")
        prootManager.exec("/bin/sh", "-c",
            "echo 'en_US.UTF-8 UTF-8' > /etc/locale.gen"
        )

        _state.value = InstallState.Progress(65, "타임존 설정 중...")
        prootManager.exec("/bin/sh", "-c",
            "ln -sf /usr/share/zoneinfo/Asia/Seoul /etc/localtime"
        )

        // APT sandbox config — PRoot에서 _apt 사용자 권한 문제 방지
        _state.value = InstallState.Progress(66, "APT 설정 중...")
        val aptConfDir = File(rootfsDir, "etc/apt/apt.conf.d")
        aptConfDir.mkdirs()
        File(aptConfDir, "99proot-sandbox").writeText(
            "APT::Sandbox::User \"root\";\n"
        )
        Log.i(TAG, "APT sandbox config written")

        _state.value = InstallState.Progress(67, "사용자 계정 생성 중...")
        val password = generatePassword()
        prefs.edit().putString(KEY_SSH_PASSWORD, password).apply()

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

    /**
     * Dropbear SSH 서버 설치
     * Strategy 1: apt-get (PRoot 내 서브프로세스 실행)
     * Strategy 2: Java-side .deb 파일 직접 추출 (Samsung ENOSYS 대응)
     */
    private suspend fun installDropbear() {
        _state.value = InstallState.Progress(79, "패키지 목록 업데이트 중...")
        prootManager.exec("/bin/sh", "-c", "apt-get update -qq")

        // Strategy 1: apt-get install
        _state.value = InstallState.Progress(81, "Dropbear SSH 설치 중 (apt-get)...")
        val aptSuccess = tryAptGetInstall()

        if (aptSuccess) {
            Log.i(TAG, "Dropbear installed via apt-get (Strategy 1)")
        } else {
            // Strategy 2: Java-side .deb extraction
            Log.w(TAG, "apt-get failed, falling back to Java .deb extraction (Strategy 2)")
            _state.value = InstallState.Progress(83, "SSH 서버 설치 중 (대체 방식)...")
            tryJavaDebExtract()
            Log.i(TAG, "Dropbear installed via Java .deb extraction (Strategy 2)")
        }

        // Dropbear 설정 (공통)
        _state.value = InstallState.Progress(89, "SSH 서버 설정 중...")
        configureDropbear()

        Log.i(TAG, "Dropbear installed and configured")
    }

    private suspend fun tryAptGetInstall(): Boolean {
        val result = prootManager.exec("/bin/sh", "-c",
            "DEBIAN_FRONTEND=noninteractive apt-get install -y -qq " +
            "-o APT::Sandbox::User=root " +
            "-o Dpkg::Options::=--force-unsafe-io " +
            "dropbear"
        )
        if (!result.isSuccess) {
            Log.w(TAG, "apt-get install failed: ${result.output.takeLast(300)}")
            return false
        }
        return verifyDropbearBinary()
    }

    private suspend fun tryJavaDebExtract() = withContext(Dispatchers.IO) {
        val rootfsDir = File(context.filesDir, "ubuntu")
        val cacheDir = File(context.cacheDir, "deb-cache")
        cacheDir.mkdirs()

        // PRoot+Python zstd 추출 스크립트를 rootfs /tmp에 준비
        ensureZstExtractScript(rootfsDir)

        for ((index, deb) in DROPBEAR_DEBS.withIndex()) {
            val progress = 83 + (index * 5 / DROPBEAR_DEBS.size)
            _state.value = InstallState.Progress(progress, "패키지 설치 중: ${deb.name}...")

            try {
                val debFile = downloadDebPackage(deb, cacheDir)
                val dataTar = extractDataTarFromDeb(debFile)

                if (dataTar.name.endsWith(".tar.zst") || dataTar.name.endsWith(".tar.zstd")) {
                    // zst: PRoot + Python3 + libzstd.so ctypes로 디코딩+추출
                    extractZstViaProot(dataTar, rootfsDir)
                } else {
                    // xz/gz: Java에서 직접 처리
                    extractDataTarToRootfs(dataTar, rootfsDir)
                }

                debFile.delete()
                dataTar.delete()
                Log.i(TAG, "Extracted .deb: ${deb.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract ${deb.name}: ${e.message}")
                throw RuntimeException("패키지 설치 실패 (${deb.name}): ${e.message}")
            }
        }

        // 스크립트 정리
        File(rootfsDir, "tmp/zst_extract.py").delete()
        cacheDir.deleteRecursively()
    }

    /**
     * Python3 + ctypes + libzstd.so를 사용하는 zstd 추출 스크립트를 rootfs에 준비
     */
    private fun ensureZstExtractScript(rootfsDir: File) {
        val tmpDir = File(rootfsDir, "tmp")
        tmpDir.mkdirs()
        File(tmpDir, "zst_extract.py").writeText("""
import ctypes, tarfile, io, sys, os

lib = ctypes.CDLL('libzstd.so.1')
lib.ZSTD_getFrameContentSize.argtypes = [ctypes.c_void_p, ctypes.c_size_t]
lib.ZSTD_getFrameContentSize.restype = ctypes.c_ulonglong
lib.ZSTD_decompress.argtypes = [ctypes.c_void_p, ctypes.c_size_t, ctypes.c_void_p, ctypes.c_size_t]
lib.ZSTD_decompress.restype = ctypes.c_size_t
lib.ZSTD_isError.argtypes = [ctypes.c_size_t]
lib.ZSTD_isError.restype = ctypes.c_uint

src_path = sys.argv[1]
with open(src_path, 'rb') as f:
    src = f.read()

buf = ctypes.create_string_buffer(src)
dst_size = lib.ZSTD_getFrameContentSize(buf, len(src))
if dst_size >= 0xFFFFFFFFFFFFFFFE:
    print('ERROR: cannot determine decompressed size', file=sys.stderr)
    sys.exit(1)

dst = ctypes.create_string_buffer(int(dst_size))
result = lib.ZSTD_decompress(dst, int(dst_size), buf, len(src))
if lib.ZSTD_isError(result):
    print('ERROR: zstd decompression failed', file=sys.stderr)
    sys.exit(1)

with tarfile.open(fileobj=io.BytesIO(dst.raw[:result])) as tf:
    tf.extractall('/')

print('OK: {} -> {} bytes'.format(len(src), result))
""".trimIndent())
        Log.i(TAG, "zst_extract.py written to rootfs /tmp")
    }

    /**
     * data.tar.zst를 rootfs /tmp에 복사 후 PRoot+Python3으로 디코딩+추출
     * Python3는 ctypes로 rootfs의 libzstd.so.1을 직접 호출
     */
    private suspend fun extractZstViaProot(dataTarFile: File, rootfsDir: File) {
        val tmpDir = File(rootfsDir, "tmp")
        val tmpFile = File(tmpDir, dataTarFile.name)
        dataTarFile.copyTo(tmpFile, overwrite = true)

        val result = prootManager.exec("/bin/sh", "-c",
            "python3 /tmp/zst_extract.py /tmp/${dataTarFile.name}"
        )

        tmpFile.delete()

        if (!result.isSuccess) {
            throw RuntimeException("zstd 추출 실패 (Python): ${result.output.takeLast(300)}")
        }

        Log.i(TAG, "zstd extracted via PRoot+Python: ${result.output.takeLast(100)}")
    }

    private fun downloadDebPackage(deb: DebPackage, cacheDir: File): File {
        val targetFile = File(cacheDir, "${deb.name}.deb")
        val urls = listOfNotNull(deb.primaryUrl, deb.fallbackUrl)
        var lastError: Exception? = null

        for (urlStr in urls) {
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 30_000
                conn.readTimeout = 60_000
                conn.instanceFollowRedirects = true
                conn.connect()

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    conn.inputStream.buffered().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.i(TAG, "Downloaded ${deb.name}: ${targetFile.length()} bytes from $urlStr")
                    return targetFile
                } else {
                    Log.w(TAG, "HTTP ${conn.responseCode} for $urlStr")
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Download failed for $urlStr: ${e.message}")
            }
        }

        throw RuntimeException("모든 URL에서 다운로드 실패: ${deb.name}", lastError)
    }

    /**
     * .deb 파일에서 data.tar.* 추출
     * .deb = ar 아카이브 (debian-binary + control.tar.* + data.tar.*)
     */
    private fun extractDataTarFromDeb(debFile: File): File {
        ArArchiveInputStream(debFile.inputStream().buffered()).use { arInput ->
            var entry = arInput.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("data.tar")) {
                    val dataTarFile = File(debFile.parentFile, entry.name)
                    FileOutputStream(dataTarFile).use { output ->
                        arInput.copyTo(output)
                    }
                    Log.i(TAG, "Extracted ${entry.name} (${dataTarFile.length()} bytes)")
                    return dataTarFile
                }
                entry = arInput.nextEntry
            }
        }
        throw RuntimeException("data.tar.* not found in ${debFile.name}")
    }

    /**
     * data.tar.* 를 rootfs에 풀기 (xz/gz만 — zst는 extractZstViaProot으로 처리)
     */
    private fun extractDataTarToRootfs(dataTarFile: File, rootfsDir: File) {
        val name = dataTarFile.name

        val decompressedStream: InputStream = when {
            name.endsWith(".tar.xz") -> {
                XZInputStream(dataTarFile.inputStream().buffered())
            }
            name.endsWith(".tar.gz") || name.endsWith(".tar.gzip") -> {
                GZIPInputStream(dataTarFile.inputStream().buffered())
            }
            name.endsWith(".tar") -> {
                dataTarFile.inputStream().buffered()
            }
            else -> throw RuntimeException("지원하지 않는 압축 형식: $name (zst는 extractZstViaProot 사용)")
        }

        TarArchiveInputStream(BufferedInputStream(decompressedStream)).use { tarInput ->
            var entry = tarInput.nextEntry
            while (entry != null) {
                // ./ 접두사 제거
                val entryName = entry.name.removePrefix("./")
                if (entryName.isEmpty()) {
                    entry = tarInput.nextEntry
                    continue
                }

                val outputFile = File(rootfsDir, entryName)

                // Path traversal 방지
                if (!outputFile.canonicalPath.startsWith(rootfsDir.canonicalPath)) {
                    Log.w(TAG, "Skipping path traversal: $entryName")
                    entry = tarInput.nextEntry
                    continue
                }

                when {
                    entry.isSymbolicLink -> {
                        outputFile.parentFile?.mkdirs()
                        try {
                            val linkTarget = Paths.get(entry.linkName)
                            val linkPath = outputFile.toPath()
                            if (Files.exists(linkPath) || Files.isSymbolicLink(linkPath)) {
                                Files.delete(linkPath)
                            }
                            Files.createSymbolicLink(linkPath, linkTarget)
                        } catch (e: Exception) {
                            Log.w(TAG, "Symlink failed: $entryName -> ${entry.linkName}")
                        }
                    }
                    entry.isDirectory -> {
                        outputFile.mkdirs()
                    }
                    else -> {
                        outputFile.parentFile?.mkdirs()
                        FileOutputStream(outputFile).use { output ->
                            tarInput.copyTo(output)
                        }
                        if (entry.mode and 0b001_001_001 != 0) {
                            outputFile.setExecutable(true, false)
                        }
                    }
                }

                entry = tarInput.nextEntry
            }
        }
    }

    private fun verifyDropbearBinary(): Boolean {
        val rootfsDir = File(context.filesDir, "ubuntu")
        val paths = listOf("usr/sbin/dropbear", "usr/bin/dropbear")
        return paths.any { File(rootfsDir, it).exists() }
    }

    private suspend fun configureDropbear() {
        val rootfsDir = File(context.filesDir, "ubuntu")

        // Dropbear 설정 디렉토리 생성 (Java-side)
        File(rootfsDir, "etc/dropbear").mkdirs()
        File(rootfsDir, "etc/default").mkdirs()

        prootManager.exec("/bin/sh", "-c",
            "echo 'DROPBEAR_PORT=2022' > /etc/default/dropbear && " +
            "echo 'DROPBEAR_EXTRA_ARGS=\"-w\"' >> /etc/default/dropbear && " +
            "echo 'NO_START=0' >> /etc/default/dropbear"
        )

        // 호스트 키 생성 (dropbearkey가 존재할 때만)
        val hasDropbearkey = listOf("usr/bin/dropbearkey", "usr/sbin/dropbearkey")
            .any { File(rootfsDir, it).exists() }

        if (hasDropbearkey) {
            prootManager.exec("/bin/sh", "-c",
                "dropbearkey -t rsa -f /etc/dropbear/dropbear_rsa_host_key 2>/dev/null || true"
            )
            prootManager.exec("/bin/sh", "-c",
                "dropbearkey -t ecdsa -f /etc/dropbear/dropbear_ecdsa_host_key 2>/dev/null || true"
            )
        } else {
            Log.w(TAG, "dropbearkey not found, host keys will be generated on first SSH connection")
        }
    }

    private suspend fun verifyInstallation() {
        _state.value = InstallState.Progress(93, "설치 검증 중...")

        val bashCheck = prootManager.exec("/bin/bash", "-c", "echo 'ok'")
        if (!bashCheck.isSuccess || !bashCheck.output.contains("ok")) {
            throw RuntimeException("PRoot 환경 검증 실패")
        }

        // Java 파일시스템으로 Dropbear 바이너리 존재 확인 (PRoot which 대신)
        if (!verifyDropbearBinary()) {
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
            "https://cloud-images.ubuntu.com/minimal/releases/noble/release/" +
            "ubuntu-24.04-minimal-cloudimg-arm64-root.tar.xz"

        // Dropbear .deb packages — Java-side extraction (Strategy 2)
        // Noble (24.04) primary + Jammy (22.04) fallback
        // data.tar.zst는 aircompressor 순수 Java zstd 디코더로 처리
        // URL base: ports.ubuntu.com/ubuntu-ports/pool/ (arm64 아카이브 경로)
        private const val PORTS_BASE = "https://ports.ubuntu.com/ubuntu-ports/pool"

        val DROPBEAR_DEBS = listOf(
            DebPackage(
                name = "libtommath1",
                primaryUrl = "$PORTS_BASE/main/libt/libtommath/libtommath1_1.2.0-6build3_arm64.deb",
                fallbackUrl = "$PORTS_BASE/main/libt/libtommath/libtommath1_1.2.0-3_arm64.deb"
            ),
            DebPackage(
                name = "libtomcrypt1",
                primaryUrl = "$PORTS_BASE/universe/libt/libtomcrypt/libtomcrypt1_1.18.2+dfsg-7build1_arm64.deb",
                fallbackUrl = "$PORTS_BASE/universe/libt/libtomcrypt/libtomcrypt1_1.18.2-5_arm64.deb"
            ),
            DebPackage(
                name = "dropbear-bin",
                primaryUrl = "$PORTS_BASE/universe/d/dropbear/dropbear-bin_2022.83-4_arm64.deb",
                fallbackUrl = "$PORTS_BASE/universe/d/dropbear/dropbear-bin_2020.81-5_arm64.deb"
            )
        )

        fun generatePassword(length: Int = 12): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            val random = SecureRandom()
            return (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
        }
    }
}
