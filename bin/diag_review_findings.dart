// diag_review_findings.dart — 代码审查疑点实证脚本。
// 验证三个算法层发现：
//  A) RS 纠错在集成算法中是否为死代码（全块 bits vs 码字长度不匹配）
//  B) 无水印 bit 的块是否也被 DCT 往返修改（PSNR 无谓损失）
//  C) maxTextLength 是否少预留长度前缀字节（满容量截断校验位）
//  D) 未嵌水印图提取是否产生乱码（误报）
import 'dart:math' as m;
import 'package:dct_watermark_app/algorithm/algorithm.dart';
import '../lib/algorithm/dct_math.dart';

WmBitmap colorImage(int w, int h, {double noise = 0}) {
  final rng = m.Random(7);
  final px = List<int>.filled(w * h, 0);
  for (int y = 0; y < h; y++) {
    for (int x = 0; x < w; x++) {
      final r = (120 + 80 * m.sin(x / 30 + y / 20)).clamp(0, 255).round();
      final g = (100 + 70 * m.cos(x / 25 - y / 15)).clamp(0, 255).round();
      final b = (140 + 60 * m.sin(x / 20 + y / 25)).clamp(0, 255).round();
      final nr = (r + (rng.nextDouble() - 0.5) * 2 * noise).clamp(0, 255).round();
      final ng = (g + (rng.nextDouble() - 0.5) * 2 * noise).clamp(0, 255).round();
      final nb = (b + (rng.nextDouble() - 0.5) * 2 * noise).clamp(0, 255).round();
      px[y * w + x] = 0xFF000000 | (nr << 16) | (ng << 8) | nb;
    }
  }
  return WmBitmap(w, h, px);
}

double psnr(WmBitmap a, WmBitmap b) {
  double mse = 0;
  final n = a.width * a.height;
  for (int i = 0; i < n; i++) {
    final pa = a.pixelAt(i), pb = b.pixelAt(i);
    final dr = ((pa >> 16) & 0xFF) - ((pb >> 16) & 0xFF);
    final dg = ((pa >> 8) & 0xFF) - ((pb >> 8) & 0xFF);
    final db = (pa & 0xFF) - (pb & 0xFF);
    mse += dr * dr + dg * dg + db * db;
  }
  mse /= (n * 3);
  if (mse == 0) return double.infinity;
  return 10 * m.log(255 * 255 / mse) / m.ln10;
}

// 复刻 DctQimColorAlgorithm.extract 的逐块奇偶读取（提取端只算 (2,3)）
List<int> extractBits(WmBitmap img, DctQimColorAlgorithm algo) {
  final dct = DCT();
  final w23 = List.generate(8, (i) => List.generate(8, (j) => dct.C[2][i] * dct.C[3][j]));
  final blocksW = img.width ~/ 8, blocksH = img.height ~/ 8;
  final bits = <int>[];
  for (int by = 0; by < blocksH; by++) {
    for (int bx = 0; bx < blocksW; bx++) {
      double acc = 0;
      for (int i = 0; i < 8; i++) {
        for (int j = 0; j < 8; j++) {
          acc += (brightnessOf(img.getPixel(bx * 8 + j, by * 8 + i)) - 128) * w23[i][j];
        }
      }
      bits.add((acc / 8.0).round().isEven ? 0 : 1);
    }
  }
  return bits;
}

List<int> bitsToBytes(List<int> bits, int nBytes) {
  final bytes = <int>[];
  for (int i = 0; i < nBytes; i++) {
    int byte = 0;
    for (int bit = 0; bit < 8; bit++) {
      final idx = i * 8 + bit;
      if (idx < bits.length && bits[idx] == 1) byte |= (0x80 >> bit);
    }
    bytes.add(byte);
  }
  return bytes;
}

