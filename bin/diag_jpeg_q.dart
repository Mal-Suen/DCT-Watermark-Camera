// diag_jpeg_q.dart — QIM-DCT 保色算法的 q 值 × JPEG 质量扫描。
// 验证：提高嵌入强度 q 能否扩展 JPEG 存活区间，代价是多少 PSNR。
import 'dart:math' as m;
import 'package:image/image.dart' as img;
import 'package:dct_watermark_app/algorithm/algorithm.dart';

WmBitmap colorImage(int w, int h) {
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

img.Image wmToImage(WmBitmap wm) {
  final image = img.Image(width: wm.width, height: wm.height);
  for (int y = 0; y < wm.height; y++) {
    for (int x = 0; x < wm.width; x++) {
      final p = wm.getPixel(x, y);
      image.setPixelRgba(x, y, (p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF, 255);
    }
  }
  return image;
}

WmBitmap imageToWm(img.Image image) {
  final px = List<int>.filled(image.width * image.height, 0);
  for (int y = 0; y < image.height; y++) {
    for (int x = 0; x < image.width; x++) {
      final p = image.getPixel(x, y);
      px[y * image.width + x] =
          0xFF000000 | (p.r.toInt() << 16) | (p.g.toInt() << 8) | p.b.toInt();
    }
  }
  return WmBitmap(image.width, image.height, px);
}

double psnr(WmBitmap a, WmBitmap b) {
  double mse = 0;
  final n = a.width * a.height;
  for (int i = 0; i < n; i++) {
    final pa = a.pixelAt(i), pb = b.pixelAt(i);
    final dr = ((pa >> 16) & 0xFF) - ((pb >> 16) & 0xFF);
    final dg = ((pa >> 8) & 0xFF) - ((pb >> 8) & 0xFF);
    final db = (pa & 0xFF) - (pb & 0xFF);
    mse += dr * dr + dg * dg + db * db;
  }
  mse /= (n * 3);
  if (mse == 0) return double.infinity;
  return 10 * m.log(255 * 255 / mse) / m.ln10;
}

void main() {
  print('=== QIM-DCT 保色：q 值 × JPEG 质量扫描（640x480）===');
  final src = colorImage(640, 480);
  const text = 'DCT 2026-10-08T09:30 g31.235,121.473 d8304c53be0aaa7af';
  final qualities = [90, 80, 75, 70, 60, 50];

  for (final q in [8.0, 16.0, 24.0, 32.0]) {
    final algo = DctQimColorAlgorithm(q: q, eccBytes: 6);
    final embedded = algo.embed(src, text);
    final p = psnr(src, embedded);
    final sb = StringBuffer();
    for (final jq in qualities) {
      final jpg = img.encodeJpg(wmToImage(embedded), quality: jq);
      final decoded = imageToWm(img.decodeJpg(jpg)!);
      final ok = algo.extract(decoded) == text;
      sb.write('Q$jq=${ok ? "OK" : "F"}  ');
    }
    print('  q=${q.toStringAsFixed(0).padLeft(2)}  PSNR=${p.toStringAsFixed(1)}dB  $sb');
  }
}
