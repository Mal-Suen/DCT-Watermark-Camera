// main.dart — DCT 取证水印相机（Flutter 跨平台版）· 界面 v3
//
// 常规相机布局：全屏取景 + 悬浮功能键
//  - 打开即全屏相机取景；快门 = 拍摄并自动嵌入取证水印（时间+经纬度+设备ID）→ 存相册
//  - 右上功能键：验水印（底部面板：相册选图 → 提取 → 结构化展示）
//  - 左上功能键：水印设置（底部面板：算法切换）
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:camera/camera.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:gal/gal.dart';
import 'package:geolocator/geolocator.dart';
import 'package:image_picker/image_picker.dart';
import 'package:dct_watermark_app/algorithm/algorithm.dart';

import 'evidence_collector.dart';
import 'image_codec.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // 预取相机列表；失败时传空，UI 显示"相机不可用"
  List<CameraDescription> cameras = const [];
  try {
    cameras = await availableCameras();
  } catch (_) {}
  runApp(DctWatermarkApp(cameras: cameras));
}

// ---- 后台 isolate 计算函数（避免阻塞 UI 主线程）----

// 拍摄管线（后台）：RGBA→ARGB 转换 + 嵌入水印 + ARGB→RGBA 转换。
// 修复（2026-10-08 实测卡死）：这两段 2M 像素级的纯 Dart 循环原先跑在主
// isolate，debug 模式模拟器上冻结 UI 数秒。边界只传 Uint8List（typed data
// 跨 isolate 为快速拷贝；WmBitmap 的 List<int> 装箱数组拷贝很慢）。
Uint8List _capturePipelineInBackground(List<Object?> args) {
  final rgba = args[0] as Uint8List;
  final width = args[1] as int;
  final height = args[2] as int;
  final watermark = args[3] as String;
  final algo = _selectAlgorithm(args[4] as String);

  // RGBA 字节 → WmBitmap（ARGB int）
  final n = width * height;
  final pixels = List<int>.filled(n, 0);
  for (int i = 0; i < n; i++) {
    pixels[i] =
        0xFF000000 | (rgba[i * 4] << 16) | (rgba[i * 4 + 1] << 8) | rgba[i * 4 + 2];
  }
  final wm = WmBitmap(width, height, pixels);
  final result = algo.embed(wm, watermark);

  // WmBitmap（ARGB int）→ RGBA 字节
  final out = Uint8List(n * 4);
  for (int i = 0; i < n; i++) {
    final argb = result.pixelAt(i);
    out[i * 4] = (argb >> 16) & 0xFF;
    out[i * 4 + 1] = (argb >> 8) & 0xFF;
    out[i * 4 + 2] = argb & 0xFF;
    out[i * 4 + 3] = 0xFF;
  }
  return out;
}

// 修复（2026-10-08 代码审查 C3）：遍历全部注册算法提取（每个约 40ms），
// 优先返回可解析为取证格式的结果——不再依赖用户记住嵌入时选的算法。
// RS fail-closed（C5/C7 修复）保证错误算法对含水印图也返回空串。
String _extractInBackground(List<Object?> args) {
  final src = args[0] as WmBitmap;
  String fallback = '';
  for (final a in defaultAlgorithms()) {
    String msg;
    try {
      msg = a.extract(src);
    } catch (_) {
      continue;
    }
    if (msg.isEmpty) continue;
    if (parseEvidence(msg) != null) return msg;
    if (fallback.isEmpty) fallback = msg; // 非取证格式的水印文本（兜底展示）
  }
  return fallback;
}

WmAlgorithm _selectAlgorithm(String id) {
  for (final a in defaultAlgorithms()) {
    if (a.id == id) return a;
  }
  return DctQimColorAlgorithm();
}

class DctWatermarkApp extends StatelessWidget {
  const DctWatermarkApp({super.key, required this.cameras});

