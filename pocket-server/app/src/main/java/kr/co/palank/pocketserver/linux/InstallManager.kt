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

    private val _state = MutableStateFlow<InstallState>(
        if (context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_INSTALLED, false)) InstallState.Completed
        else InstallState.Idle
    )
    val state: StateFlow<InstallState> = _state

    private val prootManager = ProotManager(context)
    private val swapManager = SwapManager(context, prootManager)
    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val isInstalled: Boolean
        get() = prefs.getBoolean(KEY_INSTALLED, false)

    val sshPassword: String?
        get() = prefs.getString(KEY_SSH_PASSWORD, null)

    /**
     * Resumable installation — checks step markers and skips already-completed steps.
     * On failure, completed steps are preserved so next retry resumes from the failure point.
     * Inspired by UserLand's .success_filesystem_extraction marker pattern.
     */
    suspend fun startInstallation() {
        if (isInstalled) {
            _state.value = InstallState.Completed
            return
        }

        try {
            val rootfsDir = File(context.filesDir, "ubuntu")

            // Step 1: PRoot 바이너리 추출 (0-5%)
            if (!stepDone(STEP_PROOT_EXTRACTED)) {
                _state.value = InstallState.Progress(0, "PRoot 환경 초기화 중...")
                withContext(Dispatchers.IO) {
                    ProotBinaryManager.ensureReady(context)
                }
                markStep(STEP_PROOT_EXTRACTED)
            }
            _state.value = InstallState.Progress(5, "PRoot 준비 완료")

            // Step 2: rootfs 다운로드 (5-40%)
            if (!stepDone(STEP_ROOTFS_DOWNLOADED)) {
                if (!rootfsDir.exists() || rootfsDir.list()?.isEmpty() != false) {
                    _state.value = InstallState.Progress(5, "Ubuntu 24.04 다운로드 중...")
                    val tarball = downloadRootfs()

                    // Step 3: 압축 해제 (40-60%)
                    if (!stepDone(STEP_ROOTFS_EXTRACTED)) {
                        _state.value = InstallState.Progress(40, "시스템 압축 해제 중...")
                        extractRootfs(tarball, rootfsDir)
                        markStep(STEP_ROOTFS_EXTRACTED)
                    }
                    tarball.delete()
                    markStep(STEP_ROOTFS_DOWNLOADED)
                } else {
                    _state.value = InstallState.Progress(60, "기존 rootfs 감지됨, 건너뜀")
                    markStep(STEP_ROOTFS_DOWNLOADED)
                    markStep(STEP_ROOTFS_EXTRACTED)
                }
            } else {
                _state.value = InstallState.Progress(60, "rootfs 다운로드 완료 (이전 세션)")
            }

            // Step 3.5: UserLand 지원 파일 복사 (rootfs/support/)
            if (!stepDone(STEP_SUPPORT_COPIED)) {
                _state.value = InstallState.Progress(58, "지원 파일 설정 중...")
                withContext(Dispatchers.IO) {
                    ProotBinaryManager.setupRootfsSupport(context, rootfsDir)
                }
                markStep(STEP_SUPPORT_COPIED)
            }

            // Step 4: 시스템 설정 (60-72%)
            if (!stepDone(STEP_CONFIGURED)) {
                _state.value = InstallState.Progress(60, "시스템 설정 중...")
                configureSystem()
                markStep(STEP_CONFIGURED)
            }

            // Step 5: 스왑 메모리 설정 (72-78%)
            if (!stepDone(STEP_SWAP_DONE)) {
                _state.value = InstallState.Progress(72, "스왑 메모리 설정 중...")
                setupSwap()
                markStep(STEP_SWAP_DONE)
            }

            // Step 6: Dropbear 설치 (78-89%)
            if (!stepDone(STEP_DROPBEAR_INSTALLED)) {
                _state.value = InstallState.Progress(78, "SSH 서버 설치 중...")
                installDropbear()
                markStep(STEP_DROPBEAR_INSTALLED)
            }

            // Step 6.5: libnetstub.so 설치 (89-92%)
            // getifaddrs() LD_PRELOAD shim — Android 11+ netlink EACCES 대응
            // Python, Go, Rust, Node.js 등 모든 언어에서 네트워크 인터페이스 조회 가능
            if (!stepDone(STEP_NETSTUB_INSTALLED)) {
                _state.value = InstallState.Progress(89, "네트워크 호환성 라이브러리 설치 중...")
                installNetStub()
                markStep(STEP_NETSTUB_INSTALLED)
            }

            // 비밀번호 설정은 step 체크와 무관하게 매번 실행
            // rootfs 재추출 시 /etc/shadow가 초기화되므로 항상 재설정 필요
            _state.value = InstallState.Progress(92, "SSH 비밀번호 설정 중...")
            ensureSshPassword()

            // Step 7: 최종 검증 (95-100%)
            _state.value = InstallState.Progress(95, "설치 검증 중...")
            verifyInstallation()

            prefs.edit().putBoolean(KEY_INSTALLED, true).apply()
            _state.value = InstallState.Progress(100, "설치 완료!")
            _state.value = InstallState.Completed
            Log.i(TAG, "Installation completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Installation failed (completed steps preserved for retry)", e)
            _state.value = InstallState.Error(e.message ?: "알 수 없는 오류 발생")
        }
    }

    private fun stepDone(key: String): Boolean = prefs.getBoolean(key, false)

    private fun markStep(key: String) {
        prefs.edit().putBoolean(key, true).apply()
        Log.i(TAG, "Step completed: $key")
    }

    /**
     * Download rootfs with mirror fallback + exponential backoff retry.
     * Tries each mirror URL up to MAX_RETRIES_PER_URL times before moving to next mirror.
     */
    /**
     * Download rootfs with mirror fallback + exponential backoff retry + HTTP Range resume.
     * Partial downloads are preserved across retries so resume works automatically.
     * Only deletes partial file when switching to a different mirror (incompatible file).
     */
    private suspend fun downloadRootfs(): File = withContext(Dispatchers.IO) {
        val targetFile = File(context.cacheDir, "ubuntu-rootfs.tar.xz")

        var lastError: Exception? = null

        for ((mirrorIndex, mirrorUrl) in ROOTFS_URLS.withIndex()) {
            // When switching mirrors, delete partial file (different server = incompatible resume)
            if (mirrorIndex > 0 && targetFile.exists()) {
                targetFile.delete()
                Log.i(TAG, "Switching mirror, deleted partial file")
            }

            for (attempt in 1..MAX_RETRIES_PER_URL) {
                try {
                    _state.value = InstallState.Progress(
                        5, "Ubuntu 24.04 다운로드 중... (미러 ${mirrorIndex + 1}/${ROOTFS_URLS.size}, 시도 $attempt/$MAX_RETRIES_PER_URL)"
                    )

                    downloadFile(mirrorUrl, targetFile)

                    if (targetFile.length() > 0) {
                        Log.i(TAG, "rootfs downloaded: ${targetFile.length()} bytes from mirror #${mirrorIndex + 1}")
                        return@withContext targetFile
                    }
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "Download failed (mirror #${mirrorIndex + 1}, attempt $attempt/$MAX_RETRIES_PER_URL): ${e.message}")
                    // Do NOT delete partial file — next retry will resume via Range header

                    if (attempt < MAX_RETRIES_PER_URL) {
                        val delayMs = INITIAL_RETRY_DELAY_MS * (1L shl (attempt - 1)) // 2s, 4s, 8s
                        _state.value = InstallState.Progress(
                            5, "다운로드 재시도 대기 중... (${delayMs / 1000}초)"
                        )
                        kotlinx.coroutines.delay(delayMs)
                    }
                }
            }
        }

        throw RuntimeException("모든 미러에서 rootfs 다운로드 실패", lastError)
    }

    /**
     * Download a single file with HTTP Range resume support + progress tracking.
     * If a partial file already exists, sends Range header to resume from that point.
     * This eliminates the need for Android DownloadManager while providing resume capability.
     */
    private fun downloadFile(urlStr: String, targetFile: File) {
        val existingSize = if (targetFile.exists()) targetFile.length() else 0L

        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 90_000
        connection.instanceFollowRedirects = true

        // Resume: request bytes from where we left off
        if (existingSize > 0) {
            connection.setRequestProperty("Range", "bytes=$existingSize-")
            Log.i(TAG, "Resuming download from byte $existingSize")
        }

        connection.connect()

        val responseCode = connection.responseCode

        // 206 = Partial Content (resume accepted), 200 = full download
        val isResume = responseCode == HttpURLConnection.HTTP_PARTIAL
        if (responseCode != HttpURLConnection.HTTP_OK && !isResume) {
            throw RuntimeException("HTTP $responseCode from $urlStr")
        }

        // If server doesn't support Range (returned 200 instead of 206), restart from scratch
        val downloadedSoFar = if (isResume) existingSize else 0L
        val contentLength = connection.contentLengthLong
        val totalSize = if (isResume) downloadedSoFar + contentLength else contentLength

        if (!isResume && existingSize > 0) {
            Log.w(TAG, "Server doesn't support Range, restarting download from scratch")
            targetFile.delete()
        }

        var downloadedSize = downloadedSoFar
        val appendMode = isResume

        connection.inputStream.buffered().use { input ->
            FileOutputStream(targetFile, appendMode).use { output ->
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

        // Ensure required directories exist (UserLand pattern)
        // /root is critical — Dropbear chdir(pw_dir) fails if /root doesn't exist
        for (dir in listOf("tmp", "run", "var/run", "etc/profile.d", "etc/dropbear", "root", "home/pocketserver")) {
            File(rootfsDir, dir).mkdirs()
        }

        // UserLand userland_profile.sh pattern: clear Android LD_PRELOAD/LD_LIBRARY_PATH
        File(rootfsDir, "etc/profile.d/pocketserver.sh").writeText(
            "#!/bin/sh\nunset LD_PRELOAD\nunset LD_LIBRARY_PATH\n" +
            "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\n"
        )
        Log.i(TAG, "Profile script written to /etc/profile.d/pocketserver.sh")

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

        // 비밀번호 설정은 ensureSshPassword()에서 처리 (매 설치 시 실행)

        Log.i(TAG, "System configured")
    }

    /**
     * SSH 비밀번호를 항상 설정/재설정한다.
     * rootfs가 재추출되면 /etc/shadow가 초기화되므로 step 체크와 무관하게 매번 실행.
     */
    internal suspend fun ensureSshPassword() {
        val password = sshPassword ?: generatePassword()
        prefs.edit().putString(KEY_SSH_PASSWORD, password).apply()
        Log.i(TAG, "ensureSshPassword: setting password (length=${password.length})")

        // chpasswd로 비밀번호 설정 (최대 3회 시도)
        var result: ProotManager.ExecResult? = null
        for (attempt in 1..3) {
            result = prootManager.exec("/bin/sh", "-c",
                "echo 'root:$password' | chpasswd 2>&1"
            )
            if (result.isSuccess) break
            Log.w(TAG, "chpasswd root attempt $attempt/3 failed: ${result.output.take(200)}")
            if (attempt < 3) kotlinx.coroutines.delay(500)
        }
        Log.i(TAG, "chpasswd root: success=${result?.isSuccess}, output=${result?.output?.take(200)}")

        prootManager.exec("/bin/sh", "-c",
            "id pocketserver >/dev/null 2>&1 || useradd -m -s /bin/bash pocketserver"
        )
        result = null
        for (attempt in 1..3) {
            result = prootManager.exec("/bin/sh", "-c",
                "echo 'pocketserver:$password' | chpasswd 2>&1"
            )
            if (result.isSuccess) break
            Log.w(TAG, "chpasswd pocketserver attempt $attempt/3 failed: ${result.output.take(200)}")
            if (attempt < 3) kotlinx.coroutines.delay(500)
        }
        Log.i(TAG, "chpasswd pocketserver: success=${result?.isSuccess}, output=${result?.output?.take(200)}")

        // /etc/shadow 해시를 /etc/passwd에 복사 (Dropbear가 /etc/passwd만 읽는 경우 대비)
        // sed 대신 awk 사용 — 해시에 포함된 $ 문자가 sed를 깨뜨리는 문제 방지
        result = prootManager.exec("/bin/sh", "-c",
            "awk -F: 'NR==FNR{hash[\$1]=\$2;next} \$1 in hash{\$2=hash[\$1]} {print}' OFS=: /etc/shadow /etc/passwd > /tmp/passwd.tmp && mv /tmp/passwd.tmp /etc/passwd && chmod 644 /etc/passwd 2>&1"
        )
        Log.i(TAG, "passwd hash copy: success=${result.isSuccess}, output=${result.output.take(200)}")

        // 검증: /etc/passwd에서 root 해시가 실제로 설정되었는지 확인
        result = prootManager.exec("/bin/sh", "-c",
            "head -1 /etc/passwd | cut -d: -f2 | head -c 10"
        )
        Log.i(TAG, "root passwd hash prefix: ${result.output.take(20)}")

        prootManager.exec("/bin/sh", "-c",
            "chmod 700 /root && test -f /root/.bashrc || echo '# .bashrc' > /root/.bashrc"
        )

        Log.i(TAG, "SSH passwords ensured (root + pocketserver)")
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
     * Python3 + ctypes + libzstd.so 스트리밍 API를 사용하는 zstd 추출 스크립트를 rootfs에 준비
     * ZSTD_decompressStream 사용 — content-size 헤더 없는 프레임도 처리 가능
     */
    private fun ensureZstExtractScript(rootfsDir: File) {
        val tmpDir = File(rootfsDir, "tmp")
        tmpDir.mkdirs()
        File(tmpDir, "zst_extract.py").writeText("""
import ctypes, tarfile, io, sys

lib = ctypes.CDLL('libzstd.so.1')

lib.ZSTD_createDStream.restype = ctypes.c_void_p
lib.ZSTD_freeDStream.argtypes = [ctypes.c_void_p]
lib.ZSTD_initDStream.argtypes = [ctypes.c_void_p]
lib.ZSTD_initDStream.restype = ctypes.c_size_t
lib.ZSTD_decompressStream.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p]
lib.ZSTD_decompressStream.restype = ctypes.c_size_t
lib.ZSTD_isError.argtypes = [ctypes.c_size_t]
lib.ZSTD_isError.restype = ctypes.c_uint
lib.ZSTD_DStreamOutSize.restype = ctypes.c_size_t

class ZSTD_inBuffer(ctypes.Structure):
    _fields_ = [("src", ctypes.c_void_p), ("size", ctypes.c_size_t), ("pos", ctypes.c_size_t)]

class ZSTD_outBuffer(ctypes.Structure):
    _fields_ = [("dst", ctypes.c_void_p), ("size", ctypes.c_size_t), ("pos", ctypes.c_size_t)]

src_path = sys.argv[1]
with open(src_path, 'rb') as f:
    compressed = f.read()

print('DEBUG: file size = {} bytes, magic = {}'.format(
    len(compressed), compressed[:4].hex() if len(compressed) >= 4 else 'too short'), file=sys.stderr)

dstream = lib.ZSTD_createDStream()
ret = lib.ZSTD_initDStream(dstream)
if lib.ZSTD_isError(ret):
    print('ERROR: ZSTD_initDStream failed', file=sys.stderr)
    sys.exit(1)

out_size = lib.ZSTD_DStreamOutSize()
out_buf_data = ctypes.create_string_buffer(out_size)

in_buf_data = ctypes.create_string_buffer(compressed)
in_buf = ZSTD_inBuffer(ctypes.cast(in_buf_data, ctypes.c_void_p), len(compressed), 0)

result_chunks = []
while in_buf.pos < in_buf.size:
    out_buf = ZSTD_outBuffer(ctypes.cast(out_buf_data, ctypes.c_void_p), out_size, 0)
    ret = lib.ZSTD_decompressStream(dstream, ctypes.byref(out_buf), ctypes.byref(in_buf))
    if lib.ZSTD_isError(ret):
        lib.ZSTD_freeDStream(dstream)
        print('ERROR: ZSTD_decompressStream failed (ret={})'.format(ret), file=sys.stderr)
        sys.exit(1)
    if out_buf.pos > 0:
        result_chunks.append(ctypes.string_at(out_buf.dst, out_buf.pos))

lib.ZSTD_freeDStream(dstream)
decompressed = b''.join(result_chunks)
print('DEBUG: decompressed {} -> {} bytes'.format(len(compressed), len(decompressed)), file=sys.stderr)

with tarfile.open(fileobj=io.BytesIO(decompressed)) as tf:
    tf.extractall('/')

print('OK: {} -> {} bytes'.format(len(compressed), len(decompressed)))
""".trimIndent())
        Log.i(TAG, "zst_extract.py (streaming API) written to rootfs /tmp")
    }

    /**
     * data.tar.zst를 rootfs에 추출 — 3단계 폴백:
     * 1. tar --zstd -xf (가장 간단, tar에 zstd 지원 내장 시)
     * 2. zstdcat | tar (zstd CLI 바이너리 존재 시)
     * 3. Python3 스트리밍 zstd (ctypes + libzstd.so, 최후 수단)
     */
    private suspend fun extractZstViaProot(dataTarFile: File, rootfsDir: File) {
        val tmpDir = File(rootfsDir, "tmp")
        val tmpFile = File(tmpDir, dataTarFile.name)
        dataTarFile.copyTo(tmpFile, overwrite = true)

        val zstPath = "/tmp/${dataTarFile.name}"

        // Strategy 2a: tar --zstd
        Log.d(TAG, "zstd Strategy 2a: tar --zstd")
        val tarZstResult = prootManager.exec("/bin/sh", "-c",
            "tar --zstd -xf $zstPath -C / 2>&1"
        )
        if (tarZstResult.isSuccess) {
            tmpFile.delete()
            Log.i(TAG, "zstd extracted via tar --zstd (Strategy 2a)")
            return
        }
        Log.w(TAG, "tar --zstd failed (code=${tarZstResult.exitCode}): ${tarZstResult.output.takeLast(200)}")

        // Strategy 2b: zstdcat | tar
        Log.d(TAG, "zstd Strategy 2b: zstdcat | tar")
        val zstdcatResult = prootManager.exec("/bin/sh", "-c",
            "zstdcat $zstPath | tar -xf - -C / 2>&1"
        )
        if (zstdcatResult.isSuccess) {
            tmpFile.delete()
            Log.i(TAG, "zstd extracted via zstdcat | tar (Strategy 2b)")
            return
        }
        Log.w(TAG, "zstdcat | tar failed (code=${zstdcatResult.exitCode}): ${zstdcatResult.output.takeLast(200)}")

        // Strategy 2c: Python3 streaming zstd (ctypes + libzstd.so)
        Log.d(TAG, "zstd Strategy 2c: Python3 streaming zstd")
        val pythonResult = prootManager.exec("/bin/sh", "-c",
            "python3 /tmp/zst_extract.py $zstPath 2>&1"
        )

        tmpFile.delete()

        if (!pythonResult.isSuccess) {
            throw RuntimeException("zstd 추출 실패 (모든 전략 실패): ${pythonResult.output.takeLast(300)}")
        }

        Log.i(TAG, "zstd extracted via Python3 streaming (Strategy 2c): ${pythonResult.output.takeLast(100)}")
    }

    private fun downloadDebPackage(deb: DebPackage, cacheDir: File): File {
        val targetFile = File(cacheDir, "${deb.name}.deb")
        val urls = listOfNotNull(deb.primaryUrl, deb.fallbackUrl)
        var lastError: Exception? = null

        for (urlStr in urls) {
            for (attempt in 1..MAX_RETRIES_PER_URL) {
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
                        Log.i(TAG, "Downloaded ${deb.name}: ${targetFile.length()} bytes from $urlStr (attempt $attempt)")
                        return targetFile
                    } else {
                        Log.w(TAG, "HTTP ${conn.responseCode} for $urlStr (attempt $attempt)")
                    }
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "Download failed for $urlStr (attempt $attempt): ${e.message}")
                }

                if (attempt < MAX_RETRIES_PER_URL) {
                    val delayMs = INITIAL_RETRY_DELAY_MS * (1L shl (attempt - 1))
                    Thread.sleep(delayMs)
                }
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
            "echo 'DROPBEAR_EXTRA_ARGS=\"\"' >> /etc/default/dropbear && " +
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

    /**
     * Install libnetstub.so — LD_PRELOAD shim that intercepts getifaddrs().
     *
     * On Android 11+, netlink socket bind() returns EACCES for unprivileged apps.
     * This breaks getifaddrs() used by Python (socket.if_nameindex, psutil),
     * Go (net.Interfaces), Rust (nix::ifaddrs), and many other languages/libraries.
     * Node.js has a separate fix via net-fix.js, but this covers ALL native code.
     *
     * The shim:
     * 1. Calls the real getifaddrs() via dlsym(RTLD_NEXT)
     * 2. If it fails with EACCES, falls back to:
     *    - ioctl(SIOCGIFCONF) for IPv4 interfaces
     *    - Parsing /proc/net/if_inet6 for IPv6 addresses
     * 3. Returns a synthetic struct ifaddrs linked list
     *
     * Registered in /etc/ld.so.preload so it loads for ALL processes automatically.
     * Reference: George-Seven/Termux-Proot-Utils approach.
     */
    private suspend fun installNetStub() {
        val rootfsDir = File(context.filesDir, "ubuntu")
        val libDir = File(rootfsDir, "usr/local/lib")
        val netstubSo = File(libDir, "libnetstub.so")

        // Idempotent: skip if already compiled
        if (netstubSo.exists() && netstubSo.length() > 0) {
            Log.i(TAG, "libnetstub.so already exists (${netstubSo.length()} bytes), ensuring ld.so.preload")
            ensureNetstubInLdPreload(rootfsDir)
            return
        }

        libDir.mkdirs()

        // Write C source
        val cSource = NETSTUB_C_SOURCE
        File(rootfsDir, "tmp/netstub.c").writeText(cSource)

        _state.value = InstallState.Progress(90, "네트워크 호환성 라이브러리 컴파일 중...")

        try {
            // Try compiling with available compilers (gcc → tcc → cc),
            // install gcc as last resort if none available.
            // -nostartfiles: no crt*.o startup code (pure .so, not executable)
            val result = prootManager.exec(
                "/bin/sh", "-c",
                "if command -v gcc >/dev/null 2>&1; then " +
                "  gcc -shared -fPIC -nostartfiles -o /usr/local/lib/libnetstub.so /tmp/netstub.c -ldl 2>&1; " +
                "elif command -v tcc >/dev/null 2>&1; then " +
                "  tcc -shared -o /usr/local/lib/libnetstub.so /tmp/netstub.c -ldl 2>&1; " +
                "elif command -v cc >/dev/null 2>&1; then " +
                "  cc -shared -fPIC -nostartfiles -o /usr/local/lib/libnetstub.so /tmp/netstub.c -ldl 2>&1; " +
                "else " +
                "  apt-get install -y -qq gcc 2>&1 && " +
                "  gcc -shared -fPIC -nostartfiles -o /usr/local/lib/libnetstub.so /tmp/netstub.c -ldl 2>&1; " +
                "fi"
            )

            if (netstubSo.exists() && netstubSo.length() > 0) {
                netstubSo.setReadable(true, false)
                netstubSo.setExecutable(true, false)
                Log.i(TAG, "libnetstub.so compiled (${netstubSo.length()} bytes)")
                ensureNetstubInLdPreload(rootfsDir)
            } else {
                Log.w(TAG, "libnetstub.so compilation failed (non-fatal): ${result.output.takeLast(500)}")
            }
        } catch (e: Exception) {
            // Non-fatal: getifaddrs shim is a quality-of-life improvement, not critical
            Log.w(TAG, "libnetstub.so installation failed (non-fatal): ${e.message}")
        }

        // Cleanup source file
        File(rootfsDir, "tmp/netstub.c").delete()

        _state.value = InstallState.Progress(91, "네트워크 호환성 설정 완료")
    }

    /**
     * Ensure /usr/local/lib/libnetstub.so is registered in ld.so.preload.
     * Preserves existing entries (e.g., /support/libdisableselinux.so).
     * The support/ld.so.preload file is bind-mounted to /etc/ld.so.preload by ProotManager.
     */
    private fun ensureNetstubInLdPreload(rootfsDir: File) {
        val netstubPath = "/usr/local/lib/libnetstub.so"
        val ldPreloadFile = File(rootfsDir, "support/ld.so.preload")

        if (!ldPreloadFile.parentFile!!.exists()) {
            ldPreloadFile.parentFile!!.mkdirs()
        }

        if (ldPreloadFile.exists()) {
            val content = ldPreloadFile.readText()
            if (content.contains(netstubPath)) {
                Log.d(TAG, "libnetstub.so already in ld.so.preload")
                return
            }
            // Append on a new line, preserving existing entries
            val newContent = content.trimEnd() + "\n" + netstubPath + "\n"
            ldPreloadFile.writeText(newContent)
            Log.i(TAG, "Appended libnetstub.so to existing ld.so.preload")
        } else {
            ldPreloadFile.writeText("$netstubPath\n")
            Log.i(TAG, "Created ld.so.preload with libnetstub.so")
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
            .remove(STEP_PROOT_EXTRACTED)
            .remove(STEP_ROOTFS_DOWNLOADED)
            .remove(STEP_ROOTFS_EXTRACTED)
            .remove(STEP_SUPPORT_COPIED)
            .remove(STEP_CONFIGURED)
            .remove(STEP_SWAP_DONE)
            .remove(STEP_DROPBEAR_INSTALLED)
            .remove(STEP_NETSTUB_INSTALLED)
            .apply()
        _state.value = InstallState.Idle
        Log.i(TAG, "Installation reset (all step markers cleared)")
    }

    companion object {
        private const val TAG = "InstallManager"
        private const val PREFS_NAME = "pocketserver_prefs"
        private const val KEY_INSTALLED = "server_installed"
        private const val KEY_SSH_PASSWORD = "ssh_password"

        // Step markers for resumable installation (UserLand .success_* pattern)
        private const val STEP_PROOT_EXTRACTED = "step_proot_extracted"
        private const val STEP_ROOTFS_DOWNLOADED = "step_rootfs_downloaded"
        private const val STEP_ROOTFS_EXTRACTED = "step_rootfs_extracted"
        private const val STEP_SUPPORT_COPIED = "step_support_copied"
        private const val STEP_CONFIGURED = "step_configured"
        private const val STEP_SWAP_DONE = "step_swap_done"
        private const val STEP_DROPBEAR_INSTALLED = "step_dropbear_installed"
        private const val STEP_NETSTUB_INSTALLED = "step_netstub_installed"
        // 3 mirror URLs for rootfs — tries in order, each with retry
        private val ROOTFS_URLS = listOf(
            "https://cloud-images.ubuntu.com/minimal/releases/noble/release/" +
                "ubuntu-24.04-minimal-cloudimg-arm64-root.tar.xz",
            "https://cdimage.ubuntu.com/ubuntu-base/releases/noble/release/" +
                "ubuntu-base-24.04.2-base-arm64.tar.gz",
            "https://pub-832ccadf097e4bf687650db1e57df66b.r2.dev/" +
                "ubuntu-24.04-minimal-cloudimg-arm64-root.tar.xz"
        )
        private const val MAX_RETRIES_PER_URL = 3
        private const val INITIAL_RETRY_DELAY_MS = 2000L

        // Dropbear .deb packages — Java-side extraction (Strategy 2)
        // Noble (24.04) primary + Jammy (22.04) fallback
        // data.tar.zst는 PRoot 내 tar --zstd / zstdcat / Python3 streaming zstd로 처리
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

        /**
         * C source for libnetstub.so — getifaddrs() LD_PRELOAD shim.
         *
         * On Android 11+, netlink socket bind() returns EACCES inside PRoot.
         * This breaks getifaddrs() which is used by Python, Go, Rust, and many
         * other languages/libraries for network interface enumeration.
         *
         * The shim intercepts getifaddrs() and freeifaddrs():
         * - Tries the real getifaddrs() first via dlsym(RTLD_NEXT)
         * - If it fails with EACCES (errno 13), falls back to:
         *   - ioctl(SIOCGIFCONF) for IPv4 interface enumeration
         *   - Parsing /proc/net/if_inet6 for IPv6 addresses
         * - Builds a proper struct ifaddrs linked list
         *
         * Reference: George-Seven/Termux-Proot-Utils approach.
         */
        val NETSTUB_C_SOURCE = """
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <ifaddrs.h>
#include <net/if.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>

/* Maximum number of interfaces we support in fallback mode */
#define MAX_IFS 32

/* Node in our synthetic ifaddrs linked list */
struct ifa_node {
    struct ifaddrs ifa;
    char name[IF_NAMESIZE];
    struct sockaddr_storage addr;
    struct sockaddr_storage netmask;
};

static struct ifa_node *g_nodes = NULL;
static int g_node_count = 0;

/*
 * Build a synthetic ifaddrs list using ioctl(SIOCGIFCONF) for IPv4
 * and parsing /proc/net/if_inet6 for IPv6.
 * Returns 0 on success, -1 on failure.
 */
static int fallback_getifaddrs(struct ifaddrs **ifap) {
    int sock = -1;
    int count = 0;
    struct ifconf ifc;
    char buf[sizeof(struct ifreq) * MAX_IFS];
    struct ifa_node *nodes = NULL;

    *ifap = NULL;

    /* --- IPv4 via ioctl --- */
    sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock < 0) goto build_result;

    ifc.ifc_len = sizeof(buf);
    ifc.ifc_buf = buf;
    if (ioctl(sock, SIOCGIFCONF, &ifc) < 0) {
        close(sock);
        sock = -1;
        goto try_ipv6;
    }

    int n_ifs = ifc.ifc_len / sizeof(struct ifreq);
    if (n_ifs > MAX_IFS) n_ifs = MAX_IFS;

    /* Allocate enough nodes for IPv4 + potential IPv6 entries */
    nodes = (struct ifa_node *)calloc(MAX_IFS * 2, sizeof(struct ifa_node));
    if (!nodes) {
        close(sock);
        return -1;
    }

    for (int i = 0; i < n_ifs; i++) {
        struct ifreq *ifr = &((struct ifreq *)ifc.ifc_buf)[i];
        struct ifa_node *node = &nodes[count];

        strncpy(node->name, ifr->ifr_name, IF_NAMESIZE - 1);
        node->name[IF_NAMESIZE - 1] = '\0';
        node->ifa.ifa_name = node->name;
        node->ifa.ifa_flags = 0;

        /* Get flags */
        struct ifreq flags_req;
        strncpy(flags_req.ifr_name, ifr->ifr_name, IF_NAMESIZE - 1);
        if (ioctl(sock, SIOCGIFFLAGS, &flags_req) == 0) {
            node->ifa.ifa_flags = flags_req.ifr_flags;
        }

        /* Address */
        memcpy(&node->addr, &ifr->ifr_addr, sizeof(struct sockaddr_in));
        node->ifa.ifa_addr = (struct sockaddr *)&node->addr;

        /* Netmask */
        struct ifreq mask_req;
        strncpy(mask_req.ifr_name, ifr->ifr_name, IF_NAMESIZE - 1);
        if (ioctl(sock, SIOCGIFNETMASK, &mask_req) == 0) {
            memcpy(&node->netmask, &mask_req.ifr_netmask, sizeof(struct sockaddr_in));
            node->ifa.ifa_netmask = (struct sockaddr *)&node->netmask;
        }

        node->ifa.ifa_next = NULL;
        count++;
    }

    close(sock);
    sock = -1;

try_ipv6:
    /* --- IPv6 via /proc/net/if_inet6 --- */
    {
        FILE *f = fopen("/proc/net/if_inet6", "r");
        if (f) {
            if (!nodes) {
                nodes = (struct ifa_node *)calloc(MAX_IFS * 2, sizeof(struct ifa_node));
                if (!nodes) {
                    fclose(f);
                    return -1;
                }
            }

            char line[128];
            while (fgets(line, sizeof(line), f) && count < MAX_IFS * 2) {
                /* Format: addr6 index prefix_len scope flags name */
                char addr6_hex[33];
                int index, prefix_len, scope, flags;
                char ifname[IF_NAMESIZE];

                if (sscanf(line, "%32s %x %x %x %x %15s",
                           addr6_hex, &index, &prefix_len, &scope, &flags, ifname) != 6) {
                    continue;
                }

                struct ifa_node *node = &nodes[count];
                strncpy(node->name, ifname, IF_NAMESIZE - 1);
                node->name[IF_NAMESIZE - 1] = '\0';
                node->ifa.ifa_name = node->name;
                node->ifa.ifa_flags = IFF_UP;

                /* Parse hex IPv6 address */
                struct sockaddr_in6 *sa6 = (struct sockaddr_in6 *)&node->addr;
                sa6->sin6_family = AF_INET6;
                sa6->sin6_scope_id = (scope == 0x20) ? index : 0; /* link-local */
                for (int i = 0; i < 16; i++) {
                    unsigned int byte_val;
                    char hex[3] = { addr6_hex[i*2], addr6_hex[i*2+1], '\0' };
                    sscanf(hex, "%x", &byte_val);
                    sa6->sin6_addr.s6_addr[i] = (unsigned char)byte_val;
                }
                node->ifa.ifa_addr = (struct sockaddr *)&node->addr;

                /* Build prefix mask */
                struct sockaddr_in6 *mask6 = (struct sockaddr_in6 *)&node->netmask;
                mask6->sin6_family = AF_INET6;
                for (int i = 0; i < 16; i++) {
                    int bits = prefix_len - i * 8;
                    if (bits >= 8) mask6->sin6_addr.s6_addr[i] = 0xFF;
                    else if (bits > 0) mask6->sin6_addr.s6_addr[i] = (0xFF << (8 - bits)) & 0xFF;
                    else mask6->sin6_addr.s6_addr[i] = 0;
                }
                node->ifa.ifa_netmask = (struct sockaddr *)&node->netmask;

                node->ifa.ifa_next = NULL;
                count++;
            }
            fclose(f);
        }
    }

build_result:
    if (sock >= 0) close(sock);

    if (count == 0) {
        free(nodes);
        /* Return empty list (not an error — just no interfaces found) */
        *ifap = NULL;
        /* Store for freeifaddrs */
        g_nodes = NULL;
        g_node_count = 0;
        return 0;
    }

    /* Link the nodes */
    for (int i = 0; i < count - 1; i++) {
        nodes[i].ifa.ifa_next = &nodes[i + 1].ifa;
    }
    nodes[count - 1].ifa.ifa_next = NULL;

    *ifap = &nodes[0].ifa;

    /* Store reference for freeifaddrs */
    g_nodes = nodes;
    g_node_count = count;

    return 0;
}

/*
 * Intercepted getifaddrs — tries real function first,
 * falls back on EACCES (Android 11+ netlink restriction).
 */
int getifaddrs(struct ifaddrs **ifap) {
    static int (*real_getifaddrs)(struct ifaddrs **) = NULL;
    if (!real_getifaddrs) {
        real_getifaddrs = (int (*)(struct ifaddrs **))dlsym(RTLD_NEXT, "getifaddrs");
    }

    if (real_getifaddrs) {
        int ret = real_getifaddrs(ifap);
        if (ret == 0) return 0;
        /* Only fall back on EACCES — other errors pass through */
        if (errno != EACCES) return ret;
    }

    return fallback_getifaddrs(ifap);
}

/*
 * Intercepted freeifaddrs — handles both real and synthetic lists.
 */
void freeifaddrs(struct ifaddrs *ifa) {
    /* Check if this is our synthetic list */
    if (g_nodes && ifa == &g_nodes[0].ifa) {
        free(g_nodes);
        g_nodes = NULL;
        g_node_count = 0;
        return;
    }

    /* Not ours — call the real freeifaddrs */
    static void (*real_freeifaddrs)(struct ifaddrs *) = NULL;
    if (!real_freeifaddrs) {
        real_freeifaddrs = (void (*)(struct ifaddrs *))dlsym(RTLD_NEXT, "freeifaddrs");
    }
    if (real_freeifaddrs) {
        real_freeifaddrs(ifa);
    }
}
""".trimIndent()

        fun generatePassword(length: Int = 12): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            val random = SecureRandom()
            return (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
        }
    }
}
