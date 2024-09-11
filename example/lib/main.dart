import 'dart:async';

import 'package:file_transfer_helper/file_transfer_helper.dart';
import 'package:file_transfer_helper/model/move_progress.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(const MaterialApp(home: MyApp()));
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final fileTransferHelperPlugin = FileTransferHelper();

  String? selectedDirectoryUri;
  String operationStatus = "No operations yet";

  String fromDirectory = 'Not selected';
  String toDirectory = 'Not selected';

  Future<void> export() async {
    try {
      // final result = await getDownloadsDirectory();
      final result = await getApplicationDocumentsDirectory();
      debugPrint('selectDirectory result: $result');
      fromDirectory = result.absolute.path;
      final selResult = await fileTransferHelperPlugin.selectDirectory();

      if (selResult == null) {
        return;
      }

      toDirectory = selResult.toString();

      moveDirectory();

      setState(() {});
    } on PlatformException catch (e) {
      print("Failed to get directory: '${e.message}'.");
    }
  }

  Future<void> import() async {
    try {
      final result = await fileTransferHelperPlugin.selectDirectory();
      if (result == null) {
        return;
      }
      fromDirectory = result.toString();
      toDirectory = await getApplicationDocumentsDirectory().then((value) => value.absolute.path);
      setState(() {});
      await moveDirectory();
    } on PlatformException catch (e) {
      print("Failed to get directory: '${e.message}'.");
    }
  }

  Future<void> selectDirectory(String type) async {
    try {
      final result = await fileTransferHelperPlugin.selectDirectory();
      debugPrint('selectDirectory result: $result');
      if (result == null) {
        return;
      }
      setState(() {
        if (type == 'from') {
          fromDirectory = result.toString();
        } else {
          toDirectory = result.toString();
        }
      });
    } on PlatformException catch (e) {
      print("Failed to get directory: '${e.message}'.");
    }
  }

  Future<void> moveDirectory() async {
    try {
      if (fromDirectory == 'Not selected' || toDirectory == 'Not selected') {
        setState(() {
          operationStatus = "Please select both directories";
        });
        return;
      }

      final storagePermissionStatus = await Permission.manageExternalStorage.request();

      if (storagePermissionStatus != PermissionStatus.granted) {
        setState(() {
          operationStatus = "Storage permission not granted";
        });
        return;
      }

      final sub = fileTransferHelperPlugin.move(fromDirectory, toDirectory);
      late final StreamSubscription<MoveProgress> subscription;
      subscription = sub.listen((event) {
        if (event.progress == 100) {
          subscription.cancel();
          operationStatus = "Done moving files";
        }
        setState(() {
          operationStatus = "Moving file: ${event.currentFile} (${event.progress})";
        });
      });
    } on PlatformException catch (e) {
      print("Failed to move directory: ${e.message}");
      setState(() {
        operationStatus = "Failed to move directory: ${e.message}";
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Directory Import/Export Example"),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            Text(fromDirectory),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: () {
                // export()
                selectDirectory('from');
              },
              child: const Text("set from Directory"),
            ),
            const SizedBox(height: 20),
            Text(toDirectory),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: () => selectDirectory('to'),
              child: const Text("set to Directory"),
            ),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: moveDirectory,
              child: const Text("Move Directory"),
            ),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: export,
              child: const Text("Export Directory"),
            ),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: import,
              child: const Text("Import Directory"),
            ),
            const SizedBox(height: 20),
            Text(operationStatus),
            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }
}
