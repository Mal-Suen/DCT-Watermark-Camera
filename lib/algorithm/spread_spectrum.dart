// spread_spectrum.dart — 扩频水印算法，视觉无损。
//
// 机制：对 YCoCg 的 Y（亮度）通道做 8×8 块 DCT，把水印 bit 用伪随机扩频序列
// （PN）扩频后叠加到中频系数；Co/Cg（色度）保持不变。
// 提取用相关检测（PN 序列与系数内积符号）还原 bit。
//
// 优势：扩频信号能量分散，抗裁剪/缩放/JPEG 等攻击能力最强。
// 代价：每个 bit 需多个系数承载（扩频因子），容量较小。
//
// 释放兼容：不保证与旧版互认（重构决策）。本文件纯 Dart。
import 'dart:math' as m;

import 'reedsolomon.dart';
import 'watermark.dart';
import 'wm_algorithm.dart';
import 'color_space.dart';
import 'dct_math.dart';

/// 扩频水印算法（视觉无损，鲁棒性最强）。
///
/// 性能优化（2026-09-30）：
/// - 嵌入端消除重复 forwardDCT：原实现"收集系数"和"回写"各做一次完整 DCT，
///   现改为第一次 forwardDCT 保存完整系数矩阵，回写时直接改 (2,3) 再逆变换。
/// - 提取端只算中频系数 (2,3)，用预计算权重矩阵 `W[i][j]=C[2][i]*C[3][j]`，
///   从完整 DCT（1024 次乘加/块）降到 64 次乘加/块，提速约 16 倍。
/// - 复用缓冲区，避免每块重复分配。
class SpreadSpectrumAlgorithm implements WmAlgorithm {
  /// 扩频因子：每个 bit 用多少个系数承载。越大越鲁棒但容量越小。
  final int spreadFactor;

  /// 嵌入强度（alpha）。越大越鲁棒但可见性越高。
  final double alpha;

  /// PN 序列种子（固定，保证嵌入/提取一致）。
  final int seed;

  /// RS 纠错字节数。
  final int eccBytes;

  /// 中频系数位置（行,列）——(2,3) 能量稳定、视觉影响小。
  static const int _coefRow = 2;
  static const int _coefCol = 3;

  /// 预计算的中频系数权重矩阵：`W[i][j] = C[2][i] * C[3][j]`（提取只算 (2,3) 用）。
  late final List<List<double>> _w23;

  /// 复用的 DCT 实例（含预计算 C/Ct 矩阵）。
  final DCT _dct = DCT();

  /// 复用的块缓冲区（避免每块重复分配）。
  final List<List<int>> _input8 = _makeBuffer();
  final List<List<int>> _coef = _makeBuffer();
  final List<List<int>> _back = _makeBuffer();

  static List<List<int>> _makeBuffer() =>
      List.generate(8, (_) => List<int>.filled(8, 0));

  SpreadSpectrumAlgorithm({
    this.spreadFactor = 8,
    this.alpha = 8.0,
    this.seed = 42,
    this.eccBytes = 6,
  }) {
    // 预计算 (2,3) 权重矩阵：coef[2][3] = Σ_i Σ_j (input-128) * C[2][i] * C[3][j]
    _w23 = List.generate(8, (i) {
      return List.generate(8, (j) {
        return _dct.C[2][i] * _dct.C[3][j];
      });
    });
  }

  @override
  String get name => '扩频';

  @override
  String get id => 'spread_spectrum';

  @override
  int maxTextLength(WmBitmap image) {
    final blocksW = image.width ~/ 8;
    final blocksH = image.height ~/ 8;
    final capacityBits = (blocksW * blocksH) ~/ spreadFactor;
    // 预留：长度前缀 3 份（头部保护）+ RS 校验 eccBytes 字节
    return ((capacityBits - _headerBits - eccBytes * 8) ~/ 8)
        .clamp(0, 255)
        .toInt();
  }

