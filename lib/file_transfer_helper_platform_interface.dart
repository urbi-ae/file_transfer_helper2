import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'file_transfer_helper_method_channel.dart';
import 'model/external_storage.dart';
import 'model/move_progress.dart';

abstract class FileTransferHelperPlatform extends PlatformInterface {
  /// Constructs a FileTransferHelperPlatform.
  FileTransferHelperPlatform() : super(token: _token);

  static final Object _token = Object();

  static FileTransferHelperPlatform _instance = MethodChannelFileTransferHelper();

  /// The default instance of [FileTransferHelperPlatform] to use.
  ///
  /// Defaults to [MethodChannelFileTransferHelper].
  static FileTransferHelperPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FileTransferHelperPlatform] when
  /// they register themselves.
  static set instance(FileTransferHelperPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> selectDirectory() {
    throw UnimplementedError('selectDirectory() has not been implemented.');
  }

  Stream<MoveProgress> move(
    String from,
    String to, {
    bool deleteOriginal = true,
  }) {
    // TODO: implement move
    throw UnimplementedError();
  }

  Future<List<ExternalStorage>> getExternalStorageInfo() {
    // TODO: implement move

    throw UnimplementedError();
  }
}

class FileTransferException implements Exception {
  final String code;
  final String? message;
  final dynamic details;

  FileTransferException({
    required this.code,
    this.message,
    this.details,
  });

  @override
  String toString() => 'FileTransferException($code): $message';
}
