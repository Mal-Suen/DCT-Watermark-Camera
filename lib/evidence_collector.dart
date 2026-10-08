// evidence_collector.dart — 取证水印证据采集与内容组装。
//
// 纯取证水印相机：水印内容由拍摄现场的"客观事实"自动构成，
// 包括 算法标识 + 拍摄时间 + 经纬度 + 设备标识，全 ASCII（符合 DCT 字符集）。
// 本文件纯 Dart 无 UI 依赖，可独立单测。

/// 单条取证证据的组成字段。
class Evidence {
  /// 拍摄时间（设备本地时间，本地时区）。
  final DateTime capturedAt;

  /// 纬度；未知时为 null。
  final double? latitude;

  /// 经度；未知时为 null。
  final double? longitude;

  /// 设备唯一标识（ANDROID_ID）；未知时为 'n/a'。
  final String deviceId;

  /// 水印算法 id（取当前使用的算法）。
  final String algorithmId;

  const Evidence({
    required this.capturedAt,
    this.latitude,
    this.longitude,
    this.deviceId = 'n/a',
    this.algorithmId = 'dct',
  });

  /// 位置是否成功获取。
  bool get hasLocation => latitude != null && longitude != null;

  /// 组装为取证水印文本（ASCII）。
  ///
  /// 优先级：算法+设备ID 完整 > 时间 > 坐标。
  /// 坐标精度自适应，必要时降小数位以容纳完整设备ID。
  /// [maxLen] 为当前算法在该图像上的容量上限（由调用方按算法+图像尺寸传入）。
  /// 格式示例：
  ///   DCT 2026-09-18T09:30 g31.2345,121.4731 d8304c53be0aaa7af
  String toWatermarkString({int maxLen = 54}) {
    final alg = algorithmId.toUpperCase();
    final dev = deviceId.isEmpty ? 'na' : deviceId;
    final prefix = alg;
    final max = maxLen;
    final ts = _timestamp(capturedAt);

    // 基础：算法 + 时间 + 设备
    var s = '$prefix $ts d$dev';
    if (s.length <= max) {
      // 有空间就插入坐标（调整小数位），固定插在时间后、设备前
      if (hasLocation) {
        for (int prec = 4; prec >= 1; prec--) {
          final geo = 'g${_num(latitude!, prec)},${_num(longitude!, prec)}';
          final candidate = '$prefix $ts $geo d$dev';
          if (candidate.length <= max) {
            s = candidate;
            break;
          }
        }
      }
      return s;
    }
    // 基础已超（极长设备ID）：优先保算法+设备，砍时间到日
    final shortTs = '${capturedAt.year}${_pad(capturedAt.month)}${_pad(capturedAt.day)}';
    var s2 = '$prefix 日期$shortTs d...';
    s2 = '$prefix $shortTs d$dev';
    if (s2.length <= max) {
      return s2;
    }
    // 仍超：截断设备ID
    final room = max - ('$prefix d').length;
    final shortDev =
        dev.length > room ? dev.substring(0, room > 0 ? room : 0) : dev;
    return '$prefix d$shortDev';
  }
}

// 算法字符串长度上限由调用方按"当前算法+图像尺寸"传入（见 toWatermarkString 的 maxLen 参数）。
// 旧 DCT 固定 54；QIM-DCT 随图像尺寸增长（见 WmAlgorithm.maxTextLength）。

/// 时间格式 yyyy-MM-dd'T'HH:mm（大写 T 分隔，ASCII）
String _timestamp(DateTime t) {
  return '${t.year}-${_pad(t.month)}-${_pad(t.day)}T${_pad(t.hour)}:${_pad(t.minute)}';
}

/// 补零（2 位）。
String _pad(int v) => v.toString().padLeft(2, '0');

/// 坐标数字格式化：保留小数后 [prec] 位，去掉多余 0。
String _num(double v, int prec) {
  var s = v.toStringAsFixed(prec);
  while (s.length > 1 && s.contains('.') && s.endsWith('0')) {
    s = s.substring(0, s.length - 1);
  }
  if (s.endsWith('.')) s = s.substring(0, s.length - 1);
  return s;
}

/// 解析取证水印文本，还原字段。
/// 返回 null 表示无法解析（非取证格式）。
///
/// 支持形如：
///   DCT 2026-09-18T09:30 g31.2345,121.4731 d8304c53be0aaa7af
/// 无定位时：
///   DCT 2026-09-18T09:30 d8304c53be0aaa7af
Evidence? parseEvidence(String watermark) {
  if (watermark.isEmpty) return null;
  final parts = watermark.split(' ');
  if (parts.length < 3) return null;

  DateTime? time;
  double? lat, lon;
  String dev = 'n/a';

  for (final p in parts) {
    // 时间
    if (RegExp(r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$').hasMatch(p)) {
      try {
        time = DateTime.parse(p.replaceFirst('T', ' '));
      } catch (_) {}
    }
    // 坐标：g<lat>,<lon>
    if (p.startsWith('g')) {
      final v = p.substring(1);
      final comma = v.indexOf(',');
      if (comma > 0) {
        lat = double.tryParse(v.substring(0, comma));
        lon = double.tryParse(v.substring(comma + 1));
      }
    }
    // 设备
    if (p.startsWith('d')) {
      final v = p.substring(1);
      if (v.isNotEmpty && v != 'na') dev = v;
    }
  }

  if (time == null) return null;
  return Evidence(
    capturedAt: time,
    latitude: lat,
    longitude: lon,
    deviceId: dev,
  );
}

/// 取水印算法 id（用于提取端对算法做初步匹配；当前仅 dct）。
String algorithmIdFromWatermark(String watermark) {
  final first = watermark.split(' ').firstOrNull;
  return (first != null && first.isNotEmpty) ? first.toLowerCase() : 'unknown';
}