  final List<CameraDescription> cameras;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'DCT 取证水印相机',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF00897B)),
        useMaterial3: true,
      ),
      home: HomeScreen(cameras: cameras),
    );
  }
}

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.cameras});

  final List<CameraDescription> cameras;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  // ---- 相机状态 ----
  CameraController? _camera;
  bool _cameraReady = false;
  String _editStatus = '正在启动相机…';
  bool _busy = false;
  ui.Image? _lastThumb; // 最近一次拍摄的缩略图（底部相册入口，iPhone 相机风格）

  // 当前水印算法（默认保色 QIM-DCT；可在设置面板切换）
  WmAlgorithm _algorithm = DctQimColorAlgorithm();

  @override
  void initState() {
    super.initState();
    _initCamera();
  }

  // 初始化后置摄像头
  Future<void> _initCamera() async {
    if (widget.cameras.isEmpty) {
      setState(() {
        _cameraReady = false;
        _editStatus = '未检测到相机';
      });
      return;
    }
    // 优先后置摄像头
    final desc = widget.cameras.firstWhere(
      (c) => c.lensDirection == CameraLensDirection.back,
      orElse: () => widget.cameras.first,
    );
    try {
      final controller = CameraController(desc, ResolutionPreset.high);
      await controller.initialize();
      if (!mounted) return;
      setState(() {
        _camera = controller;
        _cameraReady = true;
        _editStatus = '相机就绪，点击快门取证';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _cameraReady = false;
        _editStatus = '相机启动失败：$e';
      });
    }
  }

  // ---- 设备唯一标识：Android 用 ANDROID_ID，iOS 用 identifierForVendor ----
  // 修复（2026-10-08 代码审查 C2）：原实现用 device_info_plus 的 info.id，
  // 实际是 Build.ID（OS 构建号，同 ROM 设备共享、OTA 后变化），取证归因失效。
  // 现通过 platform channel（MainActivity.kt）读 Settings.Secure.ANDROID_ID。
  Future<String> _deviceId() async {
    try {
      if (defaultTargetPlatform == TargetPlatform.android) {
        final id = await const MethodChannel('dct_watermark_app/device_id')
            .invokeMethod<String>('getAndroidId');
        if (id != null && id.isNotEmpty) return id;
      } else if (defaultTargetPlatform == TargetPlatform.iOS) {
        final info = await DeviceInfoPlugin().iosInfo;
        final id = info.identifierForVendor; // 同一厂商应用共享，卸载重装可能变化
        if (id != null && id.isNotEmpty) return id;
      }
    } catch (_) {}
    return 'unknown-device';
  }

  // ---- 取证快门：拍摄 → 采集证据 → 嵌入水印 → 存相册 ----
  // 主 isolate 只做原生异步调用（解码/GPU 读回/PNG 编码/相册写入），
  // 全部像素级纯 Dart 循环都在 compute isolate（见 _capturePipelineInBackground）。
  Future<void> _captureAndWatermark() async {
    final camera = _camera;
    if (camera == null || !camera.value.isInitialized) {
      _toast('相机未就绪');
      return;
    }
    setState(() {
      _busy = true;
      _editStatus = '正在拍摄…';
    });
    try {
      // 1. 拍摄并解码（原生）
      final xfile = await camera.takePicture();
      final bytes = await xfile.readAsBytes();
      final image = await decodeImage(bytes);
      final rgba = await image.toByteData(format: ui.ImageByteFormat.rawRgba);
      if (rgba == null) {
        throw StateError('Failed to read image pixels');
      }
      final width = image.width;
      final height = image.height;
      image.dispose(); // 修复（C6）：临时解码用完即释放

      // 2. 采集取证证据（时间 + 经纬度 + 设备ID）
      final deviceId = await _deviceId();
      final pos = await _currentPosition();
      final evidence = Evidence(
        capturedAt: DateTime.now(),
        latitude: pos?.latitude,
        longitude: pos?.longitude,
        deviceId: deviceId,
        algorithmId: _algorithm.id,
      );
      // 容量上限按当前算法 + 拍摄尺寸计算（maxTextLength 只读宽高，空像素即可）
      final maxLen = _algorithm
          .maxTextLength(WmBitmap(width, height, const <int>[]));
      final watermark = evidence.toWatermarkString(maxLen: maxLen);

      // 3. 后台管线：转换 + 嵌入 + 转换（typed data 跨 isolate 快速拷贝）
      setState(() => _editStatus = '正在嵌入取证水印…');
      final outRgba = await compute(_capturePipelineInBackground, [
        rgba.buffer.asUint8List(rgba.offsetInBytes, rgba.lengthInBytes),
        width,
        height,
        watermark,
        _algorithm.id,
      ]);

      // 4. PNG 编码（引擎原生）→ 保存到系统相册（无损，保证事后可精确提取）
      final png = await rawRgbaToPng(outRgba, width, height);
      await Gal.putImageBytes(png);

      // 5. 更新底部相册入口缩略图（下采样小图，~30KB 原生内存）
      final oldThumb = _lastThumb;
      final newThumb = await makeThumbnail(outRgba, width, height);

      if (!mounted) {
        newThumb.dispose();
        return;
      }
      setState(() {
        _busy = false;
        _lastThumb = newThumb;
        _editStatus = '取证水印已嵌入并保存到相册';
      });
      if (oldThumb != null) {
        WidgetsBinding.instance.addPostFrameCallback((_) => oldThumb.dispose());
      }
      _toast('已保存：$watermark');
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _busy = false;
        _editStatus = '取证失败：$e';
      });
    }
  }

  // ---- 获取当前经纬度（失败返回 null）----
  Future<Position?> _currentPosition() async {
    try {
      bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
      if (!serviceEnabled) return null;
      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
      }
      if (permission == LocationPermission.denied ||
          permission == LocationPermission.deniedForever) {
        return null;
      }
      return await Geolocator.getCurrentPosition();
    } catch (_) {
      return null;
    }
  }

  void _toast(String msg) {
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
    }
  }

  // ================= UI：iPhone 相机风格布局 =================
  // 顶部纯取景（无控件）；底部独立控制条：相册缩略图(左) + 快门(中) + 设置(右)。
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          Column(
            children: [
              // 取景区：占满控制条以上的全部空间
              Expanded(child: _buildViewfinder()),
              // 底部控制条（iPhone 相机风格）
              SafeArea(
                top: false,
                child: _buildBottomBar(),
              ),
            ],
          ),
          // 处理中遮罩（阻断误触；Positioned.fill 保证铺满）
          if (_busy) const Positioned.fill(child: ColoredBox(color: Colors.black26)),
        ],
      ),
    );
  }

  // ---- 取景区：contain 完整视野，上对齐（黑边汇入底部控制区，iPhone 布局）----
  Widget _buildViewfinder() {
    final camera = _camera;
    if (camera != null && camera.value.isInitialized && _cameraReady) {
      // CameraValue.aspectRatio 是传感器方向的宽高比（横值）；CameraPreview
      // 内部在竖屏下按 1/aspectRatio 渲染并旋转画面。外层必须用同一纵向比。
      final isPortrait =
          MediaQuery.of(context).orientation == Orientation.portrait;
      final previewAspect = isPortrait
          ? 1 / camera.value.aspectRatio
          : camera.value.aspectRatio;
      return Align(
        alignment: Alignment.topCenter,
        child: AspectRatio(
          aspectRatio: previewAspect,
          child: CameraPreview(camera),
        ),
      );
    }
    return ColoredBox(
      color: Colors.black,
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.photo_camera_outlined,
                size: 64, color: Colors.white38),
            const SizedBox(height: 12),
            const Text('相机不可用',
                style: TextStyle(color: Colors.white54, fontSize: 16)),
          ],
        ),
      ),
    );
  }

  // ---- 底部控制条：相册缩略图(左) + 快门(中) + 设置(右) ----
  Widget _buildBottomBar() {
    return Container(
      color: Colors.black,
      padding: const EdgeInsets.fromLTRB(24, 6, 24, 14),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // 状态行（小字居中）
          SizedBox(
            height: 18,
            child: Center(
              child: Text(
                _editStatus,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(color: Colors.white54, fontSize: 12),
              ),
            ),
          ),
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              // 左：相册/验水印入口（拍摄后显示实拍缩略图）
              _thumbButton(),
              // 中：快门
              _buildShutter(),
              // 右：水印设置
              _barButton(
                icon: Icons.tune,
                onTap: _busy ? null : _showSettingsSheet,
              ),
            ],
          ),
        ],
      ),
    );
  }

  // ---- 相册入口：有实拍缩略图显示缩略图，否则显示占位图标 ----
  Widget _thumbButton() {
    final thumb = _lastThumb;
    return SizedBox(
      width: 56,
      height: 56,
      child: Material(
        color: Colors.white12,
        borderRadius: BorderRadius.circular(10),
        child: InkWell(
          borderRadius: BorderRadius.circular(10),
          onTap: _showVerifySheet,
          child: thumb != null
              ? ClipRRect(
                  borderRadius: BorderRadius.circular(10),
                  child: RawImage(image: thumb, fit: BoxFit.cover),
                )
              : const Icon(Icons.photo_library, color: Colors.white70, size: 26),
        ),
      ),
    );
  }

  // ---- 底部条圆形功能键 ----
  Widget _barButton({
    required IconData icon,
    VoidCallback? onTap,
  }) {
    return SizedBox(
      width: 56,
      height: 56,
      child: Material(
        color: Colors.white12,
        borderRadius: BorderRadius.circular(28),
        child: InkWell(
          borderRadius: BorderRadius.circular(28),
          onTap: onTap,
          child: Icon(icon, color: Colors.white, size: 26),
        ),
      ),
    );
  }

  // ---- 经典相机快门：外环 + 内芯，处理中显示进度 ----
  Widget _buildShutter() {
    final enabled = _cameraReady && !_busy;
    return GestureDetector(
      key: const ValueKey('shutter'),
      onTap: enabled ? _captureAndWatermark : null,
      child: Container(
        width: 74,
        height: 74,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          border: Border.all(color: Colors.white, width: 4),
        ),
        alignment: Alignment.center,
        child: _busy
            ? const SizedBox(
                width: 52,
                height: 52,
                child: CircularProgressIndicator(
                    color: Colors.white, strokeWidth: 3),
              )
            : Container(
                width: 58,
                height: 58,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: enabled ? Colors.white : Colors.white38,
                ),
              ),
      ),
    );
  }

  // ---- 水印设置面板（算法切换）----
  Future<void> _showSettingsSheet() {
    WmAlgorithm selected = _algorithm;
    return showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSheet) => SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text('水印设置',
                    style: Theme.of(ctx).textTheme.titleLarge),
                const SizedBox(height: 6),
                const Text(
                    '水印内容由拍摄现场自动采集（时间 / 经纬度 / 设备标识），'
                    '无需手动输入。此处仅选择嵌入算法。',
                    style: TextStyle(fontSize: 13, color: Colors.grey)),
                const SizedBox(height: 16),
                DropdownButtonFormField<String>(
                  value: selected.id,
                  decoration: const InputDecoration(
                    labelText: '水印算法',
                    border: OutlineInputBorder(),
                    prefixIcon: Icon(Icons.account_tree_outlined),
                  ),
                  items: [
                    for (final a in defaultAlgorithms())
                      DropdownMenuItem(value: a.id, child: Text(a.name)),
                  ],
                  onChanged: (id) {
                    if (id == null) return;
                    for (final a in defaultAlgorithms()) {
                      if (a.id == id) {
                        // 立即生效：父级 setState 更新取景层，setSheet 刷新面板说明
                        setState(() => _algorithm = a);
                        setSheet(() => selected = a);
                        break;
                      }
                    }
                  },
                ),
                const SizedBox(height: 12),
                Text(_algorithmHint(selected.id),
                    style: const TextStyle(fontSize: 13, color: Colors.grey)),
              ],
            ),
          ),
        ),
      ),
    );
  }

  static String _algorithmHint(String id) {
    switch (id) {
      case 'dct_qim_color':
        return '视觉无损（PSNR≈55dB，色彩零损失）；JPEG 转发存活至 Q60；容量大。'
            '日常取证推荐。';
      case 'dwt':
        return '速度最快、抗噪最强（noise≤10）；JPEG 压缩下较弱（仅 Q95 存活）。';
      case 'spread_spectrum':
        return '鲁棒性最强（noise≤15、JPEG Q70 存活）；容量较小。';
      default:
        return '';
    }
  }

  // ---- 验水印面板（独立 StatefulWidget：面板状态自管理）----
  Future<void> _showVerifySheet() {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (ctx) => const _VerifySheet(),
    );
  }

  @override
  void dispose() {
    _camera?.dispose();
    _lastThumb?.dispose();
    super.dispose();
  }
}

