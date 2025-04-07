import 'package:file_transfer_helper/model/move_progress.dart';
import 'package:flutter/services.dart';

import 'file_transfer_helper_platform_interface.dart';

/// An implementation of [FileTransferHelperPlatform] that uses method channels.
class MethodChannelFileTransferHelper extends FileTransferHelperPlatform {
  static const MethodChannel _channel = MethodChannel('file_transfer_helper');
  static const EventChannel _progressChannel = EventChannel('file_transfer_helper/progress');

  @override
  Future<String?> selectDirectory() => _channel.invokeMethod<String>('selectDirectory');

  @override
  Stream<MoveProgress> move(
    String from,
    String to, {
    bool deleteOriginal = true,
  }) {
    final stream = _progressChannel
        .receiveBroadcastStream()
        .where((t) => t != null)
        .map((event) => MoveProgress.fromMap(Map<String, dynamic>.from(event as Map)));

    _channel.invokeMethod(
      'move',
      {
        'from': from,
        'to': to,
        'deleteOriginal': deleteOriginal,
      },
    );

    return stream.handleError((error) {
      if (error is PlatformException) {
        // Обработка ошибки от native-кода
        throw FileTransferException(
          code: error.code,
          message: error.message,
          details: error.details,
        );
      } else {
        throw error;
      }
    });
  }
}
