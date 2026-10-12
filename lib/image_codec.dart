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

/// 把原始 RGBA 像素编码为 PNG 字节（引擎原生编码，不阻塞 UI）。
///
/// 修复（2026-10-08 实测卡死）：拍摄管线原先在主 isolate 跑两段 2M 像素的
/// 纯 Dart 转换循环（RGBA→ARGB / ARGB→RGBA），debug 模式模拟器上冻结 UI 数秒。
/// 现转换循环移入 compute isolate，主 isolate 只做原生异步调用（解码/编码/GPU 读回）。
Future<Uint8List> rawRgbaToPng(Uint8List rgba, int width, int height) async {
  final completer = Completer<ui.Image>();
  ui.decodeImageFromPixels(
      rgba, width, height, ui.PixelFormat.rgba8888, completer.complete);
  final img = await completer.future;
  final byteData = await img.toByteData(format: ui.ImageByteFormat.png);
  img.dispose();
  if (byteData == null) {
    throw StateError('Failed to encode PNG');
  }
  return byteData.buffer.asUint8List();
}

/// 从原始 RGBA 像素生成小尺寸缩略图（最近邻下采样，用于相机底部相册入口）。
Future<ui.Image> makeThumbnail(Uint8List rgba, int width, int height,
    {int targetWidth = 64}) {
  final tw = targetWidth;
  final th = (height * tw / width).round().clamp(1, 100000);
  final thumb = Uint8List(tw * th * 4);
  for (int y = 0; y < th; y++) {
    final sy = (y * height ~/ th).clamp(0, height - 1);
    for (int x = 0; x < tw; x++) {
      final sx = (x * width ~/ tw).clamp(0, width - 1);
      final si = (sy * width + sx) * 4;
      final di = (y * tw + x) * 4;
      thumb[di] = rgba[si];
      thumb[di + 1] = rgba[si + 1];
      thumb[di + 2] = rgba[si + 2];
      thumb[di + 3] = 0xFF;
    }
  }
  final completer = Completer<ui.Image>();
  ui.decodeImageFromPixels(
      thumb, tw, th, ui.PixelFormat.rgba8888, completer.complete);
  return completer.future;
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