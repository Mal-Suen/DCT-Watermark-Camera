// diag_perf.dart — 实测 DCT 嵌入/提取耗时，评估"盲提取遍历"是否可行。
import 'package:dct_watermark_app/algorithm/algorithm.dart';

WmBitmap plainImage(int w, int h) {
  final pixels = List<int>.filled(w * h, 0);
  for (int y = 0; y < h; y++) {
    for (int x = 0; x < w; x++) {
      final v = 100 + ((x * 255) ~/ w).round() ~/ 4;
      pixels[y * w + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
    }
  }
  return WmBitmap(w, h, pixels);
}

int ms(Stopwatch s) => s.elapsedMilliseconds;

void main() {
  const sizes = [256, 512, 1024, 1440];
  final algo = DctAlgorithm();
  for (final s in sizes) {
    final src = plainImage(s, s);
    final wm = algo.embed(src, 'watermark test content 5123456789');
    final sw = Stopwatch()..start();
    // 提取 3 次求均值
    String r = '';
    for (int i = 0; i < 3; i++) {
      r = algo.extract(wm);
    }
    sw.stop();
    final avg = (sw.elapsedMilliseconds / 3).toStringAsFixed(0);
    print('${s}x$s 图: 提取≈${avg}ms/次  (结果=<$r>)');
  }
  print('--- 盲提取遍历耗时估算(N个算法=单次×N) 见上方实测 ---');
}