// main.dart — DCT 数字水印相机（Flutter 跨平台版）· 界面 v2
//
// 双 Tab：📷 取证拍照(嵌入) / 🔍 验水印(提取)
//  - 取证拍照：相机实时取景 → 快门自动采集(时间+经纬度+设备ID)组装水印 → 嵌入 → 存相册
//  - 提取：可打开任意图片 → 算法解析 → 显示水印内容（结构化字段）
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
  // 预取相机列表（供取证拍照 Tab 使用）；失败时传空，UI 显示"相机不可用"
  List<CameraDescription> cameras = const [];
  try {
    cameras = await availableCameras();
  } catch (_) {}
  runApp(DctWatermarkApp(cameras: cameras));
}

// ---- 后台 isolate 计算函数（避免阻塞 UI 主线程）----
// 根据算法 id 从注册表选中对应实现，分派嵌入/提取。后续新增算法自动生效。
WmBitmap _embedInBackground(List<Object?> args) {
  final algo = _selectAlgorithm(args[2] as String);
  return algo.embed(args[0] as WmBitmap, args[1] as String);
}

// 修复（2026-10-08 代码审查 C3）：遍历全部注册算法提取（每个约 40ms），
// 优先返回可解析为取证格式的结果——不再依赖拍照 Tab 的算法下拉选择
// （原实现用当前选中算法提取，切错算法会把有效水印误报为"无水印"）。
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
      title: 'DCT 数字水印相机',
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
  // ---- 取证拍照模式状态 ----
  CameraController? _camera; // 相机控制器
  bool _cameraReady = false; // 相机是否初始化成功
  ui.Image? _editPreview; // 拍摄/嵌入后预览
  WmBitmap? _editResult; // 嵌入后的水印图
  String _editStatus = '正在启动相机…';

  // ---- 提取模式状态 ----
  ui.Image? _inspectImage; // 待验图片预览
  WmBitmap? _inspectWm;
  String _inspectStatus = '选择一张带水印的图片进行验证';
  String? _extractedText;

  // ---- 共享 ----
  bool _busy = false;

  // 当前水印算法（默认保色 QIM-DCT；可在 UI 下拉切换）
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

  // ---- 提取：从相册选图 ----
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

  // ---- 取证快门：拍摄 → 采集证据 → 嵌入水印 → 存相册 ----
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
      // 1. 拍摄
      final xfile = await camera.takePicture();
      final bytes = await xfile.readAsBytes();
      final image = await decodeImage(bytes);
      final wm = await uiImageToWmBitmap(image);
      image.dispose(); // 修复（C6）：临时解码用完即释放（像素已拷入 WmBitmap）

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
      // 容量上限按当前算法 + 拍摄图像尺寸计算
      final maxLen = _algorithm.maxTextLength(wm);
      final watermark = evidence.toWatermarkString(maxLen: maxLen);

      // 3. 后台嵌入
      setState(() => _editStatus = '正在嵌入取证水印…');
      final result =
          await compute(_embedInBackground, [wm, watermark, _algorithm.id]);
      final uiImg = await wmBitmapToUiImage(result);

      // 4. 保存到系统相册
      final png = await wmBitmapToPng(result);
      await Gal.putImageBytes(png);

      if (!mounted) return;
      // 修复（C6）：释放被替换的旧拍摄预览图
      final oldPreview = _editPreview;
      setState(() {
        _editResult = result;
        _editPreview = uiImg;
        _busy = false;
        _editStatus = '取证水印已嵌入并保存到相册';
      });
      if (oldPreview != null) {
        WidgetsBinding.instance
            .addPostFrameCallback((_) => oldPreview.dispose());
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

  // ---- 提取 ----
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
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
    }
  }

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('DCT 数字水印相机'),
          bottom: const TabBar(
            tabs: [
              Tab(icon: Icon(Icons.photo_camera), text: '取证拍照'),
              Tab(icon: Icon(Icons.search), text: '验水印（提取）'),
            ],
          ),
        ),
        body: Stack(
          children: [
            TabBarView(
              children: [
                _buildEditorTab(),
                _buildInspectorTab(),
              ],
            ),
            if (_busy) const _BusyOverlay(),
          ],
        ),
      ),
    );
  }

  // ---- 取证拍照 Tab ----
  Widget _buildEditorTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // 相机实时取景（或拍摄结果预览）
          _buildCameraView(),
          const SizedBox(height: 14),

          // ---- 算法选择 ----
          DropdownButtonFormField<String>(
            value: _algorithm.id,
            decoration: const InputDecoration(
              labelText: '水印算法',
              border: OutlineInputBorder(),
              prefixIcon: Icon(Icons.account_tree_outlined),
            ),
            items: [
              for (final a in defaultAlgorithms())
                DropdownMenuItem(value: a.id, child: Text(a.name)),
            ],
            onChanged: _busy
                ? null
                : (id) {
                    if (id != null) {
                      for (final a in defaultAlgorithms()) {
                        if (a.id == id) {
                          setState(() => _algorithm = a);
                          break;
                        }
                      }
                    }
                  },
          ),
          const SizedBox(height: 12),

          // ---- 快门 ----
          FilledButton.icon(
            onPressed: (_busy || !_cameraReady) ? null : _captureAndWatermark,
            icon: const Icon(Icons.photo_camera),
            label: const Text('快门取证（拍摄+自动加水印）'),
            style: FilledButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 18)),
          ),
          const SizedBox(height: 12),

          // ---- 拍摄结果提示 ----
          if (_editResult != null)
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: const Color(0xFFFFF7ED),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: const Color(0xFFFDBA74)),
              ),
              child: const Text('已自动嵌入取证水印并保存到系统相册',
                  style: TextStyle(fontWeight: FontWeight.w600)),
            ),
          const SizedBox(height: 14),
          _StatusLine(
              icon: Icons.info_outline, text: _editStatus, color: Colors.teal),
        ],
      ),
    );
  }

  // ---- 相机视图：就绪时实时取景，否则显示拍摄结果/提示 ----
  Widget _buildCameraView() {
    final camera = _camera;
    if (camera != null && camera.value.isInitialized && _cameraReady) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: AspectRatio(
          aspectRatio: camera.value.aspectRatio,
          child: CameraPreview(camera),
        ),
      );
    }
    // 相机不可用或已拍摄：显示结果预览
    return _ImagePreview(
      image: _editPreview,
      hint: _cameraReady ? '点击快门拍摄取证' : '相机不可用',
    );
  }

  // ---- 提取 Tab ----
  Widget _buildInspectorTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // 提取只从相册选图：拍照所得照片必然无水印，故无拍照按钮
          FilledButton.tonalIcon(
            onPressed: _busy ? null : _pickInspectImage,
            icon: const Icon(Icons.photo_library),
            label: const Text('从相册选择要验证的图片'),
            style: FilledButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 16)),
          ),
          const SizedBox(height: 14),
          _ImagePreview(image: _inspectImage, hint: '从相册选择一张带水印的图片进行验证'),
          const SizedBox(height: 14),
          FilledButton.tonalIcon(
            onPressed: _busy ? null : _extractWatermark,
            icon: const Icon(Icons.manage_search),
            label: const Text('提取水印'),
            style: FilledButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 16)),
          ),
          const SizedBox(height: 14),
          if (_extractedText != null) _buildExtractResult(),
          _StatusLine(
              icon: Icons.search, text: _inspectStatus, color: Colors.indigo),
        ],
      ),
    );
  }

  Widget _buildExtractResult() {
    final text = _extractedText!;
    if (text.isEmpty) {
      return Container(
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
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFFF0FDF4),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFF86EFAC)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 算法标识
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
            // 结构化字段
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
                style: const TextStyle(
                    fontSize: 13, fontFamily: 'monospace')),
          ] else ...[
            // 非取证格式，仅显示原始文本
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

  // 结构化字段行
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

  @override
  void dispose() {
    _camera?.dispose();
    // 修复（C6）：释放状态持有的原生图像句柄
    _editPreview?.dispose();
    _inspectImage?.dispose();
    super.dispose();
  }
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

// ---- 忙碌遮罩 ----
class _BusyOverlay extends StatelessWidget {
  const _BusyOverlay();

  @override
  Widget build(BuildContext context) {
    return Positioned.fill(
      child: Container(
        color: Colors.black38,
        alignment: Alignment.center,
        child: const Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 12),
            Text('处理中…', style: TextStyle(color: Colors.white)),
          ],
        ),
      ),
    );
  }
}