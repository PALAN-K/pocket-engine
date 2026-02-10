package kr.co.palank.pocketserver.linux

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object ProotBinaryManager {

    private const val TAG = "ProotBinaryManager"
    private const val BINARY_DIR = "proot"

    fun getProotPath(context: Context): String {
        return File(context.filesDir, "$BINARY_DIR/proot").absolutePath
    }

    fun getLoaderPath(context: Context): String {
        return File(context.filesDir, "$BINARY_DIR/loader").absolutePath
    }

    fun getLoader32Path(context: Context): String {
        return File(context.filesDir, "$BINARY_DIR/loader32").absolutePath
    }

    fun isExtracted(context: Context): Boolean {
        val binary = File(context.filesDir, "$BINARY_DIR/proot")
        val loader = File(context.filesDir, "$BINARY_DIR/loader")
        return binary.exists() && binary.canExecute() && loader.exists()
    }

    fun extract(context: Context) {
        val targetDir = File(context.filesDir, BINARY_DIR)
        if (!targetDir.exists()) targetDir.mkdirs()

        // PRoot 바이너리 + loader + loader32 추출
        for (name in listOf("proot", "loader", "loader32")) {
            try {
                val targetFile = File(targetDir, name)
                context.assets.open("proot/$name").use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                targetFile.setExecutable(true, false)
                targetFile.setReadable(true, false)
                Log.i(TAG, "Extracted: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            } catch (e: Exception) {
                if (name == "proot") throw e // proot은 필수
                Log.w(TAG, "Optional file not found in assets: $name")
            }
        }

        val prootFile = File(targetDir, "proot")
        if (!prootFile.canExecute()) {
            throw IllegalStateException("Failed to set execute permission on PRoot binary")
        }

        Log.i(TAG, "PRoot binaries extracted to ${targetDir.absolutePath}")
    }

    fun computeSha256(context: Context): String {
        val binary = File(context.filesDir, "$BINARY_DIR/proot")
        if (!binary.exists()) throw IllegalStateException("PRoot binary not found")

        val digest = MessageDigest.getInstance("SHA-256")
        binary.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun ensureReady(context: Context) {
        if (!isExtracted(context)) {
            extract(context)
        }
    }
}
