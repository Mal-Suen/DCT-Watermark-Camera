// diag_extract_plain.dart — 验证 DCT 提取对"无水印普通图"的真实返回值。
// 决定"多算法盲识别"的判据是否可行。
import 'package:dct_watermark_app/algorithm/algorithm.dart';

WmBitmap plainImage(int w, int h) {
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
  final algo = DctAlgorithm();
  // 1. 无水印普通图
  print('1) 无水印普通图 extract = <${algo.extract(plainImage(256, 256))}>');
  // 2. 空白/单色图
  final solid = WmBitmap(128, 128, List.filled(128 * 128, 0xFF808080));
  print('2) 纯灰图 extract = <${algo.extract(solid)}>');
  // 3. 嵌了一次水印的图
  final wm = algo.embed(plainImage(256, 256), 'hello dct');
  print('3) 已嵌水印图 extract = <${algo.extract(wm)}>');
  print('defaultErrorMessage = <${DctAlgorithm.defaultErrorMessage}>');
}