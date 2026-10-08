// wm_algorithm.dart — 水印算法抽象接口。
//
// 预留多算法扩展：不同水印算法实现此接口即可被上层（App UI / 服务端）切换使用。
// 当前只有 DCT 频域算法（默认）；后续可新增空间域、其他频域等实现。

import 'watermark.dart';

/// 水印算法能力：嵌入 + 提取。
abstract class WmAlgorithm {
  /// 算法显示名（用于 UI 下拉选择）。
  String get name;

  /// 唯一标识（用于持久化/日志）。
  String get id;

  /// 向图像 [source] 嵌入文本水印 [watermark]，返回嵌入后的新图像。
  WmBitmap embed(WmBitmap source, String watermark);

  /// 从 [image] 提取文本水印；无有效水印或提取失败时返回空串。
  String extract(WmBitmap image);

  /// 该算法在 [image] 上可承载的最大水印字符数（ASCII）。
  /// 不同算法容量不同（旧 DCT 固定 54；QIM-DCT 随图像尺寸增长）。
  int maxTextLength(WmBitmap image);
}