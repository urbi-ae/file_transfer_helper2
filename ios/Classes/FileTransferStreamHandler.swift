
import Flutter
import Foundation

public class FileTransferStreamHandler: NSObject, FlutterStreamHandler {
  private var sink: FlutterEventSink?

  public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
    print("✅ StreamHandler onListen called")
    sink = events
    return nil
  }

  public func onCancel(withArguments arguments: Any?) -> FlutterError? {
    sink = nil
    return nil
  }

  public func startMove(from: String, to: String, deleteOriginal: Bool) {
    DispatchQueue.global().async {
      guard let fromURL = URL(string: from),
            let toURL = URL(string: to) else {
        print("❌ Bad URL format")
        self.sink?(FlutterError(code: "INVALID_URL", message: "Bad URL", details: nil))
        return
      }

      guard fromURL.startAccessingSecurityScopedResource(),
            toURL.startAccessingSecurityScopedResource() else {
        print("❌ Failed to access security-scoped resources")
        self.sink?(FlutterError(code: "SECURITY", message: "Access denied", details: nil))
        return
      }

      defer {
        fromURL.stopAccessingSecurityScopedResource()
        toURL.stopAccessingSecurityScopedResource()
      }

      do {
        let fileManager = FileManager.default
        print("📂 Reading from: \(fromURL.path)")
        print("📁 Writing to: \(toURL.path)")

        let files = try fileManager.contentsOfDirectory(atPath: fromURL.path)
        print("🔍 Found \(files.count) file(s): \(files)")

        let total = files.count
        var success = 0
        var failure = 0

        for file in files {
          if self.shouldSkip(file) {
            print("⏭ Skipping file: \(file)")
            continue
          }

          let src = fromURL.appendingPathComponent(file)
          let dst = toURL.appendingPathComponent(file)

          print("📤 Attempting to copy \(src.path) → \(dst.path)")

          do {
            if fileManager.fileExists(atPath: dst.path) {
              try fileManager.removeItem(at: dst)
              print("🧹 Removed existing at destination: \(dst.path)")
            }
            try fileManager.copyItem(at: src, to: dst)
            if deleteOriginal {
              try? fileManager.removeItem(at: src)
              print("🗑 Deleted original: \(src.path)")
            }
            success += 1
          } catch {
            print("❌ Failed to copy \(file): \(error.localizedDescription)")
            failure += 1
            self.sink?(FlutterError(code: "COPY_FAILED", message: error.localizedDescription, details: nil))
          }

          let progress = (success + failure) * 100 / max(total, 1)
          self.sink?([
            "currentFile": file,
            "progress": progress,
            "successCount": success,
            "failureCount": failure,
            "totalCount": total
          ])
        }
      } catch {
        print("❌ Failed to list directory: \(error.localizedDescription)")
        self.sink?(FlutterError(code: "LIST_FAILED", message: error.localizedDescription, details: nil))
      }
    }
  }

  private func shouldSkip(_ name: String) -> Bool {
    return name.hasPrefix(".") ||
           name == ".DS_Store" ||
           name.lowercased().contains("trash") ||
           name.lowercased().contains("spotlight")
  }
}
