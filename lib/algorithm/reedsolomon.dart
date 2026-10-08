// reedsolomon.dart — Reed-Solomon 纠错编码,逐行移植自 Android 工程自带的
// zxing 实现 (Utils/dct/.../reedsolomon/*)。用于水印内容抗误码。
//
// 仅支持 GF(256) QR_CODE_FIELD_256,与本水印系统禁用错误纠正一致的使用方式。
// Dart 中 int 为 64 位,GF 运算全部在 0..255 内,移植时注意按位 &0xff / 模取。

/// 伽罗华域 GF(2^8) — QR 码原生多项式。
class GenericGF {
  static const int _qrPrimitive = 0x011D; // x^8 + x^4 + x^3 + x^2 + 1
  static const int _size = 256;

  static final GenericGF qrCodeField256 = GenericGF._();

  final List<int> _expTable = List<int>.filled(_size, 0);
  final List<int> _logTable = List<int>.filled(_size, 0);
  GenericGFPoly? _zero;
  GenericGFPoly? _one;
  bool _initialized = false;

  GenericGF._() {
    _initialize();
  }

  static int addOrSubtract(int a, int b) => a ^ b;

  void _initialize() {
    int x = 1;
    for (int i = 0; i < _size; i++) {
      _expTable[i] = x;
      x <<= 1;
      if (x >= _size) {
        x ^= _qrPrimitive;
        x &= _size - 1;
      }
    }
    for (int i = 0; i < _size - 1; i++) {
      _logTable[_expTable[i]] = i;
    }
    _zero = GenericGFPoly(this, [0]);
    _one = GenericGFPoly(this, [1]);
    _initialized = true;
  }

  int get size => _size;

  int exp(int a) {
    if (!_initialized) {
      throw StateError('uninitialized');
    }
    return _expTable[a];
  }

  int log(int a) {
    if (!_initialized) {
      throw StateError('uninitialized');
    }
    if (a == 0) {
      throw ArgumentError('log(0) undefined in GF');
    }
    return _logTable[a];
  }

  int inverse(int a) {
    if (!_initialized) {
      throw StateError('uninitialized');
    }
    if (a == 0) {
      throw ArgumentError('inverse(0) undefined in GF');
    }
    return _expTable[_size - _logTable[a] - 1];
  }

  int multiply(int a, int b) {
    if (!_initialized) {
      throw StateError('uninitialized');
    }
    if (a == 0 || b == 0) {
      return 0;
    }
    if (a < 0 || b < 0 || a >= _size || b >= _size) {
      // 原实现有防御性;正常路径不触发
    }
    final logSum = _logTable[a] + _logTable[b];
    return _expTable[logSum % _size + logSum ~/ _size];
  }

  GenericGFPoly get zero {
    _ensureInit();
    return _zero!;
  }

  GenericGFPoly get one {
    _ensureInit();
    return _one!;
  }

  void _ensureInit() {
    if (!_initialized) {
      _initialize();
    }
  }

  GenericGFPoly buildMonomial(int degree, int coefficient) {
    _ensureInit();
    if (degree < 0) {
      throw ArgumentError('degree < 0');
    }
    if (coefficient == 0) {
      return _zero!;
    }
    final coefficients = List<int>.filled(degree + 1, 0);
    coefficients[0] = coefficient;
    return GenericGFPoly(this, coefficients);
  }
}

/// 伽罗华域多项式。
class GenericGFPoly {
  final GenericGF field;
  late final List<int> coefficients; // 从最高次项到常数项

  GenericGFPoly(this.field, List<int> source) {
    if (source.isEmpty) {
      throw ArgumentError('empty coefficients');
    }
    final len = source.length;
    if (len > 1 && source[0] == 0) {
      int firstNonZero = 1;
      while (firstNonZero < len && source[firstNonZero] == 0) {
        firstNonZero++;
      }
      if (firstNonZero == len) {
        coefficients = field.zero.coefficients;
      } else {
        coefficients = source.sublist(firstNonZero);
      }
    } else {
      coefficients = source;
    }
  }

