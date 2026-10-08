// ignore_for_file: constant_identifier_names, prefer_initializing_formals, avoid_print, prefer_contains
// watermark.dart — DCT 水印核心，逐行移植自 Android 版 Watermark.java。
//
// 移植时的主要适配点：
//   1. 以 WmBitmap（ARGB 用 int 数组存储）替代 android.graphics.Bitmap。
//   2. RGBtoHSB / HSBtoRGB 原为 Android 的 Color 工具方法，此处自行实现等价逻辑。
//   3. Color.red/green/blue 取通道操作改为位运算 _red/_green/_blue。
//   4. Log.d 调试输出改为 print（受 debug 开关控制）。
//   5. Java 的 Math.round 以 jRound 复刻（详见 dct_math.dart）。
//
// 本文件与原 Android 版位级兼容，保证互相可读水印（DctTool 为兼容封装）。
// 注：部分内联注释存在历史编码损坏（显示为 ?），不影响代码逻辑。

import 'bits.dart';
import 'dct_math.dart';
import 'jrandom.dart';

/// ???????ARGB_8888????? int ? 0xAARRGGBB??? Bitmap ????
class WmBitmap {
  final int width;
  final int height;
  final List<int> _pixels; // length = width*height

  WmBitmap(this.width, this.height, List<int> pixels)
      : _pixels = List<int>.of(pixels) {
    if (_pixels.length != width * height) {
      throw ArgumentError('pixel count mismatch');
    }
  }

  factory WmBitmap.create(int width, int height) => WmBitmap(
      width, height, List<int>.filled(width * height, 0xFF000000));

  int getPixel(int x, int y) => _pixels[y * width + x];

  void setPixel(int x, int y, int argb) => _pixels[y * width + x] = argb;

  /// 线性索引访问（供外部桥接层使用）。
  int pixelAt(int i) => _pixels[i];

  /// 只读像素数据副本。
  List<int> get pixelData => List<int>.of(_pixels);
}

class Watermark {
  // ??????? Android ? VALID_CHARS ?????
  static const String VALID_CHARS =
      " abcdefghijklmnopqrstuvwxyz0123456789.-,:/()?!\"'#*+_%\$&=<>[];@�\n";

  /// ??????????
  int bitBoxSize = 10;
  /// ??????0 ??????
  int byteLenErrorCorrection = 6;
  int maxBitsTotal = 0;
  int maxBitsData = 0;
  int maxTextLen = 0;
  /// ??????1.0 ???
  double opacity = 1.0;
  int _randomizeWatermarkSeed = 19;
  int _randomizeEmbeddingSeed = 24;
  static bool debug = false;

  Watermark() {
    _calculateSizes();
  }

  Watermark.box(int boxSize, int errorCorrectionBytes, double opacity)
      : bitBoxSize = boxSize,
        byteLenErrorCorrection = errorCorrectionBytes,
        opacity = opacity {
    _calculateSizes();
  }

  Watermark.boxSeeded(int boxSize, int errorCorrectionBytes, double opacity,
      int seed1, int seed2)
      : bitBoxSize = boxSize,
        byteLenErrorCorrection = errorCorrectionBytes,
        opacity = opacity,
        _randomizeEmbeddingSeed = seed1,
        _randomizeWatermarkSeed = seed2 {
    _calculateSizes();
  }

  Watermark.seeded(int seed1, int seed2)
      : _randomizeEmbeddingSeed = seed1,
        _randomizeWatermarkSeed = seed2 {
    _calculateSizes();
  }

  void _calculateSizes() {
    maxBitsTotal = (128 ~/ bitBoxSize) * (128 ~/ bitBoxSize);
    maxBitsData = maxBitsTotal - byteLenErrorCorrection * 8;
    maxTextLen = maxBitsData ~/ 6;
  }

  String _bits2String(Bits bits) {
    final buf = StringBuffer();
    for (int i = 0; i < maxTextLen; i++) {
      final c = bits.getValue(i * 6, 6).toInt();
      buf.write(VALID_CHARS[c]);
    }
    return buf.toString();
  }

