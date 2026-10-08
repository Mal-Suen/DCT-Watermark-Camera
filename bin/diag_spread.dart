// diag_spread.dart — 扩频水印算法验收。
// 验证：嵌入→提取往返、视觉无损（PSNR）、色彩保留、抗噪。
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
  print('=== SpreadSpectrumAlgorithm 验收 ===');
  // S3 修复后容量=11（预留长度前缀字节）；用恰好 11 字符验证满容量不截断
  final text = 'SS test 830';
  final algo = SpreadSpectrumAlgorithm(
      spreadFactor: 8, alpha: 8.0, seed: 42, eccBytes: 6);

  // 1) 往返 + PSNR + 色彩保留
  final img = colorImage(320, 240);
  final embedded = algo.embed(img, text);
  final extracted = algo.extract(embedded);
  print('  往返: ${extracted == text ? "OK" : "FAIL"}');
  print('  PSNR: ${psnr(img, embedded).toStringAsFixed(2)} dB (视觉无损需≥40)');
  print('  色度偏差: ${chromaDiff(img, embedded).toStringAsFixed(3)}');

  // 2) 抗噪
  for (final noise in [0.0, 3.0, 6.0, 10.0, 15.0]) {
    final nimg = colorImage(320, 240, noise: noise);
    final em = algo.embed(nimg, text);
    final ex = algo.extract(em);
    print('  noise=$noise => ${ex == text ? "OK" : "FAIL"}');
  }

  // 3) 未嵌水印图提取
  final plain = colorImage(256, 256);
  print('  未嵌水印图 extract => <${algo.extract(plain)}>');

  // 4) 容量
  print('  容量(320x240): ${algo.maxTextLength(img)} 字符');
}
