// bits.dart — 逐行移植自 Android 版 Bits.java
// 仅保留 DCT 水印流程实际使用的方法；GZIP 编解码与 Reed-Solomon 纠错
// 在本 App 的参数（errorCorrection=0）下不会被调用，故不移植。

class Bits {
  final List<bool> _bits;
  int _readPosition = 0;

  Bits() : _bits = <bool>[];

  Bits.fromBits(Bits other) : _bits = <bool>[...other._bits];

  Bits.fromBooleans(Iterable<bool> bits) : _bits = <bool>[...bits];

  void addBit(bool bit) => _bits.add(bit);

  // Java addBits(boolean[]) / addBits(Collection<Boolean>) —— 本流程未用到，
  // 但为忠实保留接口形态。
  void addBitsList(List<bool> bits) {
    for (final b in bits) {
      addBit(b);
    }
  }

  void addBytes(List<int> bytes) => addBytesLen(bytes, bytes.length);

  void addBytesLen(List<int> bytes, int len) {
    for (int i = 0; i < len; i++) {
      int bit = 0x01;
      for (int j = 0; j < 8; j++) {
        addBit((bytes[i] & bit) > 0);
        bit <<= 1;
      }
    }
  }

  void addValue(int bits, int len) {
    int bit = 0x01;
    for (int i = 0; i < len; i++) {
      addBit((bits & bit) > 0);
      bit <<= 1;
    }
  }

  bool getBit(int index) => _bits[index];

  List<bool> getBitsList() => _bits;

  List<bool> getBitsRange(int fromIndex, int toIndex) =>
      _bits.sublist(fromIndex, toIndex);

  List<int> getBytes() {
    final bytes = List<int>.filled((_bits.length + 7) ~/ 8, 0);
    for (int i = 0; i < bytes.length; i++) {
      int bit = 0x01;
      for (int j = 0; j < 8 && i * 8 + j < _bits.length; j++) {
        if (_bits[i * 8 + j]) {
          bytes[i] |= bit;
        }
        bit <<= 1;
      }
    }
    return bytes;
  }

  // Java addData(byte[]) —— 本流程未用到；getData 亦未在本流程使用，
  // 但保留字节级读写接口以防后续需要。为与 Java byte 有符号语义对齐，
  // 这里返回的是无符号 0..255 的值（Bitmap 流程不受影响）。
  void addData(List<int> data) => addBytesLen(data, data.length);

  int getValue(int index, int len) {
    int result = 0;
    int bit = 0x01;
    for (int i = index; i < index + len; i++) {
      if (_bits[i]) {
        result |= bit;
      }
      bit <<= 1;
    }
    return result;
  }

  bool hasNext() => _readPosition < _bits.length;

  bool hasNextN(int len) => _readPosition + len < _bits.length + 1;

  bool popBit() => getBit(_readPosition++);

  List<bool> popBits(int len) {
    _readPosition += len;
    return getBitsRange(_readPosition - len, _readPosition);
  }

  int popValue(int len) {
    _readPosition += len;
    return getValue(_readPosition - len, len);
  }

  void reset() {
    _bits.clear();
    _readPosition = 0;
  }

  void setBit(int index, bool bit) => _bits[index] = bit;

  int size() => _bits.length;

  @override
  String toString() {
    final buf = StringBuffer();
    for (int i = 0; i < _bits.length; i++) {
      buf.write(_bits[i] ? '1' : '0');
    }
    return buf.toString();
  }
}