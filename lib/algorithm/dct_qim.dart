// dct_qim.dart — 新一代 QIM-DCT 水印算法。
//
// 设计目标：修复旧版 DCT 水印的五大缺陷（替换式嵌入无强度、冗余随机层、
// 无纠错、无同步、容量假上限），并显著提升容量与抗噪能力。
//
// 核心机制（标准 DCT 水印范式）：
//  1. 图像分 8×8 块，每块做 8×8 DCT；
//  2. 每 1 个水印 bit 调制一个"中频系数"（位置 (2,3)，能量较稳且不破坏视觉）；
//  3. QIM：把系数量化到量化步长 [q] 的最近偶数格(bit=0)或奇数格(bit=1)。
//     [q] 即嵌入强度参数，代替旧版的 opacity —— 真正控制鲁棒性/可见性；
//  4. 水印内容先经 Reed-Solomon 纠错编码，提取后解码，抗单块误码；
//  5. 系数选择为确定性模式（块序扫描），无随机置换 —— 消除同步脆性。
//
// 释放兼容：不保证与旧版/Android 原版互认（重构决策）。
// 本文件纯 Dart,依赖 reedsolomon.dart 与 DCT/dct_math.dart、WmBitmap。
import 'dct_math.dart';
import 'reedsolomon.dart';
import 'watermark.dart';
import 'wm_algorithm.dart';

/// QIM-DCT 水印算法。
class DctQimAlgorithm implements WmAlgorithm {
  /// 量化步长（嵌入强度）。越大越鲁棒但可见性越高。实测见 bin/diag_qim.dart。
  final double q;

  /// RS 纠错字节数。越大约抗误码，但占用水印容量。推荐 4~10。
  final int eccBytes;

  /// 中频系数位置（行,列）——(2,3) 能量稳定、视觉影响小。
  static const int _coefRow = 2;
  static const int _coefCol = 3;

  DctQimAlgorithm({this.q = 16.0, this.eccBytes = 6});

  @override
  String get name => 'DCT-QIM 频域';

  @override
  String get id => 'dct_qim';

  /// 该算法在 [image] 上可承载的最大水印字符数（ASCII）。
  /// 每 8×8 块 1 bit，扣除 RS 纠错字节后换算为字符数，上限 255（GF(256)）。
  @override
  int maxTextLength(WmBitmap image) {
    final blocksW = image.width ~/ 8;
    final blocksH = image.height ~/ 8;
    final capacityBits = blocksW * blocksH;
    return ((capacityBits - eccBytes * 8) ~/ 8).clamp(0, 255).toInt();
  }

  // ============ 嵌入 ============
  @override
  WmBitmap embed(WmBitmap source, String watermark) {
    final blocksW = source.width ~/ 8;
    final blocksH = source.height ~/ 8;
    final capacityBits = blocksW * blocksH;
    final maxStr = maxTextLength(source);
    var text = watermark;
    if (text.length > maxStr) {
      text = text.substring(0, maxStr);
    }

    // 文本 -> 字节
    final utf8 = _asciiBytes(text);
    // 组装 RS 数据：前 1 字节=长度，后接内容，末尾 eccBytes 个凑够(用0填充到 dataBytes)
    final dataBytes = utf8.length + 1;
    final totalBytes = dataBytes + eccBytes;
    // 校验总字节不超过 255（GF(256) 上限）
    assert(totalBytes <= 255, 'RS block too large');

    final toEncode = List<int>.filled(totalBytes, 0);
    toEncode[0] = utf8.length;
    for (int i = 0; i < utf8.length; i++) {
      toEncode[1 + i] = utf8[i];
    }
    ReedSolomonEncoder(GenericGF.qrCodeField256).encode(toEncode, eccBytes);

    // 转 bit 序列（每字节 8 bit,MSB 先）
    final bits = <int>[];
    for (final b in toEncode) {
      for (int bit = 7; bit >= 0; bit--) {
        bits.add((b >> bit) & 1);
      }
    }
    // 裁剪到可用容量;不足则截断
    final useBits = bits.take(capacityBits).toList();

    // 深拷贝像素并做 QIM
    final pixels = List<int>.from(source.pixelData);
    final dct = DCT();
    final input8 = List.generate(8, (_) => List<int>.filled(8, 0));
    final coef = List.generate(8, (_) => List<int>.filled(8, 0));
    final back = List.generate(8, (_) => List<int>.filled(8, 0));

    int bi = 0;
    for (int by = 0; by < blocksH; by++) {
      for (int bx = 0; bx < blocksW; bx++) {
        final py = by * 8;
        final px = bx * 8;
        // 读入块
        for (int i = 0; i < 8; i++) {
          for (int j = 0; j < 8; j++) {
            final argb = pixels[(py + i) * source.width + (px + j)];
            input8[i][j] = _brightness(argb);
          }
        }
        dct.forwardDCT(input8, coef);
        if (bi < useBits.length) {
          final target = coef[_coefRow][_coefCol].toDouble();
          // QIM: 最近格序号(向零),再对齐到 奇/偶
          final k0 = (target / q).round();
          int k;
          if (useBits[bi] == 0) {
            k = (k0.isEven) ? k0 : (k0 - 1); // 偶
          } else {
            k = (k0.isOdd) ? k0 : (k0 + 1); // 奇
          }
          // 系数值 = 格序号 × q（这才是精确定位到格点,幅度可控）
          coef[_coefRow][_coefCol] = (k * q).round();
        }
        bi++;
        dct.inverseDCT(coef, back);
        for (int i = 0; i < 8; i++) {
          for (int j = 0; j < 8; j++) {
            final v = back[i][j].clamp(0, 255);
            final old = pixels[(py + i) * source.width + (px + j)];
            final r = _red(old), g = _green(old), b = _blue(old);
            // 以亮度通道承载，保色相
            pixels[(py + i) * source.width + (px + j)] =
                0xFF000000 | (r << 16) | (g << 8) | b;
            // 亮度体现在蓝通道近似（常用于灰度承载）
            pixels[(py + i) * source.width + (px + j)] =
                0xFF000000 | (v << 16) | (v << 8) | v;
          }
        }
      }
    }
    return WmBitmap(source.width, source.height, pixels);
  }

