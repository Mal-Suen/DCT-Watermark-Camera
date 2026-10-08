// diag_capacity.dart — 研究 bitBoxSize 对容量与鲁棒性的影响。
// 目标：突破当前 54 字符上限，同时保持合理抗噪能力。
import 'dart:math' as m;
import 'package:dct_watermark_app/algorithm/watermark.dart';

/// 平滑渐变 + 可控噪声的"真实"测试图。
WmBitmap realistic(int w, int h, {double noise = 0}) {
  final rng = m.Random(7);
  final px = List<int>.filled(w * h, 0);
  for (int y = 0; y < h; y++)
    for (int x = 0; x < w; x++) {
      final smooth = 100 + (60 * m.sin(x / 40) + 50 * m.cos(y / 30)).round();
      final v = (smooth + (rng.nextDouble() - 0.5) * 2 * noise)
          .clamp(20, 235)
          .toInt();
      px[y * w + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
    }
  return WmBitmap(w, h, px);
}

void main() {
  print('=== bitBoxSize 容量-鲁棒性 扫描 ===');
  final lens = {4: 170, 5: 104, 6: 73, 7: 54};
  for (final e in lens.entries) {
    final box = e.key;
    final cap = e.value;
    final wm = Watermark.boxSeeded(box, 0, 0.5, 15, 10);
    print('--- box=$box maxTextLen=$cap ---');
    for (final useLen in [cap, cap ~/ 2, cap ~/ 3]) {
      final text = List.generate(useLen, (i) => 'a').join();
      for (final noise in [0.0, 4.0]) {
        final img = realistic(320, 240, noise: noise);
        try {
          final watermarked = wm.embedString(img, text);
          final extracted = wm.extractText(watermarked);
          final ok = extracted == text;
          print('  len=$useLen noise=$noise => ${ok ? "OK" : "FAIL"}');
        } catch (ex) {
          print('  len=$useLen noise=$noise => EXC $ex');
        }
      }
    }
  }
}