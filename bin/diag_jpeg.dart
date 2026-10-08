// diag_jpeg.dart — JPEG 压缩鲁棒性实测（照片分享场景的核心威胁）。
// 流程：嵌入 → JPEG 编码（多质量档）→ 解码 → 提取，统计各算法存活区间。
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

void main() {
  print('=== JPEG 压缩鲁棒性实测（640x480 彩色图）===');
  final src = colorImage(640, 480);
  const text = 'DCT 2026-10-08T09:30 g31.235,121.473 d8304c53be0aaa7af';
  final qualities = [95, 90, 85, 80, 75, 70, 60, 50];

  for (final algo in defaultAlgorithms()) {
    final cap = algo.maxTextLength(src);
    final t = text.length > cap ? text.substring(0, cap) : text;
    final embedded = algo.embed(src, t);
    final sb = StringBuffer();
    for (final q in qualities) {
      final jpg = img.encodeJpg(wmToImage(embedded), quality: q);
      final decoded = imageToWm(img.decodeJpg(jpg)!);
      final ok = algo.extract(decoded) == t;
      sb.write('Q$q=${ok ? "OK" : "FAIL"}  ');
    }
    print('  ${algo.name.padRight(14)} $sb');
  }
}