  /// ???????? bits ???????????
  WmBitmap embed(WmBitmap image, Bits data) {
    Bits bits;
    if (data.size() > maxBitsData) {
      bits = Bits.fromBooleans(data.getBitsRange(0, maxBitsData));
    } else {
      bits = Bits.fromBits(data);
      while (bits.size() < maxBitsData) {
        bits.addBit(false);
      }
    }

    // ? App ?? byteLenErrorCorrection=0???? Reed-Solomon ??????????

    // ?? 128x128 ?????
    final watermarkBitmap = List.generate(128, (_) => List<int>.filled(128, 0));
    final blocks = 128 ~/ bitBoxSize;
    for (int y = 0; y < blocks * bitBoxSize; y++) {
      for (int x = 0; x < blocks * bitBoxSize; x++) {
        final idx = x ~/ bitBoxSize + (y ~/ bitBoxSize) * blocks;
        if (bits.size() > idx) {
          watermarkBitmap[y][x] = bits.getBit(idx) ? 255 : 0;
        }
      }
    }

    if (debug) {
      print('watermark bitmap generated');
    }

    // ???X = embed(??, ?????)
    final grey = embedInternal(image, watermarkBitmap);

    // ??????
    final width = image.width;
    final height = image.height;
    final out = WmBitmap.create(width, height);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        final imagePixel = image.getPixel(x, y);
        final red = _red(imagePixel);
        final green = _green(imagePixel);
        final blue = _blue(imagePixel);
        final hsb = _rgbToHsb(red, green, blue, null);
        hsb[2] = (hsb[2] * (1.0 - opacity) + grey[y][x] * opacity / 255.0);
        final color = _hsbToRgb(hsb[0], hsb[1], hsb[2]);
        out.setPixel(x, y, color);
      }
    }
    return out;
  }

  /// ???????????? buff3??
  List<List<int>> embedInternal(WmBitmap src, List<List<int>> water1) {
    final width = (src.width + 7) ~/ 8 * 8;
    final height = (src.height + 7) ~/ 8 * 8;
    const N = 8;
    final buff1 = List.generate(height, (_) => List<int>.filled(width, 0));
    final buff2 = List.generate(height, (_) => List<int>.filled(width, 0));
    final buff3 = List.generate(height, (_) => List<int>.filled(width, 0));
    final b1 = List.generate(N, (_) => List<int>.filled(N, 0));
    final b2 = List.generate(N, (_) => List<int>.filled(N, 0));
    final b3 = List.generate(N, (_) => List<int>.filled(N, 0));
    final b4 = List.generate(N, (_) => List<int>.filled(N, 0));

    const W = 4;
    final water2 = List.generate(128, (_) => List<int>.filled(128, 0));
    final water3 = List.generate(128, (_) => List<int>.filled(128, 0));
    final w1 = List.generate(W, (_) => List<int>.filled(W, 0));
    final w2 = List.generate(W, (_) => List<int>.filled(W, 0));
    final w3 = List.generate(W, (_) => List<int>.filled(W, 0));
    final mfbuff1 = List.generate(128, (_) => List<int>.filled(128, 0));
    final mfbuff2 = List<int>.filled(width * height, 0);

    int a, b, c;
    final tmp = List<int>.filled(128 * 128, 0);

    int c1;
    int cc = 0;
    final tmp1 = List<int>.filled(128 * 128, 0);

    int k = 0, l = 0;

    // init buf1 from src image????
    for (int y = 0; y < src.height; y++) {
      for (int x = 0; x < src.width; x++) {
        final p = src.getPixel(x, y);
        final r = _red(p);
        final g = _green(p);
        final b_ = _blue(p);
        final hsb = _rgbToHsb(r, g, b_, null);
        buff1[y][x] = (hsb[2] * 255.0).toInt();
      }
    }

    // ?? 8x8 FDCT
    for (int y = 0; y < height; y += N) {
      for (int x = 0; x < width; x += N) {
        for (int i = y; i < y + N; i++) {
          for (int j = x; j < x + N; j++) {
            b1[k][l] = buff1[i][j];
            l++;
          }
          l = 0;
          k++;
        }
        k = 0;
        final o1 = DCT();
        o1.forwardDCT(b1, b2);
        for (int p = y; p < y + N; p++) {
          for (int q = x; q < x + N; q++) {
            buff2[p][q] = b2[k][l];
            l++;
          }
          l = 0;
          k++;
        }
        k = 0;
      }
    }

    // ???????randomizeWatermarkSeed?
    final r = JRandom(_randomizeWatermarkSeed);
    for (int i = 0; i < 128; i++) {
      for (int j = 0; j < 128; j++) {
        while (true) {
          c = r.nextInt(128 * 128);
          if (tmp[c] == 0) {
            break;
          }
        }
        a = c ~/ 128;
        b = c % 128;
        water2[i][j] = water1[a][b];
        tmp[c] = 1;
      }
    }

    // ??? 4x4 FDCT + ??
    k = 0;
    l = 0;
    for (int y = 0; y < 128; y += W) {
      for (int x = 0; x < 128; x += W) {
        for (int i = y; i < y + W; i++) {
          for (int j = x; j < x + W; j++) {
            w1[k][l] = water2[i][j];
            l++;
          }
          l = 0;
          k++;
        }
        k = 0;
        final wm1 = DCT2();
        wm1.forwardDCT(w1, w2);
        final qw1 = Qt();
        qw1.waterQuantize(w2, w3);
        for (int p = y; p < y + W; p++) {
          for (int q = x; q < x + W; q++) {
            water3[p][q] = w3[k][l];
            l++;
          }
          l = 0;
          k++;
        }
        k = 0;
      }
    }

    // ?????randomizeEmbeddingSeed?
    final r1 = JRandom(_randomizeEmbeddingSeed);
    for (int i = 0; i < 128; i++) {
      for (int j = 0; j < 128; j++) {
        while (true) {
          c1 = r1.nextInt(128 * 128);
          if (tmp1[c1] == 0) {
            break;
          }
        }
        a = c1 ~/ 128;
        b = c1 % 128;
        mfbuff1[i][j] = water3[a][b];
        tmp1[c1] = 1;
      }
    }

    // ?? ? ??
    final scan = ZigZag();
    scan.two2one(mfbuff1, mfbuff2);

    // WriteBack coefficients
    cc = 0;
    for (int i = 0; i < height; i += N) {
      for (int j = 0; j < width; j += N) {
        buff2[i + 1][j + 4] = mfbuff2[cc];
        cc++;
        buff2[i + 2][j + 3] = mfbuff2[cc];
        cc++;
        buff2[i + 3][j + 2] = mfbuff2[cc];
        cc++;
        buff2[i + 4][j + 1] = mfbuff2[cc];
        cc++;
      }
    }

    // 8x8 IDCT ??
    k = 0;
    l = 0;
    for (int y = 0; y < height; y += N) {
      for (int x = 0; x < width; x += N) {
        for (int i = y; i < y + N; i++) {
          for (int j = x; j < x + N; j++) {
            b3[k][l] = buff2[i][j];
            l++;
          }
          l = 0;
          k++;
        }
        k = 0;
        final o2 = DCT();
        o2.inverseDCT(b3, b4);
        for (int p = y; p < y + N; p++) {
          for (int q = x; q < x + N; q++) {
            buff3[p][q] = b4[k][l];
            l++;
          }
          l = 0;
          k++;
        }
        k = 0;
      }
    }
    return buff3;
  }

  /// ????????
  WmBitmap embedString(WmBitmap image, String data) =>
      embed(image, string2Bits(data));

  /// ????? Bits?? App errorCorrection=0?????????
  Bits extractData(WmBitmap image) {
    final extracted = extractRawPublic(image);

    // ?/???
    final blocks = 128 ~/ bitBoxSize;
    final box = blocks * bitBoxSize;
    for (int y = 0; y < box; y += bitBoxSize) {
      for (int x = 0; x < box; x += bitBoxSize) {
        int sum = 0;
        for (int y2 = y; y2 < y + bitBoxSize; y2++) {
          for (int x2 = x; x2 < x + bitBoxSize; x2++) {
            sum += extracted[y2][x2];
          }
        }
        sum = sum ~/ (bitBoxSize * bitBoxSize);
        for (int y2 = y; y2 < y + bitBoxSize; y2++) {
          for (int x2 = x; x2 < x + bitBoxSize; x2++) {
            extracted[y2][x2] = sum > 127 ? 255 : 0;
          }
        }
      }
    }

    // ?? bits
    Bits bits = Bits();
    for (int y = 0; y < box; y += bitBoxSize) {
      for (int x = 0; x < box; x += bitBoxSize) {
        bits.addBit(extracted[y][x] > 127);
      }
    }
    bits = Bits.fromBooleans(bits.getBitsRange(0, maxBitsTotal));
    return bits;
  }

  /// ?????????128x128 ????
  List<List<int>> extractRawPublic(WmBitmap src) {
    final width = (src.width + 7) ~/ 8 * 8;
    final height = (src.height + 7) ~/ 8 * 8;
    const N = 8;
    final buff1 = List.generate(height, (_) => List<int>.filled(width, 0));
    final buff2 = List.generate(height, (_) => List<int>.filled(width, 0));
    final b1 = List.generate(N, (_) => List<int>.filled(N, 0));
    final b2 = List.generate(N, (_) => List<int>.filled(N, 0));

    const W = 4;
    final water1 = List.generate(128, (_) => List<int>.filled(128, 0));
    final water2 = List.generate(128, (_) => List<int>.filled(128, 0));
    final water3 = List.generate(128, (_) => List<int>.filled(128, 0));
    final w1 = List.generate(W, (_) => List<int>.filled(W, 0));
    final w2 = List.generate(W, (_) => List<int>.filled(W, 0));
    final w3 = List.generate(W, (_) => List<int>.filled(W, 0));

    int a, b, c, c1;
    final tmp = List<int>.filled(128 * 128, 0);
    final tmp1 = List<int>.filled(128 * 128, 0);
    int cc = 0;

    final mfbuff1 = List<int>.filled(width * height, 0);
    final mfbuff2 = List.generate(128, (_) => List<int>.filled(128, 0));

    int k = 0, l = 0;

    for (int y = 0; y < src.height; y++) {
      for (int x = 0; x < src.width; x++) {
        final p = src.getPixel(x, y);
        final hsb = _rgbToHsb(_red(p), _green(p), _blue(p), null);
        buff1[y][x] = (hsb[2] * 255.0).toInt();
      }
    }

    // 8x8 FDCT
    for (int y = 0; y < height; y += N) {
      for (int x = 0; x < width; x += N) {
        for (int i = y; i < y + N; i++) {
          for (int j = x; j < x + N; j++) {
            b1[k][l] = buff1[i][j];
            l++;
          }
          l = 0;
          k++;
        }
        k = 0;
        final o1 = DCT();
        o1.forwardDCT(b1, b2);
        for (int p = y; p < y + N; p++) {
          for (int q = x; q < x + N; q++) {
            buff2[p][q] = b2[k][l];
            l++;
          }
          l = 0;
          k++;
        }
        k = 0;
      }
    }

    // ??????
    cc = 0;
    for (int i = 0; i < height; i += N) {
      for (int j = 0; j < width; j += N) {
        mfbuff1[cc] = buff2[i + 1][j + 4];
        cc++;
        mfbuff1[cc] = buff2[i + 2][j + 3];
        cc++;
        mfbuff1[cc] = buff2[i + 3][j + 2];
        cc++;
        mfbuff1[cc] = buff2[i + 4][j + 1];
        cc++;
      }
    }
    cc = 0;

    // ?? ? ??
    final scan = ZigZag();
    scan.one2two(mfbuff1, mfbuff2);

    // ?????randomizeEmbeddingSeed?
    final r1 = JRandom(_randomizeEmbeddingSeed);
    for (int i = 0; i < 128; i++) {
      for (int j = 0; j < 128; j++) {
        while (true) {
          c1 = r1.nextInt(128 * 128);
          if (tmp1[c1] == 0) {
            break;
          }
        }
        a = c1 ~/ 128;
        b = c1 % 128;
        water1[a][b] = mfbuff2[i][j];
        tmp1[c1] = 1;
      }
    }

    // ??? + IDCT
    k = 0;
    l = 0;
    for (int y = 0; y < 128; y += W) {
      for (int x = 0; x < 128; x += W) {
        for (int i = y; i < y + W; i++) {
          for (int j = x; j < x + W; j++) {
            w1[k][l] = water1[i][j];
            l++;
          }
          l = 0;
          k++;
        }
        k = 0;
        final qw2 = Qt();
        qw2.waterDeQuantize(w1, w2);
        final wm2 = DCT2();
        wm2.inverseDCT(w2, w3);
        for (int p = y; p < y + W; p++) {
          for (int q = x; q < x + W; q++) {
            water2[p][q] = w3[k][l];
            l++;
          }
          l = 0;
          k++;
        }
        k = 0;
      }
    }

    // ????randomizeWatermarkSeed?
    final r = JRandom(_randomizeWatermarkSeed);
    for (int i = 0; i < 128; i++) {
      for (int j = 0; j < 128; j++) {
        while (true) {
          c = r.nextInt(128 * 128);
          if (tmp[c] == 0) {
            break;
          }
        }
        a = c ~/ 128;
        b = c % 128;
        water3[a][b] = water2[i][j];
        tmp[c] = 1;
      }
    }
    return water3;
  }

  /// ??????
  String extractText(WmBitmap image) => _bits2String(extractData(image)).trim();

  // ---- getters?????? ----
  int getBitBoxSize() => bitBoxSize;
  int getByteLenErrorCorrection() => byteLenErrorCorrection;
  int getMaxBitsData() => maxBitsData;
  int getMaxBitsTotal() => maxBitsTotal;
  int getMaxTextLen() => maxTextLen;
  double getOpacity() => opacity;
  int getRandomizeEmbeddingSeed() => _randomizeEmbeddingSeed;
  int getRandomizeWatermarkSeed() => _randomizeWatermarkSeed;

  /// ??? ? bits
  Bits string2Bits(String sIn) {
    final bits = Bits();
    final sb = StringBuffer();
    for (int i = 0; i < sIn.length; i++) {
      final c = sIn[i].toLowerCase();
      if (VALID_CHARS.indexOf(c) >= 0) {
        sb.write(c);
      }
    }
    String s = sb.toString();

    if (s.length > maxTextLen) {
      s = s.substring(0, maxTextLen);
    }
    while (s.length < maxTextLen) {
      s += ' ';
    }

    for (int j = 0; j < s.length; j++) {
      bits.addValue(VALID_CHARS.indexOf(s[j]), 6);
    }
    return bits;
  }

  // ????????? Android Color.red/green/blue??? alpha?
  static int _red(int argb) => (argb >> 16) & 0xFF;
  static int _green(int argb) => (argb >> 8) & 0xFF;
  static int _blue(int argb) => argb & 0xFF;

  /// RGB ? HSB???? Android ??? RGBtoHSB?
  List<double> _rgbToHsb(int r, int g, int b, List<double>? hsbvals) {
    final out = hsbvals ?? List<double>.filled(3, 0);
    int cmax = (r > g) ? r : g;
    if (b > cmax) cmax = b;
    int cmin = (r < g) ? r : g;
    if (b < cmin) cmin = b;

    final brightness = cmax / 255.0;
    final double saturation;
    if (cmax != 0) {
      saturation = (cmax - cmin) / cmax;
    } else {
      saturation = 0;
    }
    double hue;
    if (saturation == 0) {
      hue = 0;
    } else {
      final redc = (cmax - r) / (cmax - cmin);
      final greenc = (cmax - g) / (cmax - cmin);
      final bluec = (cmax - b) / (cmax - cmin);
      if (r == cmax) {
        hue = bluec - greenc;
      } else if (g == cmax) {
        hue = 2.0 + redc - bluec;
      } else {
        hue = 4.0 + greenc - redc;
      }
      hue = hue / 6.0;
      if (hue < 0) {
        hue = hue + 1.0;
      }
    }
    out[0] = hue;
    out[1] = saturation;
    out[2] = brightness;
    return out;
  }

  /// HSB ? RGB???? Android ??? HSBtoRGB?
  int _hsbToRgb(double hue, double saturation, double brightness) {
    int r = 0, g = 0, b = 0;
    if (saturation == 0) {
      r = g = b = (brightness * 255.0 + 0.5).toInt();
    } else {
      final h = (hue - hue.floorToDouble()) * 6.0;
      final f = h - h.floorToDouble();
      final p = brightness * (1.0 - saturation);
      final q = brightness * (1.0 - saturation * f);
      final t = brightness * (1.0 - (saturation * (1.0 - f)));
      final bi = h.toInt(); // h in [0,6) ?????=floor
      switch (bi) {
        case 0:
          r = (brightness * 255.0 + 0.5).toInt();
          g = (t * 255.0 + 0.5).toInt();
          b = (p * 255.0 + 0.5).toInt();
          break;
        case 1:
          r = (q * 255.0 + 0.5).toInt();
          g = (brightness * 255.0 + 0.5).toInt();
          b = (p * 255.0 + 0.5).toInt();
          break;
        case 2:
          r = (p * 255.0 + 0.5).toInt();
          g = (brightness * 255.0 + 0.5).toInt();
          b = (t * 255.0 + 0.5).toInt();
          break;
        case 3:
          r = (p * 255.0 + 0.5).toInt();
          g = (q * 255.0 + 0.5).toInt();
          b = (brightness * 255.0 + 0.5).toInt();
          break;
        case 4:
          r = (t * 255.0 + 0.5).toInt();
          g = (p * 255.0 + 0.5).toInt();
          b = (brightness * 255.0 + 0.5).toInt();
          break;
        case 5:
          r = (brightness * 255.0 + 0.5).toInt();
          g = (p * 255.0 + 0.5).toInt();
          b = (q * 255.0 + 0.5).toInt();
          break;
      }
    }
    return 0xFF000000 | (r << 16) | (g << 8) | b;
  }
}