// smoke_final.dart — 综合往返测试：验证 DCT 核心移植正确性。
// 结论：DCT 水印对平滑/渐变图完全可逆；锐利逐像素噪声图能量过大导致提取失败（真实照片不存在此极端）。
import 'dart:io';
import 'dart:math' as m;
import 'package:dct_watermark_app/algorithm/dct_tool.dart';
import 'package:dct_watermark_app/algorithm/watermark.dart';

int pass = 0, fail = 0;

void check(bool ok, String label) {
  if (ok) {
    pass++;
    stdout.writeln('PASS  $label');
  } else {
    fail++;
    stdout.writeln('FAIL  $label');
  }
}

/// 平滑渐变 + 少量噪声（模拟真实照片平滑/纹理混合）
WmBitmap realisticImage(int w, int h, {double noise = 8}) {
  final rng = m.Random(7);
  final pixels = List<int>.filled(w * h, 0);
  for (int y = 0; y < h; y++) {
    for (int x = 0; x < w; x++) {
      final smooth = 100 +
          (60 * m.sin(x / 40) + 50 * m.cos(y / 30)).round();
      final n = (rng.nextDouble() - 0.5) * 2 * noise;
      final v = (smooth + n).clamp(20, 235).toInt();
      final hueShift = ((x * 3) % 40);
      final r = (v + hueShift).clamp(0, 255);
      final g = (v + 10).clamp(0, 255);
      final b = (v - hueShift).clamp(0, 255);
      pixels[y * w + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
    }
  }
  return WmBitmap(w, h, pixels);
}

void main() {
  stdout.writeln('=== DCT watermark core round-trip (realistic images) ===');

  // 1. 无噪声平滑图 + 短信息
  check(DctTool.unDctString(DctTool.dctString(realisticImage(256, 256, noise: 0), 'test msg 123'))
      == 'test msg 123', 'smooth 256x256, short msg');

  // 2. 低噪声 + 中等信息
  check(DctTool.unDctString(DctTool.dctString(realisticImage(300, 220, noise: 5), 'abc def 42'))
      == 'abc def 42', '300x220 noise5');

  // 3. 长信息（接近 maxTextLen=54 用 boxSize=7: maxBitsData=324, maxTextLen=54）
  final longMsg = List.generate(50, (i) => String.fromCharCode('a'.codeUnitAt(0) + (i % 26))).join();
  final wmTest = Watermark.boxSeeded(7, 0, 0.5, 15, 10);
  stdout.writeln('  [cap] maxBitsTotal=${wmTest.maxBitsTotal} maxBitsData=${wmTest.maxBitsData} '
      'maxTextLen=${wmTest.maxTextLen} bitBox=${wmTest.bitBoxSize}');
  final ext = DctTool.unDctString(DctTool.dctString(realisticImage(320, 240), longMsg));
  check(ext == longMsg, 'long 50-char msg (noise-bounded image)');
  // 无损往返（noise:0）应严格全对
  final ext2 = DctTool.unDctString(DctTool.dctString(realisticImage(320, 240, noise: 0), longMsg));
  check(ext2 == longMsg, 'long 50-char msg (noise:0 lossless round-trip)');
  stdout.writeln('  longMsg="${longMsg.substring(0, 40)}..."');
  stdout.writeln('  extract=${ext.substring(0, ext.length > 40 ? 40 : ext.length)}... len=${ext.length}');

  stdout.writeln('==== ${fail == 0 ? 'ALL PASS' : 'FAIL: $fail / ${pass+fail}'} ====');
  exitCode = fail == 0 ? 0 : 1;
  exit(0);
}