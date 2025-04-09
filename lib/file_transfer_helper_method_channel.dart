import 'package:file_transfer_helper/model/external_storage.dart';
import 'package:file_transfer_helper/model/move_progress.dart';
import 'package:flutter/services.dart';

import 'dart:async';

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
    
    final controller = StreamController<MoveProgress>();

    final stream = _progressChannel
        .receiveBroadcastStream()
        .where((t) => t != null)
        .map((event) => MoveProgress.fromMap(Map<String, dynamic>.from(event as Map)))
        .listen(
          controller.add,
          onError: controller.addError,
          onDone: controller.close,
        );

    Future.microtask(() {
      _channel.invokeMethod(
        'move',
        {
          'from': from,
          'to': to,
          'deleteOriginal': deleteOriginal,
        },
      ).catchError(controller.addError);
    });

    return controller.stream.handleError((error) {
      if (error is PlatformException) {
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

  @override
  Future<List<ExternalStorage>> getExternalStorageInfo() => _channel.invokeMethod<Map>('getExternalStorageInfo').then(
        (value) {
          final storages = value?['storages'] as List?;

          return storages
                  ?.map((externalStorage) => ExternalStorage.fromMap(
                        Map<String, dynamic>.from(externalStorage as Map),
                      ))
                  .toList() ??
              [];
        },
      );
}
