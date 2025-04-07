
import Flutter
import UIKit

public class FileTransferHelperPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let methodChannel = FlutterMethodChannel(name: "file_transfer_helper", binaryMessenger: registrar.messenger())
    let eventChannel = FlutterEventChannel(name: "file_transfer_helper/progress", binaryMessenger: registrar.messenger())

    let methodHandler = FileTransferMethodHandler()
    let streamHandler = FileTransferStreamHandler()

    methodHandler.streamHandler = streamHandler

    methodChannel.setMethodCallHandler(methodHandler.handle)
    eventChannel.setStreamHandler(streamHandler)

    print("✅ FileTransferHelperPlugin registered")
  }
}
