class MoveProgress {
  final int totalFiles;
  final int successCount;
  final int failureCount;
  final String currentFile;
  final int progress;

  MoveProgress({
    required this.totalFiles,
    required this.successCount,
    required this.failureCount,
    required this.currentFile,
    required this.progress,
  });

  factory MoveProgress.fromMap(Map<String, dynamic> map) {
    return MoveProgress(
      totalFiles: map['totalFiles'] ?? 0,
      successCount: map['successCount'] ?? 0,
      progress: map['progress'] ?? 0,
      failureCount: map['failureCount'] ?? 0,
      currentFile: map['currentFile'] ?? '',
    );
  }
}
