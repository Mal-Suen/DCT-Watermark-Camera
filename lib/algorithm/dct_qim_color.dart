// dct_qim_color.dart — QIM-DCT 保色版水印算法。
//
// 与 DctQimAlgorithm 的区别：只在 YCoCg 的 Y（亮度）通道做 8×8 DCT + QIM 调制，
// Co/Cg（色度）保持不变。YCoCg 是可逆整数变换，往返无损，
// 从而在保留全部色彩的前提下实现视觉无损嵌入（PSNR 高）。
// 提取时也从 Y 通道读系数奇偶。
//
// 视觉无损依据：亮度通道的 QIM 量化步长 q 足够小（默认 8）时，逆变换回 RGB 后
// 与原图差异极小（PSNR 高），人眼不可感知；色度完全未改动。
//
// 释放兼容：不保证与旧版互认（重构决策）。本文件纯 Dart。
import 'dct_math.dart';
import 'reedsolomon.dart';
import 'watermark.dart';
import 'wm_algorithm.dart';
import 'color_space.dart';

/// QIM-DCT 保色版水印算法（推荐，视觉无损）。
///
/// 性能优化（2026-09-30）：
/// - 提取端只算中频系数 (2,3)，用预计算权重矩阵 `W[i][j]=C[2][i]*C[3][j]`，
///   从完整 DCT（1024 次乘加/块）降到 64 次乘加/块，提速约 16 倍。
/// - 嵌入端复用缓冲区（input8/coef/back），避免每块重复分配，减少 GC 停顿。
class DctQimColorAlgorithm implements WmAlgorithm {
  /// 亮度通道量化步长（嵌入强度）。越小越无损但鲁棒性弱；实测 q=8 视觉无损。
  final double q;

  /// RS 纠错字节数。越大约抗误码，但占用水印容量。推荐 4~10。
  final int eccBytes;

  /// 中频系数位置（行,列）——(2,3) 能量稳定、视觉影响小。
  static const int _coefRow = 2;
  static const int _coefCol = 3;

  /// 预计算的中频系数权重矩阵：`W[i][j] = C[2][i] * C[3][j]`（提取端只算 (2,3) 用）。
  late final List<List<double>> _w23;

  /// 复用的浮点 DCT 实例（含预计算 C/Ct 矩阵）。
  final DCT _dct = DCT();

  /// 复用的块缓冲区（避免每块重复分配）。
  final List<List<int>> _input8 = _makeBuffer();
  final List<List<int>> _coef = _makeBuffer();
  final List<List<int>> _back = _makeBuffer();

  static List<List<int>> _makeBuffer() =>
      List.generate(8, (_) => List<int>.filled(8, 0));

  DctQimColorAlgorithm({this.q = 8.0, this.eccBytes = 6}) {
    // 预计算 (2,3) 权重矩阵：coef[2][3] = Σ_i Σ_j (input-128) * C[2][i] * C[3][j]
    _w23 = List.generate(8, (i) {
      return List.generate(8, (j) {
        return _dct.C[2][i] * _dct.C[3][j];
      });
    });
  }

  @override
  String get name => 'QIM-DCT 保色';

  @override
  String get id => 'dct_qim_color';

