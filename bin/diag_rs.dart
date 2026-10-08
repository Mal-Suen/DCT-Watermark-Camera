// diag_rs.dart — 验证 Reed-Solomon 纠错确能修复被损坏的字节。
import 'package:dct_watermark_app/algorithm/reedsolomon.dart';

void main() {
  final field = GenericGF.qrCodeField256;
  final enc = ReedSolomonEncoder(field);
  final dec = ReedSolomonDecoder(field);

  // 原始数据
  final data = [104, 101, 108, 108, 111]; // 'hello'
  final ecc = 6;
  final total = data.length + ecc;
  final block = List<int>.filled(total, 0);
  block[0] = data.length;
  for (int i = 0; i < data.length; i++) block[1 + i] = data[i];
  enc.encode(block, ecc);
  print('编码后: ${block.map((b) => b.toRadixString(16)).join(' ')}');

  // 场景1: 无损坏
  var received = List<int>.from(block);
  dec.decode(received, ecc);
  final ok0 = _eq(received, block);
  print('无损坏解码: ${ok0 ? "OK" : "FAIL"}');

  // 场景2: 损坏 2 个字节
  received = List<int>.from(block);
  received[1] ^= 0x55; // 数据字节
  received[3] ^= 0xA3;
  dec.decode(received, ecc);
  final ok2 = _eq(received, block);
  print('损坏2字节解码${ok2 ? " OK" : " FAIL"}  -> ${String.fromCharCodes(received.sublist(1, 6))}');

  // 场景3: 损坏 5 个字节(超纠错能力,应失败或错)
  received = List<int>.from(block);
  for (int i = 0; i < 5; i++) received[1 + i] ^= (0x11 * (i + 1));
  try {
    dec.decode(received, ecc);
    print('损坏5字节解码: 未抛异常(可能已超出纠错,结果不可靠)');
  } catch (e) {
    print('损坏5字节解码: 检测到无法纠正 (期望) $e');
  }
}

bool _eq(List<int> a, List<int> b) {
  if (a.length != b.length) return false;
  for (int i = 0; i < a.length; i++) {
    if (a[i] != b[i]) return false;
  }
  return true;
}