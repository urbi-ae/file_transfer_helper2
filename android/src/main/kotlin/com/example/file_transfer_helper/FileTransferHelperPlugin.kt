package com.example.file_transfer_helper

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/** FileTransferHelperPlugin */
class FileTransferHelperPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {

    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private var eventSink: EventChannel.EventSink? = null
    private var resultPending: Result? = null
    private var activity: Activity? = null
    private val SEL_DIR_REQUEST_CODE = 10

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel = MethodChannel(flutterPluginBinding.binaryMessenger, "file_transfer_helper")
        methodChannel.setMethodCallHandler(this)
        eventChannel =
            EventChannel(flutterPluginBinding.binaryMessenger, "file_transfer_helper/progress")
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "selectDirectory" -> {
                resultPending = result
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                activity?.startActivityForResult(intent, SEL_DIR_REQUEST_CODE)
            }

            "moveDirectory" -> {
                eventChannel.setStreamHandler(object : EventChannel.StreamHandler {
                    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                        eventSink = events
                        val fromUri = Uri.parse(call.argument<String>("fromUri"))
                        val toUri = Uri.parse(call.argument<String>("toUri"))
                        moveDirectoryContent(fromUri, toUri, result)
                    }

                    override fun onCancel(arguments: Any?) {
                        eventSink = null
                    }
                })
            }

            else -> result.notImplemented()
        }
    }
    // Function to get the package name dynamically
    private fun getPackageName(): String {
        return activity!!.packageName
    }

    // Map Flutter paths to Android paths
    fun getAndroidPath(flutterPath: String): String {
        val packageName = getPackageName()

        // Define the mapping
        val pathMap = mapOf(
            "/storage/emulated/0/Android/data/$packageName/files/downloads" to "/storage/emulated/0/Android/data/$packageName/files/Download",
            "/storage/emulated/0/Android/data/$packageName/files/Documents" to "/storage/emulated/0/Android/data/$packageName/files/Documents",
            "/storage/emulated/0/Android/data/$packageName/files/Movies" to "/storage/emulated/0/Android/data/$packageName/files/Movies",
            "/storage/emulated/0/Android/data/$packageName/files/Music" to "/storage/emulated/0/Android/data/$packageName/files/Music",
            "/storage/emulated/0/Android/data/$packageName/files/Pictures" to "/storage/emulated/0/Android/data/$packageName/files/Pictures",
            "/data/user/0/$packageName/app_flutter/" to "/data/data/$packageName/files/",
            "/storage/emulated/0/Android/data/$packageName/files/" to "/storage/emulated/0/Android/data/$packageName/files/",
            "/data/data/$packageName/files/support/" to "/data/data/$packageName/files/support/"
        )

        // Return the mapped path or the original path if not found
        return pathMap[flutterPath] ?: flutterPath
    }

    private fun handleDirSelResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                resultPending?.success(uri.toString())
            } else {
                resultPending?.error("INVALID_URI", "No valid directory selected", null)
            }
        } else {
            resultPending?.error("CANCELLED", "Directory selection was cancelled", null)
        }
        resultPending = null
    }

    private fun moveDirectoryContent(fromUri: Uri, toUri: Uri, result: MethodChannel.Result) {
        val fromFile = getFileFromUri(fromUri)
        val toFile = getFileFromUri(toUri)

        if (fromFile == null || toFile == null) {
            result.error("INVALID_URI", "Invalid URIs provided", null)
            return
        }

        val progress = MoveProgress()
        try {
            when {
                fromFile is File && toFile is File -> {
                    countFiles(fromFile, progress)
                    sendProgressUpdate("total count", progress)
                    moveInternalFiles(fromFile, toFile, progress)
                }

                fromFile is DocumentFile && toFile is DocumentFile -> {
                    countFiles(fromFile, progress)
                    sendProgressUpdate("total count", progress)
                    moveExternalFiles(fromFile, toFile, progress)
                }

                fromFile is File && toFile is DocumentFile -> {
                    val androidPath = File(getAndroidPath(fromFile.absolutePath))
                    countFiles(androidPath, progress)
                    sendProgressUpdate("total count", progress)
                    moveInternalToExternal(androidPath, toUri,progress)
                }

                fromFile is DocumentFile && toFile is File -> {
                    val androidPath = File(getAndroidPath(toFile.absolutePath))
                    countFiles(fromFile, progress)
                    sendProgressUpdate("total count", progress)
                    moveExternalToInternal(fromFile, androidPath, progress)
                }

                else -> {
                    result.error("INCOMPATIBLE_URIS", "Cannot handle the URI combination", null)
                    return
                }
            }
            result.success(null)
        } catch (e: Exception) {
            result.error("MOVE_FAILED", "Error during file move: ${e.message}", null)
        }
    }

    // Function to get DocumentFile from URI
    private fun getDocumentFile(uri: Uri): DocumentFile? {
        return DocumentFile.fromTreeUri(activity!!, uri)
    }

    // Function to copy a directory and its contents
    private fun moveInternalToExternal(sourceDir: File, destinationDirUri: Uri, progress: MoveProgress) {
        val destinationDir = getDocumentFile(destinationDirUri)

        if (sourceDir.isDirectory) {
            // Create destination directory if it doesn't exist
            val destDir = destinationDir?.createDirectory(sourceDir.name)
            destDir?.let {
                // Copy each file in the directory
                sourceDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        moveFile(file, it.uri,progress)

                    } else if (file.isDirectory) {
                        moveInternalToExternal(file, it.uri, progress)
                    }
                }
            }
        } else {
            println("Source is not a directory.")
        }
    }


    private fun moveInternalFiles(fromDir: File, toDir: File, progress: MoveProgress) {
        try {
            fromDir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    val newDir = File(toDir, file.name)
                    newDir.mkdirs()
                    moveInternalFiles(file, newDir, progress)
                } else {
                    moveFile(file, toDir, file.name ?: "unknown_file", progress)
                }
            }
            fromDir.deleteRecursively()
        } catch (e: Exception) {
            eventSink?.error("MOVE_FAILED", "Error moving internal files: ${e.message}", null)
        }
    }

    private fun moveExternalFiles(
        fromDir: DocumentFile,
        toDir: DocumentFile,
        progress: MoveProgress
    ) {
        try {
            fromDir.listFiles().forEach { file ->
                if (file.isDirectory) {
                    val newDir = toDir.createDirectory(file.name ?: "unknown_directory")
                    if (newDir != null) {
                        moveExternalFiles(file, newDir, progress)
                    } else {
                        progress.failureCount++
                        sendProgressUpdate(file.name ?: "unknown_directory", progress)
                    }
                } else {
                    moveFile(file.uri, toDir.uri, file.name ?: "unknown_file", progress)
                }
            }
            // fromDir.delete()
        } catch (e: Exception) {
            eventSink?.error("MOVE_FAILED", "Error moving external files: ${e.message}", null)
        }
    }


    private fun moveInternalToExternal(
        fromFile: File,
        toDir: DocumentFile,
        progress: MoveProgress
    ) {
        if (!fromFile.exists()) {
            // File doesn't exist, update progress and return
            progress.failureCount++
            sendProgressUpdate(fromFile.name ?: "unknown_file", progress)
            return
        }

        try {

              // Check if the file exists in the destination directory
            val existingFile = toDir.findFile(fromFile.name ?: "unknown_file")

            if (existingFile != null) {
                // If the file exists, delete it
                existingFile.delete()
            }

            // Create a new file in the external directory
            val toFile =
                toDir.createFile("application/octet-stream", fromFile.name ?: "unknown_file")

            if (toFile != null) {
                // Use FileInputStream for reading the internal file
                FileInputStream(fromFile).use { inputStream ->
                    // Use ContentResolver to open OutputStream for the new external file
                    activity?.contentResolver?.openOutputStream(toFile.uri)?.use { outputStream ->
                        // Copy the content from the internal file to the external file
                        inputStream.copyTo(outputStream)
                    }

                    // Delete the original internal file
                    if (fromFile.delete()) {
                        progress.successCount++
                    } else {
                        progress.failureCount++
                    }
                    // Send progress update
                    sendProgressUpdate(fromFile.name ?: "unknown_file", progress)
                } ?: run {
                    // Failed to open output stream for the external file
                    progress.failureCount++
                    sendProgressUpdate(fromFile.name ?: "unknown_file", progress)
                }
            } else {
                // Failed to create file in the external directory
                progress.failureCount++
                sendProgressUpdate(fromFile.name ?: "unknown_file", progress)
            }
        } catch (e: Exception) {
            // Handle any exceptions that occur
            progress.failureCount++
            sendProgressUpdate(fromFile.name ?: "unknown_file", progress)
            eventSink?.error(
                "MOVE_FAILED",
                "Error moving internal file to external: ${e.message}",
                null
            )
        }
    }

    private fun moveExternalToInternal(fromDir: DocumentFile, toDir: File, progress: MoveProgress) {
        try {
            fromDir.listFiles().forEach { file ->
                if (file.isDirectory) {
                    val newDir = File(toDir, file.name ?: "unknown_directory")
                    newDir.mkdirs()
                    moveExternalToInternal(file, newDir, progress)
                } else {
                    try {
                        val contentResolver = activity?.contentResolver
                        if (contentResolver != null) {
                            val toFile = File(toDir, file.name ?: "unknown_file")
                            contentResolver.openInputStream(file.uri)?.use { inputStream ->
                                FileOutputStream(toFile).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                                file.delete()
                                progress.successCount++
                                sendProgressUpdate(file.name ?: "unknown_file", progress)
                            }
                        }
                    } catch (e: Exception) {
                        progress.failureCount++
                        eventSink?.error(
                            "MOVE_FAILED",
                            "Error moving external file to internal: ${e.message}",
                            null
                        )
                    }
                }
            }
        } catch (e: Exception) {
            eventSink?.error(
                "MOVE_FAILED",
                "Error moving external files to internal: ${e.message}",
                null
            )
        }
    }


    // Function to copy a file from internal storage to external DocumentFile
    private fun moveFile(fromFile: File, toDir: Uri, progress: MoveProgress) {
        val destinationDir = getDocumentFile(toDir)
        val destinationFile = destinationDir?.createFile("application/octet-stream", fromFile.name)

        if (destinationFile != null) {
            try {
                activity!!.contentResolver.openOutputStream(destinationFile.uri)?.use { outputStream ->
                    FileInputStream(fromFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                        fromFile.delete()
                        progress.successCount++

                    }
                }
            } catch (e: IOException) {
                eventSink?.error("MOVE_FAILED", "Error moving file: ${e.message}", null)
                progress.failureCount++

            }finally {
                sendProgressUpdate(fromFile.name, progress)
            }

        } else {
            println("Destination file creation failed.")
        }
    }

    private fun moveFile(fromFile: File, toDir: File, fileName: String, progress: MoveProgress) {
        try {
            val newFile = File(toDir, fileName)
            fromFile.copyTo(newFile, overwrite = true)
            fromFile.delete()
            progress.successCount++
            sendProgressUpdate(fileName, progress)
        } catch (e: IOException) {
            progress.failureCount++
            eventSink?.error("MOVE_FAILED", "Error moving file: ${e.message}", null)
        }
    }

    private fun moveFile(fromUri: Uri, toDirUri: Uri, fileName: String, progress: MoveProgress) {
        val contentResolver = activity?.contentResolver
        val toDir = DocumentFile.fromTreeUri(activity!!, toDirUri)
        val fromFile = DocumentFile.fromSingleUri(activity!!, fromUri)
        val newFile = toDir?.createFile("application/octet-stream", fileName)

        if (contentResolver != null && fromFile != null && newFile != null) {
            try {
                contentResolver.openInputStream(fromUri)?.use { inputStream ->
                    contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                        fromFile.delete()
                        progress.successCount++
                        sendProgressUpdate(fileName, progress)
                    }
                }
            } catch (e: Exception) {
                progress.failureCount++
                eventSink?.error("MOVE_FAILED", "Error moving file: ${e.message}", null)
            }
        } else {
            progress.failureCount++
            sendProgressUpdate(fileName, progress)
        }
    }

    private fun countFiles(dir: Any, progress: MoveProgress) {
        when (dir) {
            is File -> {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        countFiles(file, progress)
                    } else {
                        progress.totalFiles++
                    }
                }
            }

            is DocumentFile -> {
                dir.listFiles().forEach { file ->
                    if (file.isDirectory) {
                        countFiles(file, progress)
                    } else {
                        progress.totalFiles++
                    }
                }
            }
        }
    }

    private fun sendProgressUpdate(currentFile: String, progress: MoveProgress) {

        val progressCount = if (progress.totalFiles > 0) {
            (progress.successCount + progress.failureCount) * 100 / progress.totalFiles
        } else {
            0
        }

        eventSink?.success(
            mapOf(
                "currentFile" to currentFile,
                "progress" to progressCount,
                "successCount" to progress.successCount,
                "failureCount" to progress.failureCount,
                "totalCount" to progress.totalFiles
            )
        )
    }

    private fun getFileFromUri(uri: Uri): Any? {
        return when (uri.scheme) {
            "content" -> DocumentFile.fromTreeUri(activity!!, uri) // External storage
            else -> File(uri.path + "") // Internal storage
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener { requestCode, resultCode, data ->
            if (requestCode == SEL_DIR_REQUEST_CODE) {
                handleDirSelResult(resultCode, data)
                true
            } else {
                false
            }
        }
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    data class MoveProgress(
        var totalFiles: Int = 0,
        var successCount: Int = 0,
        var failureCount: Int = 0,
        var currentFile: String = ""
    )
}