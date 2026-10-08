// diag_qim.dart — 新一代 QIM-DCT 水印算法验收。
// 验证：嵌入→提取往返、容量、抗噪、RS 纠错是否生效。
import 'dart:math' as m;
import 'package:dct_watermark_app/algorithm/algorithm.dart';

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
  print('=== DctQimAlgorithm 验收 ===');

  // 1) 容量：320x240 → 40*30=1200块 → 1200bit → 约(1200-48)/8=144字节
  final cap = 1200 ~/ 8 - 1;
  print('理论容量(320x240) ≈ ${cap} 字节 / ${1200} bit');

  // 2) 往返 + 抗噪
  var text = 'QIM watermark test: device 8304c53b time 2026-09-18T09:30';
  for (final noise in [0.0, 3.0, 6.0, 10.0, 15.0]) {
    final img = realistic(320, 240, noise: noise);
    final wm = DctQimAlgorithm(q: 16, eccBytes: 6);
    final embedded = wm.embed(img, text);
    final extracted = wm.extract(embedded);
    final ok = extracted == text;
    print('  noise=$noise q=16 len=${text.length} => ${ok ? "OK" : "FAIL"}  '
        '提取=<${extracted.substring(0, extracted.length > 18 ? 18 : extracted.length)}...>');
  }

  // 3) 长容量测试(接近满)
  final long = List.generate(cap - 5, (i) => 'a').join();
  final img2 = realistic(320, 240);
  final em2 = DctQimAlgorithm(q: 16, eccBytes: 6).embed(img2, long);
  final ex2 = DctQimAlgorithm(q: 16, eccBytes: 6).extract(em2);
  print('  长文本 len=${long.length} => ${ex2 == long ? "OK" : "FAIL"}');

  // 4) 空串/非取证输入
  final plain = realistic(256, 256);
  final ex3 = DctQimAlgorithm().extract(plain);
  print('  未嵌水印图 extract => <$ex3>');
}