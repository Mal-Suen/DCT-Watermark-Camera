// dwt_algorithm.dart — DWT（小波）域水印算法，视觉无损。
//
// 机制：对 YCoCg 的 Y（亮度）通道做 8×8 块完整 2D Haar 小波变换，
// 在中频子带（HL/LH）系数做 QIM 奇偶量化嵌入；Co/Cg（色度）保持不变。
// Haar 变换可逆，YCoCg 可逆，往返无损 → 视觉无损（PSNR 高）。
//
// 与 DCT 域相比：小波域多分辨率，抗压缩/噪声能力更强。
//
// 释放兼容：不保证与旧版互认（重构决策）。本文件纯 Dart。
import 'reedsolomon.dart';
import 'watermark.dart';
import 'wm_algorithm.dart';
import 'color_space.dart';

/// DWT 域水印算法（视觉无损）。
class DwtAlgorithm implements WmAlgorithm {
  /// 中频子带系数量化步长（嵌入强度）。越小越无损但鲁棒性弱。
  final double q;

  /// RS 纠错字节数。
  final int eccBytes;

  DwtAlgorithm({this.q = 8.0, this.eccBytes = 6});

  @override
  String get name => 'DWT 域';

  @override
  String get id => 'dwt';

  @override
  int maxTextLength(WmBitmap image) {
    final blocksW = image.width ~/ 8;
    final blocksH = image.height ~/ 8;
    final capacityBits = blocksW * blocksH;
    // 预留：长度前缀 3 份（头部保护）+ RS 校验 eccBytes 字节
    return ((capacityBits - _headerBits - eccBytes * 8) ~/ 8)
        .clamp(0, 255)
        .toInt();
  }

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

    final bits = _encodeToBits(text, eccBytes, capacityBits);

    final pixels = List<int>.from(source.pixelData);
    final y = List.generate(8, (_) => List<int>.filled(8, 0));
    final coeff = List.generate(8, (_) => List<int>.filled(8, 0));

