// JRandom — 逐 bit 复刻 java.util.Random，用于保证与 Android 版 DCT 水印互相识别。
//
// 原 Android 版 Watermark.java 使用 `new Random(seed)` 生成水印/嵌入掩码，
// Dart 的 math.Random.nextDouble 序列与 Java 不一致（尤其对 2 的幂 bound，
// Java 走 (bound*next(31))>>31 取高位，Dart 取低位）。若直接用 Dart Random，
// 掩码位置全错，新 App 与已发布 Android 版水印将互相读不出来。
// 因此这里完整复刻 java.util.Random 的 next(n)、nextInt(bound)。

class JRandom {
  late int _state;

  /// multiplier 0x5DEECE66D = 25214903917
  /// addend      0xB = 11
  static const int _multiplier = 25214903917;
  static const int _addend = 0xB;
  static const int _mask = (1 << 48) - 1; // 0xFFFFFFFFFFFF

  JRandom(int seed) {
    _state = (seed ^ _multiplier) & _mask;
  }

  /// java.util.Random.next(bits) 的内部 48 位 LCG。
  /// Dart 的 int 是 64 位补码，乘法溢出按 64 位截断，与 Java long 行为一致；
  /// 随后 & 48 位掩码取低 48 位，位模式与 Java 完全一致。
  int _next(int bits) {
    _state = (_state * _multiplier + _addend) & _mask;
    return _state >>> (48 - bits);
  }

  /// java.util.Random.nextInt(int bound)
  int nextInt(int bound) {
    if (bound <= 0) {
      throw RangeError('bound must be positive');
    }
    // 用 bound 是否为 2 的幂来走不同分支 —— 必须与 Java 一致。
    if ((bound & -bound) == bound) {
      // bound 是 2 的幂：返回 (bound * next(31)) >> 31
      return (bound * _next(31)) >> 31;
    }
    // 非 2 的幂：拒绝采样，位级复刻 Java 逻辑
    int bits, value;
    do {
      bits = _next(31);
      value = bits % bound;
    } while (bits - value + (bound - 1) < 0);
    return value;
  }
}