// ================= 验水印面板 =================
// 独立 StatefulWidget：bottom sheet 是独立路由，父 State 的 setState 不会
// 触发其重建——选图/提取/结果状态由面板自持，图像句柄随面板关闭释放。
class _VerifySheet extends StatefulWidget {
  const _VerifySheet();

  @override
  State<_VerifySheet> createState() => _VerifySheetState();
}

class _VerifySheetState extends State<_VerifySheet> {
  ui.Image? _inspectImage; // 待验图片预览
  WmBitmap? _inspectWm;
  String? _extractedText;
  String _inspectStatus = '选择一张带水印的图片进行验证';
  bool _busy = false;

  // ---- 从相册选图 ----
  Future<void> _pickInspectImage() async {
    setState(() {
      _busy = true;
      _inspectStatus = '载入图片…';
    });
    try {
      final picker = ImagePicker();
      // 修复（2026-10-08 代码审查 C1）：不传 maxWidth/maxHeight/imageQuality——
      // 任何重采样都会破坏 8×8 块对齐的 DCT 水印（App 自己拍的照片将无法验证）。
      // 嵌入端保存的是无损 PNG，原样读取即可精确提取。
      final xfile = await picker.pickImage(source: ImageSource.gallery);
      if (xfile == null) {
        setState(() {
          _busy = false;
          _inspectStatus = '已取消选择';
        });
        return;
      }
      final bytes = await xfile.readAsBytes();
      final image = await decodeImage(bytes);
      final wm = await uiImageToWmBitmap(image);
      // 修复（2026-10-08 代码审查 C6）：释放被替换的旧预览图（原生光栅内存，
      // 每张 ~4MB；postFrame 时序确保最后一帧渲染完成后再释放）
      final old = _inspectImage;
      setState(() {
        _busy = false;
        _inspectImage = image;
        _inspectWm = wm;
        _extractedText = null;
        _inspectStatus = '图片已载入，点击"提取水印"验证';
      });
      if (old != null) {
        WidgetsBinding.instance.addPostFrameCallback((_) => old.dispose());
      }
    } catch (e) {
      setState(() {
        _busy = false;
        _inspectStatus = '载入失败：$e';
      });
    }
  }

