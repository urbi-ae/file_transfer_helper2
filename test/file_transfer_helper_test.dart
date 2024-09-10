import 'package:file_transfer_helper/file_transfer_helper_method_channel.dart';
import 'package:file_transfer_helper/file_transfer_helper_platform_interface.dart';
import 'package:file_transfer_helper/model/move_progress.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockFileTransferHelperPlatform with MockPlatformInterfaceMixin implements FileTransferHelperPlatform {
  @override
  Stream<MoveProgress> move(String fromPath, String toPath) {
    // TODO: implement move
    throw UnimplementedError();
  }

  @override
  Future<String?> selectDirectory() {
    // TODO: implement selectDirectory
    throw UnimplementedError();
  }

  // @override
  // Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final FileTransferHelperPlatform initialPlatform = FileTransferHelperPlatform.instance;

  test('$MethodChannelFileTransferHelper is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelFileTransferHelper>());
  });
  test('getPlatformVersion', () async {
    // FileTransferHelper fileTransferHelperPlugin = FileTransferHelper();
    // MockFileTransferHelperPlatform fakePlatform = MockFileTransferHelperPlatform();
    // FileTransferHelperPlatform.instance = fakePlatform;

    // expect(await fileTransferHelperPlugin.getPlatformVersion(), '42');
  });
}
