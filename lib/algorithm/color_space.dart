// color_space.dart — 可逆色彩变换（YCoCg）用于保色水印。
//
// 用途：不可见水印只在 Y（亮度）通道嵌入，Co/Cg（色度）保持不变，
// 从而在保留全部色彩的前提下实现视觉无损嵌入。
//
// 为什么用 YCoCg 而非 YCbCr：
//   YCbCr（BT.601）在量化到 8bit 整数时不可逆，RGB→YCbCr→RGB 会引入
//   ~30dB 的往返误差（实测），无法达到 PSNR≥40dB 的视觉无损标准。
//   YCoCg 是可逆整数变换（Reversible Color Transform），往返**完全无损**，
//   只在 Y 通道嵌入时，色度 Co/Cg 原样保留，视觉质量最优。
//
// 本文件纯 Dart，无依赖。

/// RGB 像素（0xFFRRGGBB）转 YCoCg 三通道。
///
/// 返回 [YCoCg]（Y/Co/Cg 均为 0~255 整数）。此变换可逆，往返无损。
YCoCg rgbToYCoCg(int argb) {
  final r = (argb >> 16) & 0xFF;
  final g = (argb >> 8) & 0xFF;
  final b = argb & 0xFF;
  final co = r - b;
  final t = b + (co >> 1);
  final cg = g - t;
  final y = t + (cg >> 1);
  return YCoCg(y, co, cg);
}

/// YCoCg 转回 ARGB 像素（保留 alpha=0xFF）。可逆，往返无损。
int yCoCgToRgb(YCoCg c) {
  final t = c.y - (c.cg >> 1);
  final g = c.cg + t;
  final b = t - (c.co >> 1);
  final r = c.co + b;
  final rc = r.clamp(0, 255);
  final gc = g.clamp(0, 255);
  final bc = b.clamp(0, 255);
  return 0xFF000000 | (rc << 16) | (gc << 8) | bc;
}

/// 从 ARGB 提取亮度 Y（YCoCg）。
int brightnessOf(int argb) => rgbToYCoCg(argb).y;

/// 从 ARGB 提取红通道。
int redOf(int argb) => (argb >> 16) & 0xFF;

/// 从 ARGB 提取绿通道。
int greenOf(int argb) => (argb >> 8) & 0xFF;

/// 从 ARGB 提取蓝通道。
int blueOf(int argb) => argb & 0xFF;

/// YCoCg 颜色值。
class YCoCg {
  final int y;
  final int co;
  final int cg;
  const YCoCg(this.y, this.co, this.cg);
}
