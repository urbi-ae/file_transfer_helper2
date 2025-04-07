
import Flutter
import UIKit
import MobileCoreServices

public class FileTransferMethodHandler: NSObject {
  public var streamHandler: FileTransferStreamHandler?
  public var result: FlutterResult?

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "selectDirectory":
      self.result = result
      let picker = UIDocumentPickerViewController(documentTypes: [kUTTypeFolder as String], in: .open)
      picker.delegate = self
      picker.allowsMultipleSelection = false
      UIApplication.shared.windows.first?.rootViewController?.present(picker, animated: true)

    case "move":
      guard let args = call.arguments as? [String: Any],
            let from = args["from"] as? String,
            let to = args["to"] as? String else {
        result(FlutterError(code: "INVALID_ARGUMENTS", message: "Missing from/to", details: nil))
        return
      }
      streamHandler?.startMove(from: from, to: to, deleteOriginal: args["deleteOriginal"] as? Bool ?? true)
      result(nil)

    default:
      result(FlutterMethodNotImplemented)
    }
  }
}

extension FileTransferMethodHandler: UIDocumentPickerDelegate {
  public func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
    result?(urls.first?.absoluteString)
  }
}