void main() {
  final algo = DctQimColorAlgorithm(q: 8, eccBytes: 6);
  final img = colorImage(320, 240); // 1200 块
  const text = 'AB'; // 短文本：dataBytes=3, totalBytes=9, 码字 72 bit

  print('=== A) RS 纠错是否为死代码 ===');
  final embedded = algo.embed(img, text);
  print('  正常往返: ${algo.extract(embedded) == text ? "OK" : "FAIL"}');

  final bits = extractBits(embedded, algo);
  print('  提取端读到的 bits 数 = ${bits.length}（全部块），码字实际只需 ${1 + text.length + 6} 字节 = ${(1 + text.length + 6) * 8} bit');

  // A1: 按当前实现（全部块 → 150 字节）做 RS 解码
  final fullBytes = bitsToBytes(bits, (bits.length ~/ 8).clamp(0, 255));
  try {
    ReedSolomonDecoder(GenericGF.qrCodeField256).decode(fullBytes, 6);
    print('  A1 全数组(150字节) RS decode: 成功');
  } catch (e) {
    print('  A1 全数组(150字节) RS decode: 抛异常 → $e');
    print('     → 集成路径中 RS 纠错从未生效（靠长度前缀兜底）');
  }

  // A2: 按码字精确长度（9 字节）做 RS 解码 —— 应成功
  final spanBytes = bitsToBytes(bits, 1 + text.length + 6);
  try {
    ReedSolomonDecoder(GenericGF.qrCodeField256).decode(spanBytes, 6);
    print('  A2 精确码字(9字节) RS decode: 成功（无错通过）');
  } catch (e) {
    print('  A2 精确码字(9字节) RS decode: 异常 $e');
  }

  // A3: 翻转 1 个数据 bit（第 5 字节内），对比两种解码路径
  final bitsCorrupt = List<int>.from(bits);
  bitsCorrupt[40] ^= 1; // 字节 5 的最高位（数据区）
  final fullC = bitsToBytes(bitsCorrupt, (bitsCorrupt.length ~/ 8).clamp(0, 255));
  try {
    ReedSolomonDecoder(GenericGF.qrCodeField256).decode(fullC, 6);
    print('  A3a 损坏后全数组 RS decode: 成功(?)');
  } catch (_) {
    print('  A3a 损坏后全数组 RS decode: 抛异常 → 纠错失败，长度前缀读到脏数据');
  }
  final spanC = bitsToBytes(bitsCorrupt, 1 + text.length + 6);
  try {
    ReedSolomonDecoder(GenericGF.qrCodeField256).decode(spanC, 6);
    final len = spanC[0];
    final recovered = String.fromCharCodes(spanC.sublist(1, 1 + len));
    print('  A3b 损坏后精确码字 RS decode: 成功，恢复文本="$recovered"');
  } catch (e) {
    print('  A3b 损坏后精确码字 RS decode: 异常 $e');
  }

  print('');
  print('=== B) 无水印 bit 的块是否被无谓修改 ===');
  // 短文本只写 72 bit（9 块），其余 1191 块不携带水印。
  // 若这些块被跳过，PSNR 应远高于长文本嵌入。
  final pShort = psnr(img, algo.embed(img, 'AB'));
  final pLong = psnr(img, algo.embed(img, 'A' * 100));
  print('  嵌入"AB"(9块携带水印) PSNR = ${pShort.toStringAsFixed(1)} dB');
  print('  嵌入100字符(808块携带水印) PSNR = ${pLong.toStringAsFixed(1)} dB');
  print('  → 若无水印块被跳过，短文本 PSNR 应显著更高；两者接近说明全部块都被 DCT 往返修改');

  print('');
  print('=== C) maxTextLength 差一错误 ===');
  final small = colorImage(64, 64); // 64 块
  final cap = algo.maxTextLength(small);
  final codewordBits = (1 + cap + 6) * 8;
  print('  64×64 图: maxTextLength=$cap，满容量码字需 $codewordBits bit，容量只有 64 bit');
  print('  → bits.take(64) 会截掉码字尾部（RS 校验字节），满容量嵌入的纠错位不完整');

  print('');
  print('=== D) 未嵌水印图的提取结果 ===');
  final plain = colorImage(320, 240);
  final r = algo.extract(plain);
  print('  干净图 extract => <${r.length > 60 ? r.substring(0, 60) + '…' : r}>');
  print('  → 非空乱码：无法可靠判定"无水印"（取证误报风险）');

  print('');
  print('=== E) 尾部 bit 分布 + 噪声图（真实照片模拟）上的 RS 行为 ===');
  // E1: 合成平滑图的尾部字节分布
  final tailSmooth = fullBytes.sublist(1 + text.length + 6);
  final nzSmooth = tailSmooth.where((b) => b != 0).length;
  print('  E1 平滑图尾部 ${tailSmooth.length} 字节中非零个数 = $nzSmooth'
      '${nzSmooth == 0 ? "（零延拓→仍是合法码字，RS 平凡通过）" : ""}');

  // E2: 噪声图（模拟真实照片纹理）：尾部随机 → 全数组 RS decode 的真实行为
  final noisyImg = colorImage(320, 240, noise: 40);
  final noisyEmbedded = algo.embed(noisyImg, text);
  final noisyExtractOk = algo.extract(noisyEmbedded);
  print('  E2 噪声图正常往返: ${noisyExtractOk == text ? "OK" : "FAIL(=$noisyExtractOk)"}');

  final noisyBits = extractBits(noisyEmbedded, algo);
  final noisyBytes = bitsToBytes(noisyBits, (noisyBits.length ~/ 8).clamp(0, 255));
  final tailNoisy = noisyBytes.sublist(1 + text.length + 6);
  final nzNoisy = tailNoisy.where((b) => b != 0).length;
  print('  E2 噪声图尾部非零字节 = $nzNoisy / ${tailNoisy.length}（随机纹理）');

  final before = List<int>.from(noisyBytes);
  String rsOutcome;
  try {
    ReedSolomonDecoder(GenericGF.qrCodeField256).decode(noisyBytes, 6);
    final mutated = <int>[];
    for (int i = 0; i < before.length; i++) {
      if (before[i] != noisyBytes[i]) mutated.add(i);
    }
    final hitCodeword = mutated.where((i) => i < 1 + text.length + 6).toList();
    rsOutcome = '成功且修改了 ${mutated.length} 个字节（位置 $mutated）'
        '${hitCodeword.isNotEmpty ? " → 误纠错命中码字区 $hitCodeword！" : ""}';
  } catch (e) {
    rsOutcome = '抛异常（被 catch 吞掉，靠长度前缀兜底）: $e';
  }
  print('  E2 噪声图全数组 RS decode: $rsOutcome');

  // E3: 噪声图 + 1 bit 数据损坏 → 集成路径能否纠错
  final noisyBitsC = List<int>.from(noisyBits);
  noisyBitsC[40] ^= 1;
  final noisyBytesC = bitsToBytes(noisyBitsC, (noisyBitsC.length ~/ 8).clamp(0, 255));
  try {
    ReedSolomonDecoder(GenericGF.qrCodeField256).decode(noisyBytesC, 6);
    final len = noisyBytesC[0];
    final recovered = (len > 0 && len <= noisyBytesC.length - 1)
        ? String.fromCharCodes(noisyBytesC.sublist(1, 1 + len))
        : '(len 非法: $len)';
    print('  E3 噪声图+1bit损坏 全数组 RS decode 后文本 = "$recovered"'
        ' ${recovered == text ? "(纠错成功)" : "(纠错失败/误纠)"}');
  } catch (_) {
    print('  E3 噪声图+1bit损坏 全数组 RS decode: 抛异常 → 纠错失败');
  }
}
