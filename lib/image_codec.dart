// image_codec.dart — 在 Flutter 的 ui.Image 与核心库 WmBitmap 之间互转，
// 并负责读取/写出 PNG 字节。
import 'dart:async';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:dct_watermark_app/algorithm/algorithm.dart';

/// 把 ui.Image 转为核心库的 WmBitmap（ARGB 像素数组）。
Future<WmBitmap> uiImageToWmBitmap(ui.Image image) async {
  final data = await image.toByteData(format: ui.ImageByteFormat.rawRgba);
  if (data == null) {
    throw StateError('Failed to read image pixels');
  }
  final w = image.width;
  final h = image.height;
  final bytes = data.buffer.asUint8List();
  final pixels = List<int>.filled(w * h, 0);
  for (int i = 0; i < w * h; i++) {
    final r = bytes[i * 4];
    final g = bytes[i * 4 + 1];
    final b = bytes[i * 4 + 2];
    pixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
  }
  return WmBitmap(w, h, pixels);
}

/// 把核心库的 WmBitmap（水印处理结果）转回 ui.Image。
Future<ui.Image> wmBitmapToUiImage(WmBitmap wm) async {
  final w = wm.width;
  final h = wm.height;
  final bytes = Uint8List(w * h * 4);
  for (int i = 0; i < w * h; i++) {
    final argb = wm.pixelAt(i);
    bytes[i * 4] = (argb >> 16) & 0xFF; // r
    bytes[i * 4 + 1] = (argb >> 8) & 0xFF; // g
    bytes[i * 4 + 2] = argb & 0xFF; // b
    bytes[i * 4 + 3] = 0xFF; // a
  }
  final completer = Completer<ui.Image>();
  ui.decodeImageFromPixels(bytes, w, h, ui.PixelFormat.rgba8888,
      (img) => completer.complete(img));
  return completer.future;
}

/// 把 WmBitmap 编码为 PNG 字节。
Future<Uint8List> wmBitmapToPng(WmBitmap wm) async {
  final img = await wmBitmapToUiImage(wm);
  final byteData = await img.toByteData(format: ui.ImageByteFormat.png);
  img.dispose();
  if (byteData == null) {
    throw StateError('Failed to encode PNG');
  }
  return byteData.buffer.asUint8List();
}

/// 从文件字节解码出 ui.Image。
Future<ui.Image> decodeImage(Uint8List bytes) async {
  final codec = await ui.instantiateImageCodec(bytes);
  final frame = await codec.getNextFrame();
  // 修复（2026-10-08 代码审查 S6）：释放原生 Codec（dart:ui 契约），
  // 避免连拍场景解码器状态在 GC 前累积占用原生内存。
  codec.dispose();
  return frame.image;
}