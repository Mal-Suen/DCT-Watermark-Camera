// watermark_flow_test.dart — 端到端：WmBitmap 构造 -> 嵌入 -> 提取。
// 验证 App 使用的核心库完整链路（不依赖 GPU 光栅化，可在 headless 跑）。
import 'package:flutter_test/flutter_test.dart';
import 'package:dct_watermark_app/algorithm/algorithm.dart';

WmBitmap smoothImage(int w, int h) {
  final pixels = List<int>.filled(w * h, 0);
  for (int y = 0; y < h; y++) {
    for (int x = 0; x < w; x++) {
      final v = 100 + ((x * 255) ~/ w).round() ~/ 4;
      pixels[y * w + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
    }
  }
  return WmBitmap(w, h, pixels);
}

void main() {
  testWidgets('核心库 嵌入→提取 端到端（旧版兼容 API）', (WidgetTester tester) async {
    final wm = smoothImage(256, 256);
    const msg = 'e2e flow ok 42';
    final watermarked = DctTool.dctString(wm, msg);
    final extracted = DctTool.unDctString(watermarked);
    expect(extracted, equals(msg));
  });

  testWidgets('核心库 长文本 无损往返（旧版兼容 API）', (WidgetTester tester) async {
    final wm = smoothImage(320, 240);
    final msg = List.generate(45, (i) => String.fromCharCode('a'.codeUnitAt(0) + (i % 26))).join();
    final watermarked = DctTool.dctString(wm, msg);
    final extracted = DctTool.unDctString(watermarked);
    expect(extracted, equals(msg));
  });

  // 修复（2026-10-08 代码审查 S1）：App 实际算法注册表此前零测试覆盖——
  // 旧测试只测 App 不会调用的 DctTool 兼容路径，新算法回归会静默通过。
  testWidgets('App 实际算法注册表 defaultAlgorithms() 全部往返', (WidgetTester tester) async {
    // 640×480 保证扩频容量（68 字符）> 测试文本（14 字符）
    final img = smoothImage(640, 480);
    const msg = 'e2e flow ok 42';
    for (final a in defaultAlgorithms()) {
      final embedded = a.embed(img, msg);
      expect(a.extract(embedded), equals(msg), reason: a.id);
    }
  });

  // 修复（C5）验收：干净图提取必须返回空串（"是否含水印"判据，fail-closed）
  testWidgets('干净图提取返回空串（无水印判据）', (WidgetTester tester) async {
    final img = smoothImage(320, 240);
    for (final a in defaultAlgorithms()) {
      expect(a.extract(img), isEmpty, reason: a.id);
    }
  });
}