  @override
  int maxTextLength(WmBitmap image) {
    final blocksW = image.width ~/ 8;
    final blocksH = image.height ~/ 8;
    final capacityBits = blocksW * blocksH;
    // 预留长度前缀 1 字节 + RS 校验 eccBytes 字节（修复差一错误：满容量不再截断校验位）
    return ((capacityBits - 8 - eccBytes * 8) ~/ 8).clamp(0, 255).toInt();
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

    // 文本 -> RS 编码 -> bit 序列
    final bits = _encodeToBits(text, eccBytes, capacityBits);

    // 深拷贝像素
    final pixels = List<int>.from(source.pixelData);
    final input8 = _input8;
    final coef = _coef;
    final back = _back;
    final width = source.width;

    int bi = 0;
    for (int by = 0; by < blocksH; by++) {
      for (int bx = 0; bx < blocksW; bx++) {
        final py = by * 8;
        final px = bx * 8;
        // 读入块：取每个像素的 Y 亮度
        for (int i = 0; i < 8; i++) {
          final row = (py + i) * width + px;
          final rowIn = input8[i];
          for (int j = 0; j < 8; j++) {
            rowIn[j] = brightnessOf(pixels[row + j]);
          }
        }
        _dct.forwardDCT(input8, coef);
        if (bi < bits.length) {
          final target = coef[_coefRow][_coefCol].toDouble();
          // QIM: 最近格序号,再对齐到 奇/偶
          final k0 = (target / q).round();
          int k;
          if (bits[bi] == 0) {
            k = (k0.isEven) ? k0 : (k0 - 1); // 偶
          } else {
            k = (k0.isOdd) ? k0 : (k0 + 1); // 奇
          }
          coef[_coefRow][_coefCol] = (k * q).round();
        }
        bi++;
        _dct.inverseDCT(coef, back);
        // 回写：只改 Y（亮度），保留 Co/Cg（色度）
        for (int i = 0; i < 8; i++) {
          final row = (py + i) * width + px;
          final backRow = back[i];
          for (int j = 0; j < 8; j++) {
            final newY = backRow[j].clamp(0, 255);
            final old = pixels[row + j];
            final c = rgbToYCoCg(old);
            pixels[row + j] = yCoCgToRgb(YCoCg(newY, c.co, c.cg));
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
    final bits = <int>[];
    final width = source.width;
    final w23 = _w23;
    final pixels = source.pixelData;

    for (int by = 0; by < blocksH; by++) {
      for (int bx = 0; bx < blocksW; bx++) {
        final py = by * 8;
        final px = bx * 8;
        // 只算中频系数 (2,3)：coef[2][3] = Σ_i Σ_j (input[i][j]-128) * W[i][j]
        double acc = 0;
        for (int i = 0; i < 8; i++) {
          final row = (py + i) * width + px;
          final wRow = w23[i];
          for (int j = 0; j < 8; j++) {
            final y = brightnessOf(pixels[row + j]);
            acc += (y - 128) * wRow[j];
          }
        }
        final k = (acc / q).round();
        bits.add(k.isEven ? 0 : 1);
      }
    }

    return _decodeFromBits(bits, eccBytes);
  }

  // ============ 工具 ============
  /// 文本 -> RS 编码 -> bit 序列（MSB 先），裁剪到容量。
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
    for (final b in toEncode) {
      for (int bit = 7; bit >= 0; bit--) {
        bits.add((b >> bit) & 1);
      }
    }
    return bits.take(capacityBits).toList();
  }

  /// bit 序列 -> 字节 -> RS 解码 -> 文本。
  ///
  /// 修复（2026-10-08 代码审查 C5/C7）：原实现把全部块的 bit 转字节后整组 RS
  /// 解码，而码字只占前段——真实照片的尾部随机 bit 使 RS 必然失败（纠错从未
  /// 生效，靠长度前缀兜底返回乱码）。现改为：先读长度前缀，按精确码字长度
  /// （1+len+ecc 字节）RS 解码；失败即判定无水印（返回空串，fail-closed）。
  String _decodeFromBits(List<int> bits, int eccBytes) {
    if (bits.length < 8) return '';
    // 长度前缀（第一个字节）
    int len = 0;
    for (int bit = 0; bit < 8; bit++) {
      if (bits[bit] == 1) len |= (0x80 >> bit);
    }
    // 码字总长 = 1(长度前缀) + len(数据) + eccBytes(校验)，受 RS 块上限 255 约束
    final totalBytes = 1 + len + eccBytes;
    if (len <= 0 || totalBytes > 255 || totalBytes * 8 > bits.length) {
      return ''; // 长度非法或码字超出可用 bit → 无有效水印
    }
    // 按精确码字长度取字节
    final bytes = <int>[];
    for (int i = 0; i < totalBytes; i++) {
      int byte = 0;
      for (int bit = 0; bit < 8; bit++) {
        if (bits[i * 8 + bit] == 1) {
          byte |= (0x80 >> bit);
        }
      }
      bytes.add(byte);
    }
    // RS 解码：失败 = 无水印（fail-closed，同时是"是否含水印"的判据）
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
