// DCT 数字水印相机基础 widget 冒烟测试（界面 v2 双 Tab）。
import 'package:flutter_test/flutter_test.dart';

import 'package:dct_watermark_app/main.dart';

void main() {
  testWidgets('App 启动渲染冒烟测试（双 Tab）', (WidgetTester tester) async {
    // 无相机环境（widget 测试），传空列表
    await tester.pumpWidget(const DctWatermarkApp(cameras: []));

    // 标题
    expect(find.text('DCT 数字水印相机'), findsOneWidget);
    // 两个 Tab 标签
    expect(find.text('取证拍照'), findsOneWidget);
    expect(find.text('验水印（提取）'), findsOneWidget);

    // 默认在"取证拍照"Tab：无相机时应显示"相机不可用"，快门按钮禁用
    expect(find.text('相机不可用'), findsOneWidget);
    expect(find.text('快门取证（拍摄+自动加水印）'), findsOneWidget);
    expect(find.text('水印算法'), findsOneWidget);

    // 切到"提取"Tab
    await tester.tap(find.text('验水印（提取）'));
    await tester.pumpAndSettle();
    expect(find.text('提取水印'), findsOneWidget);
  });
}
