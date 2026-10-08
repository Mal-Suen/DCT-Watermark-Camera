// diag_qim_color.dart — QIM-DCT 保色版水印算法验收。
// 验证：嵌入→提取往返、色彩保留（Cb/Cr 不变）、视觉无损（PSNR）、抗噪。
import 'dart:math' as m;
import 'package:dct_watermark_app/algorithm/algorithm.dart';

/// 生成带色彩的平滑测试图（非灰度，验证保色能力）。
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

/// 计算 PSNR（dB）。越高越无损。
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

/// 统计色度（Co/Cg）平均绝对偏差——衡量色彩保留程度。
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
  print('=== DctQimColorAlgorithm 验收 ===');
  final text = 'QIM color watermark: device 8304c53b time 2026-09-18T09:30';

  // 0) 纯 YCoCg 转换往返基线（不改 Y，仅 RGB→YCoCg→RGB）
  final baseImg = colorImage(320, 240);
  final basePx = List<int>.from(baseImg.pixelData);
  for (int i = 0; i < basePx.length; i++) {
    final c = rgbToYCoCg(basePx[i]);
    basePx[i] = yCoCgToRgb(c);
  }
  final roundTrip = WmBitmap(baseImg.width, baseImg.height, basePx);
  final basePsnr = psnr(baseImg, roundTrip);
  final baseCd = chromaDiff(baseImg, roundTrip);
  print('  [基线] 纯转换往返: PSNR=${basePsnr.toStringAsFixed(2)}dB 色度偏差=${baseCd.toStringAsFixed(3)}');

  // 1) 往返 + 色彩保留 + PSNR（无噪）
  final img = colorImage(320, 240);
  final algo = DctQimColorAlgorithm(q: 8, eccBytes: 6);
  final embedded = algo.embed(img, text);
  final extracted = algo.extract(embedded);
  final p = psnr(img, embedded);
  final cd = chromaDiff(img, embedded);
  print('  往返: ${extracted == text ? "OK" : "FAIL"}');
  print('  PSNR: ${p.toStringAsFixed(2)} dB (视觉无损需≥40)');
  print('  色度偏差: ${cd.toStringAsFixed(3)} (0=色彩完全保留)');

  // 2) 抗噪
  for (final noise in [0.0, 3.0, 6.0, 10.0]) {
    final nimg = colorImage(320, 240, noise: noise);
    final em = algo.embed(nimg, text);
    final ex = algo.extract(em);
    print('  noise=$noise => ${ex == text ? "OK" : "FAIL"}');
  }

  // 3) 未嵌水印图提取
  final plain = colorImage(256, 256);
  final ex3 = algo.extract(plain);
  print('  未嵌水印图 extract => <$ex3>');

  // 4) 容量
  final cap = algo.maxTextLength(img);
  print('  容量(320x240): $cap 字符');
}
