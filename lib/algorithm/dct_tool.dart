// dct_tool.dart — DCT 水印算法实现（原 y告知 DctTool 封装）。
// 实现 WmAlgorithm 接口，可被 App 的算法切换层选用。
// 默认参数即"兼容契约"，必须与原 Android 版完全一致，否则互相读不出水印。

import 'watermark.dart';
import 'wm_algorithm.dart';
import 'dct_qim_color.dart';
import 'dwt_algorithm.dart';
import 'spread_spectrum.dart';

/// DCT 频域水印算法。
class DctAlgorithm implements WmAlgorithm {
  static const String defaultErrorMessage = '取水印error，请重新尝试';
  static const int kBoxSize = 7;
  static const int kErrorCorrection = 0;
  static const double kOpacity = 0.5;
  static const int kSeedWatermark = 10;
  static const int kSeedEmbed = 15;

  @override
  String get name => 'DCT 频域';

  @override
  String get id => 'dct';

  Watermark _water() => Watermark.boxSeeded(
      kBoxSize, kErrorCorrection, kOpacity, kSeedEmbed, kSeedWatermark);

  @override
  WmBitmap embed(WmBitmap source, String watermark) {
    return _water().embedString(source, watermark);
  }

  @override
  String extract(WmBitmap image) {
    try {
      return _water().extractText(image);
    } catch (e) {
      return defaultErrorMessage;
    }
  }

  @override
  int maxTextLength(WmBitmap image) {
    // 旧算法固定用 128×128 区域、boxSize=7、errorCorrection=0：
    // maxTextLen = ((128~/7)^2 - 0) ~/ 6 = 54，与图像尺寸无关。
    return _water().getMaxTextLen();
  }
}

/// 向后兼容的静态封装（等价于 DctAlgorithm 实例）。
class DctTool {
  static final DctAlgorithm _algo = DctAlgorithm();

  static const String tag = 'DctAlgorithm';
  static const String defaultErrorMessage = '取水印error，请重新尝试';

  static WmBitmap dctString(WmBitmap source, String watermark) =>
      _algo.embed(source, watermark);

  static String unDctString(WmBitmap destination) =>
      _algo.extract(destination);
}

/// 默认可用算法列表（按此顺序在 UI 下拉展示）。
/// 仅保留视觉无损的保色算法（用户决策 2026-09-30）：
/// 旧 DCT（兼容原 Android）与灰度 QIM（丢色）已从默认列表移除。
/// 后续新增算法时，在这里追加实现，并保持 [WmAlgorithm] 契约即可。
List<WmAlgorithm> defaultAlgorithms() => [
      DctQimColorAlgorithm(), // QIM-DCT 保色版（视觉无损，容量大，推荐）
      DwtAlgorithm(), // DWT 域（视觉无损，抗噪更强）
      SpreadSpectrumAlgorithm(), // 扩频（视觉无损，鲁棒性最强）
    ];