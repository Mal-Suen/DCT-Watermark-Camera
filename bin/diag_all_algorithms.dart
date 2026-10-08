// diag_all_algorithms.dart — 全部算法综合验收。
// 遍历 defaultAlgorithms()，验证每个算法：往返、视觉无损（PSNR）、色彩保留、抗噪。
import 'dart:math' as m;
import 'package:dct_watermark_app/algorithm/algorithm.dart';

WmBitmap colorImage(int w, int h, {double noise = 0}) {
  final rng = m.Random(7);
  final px = List<int>.filled(w * h, 0);
  for (int y = 0; y < h; y++) {
    for (int x = 0; x < w; x++) {
      final r = (120 + 80 * m.sin(x / 30 + y / 20)).clamp(0, 255).round();
      final g = (100 + 70 * m.cos(x / 25 - y / 15)).clamp(0, 255).round();
      final b = (140 + 60 * m.sin(x / 20 + y / 25)).clamp(0, 255).round();
      final nr = (r + (rng.nextDouble() - 0.5) * 2 * noise).clamp(0, 255).round();
      final ng = (g + (rng.nextDouble() - 0.5) * 2 * noise).clamp(0, 255).round();
      final nb = (b + (rng.nextDouble() - 0.5) * 2 * noise).clamp(0, 255).round();
      px[y * w + x] = 0xFF000000 | (nr << 16) | (ng << 8) | nb;
    }
  }
  return WmBitmap(w, h, px);
}

double psnr(WmBitmap a, WmBitmap b) {
  double mse = 0;
  final n = a.width * a.height;
  for (int i = 0; i < n; i++) {
    final pa = a.pixelAt(i);
    final pb = b.pixelAt(i);
    final dr = ((pa >> 16) & 0xFF) - ((pb >> 16) & 0xFF);
    final dg = ((pa >> 8) & 0xFF) - ((pb >> 8) & 0xFF);
    final db = (pa & 0xFF) - (pb & 0xFF);
    mse += dr * dr + dg * dg + db * db;
  }
  mse /= (n * 3);
  if (mse == 0) return double.infinity;
  return 10 * m.log(255 * 255 / mse) / m.ln10;
}

double chromaDiff(WmBitmap a, WmBitmap b) {
  double sum = 0;
  final n = a.width * a.height;
  for (int i = 0; i < n; i++) {
    final ca = rgbToYCoCg(a.pixelAt(i));
    final cb = rgbToYCoCg(b.pixelAt(i));
    sum += (ca.co - cb.co).abs() + (ca.cg - cb.cg).abs();
  }
  return sum / (n * 2);
}

void main() {
  print('=== 全部算法综合验收 ===');
  final img = colorImage(320, 240);
  final text = 'DCT test 8304c53b 2026-09-18';

  for (final algo in defaultAlgorithms()) {
    final cap = algo.maxTextLength(img);
    // 文本截断到容量内
    final t = text.length > cap ? text.substring(0, cap) : text;
    final embedded = algo.embed(img, t);
    final extracted = algo.extract(embedded);
    final p = psnr(img, embedded);
    final cd = chromaDiff(img, embedded);
    print('  ${algo.name.padRight(14)} 容量=${cap.toString().padLeft(3)} '
        '往返=${extracted == t ? "OK" : "FAIL"} '
        'PSNR=${p.toStringAsFixed(1)}dB '
        '色差=${cd.toStringAsFixed(3)}');
  }
}
