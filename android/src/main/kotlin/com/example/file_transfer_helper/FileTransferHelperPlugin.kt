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
import io.flutter.plugin.common.MethodChannel.Result
import android.os.Environment
import android.os.StatFs

class FileTransferHelperPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, ActivityAware {

    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private var activity: Activity? = null
    private var resultPending: Result? = null
    private val SEL_DIR_REQUEST_CODE = 12345
    private var eventSink: EventChannel.EventSink? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel = MethodChannel(binding.binaryMessenger, "file_transfer_helper")
        eventChannel = EventChannel(binding.binaryMessenger, "file_transfer_helper/progress")
        
         methodChannel.setMethodCallHandler(this)

        eventChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                eventSink = events
            }

            override fun onCancel(arguments: Any?) {
                eventSink = null
            }
        })
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
    }

   override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
        "selectDirectory" -> {
            resultPending = result
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                         Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                         Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            activity?.startActivityForResult(intent, SEL_DIR_REQUEST_CODE)
        }
        "move" -> {
            val from = call.argument<String>("from")!!
            val to = call.argument<String>("to")!!
            val deleteOriginal = call.argument<Boolean>("deleteOriginal") ?: true

            FileMover(activity!!, eventSink).move(from, to, deleteOriginal, result)
        }
        "getExternalStorageInfo" -> {
            result.success(getExternalStorageInfo())
        }
        else -> result.notImplemented()
    }
}

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener { requestCode, resultCode, data ->
            if (requestCode == SEL_DIR_REQUEST_CODE) {
                handleDirectoryResult(resultCode, data)
                true
            } else {
                false
            }
        }
    }


     fun getExternalStorageInfo(): Map<String, Any> {
        val externalDirs = activity!!.getExternalFilesDirs(null)
        val result = mutableListOf<Map<String, Any>>()

        for (dir in externalDirs) {
            if (dir != null) {
                val stat = StatFs(dir.path)
                val isRemovable = Environment.isExternalStorageRemovable(dir)
                val available = stat.availableBytes
                result.add(
                    mapOf(
                        "path" to dir.absolutePath,
                        "isRemovable" to isRemovable,
                        "freeBytes" to available
                    )
                )
            }
        }

        return mapOf(
            "storages" to result
        )
    }

    private fun handleDirectoryResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data?.data != null) {
            val uri = data.data!!
            activity?.contentResolver?.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            resultPending?.success(uri.toString())
        } else {
            resultPending?.error("DIR_PICK_FAILED", "Directory not selected", null)
        }
        resultPending = null
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
}