import 'package:file_transfer_helper/model/move_progress.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'file_transfer_helper_platform_interface.dart';

/// An implementation of [FileTransferHelperPlatform] that uses method channels.
class MethodChannelFileTransferHelper extends FileTransferHelperPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('file_transfer_helper');

  @visibleForTesting
  final eventChannel = const EventChannel('file_transfer_helper/progress');

  @override
  Future<String?> selectDirectory() async {
    try {
      final res = await methodChannel.invokeMethod('selectDirectory');
      return res;
    } on Object catch (_) {
      rethrow;
    }
  }

  @override
  Stream<MoveProgress> move(String fromPath, String toPath) {
    try {
      methodChannel.invokeMethod('moveDirectory', {'fromUri': fromPath, 'toUri': toPath});
      return eventChannel
          .receiveBroadcastStream()
          .map((event) => MoveProgress.fromMap(Map<String, dynamic>.from(event)));
    } on Object catch (_) {
      rethrow;
    }
  }
}