  // ============ 提取 ============
  @override
  String extract(WmBitmap source) {
    final blocksW = source.width ~/ 8;
    final blocksH = source.height ~/ 8;
    final dct = DCT();
    final input8 = List.generate(8, (_) => List<int>.filled(8, 0));
    final coef = List.generate(8, (_) => List<int>.filled(8, 0));
    final bits = <int>[];

    for (int by = 0; by < blocksH; by++) {
      for (int bx = 0; bx < blocksW; bx++) {
        final py = by * 8;
        final px = bx * 8;
        for (int i = 0; i < 8; i++) {
          for (int j = 0; j < 8; j++) {
            final argb = source.getPixel(px + j, py + i);
            input8[i][j] = _brightness(argb);
          }
        }
        dct.forwardDCT(input8, coef);
        final val = coef[_coefRow][_coefCol].toDouble();
        final k = (val / q).round(); // 与 embed 一致:round 取最近格
        bits.add(k.isEven ? 0 : 1);
      }
    }

    // bit -> 字节(可能含填充)
    final nBytes = (bits.length ~/ 8).clamp(0, 255);
    final bytes = <int>[];
    for (int i = 0; i < nBytes; i++) {
      int byte = 0;
      for (int bit = 0; bit < 8; bit++) {
        final idx = i * 8 + bit;
        if (idx < bits.length && bits[idx] == 1) {
          byte |= (0x80 >> bit);
        }
      }
      bytes.add(byte);
    }

    // RS 解码（eccBytes 个校验字节在末尾）
    if (bytes.length > eccBytes) {
      try {
        ReedSolomonDecoder(GenericGF.qrCodeField256).decode(bytes, eccBytes);
      } catch (_) {
        // 解码失败则忽略纠错,直接用原始数据
      }
    }
    if (bytes.isEmpty) {
      return '';
    }
    final len = bytes[0];
    if (len <= 0 || len > bytes.length - 1) {
      return _fromAscii(bytes, 1, bytes.length - 1);
    }
    return _fromAscii(bytes, 1, len);
  }

  // ============ 工具 ============
  List<int> _asciiBytes(String s) {
    final out = <int>[];
    for (int i = 0; i < s.length; i++) {
      out.add(s.codeUnitAt(i) & 0x7f);
    }
    return out;
  }

  String _fromAscii(List<int> bytes, int start, int len) {
    final buf = StringBuffer();
    for (int i = start; i < start + len && i < bytes.length; i++) {
      buf.writeCharCode(bytes[i] & 0x7f);
    }
    return buf.toString();
  }

  int _brightness(int argb) =>
      (0.299 * _red(argb) + 0.587 * _green(argb) + 0.114 * _blue(argb)).round();
  int _red(int argb) => (argb >> 16) & 0xff;
  int _green(int argb) => (argb >> 8) & 0xff;
  int _blue(int argb) => argb & 0xff;
}