class ExternalStorage {
  final bool isRemovable;

  final int freeBytes;

  final String path;

  ExternalStorage({
    required this.isRemovable,
    required this.freeBytes,
    required this.path,
  });

  factory ExternalStorage.fromMap(Map<String, dynamic> map) {
    return ExternalStorage(
      isRemovable: map['isRemovable'],
      freeBytes: map['freeBytes'],
      path: map['path'],
    );
  }
}
