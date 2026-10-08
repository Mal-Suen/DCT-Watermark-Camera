// evidence_collector_test.dart — 取证证据采集/解析往返测试。
import 'package:flutter_test/flutter_test.dart';
import 'package:dct_watermark_app/evidence_collector.dart';

void main() {
  test('组装+解析 往返一致（有定位）', () {
    final ev = Evidence(
      capturedAt: DateTime(2026, 9, 18, 9, 30),
      latitude: 31.2345123,
      longitude: 121.4730123,
      deviceId: '8304c53be0aaa7af',
      algorithmId: 'dct',
    );
    final s = ev.toWatermarkString();
    // 固定段序：算法 时间 g坐标 d设备；54 上限下坐标降精度到 3 位
    expect(s, 'DCT 2026-09-18T09:30 g31.235,121.473 d8304c53be0aaa7af');

    final back = parseEvidence(s);
    expect(back, isNotNull);
    expect(back!.capturedAt, DateTime(2026, 9, 18, 9, 30));
    expect(back.latitude, closeTo(31.235, 1e-4));
    expect(back.longitude, closeTo(121.473, 1e-4));
    expect(back.deviceId, '8304c53be0aaa7af');
  });

  test('无定位时不含 g 段，解析还原 null', () {
    final ev = Evidence(
      capturedAt: DateTime(2026, 9, 18, 12, 0),
      deviceId: 'abc123',
    );
    final s = ev.toWatermarkString();
    expect(s, 'DCT 2026-09-18T12:00 dabc123');
    final back = parseEvidence(s);
    expect(back, isNotNull);
    expect(back!.hasLocation, isFalse);
  });

  test('水印串长度不超过 54', () {
    final ev = Evidence(
      capturedAt: DateTime(2026, 9, 18, 23, 59),
      latitude: -33.8599722,
      longitude: 151.2094444,
      deviceId: '8304c53be0aaa7af8304c53be0aaa7af', // 超长设备ID
      algorithmId: 'dct',
    );
    final s = ev.toWatermarkString();
    expect(s.length, lessThanOrEqualTo(54));
  });

  test('非法水印文本解析返回 null', () {
    expect(parseEvidence('hello world'), isNull);
    expect(parseEvidence(''), isNull);
  });
}