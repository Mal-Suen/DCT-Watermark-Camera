// diag_box6.dart — 验证 box=6 满载(73字符) 抗噪稳定性 vs box=7(54字符)。
// 目标是实证：能否"白捡"容量提升且不牺牲(甚至提升)鲁棒性。
import 'dart:math' as m;
import 'package:dct_watermark_app/algorithm/watermark.dart';

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
  print('=== box6 满载 vs box7 满载 抗噪曲线 ===');
  for (final box in [6, 7]) {
    final cap = Watermark.boxSeeded(box, 0, 0.5, 15, 10).maxTextLen;
    final text = List.generate(cap, (i) => 'a').join();
    final fails = <double>[];
    for (final noise in [0.0, 2.0, 4.0, 6.0, 8.0, 10.0]) {
      // 多次取成功率
      int ok = 0;
      for (int rep = 0; rep < 5; rep++) {
        final img = realistic(320, 240, noise: noise);
        final wm = Watermark.boxSeeded(box, 0, 0.5, 15, 10);
        try {
          final e = wm.extractText(wm.embedString(img, text));
          if (e == text) ok++;
        } catch (_) {}
      }
      final note = 'noise=$noise: $ok/5';
      print('box=$box cap=$cap  $note');
      if (ok < 5) fails.add(noise);
    }
    print('  -> 首次开始失配的噪声级 = ${fails.isEmpty ? ">10" : fails.first}');
  }
}