  bool get isZero => coefficients[0] == 0;

  int get degree => coefficients.length - 1;

  int getCoefficient(int degreeIndex) =>
      coefficients[coefficients.length - 1 - degreeIndex];

  List<int> getCoefficientsList() => coefficients;

  GenericGFPoly addOrSubtract(GenericGFPoly other) {
    if (field != other.field) {
      throw ArgumentError('fields differ');
    }
    if (isZero) {
      return other;
    }
    if (other.isZero) {
      return this;
    }
    var smaller = coefficients;
    var larger = other.coefficients;
    if (smaller.length > larger.length) {
      final t = smaller;
      smaller = larger;
      larger = t;
    }
    final sumDiff = List<int>.filled(larger.length, 0);
    final lengthDiff = larger.length - smaller.length;
    for (int i = 0; i < lengthDiff; i++) {
      sumDiff[i] = larger[i];
    }
    for (int i = lengthDiff; i < larger.length; i++) {
      sumDiff[i] =
          GenericGF.addOrSubtract(smaller[i - lengthDiff], larger[i]);
    }
    return GenericGFPoly(field, sumDiff);
  }

  GenericGFPoly multiply(GenericGFPoly other) {
    if (field != other.field) {
      throw ArgumentError('fields differ');
    }
    if (isZero || other.isZero) {
      return field.zero;
    }
    final a = coefficients;
    final aLen = a.length;
    final b = other.coefficients;
    final bLen = b.length;
    final product = List<int>.filled(aLen + bLen - 1, 0);
    for (int i = 0; i < aLen; i++) {
      final aCoeff = a[i];
      for (int j = 0; j < bLen; j++) {
        product[i + j] = GenericGF.addOrSubtract(
            product[i + j], field.multiply(aCoeff, b[j]));
      }
    }
    return GenericGFPoly(field, product);
  }

  GenericGFPoly multiplyScalar(int scalar) {
    if (scalar == 0) {
      return field.zero;
    }
    if (scalar == 1) {
      return this;
    }
    final size = coefficients.length;
    final product = List<int>.filled(size, 0);
    for (int i = 0; i < size; i++) {
      product[i] = field.multiply(coefficients[i], scalar);
    }
    return GenericGFPoly(field, product);
  }

  GenericGFPoly multiplyByMonomial(int degree, int coefficient) {
    if (degree < 0) {
      throw ArgumentError('degree < 0');
    }
    if (coefficient == 0) {
      return field.zero;
    }
    final size = coefficients.length;
    final product = List<int>.filled(size + degree, 0);
    for (int i = 0; i < size; i++) {
      product[i] = field.multiply(coefficients[i], coefficient);
    }
    return GenericGFPoly(field, product);
  }

  List<GenericGFPoly> divide(GenericGFPoly other) {
    if (field != other.field) {
      throw ArgumentError('fields differ');
    }
    if (other.isZero) {
      throw ArgumentError('divide by zero');
    }
    var quotient = field.zero;
    var remainder = this;
    final denominatorLeadingTerm = other.getCoefficient(other.degree);
    final inverseDenominatorLeadingTerm = field.inverse(denominatorLeadingTerm);
    while (remainder.degree >= other.degree && !remainder.isZero) {
      final degreeDifference = remainder.degree - other.degree;
      final scale = field.multiply(
          remainder.getCoefficient(remainder.degree),
          inverseDenominatorLeadingTerm);
      final term = other.multiplyByMonomial(degreeDifference, scale);
      final iterationQuotient = field.buildMonomial(degreeDifference, scale);
      quotient = quotient.addOrSubtract(iterationQuotient);
      remainder = remainder.addOrSubtract(term);
    }
    return [quotient, remainder];
  }

