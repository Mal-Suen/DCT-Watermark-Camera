// DCT 取证水印相机 widget 冒烟测试（界面 v4：iPhone 相机风格布局）。
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:dct_watermark_app/main.dart';

void main() {
  testWidgets('App 启动为 iPhone 相机布局（无相机环境）', (WidgetTester tester) async {
    await tester.pumpWidget(const DctWatermarkApp(cameras: []));

    // 无相机环境：取景区显示不可用提示，状态行显示详情
    expect(find.text('相机不可用'), findsOneWidget);
    expect(find.text('未检测到相机'), findsOneWidget);
    // 底部控制条：相册入口（左，占位图标）+ 快门（中）+ 设置（右）
    expect(find.byIcon(Icons.photo_library), findsOneWidget);
    expect(find.byKey(const ValueKey('shutter')), findsOneWidget);
    expect(find.byIcon(Icons.tune), findsOneWidget);

    // 相册入口打开验水印面板
    await tester.tap(find.byIcon(Icons.photo_library));
    await tester.pumpAndSettle();
    expect(find.text('从相册选择要验证的图片'), findsOneWidget);
    expect(find.text('提取水印'), findsOneWidget);

    // 点击面板外区域（顶部 barrier）关闭
    await tester.tapAt(const Offset(200, 40));
    await tester.pumpAndSettle();
    expect(find.text('从相册选择要验证的图片'), findsNothing);
  });

  testWidgets('水印设置面板可打开', (WidgetTester tester) async {
    await tester.pumpWidget(const DctWatermarkApp(cameras: []));

    await tester.tap(find.byIcon(Icons.tune));
    await tester.pumpAndSettle();
    expect(find.text('水印设置'), findsOneWidget);
    expect(find.text('水印算法'), findsOneWidget);
  });
}
