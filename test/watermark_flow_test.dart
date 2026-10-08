// watermark_flow_test.dart — 端到端：WmBitmap 构造 -> 嵌入 -> 提取。
// 验证 App 使用的核心库完整链路（不依赖 GPU 光栅化，可在 headless 跑）。
import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
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

  // JPEG 鲁棒性回归（2026-10-08 调参验收）：默认算法（QIM q=16）在聊天工具
  // 标准压缩档（JPEG Q75）后仍可完整提取——取证照片经转发后仍可验证。
  // q=8 时 Q75 即丢水印（bin/diag_jpeg.dart 实测），此测试钉住调参成果。
  testWidgets('默认算法 JPEG Q75 压缩后仍可提取', (WidgetTester tester) async {
    final wm = smoothImage(640, 480);
    const msg = 'e2e flow ok 42';
    final algo = defaultAlgorithms().first; // QIM-DCT 保色（q=16）
    final embedded = algo.embed(wm, msg);
    // WmBitmap -> image 包 -> JPEG Q75 -> 解码 -> WmBitmap
    final img0 = img.Image(width: embedded.width, height: embedded.height);
    for (int y = 0; y < embedded.height; y++) {
      for (int x = 0; x < embedded.width; x++) {
        final p = embedded.getPixel(x, y);
        img0.setPixelRgba(x, y, (p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF, 255);
      }
    }
    final jpg = img.encodeJpg(img0, quality: 75);
    final decoded = img.decodeJpg(jpg)!;
    final px = List<int>.filled(decoded.width * decoded.height, 0);
    for (int y = 0; y < decoded.height; y++) {
      for (int x = 0; x < decoded.width; x++) {
        final p = decoded.getPixel(x, y);
        px[y * decoded.width + x] = 0xFF000000 |
            (p.r.toInt() << 16) |
            (p.g.toInt() << 8) |
            p.b.toInt();
      }
    }
    final wm2 = WmBitmap(decoded.width, decoded.height, px);
    expect(algo.extract(wm2), equals(msg));
  });
}