  int evaluateAt(int a) {
    if (a == 0) {
      return getCoefficient(0);
    }
    final size = coefficients.length;
    if (a == 1) {
      int result = 0;
      for (int i = 0; i < size; i++) {
        result = GenericGF.addOrSubtract(result, coefficients[i]);
      }
      return result;
    }
    var result = coefficients[0];
    for (int i = 1; i < size; i++) {
      result = GenericGF.addOrSubtract(field.multiply(a, result), coefficients[i]);
    }
    return result;
  }

  @override
  String toString() {
    final buf = StringBuffer();
    for (int degreeIdx = degree; degreeIdx >= 0; degreeIdx--) {
      final coefficient = getCoefficient(degreeIdx);
      if (coefficient != 0) {
        if (buf.isNotEmpty) {
          buf.write(' + ');
        }
        if (degreeIdx == 0 || coefficient != 1) {
          final alphaPower = field.log(coefficient);
          if (alphaPower == 1) {
            buf.write('a');
          } else {
            buf.write('a^');
            buf.write(alphaPower);
          }
        }
        if (degreeIdx != 0) {
          if (degreeIdx == 1) {
            buf.write('x');
          } else {
            buf.write('x^');
            buf.write(degreeIdx);
          }
        }
      }
    }
    return buf.toString();
  }
}

/// Reed-Solomon 编码器（数据 + 校验字节）。
class ReedSolomonEncoder {
  final GenericGF field;
  final List<GenericGFPoly> _cachedGenerators;

  ReedSolomonEncoder(this.field)
      : _cachedGenerators = <GenericGFPoly>[] {
    _cachedGenerators.add(field.one);
  }

  GenericGFPoly _buildGenerator(int degree) {
    if (degree >= _cachedGenerators.length) {
      var lastGenerator = _cachedGenerators.last;
      for (int d = _cachedGenerators.length; d <= degree; d++) {
        final next = lastGenerator.multiply(
            GenericGFPoly(field, [1, field.exp(d - 1)]));
        _cachedGenerators.add(next);
        lastGenerator = next;
      }
    }
    return _cachedGenerators[degree];
  }

  /// 就地编码 [toEncode]：数据在前 dataBytes 个位置，末尾 ecBytes 写入校验。
  void encode(List<int> toEncode, int ecBytes) {
    if (ecBytes == 0) {
      throw ArgumentError('no error correction bytes');
    }
    final dataBytes = toEncode.length - ecBytes;
    if (dataBytes <= 0) {
      throw ArgumentError('no data bytes');
    }
    final generator = _buildGenerator(ecBytes);
    final infoCoefficients = List<int>.from(toEncode.sublist(0, dataBytes));
    GenericGFPoly info = GenericGFPoly(field, infoCoefficients);
    info = info.multiplyByMonomial(ecBytes, 1);
    final remainder = info.divide(generator)[1];
    final remCoeffs = remainder.getCoefficientsList();
    final numZero = ecBytes - remCoeffs.length;
    for (int i = 0; i < numZero; i++) {
      toEncode[dataBytes + i] = 0;
    }
    for (int i = 0; i < remCoeffs.length; i++) {
      toEncode[dataBytes + numZero + i] = remCoeffs[i] & 0xff;
    }
  }
}

/// 解码失败异常。
class ReedSolomonException implements Exception {
  final String message;
  ReedSolomonException(this.message);

  @override
  String toString() => 'ReedSolomonException: $message';
}

/// Reed-Solomon 解码器（Berlekamp-Massey / 欧几里得 + Chien + Forney）。
class ReedSolomonDecoder {
  final GenericGF field;

  ReedSolomonDecoder(this.field);