  // ---- 提取水印（后台遍历全部注册算法）----
  Future<void> _extractWatermark() async {
    final target = _inspectWm;
    if (target == null) {
      _toast('请先选择图片');
      return;
    }
    setState(() {
      _busy = true;
      _inspectStatus = '正在提取…';
    });
    try {
      final msg = await compute(_extractInBackground, [target]);
      setState(() {
        _busy = false;
        _extractedText = msg;
        _inspectStatus = msg.isEmpty ? '未检测到可提取的水印' : '提取完成';
      });
    } catch (e) {
      setState(() {
        _busy = false;
        _inspectStatus = '提取失败：$e';
      });
    }
  }

  void _toast(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  void dispose() {
    // 修复（C6）：面板关闭时释放原生图像句柄
    _inspectImage?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: MediaQuery.of(context).size.height * 0.85,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('验水印', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 4),
            const Text('从相册选择照片，提取其中的取证水印（自动尝试全部算法）',
                style: TextStyle(fontSize: 13, color: Colors.grey)),
            const SizedBox(height: 12),
            FilledButton.tonalIcon(
              onPressed: _busy ? null : _pickInspectImage,
              icon: const Icon(Icons.photo_library),
              label: const Text('从相册选择要验证的图片'),
              style: FilledButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 14)),
            ),
            const SizedBox(height: 12),
            Expanded(
              child: SingleChildScrollView(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    _ImagePreview(
                        image: _inspectImage,
                        hint: '从相册选择一张带水印的图片进行验证'),
                    const SizedBox(height: 12),
                    FilledButton.tonalIcon(
                      onPressed: _busy ? null : _extractWatermark,
                      icon: const Icon(Icons.manage_search),
                      label: const Text('提取水印'),
                      style: FilledButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 14)),
                    ),
                    const SizedBox(height: 12),
                    if (_extractedText != null) _buildExtractResult(),
                    _StatusLine(
                        icon: Icons.search,
                        text: _inspectStatus,
                        color: Colors.indigo),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildExtractResult() {
    final text = _extractedText!;
    if (text.isEmpty) {
      return Container(
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: const Color(0xFFFEF2F2),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: const Color(0xFFFCA5A5)),
        ),
        child: const Text('未检测到可提取的水印',
            style: TextStyle(fontSize: 15, color: Colors.redAccent)),
      );
    }

    // 识别算法
    final algoId = algorithmIdFromWatermark(text);
    String algoName = algoId;
    for (final a in defaultAlgorithms()) {
      if (a.id == algoId) {
        algoName = a.name;
        break;
      }
    }

    // 解析结构化字段
    final ev = parseEvidence(text);

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFFF0FDF4),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFF86EFAC)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.account_tree_outlined,
                  size: 16, color: Colors.teal),
              const SizedBox(width: 6),
              Text('算法：$algoName',
                  style: const TextStyle(
                      fontSize: 14, fontWeight: FontWeight.w600)),
            ],
          ),
          const SizedBox(height: 10),
          if (ev != null) ...[
            _fieldRow(Icons.schedule, '拍摄时间', _formatTime(ev.capturedAt)),
            if (ev.hasLocation)
              _fieldRow(Icons.place, '经纬度',
                  '${_fmtCoord(ev.latitude!)}, ${_fmtCoord(ev.longitude!)}'),
            _fieldRow(Icons.devices, '设备标识', ev.deviceId),
            const SizedBox(height: 8),
            const Divider(height: 1),
            const SizedBox(height: 8),
            const Text('原始水印内容：',
                style: TextStyle(fontSize: 12, color: Colors.grey)),
            const SizedBox(height: 4),
            Text(text,
                style:
                    const TextStyle(fontSize: 13, fontFamily: 'monospace')),
          ] else ...[
            const Text('提取到的水印内容（非取证格式）：',
                style: TextStyle(fontSize: 13, color: Colors.grey)),
            const SizedBox(height: 4),
            Text(text,
                style: const TextStyle(
                    fontSize: 15, fontWeight: FontWeight.w600)),
          ],
        ],
      ),
    );
  }

  Widget _fieldRow(IconData icon, String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 16, color: Colors.teal),
          const SizedBox(width: 6),
          SizedBox(
              width: 70,
              child: Text(label,
                  style: const TextStyle(fontSize: 13, color: Colors.grey))),
          Expanded(
            child: Text(value,
                style: const TextStyle(
                    fontSize: 14, fontWeight: FontWeight.w500)),
          ),
        ],
      ),
    );
  }

  String _formatTime(DateTime t) {
    String pad(int v) => v.toString().padLeft(2, '0');
    return '${t.year}-${pad(t.month)}-${pad(t.day)} '
        '${pad(t.hour)}:${pad(t.minute)}';
  }

  String _fmtCoord(double v) => v.toStringAsFixed(6);
}

// ---- 图片预览 ----
class _ImagePreview extends StatelessWidget {
  const _ImagePreview({required this.image, required this.hint});

  final ui.Image? image;
  final String hint;

  @override
  Widget build(BuildContext context) {
    if (image == null) {
      return Container(
        height: 220,
        decoration: BoxDecoration(
          color: Colors.grey[200],
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: Colors.grey[300]!),
        ),
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.image_outlined, size: 46, color: Colors.grey[400]),
              const SizedBox(height: 8),
              Text(hint, style: TextStyle(color: Colors.grey[500])),
            ],
          ),
        ),
      );
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: AspectRatio(
        aspectRatio: image!.width / image!.height,
        child: RawImage(image: image, fit: BoxFit.contain),
      ),
    );
  }
}

// ---- 状态行 ----
class _StatusLine extends StatelessWidget {
  const _StatusLine(
      {required this.icon, required this.text, required this.color});

  final IconData icon;
  final String text;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 16, color: color),
        const SizedBox(width: 6),
        Expanded(
          child: Text(text,
              style: TextStyle(color: color, fontSize: 13, height: 1.4)),
        ),
      ],
    );
  }
}
