/// dct_watermark_core — DCT 数字水印算法核心（跨平台纯 Dart 库）。
///
/// 原 Android 版算法逐行移植（watermark.dart / dct_math.dart 等，位级兼容原版），
/// 现再提供新一代重构算法（QIM 强度调制 + Reed-Solomon 纠错 + 视觉无损保色）。
///
/// 算法目录:
///   - WmBitmap:像素位图画布;Watermark:旧 DCT 核心(兼容)
///   - WmAlgorithm:算法抽象接口;defaultAlgorithms() 注册表
///   - DctAlgorithm:旧 DCT;DctQimAlgorithm:QIM-DCT(灰度);DctQimColorAlgorithm:保色版(视觉无损,推荐);DwtAlgorithm:DWT域(视觉无损)
///   - ReedSolomon / GenericGF:纠错码(QIM 使用)
///   - color_space:RGB⇄YCoCg(保色算法共享,往返无损)
///   - JRandom / Bits / DCT 数学:内部实现
library;

export 'dct_qim.dart';
export 'dct_qim_color.dart';
export 'dwt_algorithm.dart';
export 'spread_spectrum.dart';
export 'dct_tool.dart';
export 'watermark.dart';
export 'wm_algorithm.dart';
export 'reedsolomon.dart';
export 'color_space.dart';