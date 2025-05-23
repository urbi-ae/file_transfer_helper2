package com.example.file_transfer_helper

import android.app.Activity
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.EventChannel
import java.io.*
import android.util.Log

class FileMover(
    private val activity: Activity,
    private val sink: EventChannel.EventSink?
) {

    data class Progress(
        var totalFiles: Int = 0,
        var successCount: Int = 0,
        var failureCount: Int = 0
    )

    fun move(fromRaw: String, toRaw: String, deleteOriginal: Boolean, result: MethodChannel.Result) {
        try {
          val from = getAnyFile(fromRaw)
val to = getAnyFile(toRaw)

Log.d("FileMover", "🔄 Moving from: $fromRaw → $toRaw")

val progress = Progress()
countFiles(from, progress)
sendProgress("start", progress)

when {
    from is File && to is File -> {
        if (from.isDirectory) {
            from.listFiles()?.forEach {
                moveInternalToInternal(it, to, deleteOriginal, progress)
            }
        } else {
            moveInternalToInternal(from, to, deleteOriginal, progress)
        }
    }
    from is File && to is DocumentFile -> {
       
            moveToDocumentFile(from, to, deleteOriginal, progress)
         
    }
    from is DocumentFile && to is File -> {
        if (from.isDirectory) {
            from.listFiles().forEach {
                moveFromDocumentFile(it, to, deleteOriginal, progress)
            }
        } else {
            moveFromDocumentFile(from, to, deleteOriginal, progress)
        }
    }
    from is DocumentFile && to is DocumentFile -> {
        if (from.isDirectory) {
            from.listFiles().forEach {
                moveDocToDoc(it, to, deleteOriginal, progress)
            }
        } else {
            moveDocToDoc(from, to, deleteOriginal, progress)
        }
    }
    else -> sendError(result, sink, "UNSUPPORTED", "Unsupported move operation")
}

            result.success(null)
            sink?.endOfStream()
        } catch (e: Exception) {
            sendError(result, sink, "MOVE_FAILED", "Move operation failed", e)
        }
    }

    private fun sendError(
        methodResult: MethodChannel.Result?,
        eventSink: EventChannel.EventSink?,
        code: String,
        message: String,
        throwable: Throwable? = null
    ) {
        val fullMessage = "$message${throwable?.let { ": ${it.message}" } ?: ""}"
        val stack = throwable?.stackTraceToString()

        methodResult?.error(code, fullMessage, stack)
        eventSink?.error(code, fullMessage, stack)

        Log.e("FileMover", "❌ $code: $fullMessage")
        if (stack != null) {
            Log.e("FileMover", stack)
        }
    }

    private fun shouldSkip(file: File): Boolean {
        val name = file.name
        return name.startsWith(".") ||
               name.contains("kernel_blob") ||
               name.contains("vm_snapshot") ||
               name.contains("isolate_snapshot") ||
               name.endsWith(".so") ||
               name.contains("flutter_assets")
    }

    private fun shouldSkip(doc: DocumentFile): Boolean {
        val name = doc.name ?: return true
        return name.startsWith(".") ||
               name.contains("Spotlight") ||
               name.contains("shadowIndex") ||
               name.contains(".trash", ignoreCase = true)
    }

    private fun moveInternalToInternal(from: File, to: File, deleteOriginal: Boolean, progress: Progress) {
        from.listFiles()?.forEach { file ->
            if (shouldSkip(file)) return@forEach
            if (file.isDirectory) {
                val subDir = File(to, file.name)
                subDir.mkdirs()
                moveInternalToInternal(file, subDir, deleteOriginal, progress)
            } else {
                val dest = File(to, file.name)
                Log.d("FileMover", "Copying ${file.path} → ${dest.path}")
                val size = file.length()
                file.copyTo(dest, overwrite = true)
                Log.d("FileMover", "✅ Copied ${file.name} ($size bytes)")
                if (deleteOriginal) file.delete()
                progress.successCount++
                sendProgress(file.name, progress)
            }
        }
    }

    private fun moveToDocumentFile(source: File, target: DocumentFile, deleteOriginal: Boolean, progress: Progress) {
        if (shouldSkip(source)) return

        if (source.isDirectory) {
            Log.d("FileMover", "📁 Creating directory in DocumentFile: ${source.name}")
            val newDir = target.findFile(source.name)?.takeIf { it.isDirectory }
                ?: target.createDirectory(source.name)

            source.listFiles()?.forEach { child ->
                moveToDocumentFile(child, newDir!!, deleteOriginal, progress)
            }
            if (deleteOriginal) source.deleteRecursively()
        } else {
            Log.d("FileMover", "📄 Moving file to DocumentFile: ${source.name}")
            copyFileToDocumentFile(source, target, deleteOriginal, progress)
        }
    }

    private fun moveFromDocumentFile(source: DocumentFile, target: File, deleteOriginal: Boolean, progress: Progress) {
        if (shouldSkip(source)) return
        if (source.isDirectory) {
            val newDir = File(target, source.name ?: "unknown_dir")
            newDir.mkdirs()

            source.listFiles().forEach { child ->
                moveFromDocumentFile(child, newDir, deleteOriginal, progress)
            }
        } else {
            copyFileFromDocumentFile(source, target, deleteOriginal, progress)
        }
    }

    private fun moveDocToDoc(
        from: DocumentFile,
        to: DocumentFile,
        deleteOriginal: Boolean,
        progress: Progress
    ) {
        from.listFiles().forEach { file ->
            if (shouldSkip(file)) return@forEach
            if (file.isDirectory) {
                val newDir = to.createDirectory(file.name ?: "unnamed_dir")
                if (newDir != null) {
                    moveDocToDoc(file, newDir, deleteOriginal, progress)
                    if (deleteOriginal) file.delete()
                } else {
                    progress.failureCount++
                    sendProgress(file.name ?: "unnamed_dir", progress)
                }
            } else {
                copyFileFromDocumentFile(file, to, deleteOriginal, progress)
            }
        }
    }

    private fun copyFileToDocumentFile(
        file: File,
        destDir: DocumentFile,
        deleteOriginal: Boolean,
        progress: Progress
    ) {
        if (shouldSkip(file)) return
        try {
            val mime = "application/octet-stream"
            val name = file.name
            destDir.findFile(name)?.delete()
            val newFile = destDir.createFile(mime, name)

            val outputStream = activity.contentResolver.openOutputStream(newFile!!.uri)
            val inputStream = FileInputStream(file)

            inputStream.use { ins ->
                outputStream.use { outs ->
                    val bytes = ins.copyTo(outs!!)
                    Log.d("FileMover", "✅ Moved $name ($bytes bytes)")
                }
            }

            if (deleteOriginal) file.delete()
            progress.successCount++
        } catch (e: Exception) {
            Log.e("FileMover", "❌ Failed to copy ${file.path}", e)
            sendError(null, sink, "MOVE_FAILED", "Failed to copy file ${file.path}", e)
            progress.failureCount++
        }
        sendProgress(file.name ?: "file", progress)
    }

    private fun copyFileFromDocumentFile(
        doc: DocumentFile,
        toDir: File,
        deleteOriginal: Boolean,
        progress: Progress
    ) {
        if (shouldSkip(doc) || !doc.canRead() || doc.uri == null) {
            progress.failureCount++
            sendProgress(doc.name ?: "file", progress)
            return
        }
        try {
            val toFile = File(toDir, doc.name ?: "file")
            val inputStream = activity.contentResolver.openInputStream(doc.uri)
            val outputStream = FileOutputStream(toFile)

            inputStream.use { ins ->
                outputStream.use { outs ->
                    val bytes = ins!!.copyTo(outs)
                    Log.d("FileMover", "✅ Copied ${doc.uri} to ${toFile.path} ($bytes bytes)")
                }
            }

            if (deleteOriginal) doc.delete()
            progress.successCount++
        } catch (e: Exception) {
            Log.e("FileMover", "❌ Failed to copy ${doc.uri}", e)
            sendError(null, sink, "MOVE_FAILED", "Failed to copy from DocumentFile ${doc.uri}", e)
            progress.failureCount++
        }
        sendProgress(doc.name ?: "file", progress)
    }

    private fun copyFileFromDocumentFile(
        doc: DocumentFile,
        toDir: DocumentFile,
        deleteOriginal: Boolean,
        progress: Progress
    ) {
        if (shouldSkip(doc) || !doc.canRead() || doc.uri == null) {
            progress.failureCount++
            sendProgress(doc.name ?: "file", progress)
            return
        }
        try {
            val name = doc.name ?: "file"
            toDir.findFile(name)?.delete()
            val dest = toDir.createFile("application/octet-stream", name)

            val inputStream = activity.contentResolver.openInputStream(doc.uri)
            val outputStream = activity.contentResolver.openOutputStream(dest!!.uri)

            inputStream.use { ins ->
                outputStream.use { outs ->
                    val bytes = ins!!.copyTo(outs!!)
                    Log.d("FileMover", "✅ Moved ${doc.uri} to ${dest.uri} ($bytes bytes)")
                }
            }

            if (deleteOriginal) doc.delete()
            progress.successCount++
        } catch (e: Exception) {
            Log.e("FileMover", "❌ Failed to copy from doc to doc: ${doc.uri}", e)
            sendError(null, sink, "MOVE_FAILED", "Failed to copy to DocumentFile ${doc.uri}", e)
            progress.failureCount++
        }

        sendProgress(doc.name ?: "file", progress)
    }

    private fun countFiles(file: Any?, progress: Progress) {
        when (file) {
            is File -> {
                if (file.isFile && !shouldSkip(file)) progress.totalFiles++
                else file.listFiles()?.forEach { countFiles(it, progress) }
            }
            is DocumentFile -> {
                if (shouldSkip(file)) return
                if (file.isFile) progress.totalFiles++
                else file.listFiles().forEach { countFiles(it, progress) }
            }
        }
    }

    private fun sendProgress(currentFile: String, progress: Progress) {
        val percent = if (progress.totalFiles > 0)
            ((progress.successCount + progress.failureCount) * 100) / progress.totalFiles
        else 0

        sink?.success(
            mapOf(
                "currentFile" to currentFile,
                "progress" to percent,
                "successCount" to progress.successCount,
                "failureCount" to progress.failureCount,
                "totalCount" to progress.totalFiles
            )
        )
    }

    private fun isDocumentUri(pathOrUri: String): Boolean {
        return pathOrUri.startsWith("content://")
    }

    private fun getAnyFile(input: String): Any {
    return if (isDocumentUri(input)) {
        val uri = Uri.parse(input)
        val treeDoc = DocumentFile.fromTreeUri(activity, uri)!!

        val path = uri.path ?: return treeDoc

        val segments = path.split(":")
        if (segments.size == 2) {
            val fullPath = segments[1] // e.g.
            val parts = fullPath.split("/")
            var current = treeDoc
            for (part in parts.drop(1)) { // skip root
                current = current.findFile(part) ?: return treeDoc
            }
            current
        } else {
            treeDoc
        }
    } else {
        File(input)
    }
}

}
