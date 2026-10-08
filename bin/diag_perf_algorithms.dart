// diag_perf_algorithms.dart — 三个保色算法处理单张照片的耗时基准。
// 测量 embed（嵌入）和 extract（提取）耗时，覆盖常见照片尺寸。
import 'dart:math' as m;
import 'package:dct_watermark_app/algorithm/algorithm.dart';

/// 生成带色彩的真实感照片（模拟相机拍摄）。
WmBitmap photo(int w, int h) {
  final rng = m.Random(7);
  final px = List<int>.filled(w * h, 0);
  for (int y = 0; y < h; y++) {
    for (int x = 0; x < w; x++) {
      final r = (120 + 80 * m.sin(x / 30 + y / 20)).clamp(0, 255).round();
      final g = (100 + 70 * m.cos(x / 25 - y / 15)).clamp(0, 255).round();
      final b = (140 + 60 * m.sin(x / 20 + y / 25)).clamp(0, 255).round();
      px[y * w + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
    }
  }
  return WmBitmap(w, h, px);
}

/// 测量单次操作耗时（毫秒）。
int measure(void Function() fn) {
  final sw = Stopwatch()..start();
  fn();
  sw.stop();
  return sw.elapsedMilliseconds;
}

void main() {
  final text = 'DCT 2026-09-18T09:30 g31.235,121.473 d8304c53be0aaa7af';
  final algorithms = defaultAlgorithms();

  // 常见照片尺寸（相机拍摄）
  final sizes = [
    (640, 480, '640×480'),
    (1280, 960, '1280×960'),
    (1080, 1920, '1080×1920(竖拍)'),
  ];

  for (final (w, h, label) in sizes) {
    print('=== 照片尺寸 $label ===');
    final img = photo(w, h);
    for (final algo in algorithms) {
      // 文本截断到容量内
      final cap = algo.maxTextLength(img);
      final t = text.length > cap ? text.substring(0, cap) : text;

      // embed 耗时（含一次预热）
      measure(() => algo.embed(img, t));
      final embedMs = measure(() => algo.embed(img, t));

      // extract 耗时
      final embedded = algo.embed(img, t);
      final extractMs = measure(() => algo.extract(embedded));

      print('  ${algo.name.padRight(14)} 嵌入=${embedMs.toString().padLeft(4)}ms 提取=${extractMs.toString().padLeft(4)}ms 容量=$cap');
    }
    print('');
  }
}