package kr.co.palank.pocketserver.linux

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object ProotBinaryManager {

    private const val TAG = "ProotBinaryManager"
    private const val SUPPORT_DIR = "support"
    private const val UBUNTU_ASSETS_DIR = "ubuntu"

    fun getSupportDir(context: Context): File = File(context.filesDir, SUPPORT_DIR)

    fun getProotPath(context: Context): String =
        File(getSupportDir(context), "proot").absolutePath

    fun getLoaderPath(context: Context): String =
        File(getSupportDir(context), "loader").absolutePath

    fun getLoader32Path(context: Context): String =
        File(getSupportDir(context), "loader32").absolutePath

    fun getBusyboxPath(context: Context): String =
        File(getSupportDir(context), "busybox").absolutePath

    fun getExecInProotPath(context: Context): String =
        File(getSupportDir(context), "execInProot.sh").absolutePath

    fun isExtracted(context: Context): Boolean {
        val supportDir = getSupportDir(context)
        return File(supportDir, "proot").exists() &&
            File(supportDir, "busybox").exists() &&
            File(supportDir, "loader").exists() &&
            File(supportDir, "execInProot.sh").exists()
    }

    // Create symlinks from nativeLibraryDir lib_*.so files to files/support/*
    // nativeLibraryDir has SELinux type apk_data_file which allows execve on Android 10+
    private fun setupLinks(context: Context) {
        val supportDir = getSupportDir(context)
        supportDir.mkdirs()

        val libDir = File(context.applicationInfo.nativeLibraryDir)
        val libFiles = libDir.listFiles()
        if (libFiles == null || libFiles.isEmpty()) {
            throw IllegalStateException("nativeLibraryDir is empty: ${libDir.absolutePath}")
        }

        val a10Prefixes = listOf("lib_proot.", "lib_libtalloc", "lib_loader")

        for (libFile in libFiles) {
            var libFileName = libFile.name

            if (!libFileName.startsWith("lib_")) continue

            val hasA10Variant = a10Prefixes.any { libFileName.startsWith(it) }
            if (hasA10Variant) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (libFileName.endsWith(".a10.so")) {
                        libFileName = libFileName.replace(".a10.so", ".so")
                    } else {
                        continue
                    }
                } else {
                    if (libFileName.endsWith(".a10.so")) {
                        continue
                    }
                }
            }

            val name = libFileName.substringAfter("lib_").substringBeforeLast(".so")
            val linkFile = File(supportDir, name)

            linkFile.delete()

            try {
                Os.symlink(libFile.absolutePath, linkFile.absolutePath)
                Log.d(TAG, "Symlink: ${linkFile.name} -> ${libFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to symlink ${linkFile.name}: ${e.message}")
                throw e
            }
        }
    }

    // Extract script/data files from assets/support/ to files/support/
    private fun extractScripts(context: Context) {
        val targetDir = getSupportDir(context)
        targetDir.mkdirs()

        val assetFiles = context.assets.list(SUPPORT_DIR) ?: emptyArray()

        for (assetName in assetFiles) {
            val targetFile = File(targetDir, assetName)
            try {
                context.assets.open("$SUPPORT_DIR/$assetName").use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                targetFile.setExecutable(true, false)
                targetFile.setReadable(true, false)
                Log.d(TAG, "Extracted script: $assetName (${targetFile.length()} bytes)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to extract $assetName: ${e.message}")
            }
        }
    }

    fun extractSupport(context: Context) {
        setupLinks(context)
        extractScripts(context)

        val supportDir = getSupportDir(context)
        for (name in listOf("proot", "busybox", "loader", "execInProot.sh")) {
            val f = File(supportDir, name)
            if (!f.exists()) {
                throw IllegalStateException("Critical file missing after setup: $name")
            }
        }

        val useA10 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        Log.i(TAG, "Support ready at ${supportDir.absolutePath} (a10=$useA10, symlinks)")
    }

    fun setupRootfsSupport(context: Context, rootfsPath: File) {
        val supportInRootfs = File(rootfsPath, "support")
        if (!supportInRootfs.exists()) supportInRootfs.mkdirs()

        val assetFiles = context.assets.list(UBUNTU_ASSETS_DIR) ?: emptyArray()

        for (assetName in assetFiles) {
            val targetFile = File(supportInRootfs, assetName)
            try {
                context.assets.open("$UBUNTU_ASSETS_DIR/$assetName").use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                targetFile.setExecutable(true, false)
                targetFile.setReadable(true, false)
                Log.d(TAG, "Rootfs support: $assetName (${targetFile.length()} bytes)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to copy $assetName to rootfs: ${e.message}")
            }
        }

        File(supportInRootfs, ".proot_version").writeText("")
        Log.i(TAG, "Rootfs support set up in ${supportInRootfs.absolutePath}")
    }

    fun ensureReady(context: Context) {
        if (!isExtracted(context)) {
            extractSupport(context)
        }
    }
}