    int bi = 0;
    for (int by = 0; by < blocksH; by++) {
      for (int bx = 0; bx < blocksW; bx++) {
        // S2 修复（2026-10-08 代码审查）：无水印 bit 的块直接跳过——
        // Haar 往返含整除舍入噪声，白白拉低 PSNR；提取端只读码字跨度内的 bit。
        if (bi >= bits.length) {
          bi++;
          continue;
        }
        final py = by * 8;
        final px = bx * 8;
        // 读入 Y 通道
        for (int i = 0; i < 8; i++) {
          for (int j = 0; j < 8; j++) {
            final argb = pixels[(py + i) * source.width + (px + j)];
            y[i][j] = brightnessOf(argb);
          }
        }
        // 2D Haar 前向变换
        _haar2D(y, coeff);
        {
          // 在 HL 子带（位置 (0,4)，即水平高频/垂直低频）做 QIM
          final target = coeff[0][4].toDouble();
          final k0 = (target / q).round();
          int k;
          if (bits[bi] == 0) {
            k = (k0.isEven) ? k0 : (k0 - 1);
          } else {
            k = (k0.isOdd) ? k0 : (k0 + 1);
          }
          coeff[0][4] = (k * q).round();
        }
        bi++;
        // 2D Haar 逆变换重建 Y
        _inverseHaar2D(coeff, y);
        // 回写：只改 Y，保留 Co/Cg
        for (int i = 0; i < 8; i++) {
          for (int j = 0; j < 8; j++) {
            final newY = y[i][j].clamp(0, 255);
            final old = pixels[(py + i) * source.width + (px + j)];
            final c = rgbToYCoCg(old);
            pixels[(py + i) * source.width + (px + j)] =
                yCoCgToRgb(YCoCg(newY, c.co, c.cg));
          }
        }
      }
    }
    return WmBitmap(source.width, source.height, pixels);
  }

  @override
  String extract(WmBitmap source) {
    final blocksW = source.width ~/ 8;
    final blocksH = source.height ~/ 8;
    final y = List.generate(8, (_) => List<int>.filled(8, 0));
    final coeff = List.generate(8, (_) => List<int>.filled(8, 0));
    final bits = <int>[];

    for (int by = 0; by < blocksH; by++) {
      for (int bx = 0; bx < blocksW; bx++) {
        final py = by * 8;
        final px = bx * 8;
        for (int i = 0; i < 8; i++) {
          for (int j = 0; j < 8; j++) {
            final argb = source.getPixel(px + j, py + i);
            y[i][j] = brightnessOf(argb);
          }
        }
        _haar2D(y, coeff);
        final val = coeff[0][4].toDouble();
        final k = (val / q).round();
        bits.add(k.isEven ? 0 : 1);
      }
    }
    return _decodeFromBits(bits, eccBytes);
  }

  // ============ 完整 2D Haar 变换（8×8，可逆）============
  // 一级分解：LL(0..3,0..3) LH(4..7,0..3) HL(0..3,4..7) HH(4..7,4..7)
  void _haar2D(List<List<int>> input, List<List<int>> out) {
    // 行变换
    final tmp = List.generate(8, (_) => List<int>.filled(8, 0));
    for (int i = 0; i < 8; i++) {
      for (int j = 0; j < 4; j++) {
        final a = input[i][2 * j];
        final b = input[i][2 * j + 1];
        tmp[i][j] = (a + b) ~/ 2; // 低频
        tmp[i][j + 4] = (a - b) ~/ 2; // 高频
      }
    }
    // 列变换
    for (int j = 0; j < 8; j++) {
      for (int i = 0; i < 4; i++) {
        final a = tmp[2 * i][j];
        final b = tmp[2 * i + 1][j];
        out[i][j] = (a + b) ~/ 2; // 低频
        out[i + 4][j] = (a - b) ~/ 2; // 高频
      }
    }
  }

  void _inverseHaar2D(List<List<int>> input, List<List<int>> out) {
    // 列逆变换
    final tmp = List.generate(8, (_) => List<int>.filled(8, 0));
    for (int j = 0; j < 8; j++) {
      for (int i = 0; i < 4; i++) {
        final lo = input[i][j];
        final hi = input[i + 4][j];
        tmp[2 * i][j] = lo + hi;
        tmp[2 * i + 1][j] = lo - hi;
      }
    }
    // 行逆变换
    for (int i = 0; i < 8; i++) {
      for (int j = 0; j < 4; j++) {
        final lo = tmp[i][j];
        final hi = tmp[i][j + 4];
        out[i][2 * j] = lo + hi;
        out[i][2 * j + 1] = lo - hi;
      }
    }
  }

  // ============ RS 编码/解码 ============
  /// 头部保护：长度前缀重复 3 份（解码端按位多数表决），抗单份损坏。
  static const int _headerCopies = 3;
  static const int _headerBits = 8 * _headerCopies;

  /// 文本 -> RS 编码 -> bit 序列（MSB 先）：[长度前缀×3][RS 码字]，裁剪到容量。
  List<int> _encodeToBits(String text, int eccBytes, int capacityBits) {
    final utf8 = _asciiBytes(text);
    final dataBytes = utf8.length + 1;
    final totalBytes = dataBytes + eccBytes;
    assert(totalBytes <= 255, 'RS block too large');

    final toEncode = List<int>.filled(totalBytes, 0);
    toEncode[0] = utf8.length;
    for (int i = 0; i < utf8.length; i++) {
      toEncode[1 + i] = utf8[i];
    }
    ReedSolomonEncoder(GenericGF.qrCodeField256).encode(toEncode, eccBytes);

    final bits = <int>[];
    for (int copy = 0; copy < _headerCopies; copy++) {
      for (int bit = 7; bit >= 0; bit--) {
        bits.add((toEncode[0] >> bit) & 1);
      }
    }
    for (final b in toEncode) {
      for (int bit = 7; bit >= 0; bit--) {
        bits.add((b >> bit) & 1);
      }
    }
    return bits.take(capacityBits).toList();
  }

  String _decodeFromBits(List<int> bits, int eccBytes) {
    // 修复（2026-10-08 代码审查 C5/C7）：先读长度前缀，按精确码字长度 RS 解码，
    // 失败返回空串（fail-closed）。原实现整组解码被尾部随机 bit 破坏，纠错从未生效。
    // 头部保护：三份长度前缀按位多数表决，消除长度字节单点故障。
    if (bits.length < _headerBits) return '';
    int len = 0;
    for (int bit = 0; bit < 8; bit++) {
      int votes = 0;
      for (int copy = 0; copy < _headerCopies; copy++) {
        if (bits[copy * 8 + bit] == 1) votes++;
      }
      if (votes * 2 > _headerCopies) len |= (0x80 >> bit);
    }
    final totalBytes = 1 + len + eccBytes;
    if (len <= 0 ||
        totalBytes > 255 ||
        _headerBits + totalBytes * 8 > bits.length) {
      return '';
    }
    final bytes = <int>[];
    for (int i = 0; i < totalBytes; i++) {
      int byte = 0;
      for (int bit = 0; bit < 8; bit++) {
        if (bits[_headerBits + i * 8 + bit] == 1) {
          byte |= (0x80 >> bit);
        }
      }
      bytes.add(byte);
    }
    try {
      ReedSolomonDecoder(GenericGF.qrCodeField256).decode(bytes, eccBytes);
    } catch (_) {
      return '';
    }
    return _fromAscii(bytes, 1, len);
  }

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
}