  /// 就地纠错 [received]（数据+校验）。[twoS] 为校验字节数×1（实际用校验字节数做纠错能力）。
  void decode(List<int> received, int twoS) {
    final poly = GenericGFPoly(field, received);
    final syndromeCoefficients = List<int>.filled(twoS, 0);
    var noError = true;
    for (int i = 0; i < twoS; i++) {
      final eval = poly.evaluateAt(field.exp(i));
      syndromeCoefficients[syndromeCoefficients.length - 1 - i] = eval;
      if (eval != 0) {
        noError = false;
      }
    }
    if (noError) {
      return;
    }
    final syndrome = GenericGFPoly(field, syndromeCoefficients);
    final sigmaOmega =
        _runEuclideanAlgorithm(field.buildMonomial(twoS, 1), syndrome, twoS);
    final sigma = sigmaOmega[0];
    final omega = sigmaOmega[1];
    final errorLocations = _findErrorLocations(sigma);
    final errorMagnitudes = _findErrorMagnitudes(omega, errorLocations);
    for (int i = 0; i < errorLocations.length; i++) {
      final position = received.length - 1 - field.log(errorLocations[i]);
      if (position < 0) {
        throw ReedSolomonException('Bad error location');
      }
      received[position] =
          GenericGF.addOrSubtract(received[position], errorMagnitudes[i]);
    }
  }

  List<GenericGFPoly> _runEuclideanAlgorithm(
      GenericGFPoly a, GenericGFPoly b, int R) {
    if (a.degree < b.degree) {
      final t = a;
      a = b;
      b = t;
    }
    GenericGFPoly rLast = a;
    GenericGFPoly r = b;
    GenericGFPoly tLast = field.zero;
    GenericGFPoly t = field.one;
    while (r.degree >= R ~/ 2) {
      final rLastLast = rLast;
      final tLastLast = tLast;
      rLast = r;
      tLast = t;
      if (rLast.isZero) {
        throw ReedSolomonException('r_{i-1} was zero');
      }
      r = rLastLast;
      GenericGFPoly q = field.zero;
      final denominatorLeadingTerm = rLast.getCoefficient(rLast.degree);
      final dltInverse = field.inverse(denominatorLeadingTerm);
      while (r.degree >= rLast.degree && !r.isZero) {
        final degreeDiff = r.degree - rLast.degree;
        final scale =
            field.multiply(r.getCoefficient(r.degree), dltInverse);
        q = q.addOrSubtract(field.buildMonomial(degreeDiff, scale));
        r = r.addOrSubtract(rLast.multiplyByMonomial(degreeDiff, scale));
      }
      t = q.multiply(tLast).addOrSubtract(tLastLast);
      if (r.degree >= rLast.degree) {
        throw ReedSolomonException('Division algorithm failed to reduce polynomial');
      }
    }
    final sigmaTildeAtZero = t.getCoefficient(0);
    if (sigmaTildeAtZero == 0) {
      throw ReedSolomonException('sigmaTilde(0) was zero');
    }
    final inverse = field.inverse(sigmaTildeAtZero);
    final sigma = t.multiplyScalar(inverse);
    final omega = r.multiplyScalar(inverse);
    return [sigma, omega];
  }

  List<int> _findErrorLocations(GenericGFPoly errorLocator) {
    final numErrors = errorLocator.degree;
    if (numErrors == 1) {
      return [errorLocator.getCoefficient(1)];
    }
    final result = List<int>.filled(numErrors, 0);
    var e = 0;
    for (int i = 1; i < field.size && e < numErrors; i++) {
      if (errorLocator.evaluateAt(i) == 0) {
        result[e] = field.inverse(i);
        e++;
      }
    }
    if (e != numErrors) {
      throw ReedSolomonException('Error locator degree does not match number of roots');
    }
    return result;
  }

  List<int> _findErrorMagnitudes(
      GenericGFPoly errorEvaluator, List<int> errorLocations) {
    final s = errorLocations.length;
    final result = List<int>.filled(s, 0);
    for (int i = 0; i < s; i++) {
      final xiInverse = field.inverse(errorLocations[i]);
      var denominator = 1;
      for (int j = 0; j < s; j++) {
        if (i != j) {
          denominator =
              field.multiply(denominator,
                  GenericGF.addOrSubtract(1,
                      field.multiply(errorLocations[j], xiInverse)));
        }
      }
      result[i] = field.multiply(errorEvaluator.evaluateAt(xiInverse),
          field.inverse(denominator));
    }
    return result;
  }
}