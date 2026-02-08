package kr.co.palank.pocketserver.linux

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object ProotBinaryManager {

    private const val TAG = "ProotBinaryManager"
    private const val ASSET_PATH = "proot/proot"
    private const val BINARY_DIR = "proot"
    private const val BINARY_NAME = "proot"

    fun getProotPath(context: Context): String {
        return File(context.filesDir, "$BINARY_DIR/$BINARY_NAME").absolutePath
    }

    fun isExtracted(context: Context): Boolean {
        val binary = File(context.filesDir, "$BINARY_DIR/$BINARY_NAME")
        return binary.exists() && binary.canExecute()
    }

    fun extract(context: Context) {
        val targetDir = File(context.filesDir, BINARY_DIR)
        if (!targetDir.exists()) targetDir.mkdirs()

        val targetFile = File(targetDir, BINARY_NAME)

        context.assets.open(ASSET_PATH).use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        targetFile.setExecutable(true, false)
        targetFile.setReadable(true, false)

        if (!targetFile.canExecute()) {
            throw IllegalStateException("Failed to set execute permission on PRoot binary")
        }

        Log.i(TAG, "PRoot binary extracted to ${targetFile.absolutePath}")
    }

    fun computeSha256(context: Context): String {
        val binary = File(context.filesDir, "$BINARY_DIR/$BINARY_NAME")
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