  @override
  WmBitmap embed(WmBitmap source, String watermark) {
    final blocksW = source.width ~/ 8;
    final blocksH = source.height ~/ 8;
    final capacityBits = (blocksW * blocksH) ~/ spreadFactor;
    final maxStr = maxTextLength(source);
    var text = watermark;
    if (text.length > maxStr) {
      text = text.substring(0, maxStr);
    }

    final bits = _encodeToBits(text, eccBytes, capacityBits);
    final pn = _pnSequence(seed, capacityBits * spreadFactor);

    final pixels = List<int>.from(source.pixelData);
    final width = source.width;
    final input8 = _input8;
    final coef = _coef;
    final back = _back;
    final dct = _dct;

    // S2 修复（2026-10-08 代码审查）：只有承载扩频信号的块（bits.length×sf 个）
    // 需要 DCT 处理；尾部块跳过往返（提取端相关检测只读前段 bit，跳过无副作用）。
    // bits.length ≤ capacityBits = 总块数/sf，故 signalBlocks ≤ 总块数。
    final signalBlocks = bits.length * spreadFactor;

    // 第一次 forwardDCT：收集承载块的系数 + 保存完整系数矩阵（消除第二次 forwardDCT）
    final midFreq = <int>[];
    final coefs = <List<List<int>>>[];
    for (int block = 0; block < signalBlocks; block++) {
      final py = (block ~/ blocksW) * 8;
      final px = (block % blocksW) * 8;
      for (int i = 0; i < 8; i++) {
        final row = (py + i) * width + px;
        final rowIn = input8[i];
        for (int j = 0; j < 8; j++) {
          rowIn[j] = brightnessOf(pixels[row + j]);
        }
      }
      dct.forwardDCT(input8, coef);
      midFreq.add(coef[_coefRow][_coefCol]);
      // 深拷贝系数矩阵（保存，供回写用）
      coefs.add(List.generate(8, (i) => List<int>.from(coef[i])));
    }

    // 扩频嵌入：每个 bit 用 spreadFactor 个系数
    for (int b = 0; b < bits.length; b++) {
      final bit = bits[b] == 1 ? 1 : -1;
      for (int k = 0; k < spreadFactor; k++) {
        final idx = b * spreadFactor + k;
        if (idx < midFreq.length) {
          final pnVal = pn[idx];
          midFreq[idx] = (midFreq[idx] + alpha * pnVal * bit).round();
        }
      }
    }

    // 回写：从保存的系数矩阵改 (2,3)，再逆变换。只改 Y，保留 Co/Cg。
    for (int mi = 0; mi < signalBlocks; mi++) {
      final py = (mi ~/ blocksW) * 8;
      final px = (mi % blocksW) * 8;
      final saved = coefs[mi];
      if (mi < midFreq.length) {
        saved[_coefRow][_coefCol] = midFreq[mi];
      }
      dct.inverseDCT(saved, back);
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
    return WmBitmap(source.width, source.height, pixels);
  }

  @override
  String extract(WmBitmap source) {
    final blocksW = source.width ~/ 8;
    final blocksH = source.height ~/ 8;
    final capacityBits = (blocksW * blocksH) ~/ spreadFactor;
    final pn = _pnSequence(seed, capacityBits * spreadFactor);
    final w23 = _w23;
    final width = source.width;
    final pixels = source.pixelData;

    // 只算每个块的中频系数 (2,3)：coef[2][3] = Σ_i Σ_j (input-128) * W[i][j]
    final midFreq = <int>[];
    for (int by = 0; by < blocksH; by++) {
      for (int bx = 0; bx < blocksW; bx++) {
        final py = by * 8;
        final px = bx * 8;
        double acc = 0;
        for (int i = 0; i < 8; i++) {
          final row = (py + i) * width + px;
          final wRow = w23[i];
          for (int j = 0; j < 8; j++) {
            final y = brightnessOf(pixels[row + j]);
            acc += (y - 128) * wRow[j];
          }
        }
        midFreq.add(acc.round());
      }
    }

    // 相关检测：每个 bit 的 PN 序列与系数内积符号。
    // 先对每个扩频块做中心化（减去均值），消除图像本身 DC 偏置对相关的干扰。
    final bits = <int>[];
    for (int b = 0; b < capacityBits; b++) {
      // 收集该 bit 的扩频系数
      final block = <int>[];
      for (int k = 0; k < spreadFactor; k++) {
        final idx = b * spreadFactor + k;
        if (idx < midFreq.length) {
          block.add(midFreq[idx]);
        }
      }
      if (block.isEmpty) {
        bits.add(0);
        continue;
      }
      final mean = block.reduce((a, c) => a + c) / block.length;
      double corr = 0;
      for (int k = 0; k < block.length; k++) {
        final idx = b * spreadFactor + k;
        corr += (block[k] - mean) * pn[idx];
      }
      bits.add(corr >= 0 ? 1 : 0);
    }
    return _decodeFromBits(bits, eccBytes);
  }

  // ============ 工具 ============
  /// 生成确定性 PN 序列（±1），种子固定保证嵌入/提取一致。
  List<int> _pnSequence(int seed, int length) {
    final rng = m.Random(seed);
    return List<int>.generate(length, (_) => rng.nextBool() ? 1 : -1);
  }

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

    // 头部保护：长度前缀重复 3 份（解码端按位多数表决），抗单份损坏
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

  /// 头部保护常量：长度前缀 3 份副本。
  static const int _headerCopies = 3;
  static const int _headerBits = 8 * _headerCopies;

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
