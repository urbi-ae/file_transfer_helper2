// You have generated a new plugin project without specifying the `--platforms`
// flag. A plugin project with no platform support was generated. To add a
// platform, run `flutter create -t plugin --platforms <platforms> .` under the
// same directory. You can also find a detailed instruction on how to add
// platforms in the `pubspec.yaml` at
// https://flutter.dev/docs/development/packages-and-plugins/developing-packages#plugin-platforms.

import 'package:file_transfer_helper/model/external_storage.dart';
import 'package:file_transfer_helper/model/move_progress.dart';

import 'file_transfer_helper_platform_interface.dart';

class FileTransferHelper {
  Future<String?> selectDirectory() => FileTransferHelperPlatform.instance.selectDirectory();

  Future<List<ExternalStorage>> getExternalStorageInfo() =>
      FileTransferHelperPlatform.instance.getExternalStorageInfo();

  Stream<MoveProgress> move(
    String fromPath,
    String toPath, {
    bool deleteOriginal = true,
  }) =>
      FileTransferHelperPlatform.instance.move(
        fromPath,
        toPath,
        deleteOriginal: deleteOriginal,
      );
}
