package com.example.file_transfer_helper

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.annotation.NonNull
import androidx.documentfile.provider.DocumentFile
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

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
        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "file_transfer_helper/progress")
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "selectDirectory" -> {
                resultPending = result
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
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
        val fromDir = DocumentFile.fromTreeUri(activity!!, fromUri)
        val toDir = DocumentFile.fromTreeUri(activity!!, toUri)

        if (fromDir == null || toDir == null || !fromDir.isDirectory || !toDir.isDirectory) {
            result.error("INVALID_URI", "Invalid directory URIs provided", null)
            return
        }

        val progress = MoveProgress()
        countFiles(fromDir, progress)

        try {
            fromDir.listFiles().forEach { file ->
                if (file.isDirectory) {
                    moveSubDirectory(activity!!.contentResolver, file, toDir, progress)
                } else if (file.isFile) {
                    moveFile(activity!!.contentResolver, file.uri, toDir.uri, file.name ?: "unknown_file", progress)
                }
            }

        } catch (e: Exception) {
            result.error("MOVE_FAILED", "Error during file move: ${e.message}", null)
        }
    }

    private fun moveSubDirectory(
        contentResolver: ContentResolver,
        fromDir: DocumentFile,
        toDir: DocumentFile,
        progress: MoveProgress
    ) {
        val newDir = toDir.createDirectory(fromDir.name ?: "unknown_directory")
        if (newDir != null) {
            fromDir.listFiles().forEach { file ->
                if (file.isDirectory) {
                    moveSubDirectory(contentResolver, file, newDir, progress)
                } else if (file.isFile) {
                    moveFile(contentResolver, file.uri, newDir.uri, file.name ?: "unknown_file", progress)
                }
            }
            fromDir.delete()
        } else {
            progress.failureCount++
        }
    }

    private fun moveFile(
        contentResolver: ContentResolver,
        fromUri: Uri,
        toUri: Uri,
        fileName: String,
        progress: MoveProgress
    ) {
        try {
            val destinationDir = DocumentFile.fromTreeUri(activity!!, toUri)
            val newFile = destinationDir?.createFile("application/octet-stream", fileName)

            if (newFile != null) {
                val inputStream = contentResolver.openInputStream(fromUri)
                val outputStream = contentResolver.openOutputStream(newFile.uri)

                if (inputStream != null && outputStream != null) {
                    progress.currentFile = fileName
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()

                    DocumentFile.fromSingleUri(activity!!, fromUri)?.delete()
                    progress.successCount++
                } else {
                    progress.failureCount++
                }
                sendProgressUpdate(progress.currentFile, (progress.successCount + progress.failureCount) * 100 / progress.totalFiles, progress.successCount, progress.failureCount, progress.totalFiles)
            }
        } catch (e: Exception) {
            progress.failureCount++
        }
    }

    private fun countFiles(dir: DocumentFile, progress: MoveProgress) {
        dir.listFiles().forEach { file ->
            if (file.isDirectory) {
                countFiles(file, progress)
            } else {
                progress.totalFiles++
            }
        }
    }

    private fun sendProgressUpdate(currentFile: String, progress: Int, successCount: Int, failureCount: Int, totalCount: Int) {
        eventSink?.success(
            mapOf(
                "currentFile" to currentFile,
                "progress" to progress,
                "successCount" to successCount,
                "failureCount" to failureCount,
                "totalCount" to totalCount
            )
        )
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