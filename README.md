# DCT-Watermark-Camera

<div align="center">

**Forensic watermark camera — 3 visually-lossless algorithms (QIM-DCT/DWT/spread spectrum), zero color loss, RS error correction, survives JPEG re-sharing**

**取证水印相机：QIM-DCT/DWT/扩频三种视觉无损水印算法，色彩零损失，Reed-Solomon 纠错，JPEG 转发后仍可提取**

[![Flutter](https://img.shields.io/badge/Flutter-3.29+-02569B.svg)](https://flutter.dev)
[![Dart](https://img.shields.io/badge/Dart-3.7+-0175C2.svg)](https://dart.dev)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS-lightgrey.svg)](https://github.com/Mal-Suen/DCT-Watermark-Camera)

</div>

---

## Table of Contents / 目录

- [English](#english)
- [中文](#中文)

---

<a name="english"></a>
## English

### Algorithms

Three visually-lossless watermark algorithms, switchable in the UI. All embed into the luminance channel only (via a reversible YCoCg transform), leaving chroma untouched:

| Algorithm | Technique | Properties |
|-----------|-----------|------------|
| **QIM-DCT (color-preserving, default)** | 8×8-block DCT mid-frequency QIM quantization | Zero chroma loss, PSNR ≈ 55 dB, large capacity |
| **DWT (Haar)** | Haar wavelet HL-subband QIM | Strongest noise tolerance (noise ≤ 10), fastest |
| **Spread spectrum** | PN-sequence spreading + centered correlation detection | Most robust (noise ≤ 15, survives JPEG Q70) |

Shared by all three:

- **Reed-Solomon error correction (fail-closed)** — decoding failure returns an empty string: "no watermark" is a reliable verdict, never garbage
- **Header protection** — the length prefix is stored in 3 copies with per-bit majority voting: no single-bit point of failure
- **Measured JPEG robustness** — the default algorithm survives re-compression down to **Q60** (typical chat-app compression), so the watermark remains extractable after the photo is re-shared

### Features

- **Forensic capture** — the shutter automatically collects timestamp + GPS coordinates + device ID (`ANDROID_ID` / `identifierForVendor`) and embeds them as an invisible watermark; the photo is saved to the system gallery as lossless PNG
- **Verification** — pick any image from the gallery; structured display of algorithm / capture time / coordinates / device; every registered algorithm is tried automatically, no manual matching
- **iPhone-style camera layout** — full-screen viewfinder, bottom control bar (gallery thumbnail / shutter / settings)
- **Non-blocking UI** — all pixel-level work (conversion, embedding, extraction, PNG encoding) runs in background isolates

### How It Works & Honest Limitations

- **The watermark lives in frequency-domain coefficients** (8×8 DCT blocks / Haar subbands). It survives the app's own lossless PNG round-trip — which is exactly why the app saves PNG. Measured JPEG survival is down to Q60; stronger compression or repeated re-encoding can still damage it.
- **Device identifier** is `Settings.Secure.ANDROID_ID` on Android (stable until factory reset; per-app on Android 8+) and `identifierForVendor` on iOS (changes on reinstall). It identifies a device, not a person.
- **Coordinates** require location permission; if denied, the watermark simply omits the `g` segment.
- **Not interoperable with the original Android app**: the original Java implementation is preserved in git history (commits up to `e0073cf`) but is no longer selectable in the UI.
- **iOS builds require macOS + Xcode**; this repository is developed on Windows, so only the Android target is built and tested here.

### Build & Run

```bash
git clone https://github.com/Mal-Suen/DCT-Watermark-Camera.git
cd DCT-Watermark-Camera
flutter pub get
flutter run                    # or: flutter build apk --release
flutter test                   # 11 tests: round-trips, clean-image verdict, JPEG robustness, UI smoke
```

Requires Flutter 3.29+; Android builds need JDK 17, NDK 27.0.12077973, Kotlin 2.2.0.

### Usage

1. Launch the app — the full-screen camera viewfinder starts. Tap the settings button (bottom-right of the control bar) to pick an algorithm if you don't want the default.
2. Press the shutter — the app collects time/position/device, embeds the watermark, and saves the photo to the gallery (a toast shows the embedded string); the bottom-left thumbnail updates to the latest capture.
3. Tap the bottom-left thumbnail to open the verification sheet, pick that photo from the gallery, tap **提取水印** — the structured fields (algorithm, capture time, coordinates, device) are displayed.

### Project Structure

```
DCT-Watermark-Camera/
├── lib/
│   ├── main.dart               # App entry: iPhone-style camera UI, capture pipeline, verify sheet
│   ├── evidence_collector.dart # Evidence model: assemble & parse the watermark string
│   ├── image_codec.dart        # ui.Image <-> bitmap bridging, PNG codec, thumbnails
│   └── algorithm/              # Watermark algorithms (pure Dart, no UI deps)
│       ├── algorithm.dart      # Barrel export + defaultAlgorithms() registry
│       ├── dct_qim_color.dart  # QIM-DCT color-preserving (default)
│       ├── dwt_algorithm.dart  # DWT domain
│       ├── spread_spectrum.dart# Spread spectrum
│       ├── color_space.dart    # Reversible RGB <-> YCoCg transform
│       ├── reedsolomon.dart    # RS error correction
│       └── ...                 # Legacy compat algorithm, math utilities
├── test/                       # Unit & end-to-end tests (incl. JPEG robustness regression)
├── bin/                        # Diagnostic scripts (dart run bin/...)
└── android/ ios/ web/ windows/ # Platform projects
```

---

<a name="中文"></a>
## 中文

### 算法

三种视觉无损水印算法，UI 可切换。全部只嵌入亮度通道（经 YCoCg 可逆变换），色度完全不动：

| 算法 | 技术 | 特性 |
|------|------|------|
| **QIM-DCT 保色（默认）** | 8×8 块 DCT 中频 QIM 量化 | 色彩零损失，PSNR ≈ 55 dB，容量大 |
| **DWT 域** | Haar 小波 HL 子带 QIM | 抗噪最强（noise ≤ 10），速度最快 |
| **扩频** | PN 序列扩频 + 中心化相关检测 | 鲁棒性最强（noise ≤ 15，JPEG Q70 存活） |

三算法共享：

- **Reed-Solomon 纠错（fail-closed）**——解码失败返回空串："无水印"是可靠判定，绝不返回乱码
- **头部保护**——长度前缀 3 份副本按位多数表决：无单 bit 单点故障
- **JPEG 鲁棒性实测**——默认算法存活至 **Q60**（聊天工具标准压缩档），照片经转发后水印仍可提取

### 特性

- **取证拍照**——快门自动采集 拍摄时间 + GPS 经纬度 + 设备唯一标识（`ANDROID_ID` / `identifierForVendor`），嵌入不可见水印，以无损 PNG 保存到系统相册
- **验水印**——相册选图提取，结构化展示 算法/拍摄时间/坐标/设备；自动遍历全部注册算法，无需手动匹配
- **iPhone 相机布局**——全屏取景 + 底部控制条（相册缩略图 / 快门 / 设置）
- **UI 不阻塞**——全部像素级工作（转换、嵌入、提取、PNG 编码）在后台 isolate 执行

### 工作原理与诚实声明

- **水印承载在频域系数**（8×8 DCT 块 / Haar 子带）。它能完好通过本 App 自身的无损 PNG 往返——这正是 App 存 PNG 的原因。JPEG 存活实测至 Q60；更强的压缩或反复重编码仍可能损坏水印。
- **设备标识**：Android 用 `Settings.Secure.ANDROID_ID`（恢复出厂前恒定；Android 8+ 按应用隔离），iOS 用 `identifierForVendor`（卸载重装会变）。它标识的是设备，不是人。
- **经纬度**需要定位权限；被拒绝时水印自动省略 `g` 段。
- **与原 Android 版不互通**：原 Java 实现保留在 git 历史（`e0073cf` 及之前），但已不在 UI 可选列表中。
- **iOS 构建需要 macOS + Xcode**；本仓库在 Windows 上开发，仅构建和测试 Android 目标。

### 构建与运行

```bash
git clone https://github.com/Mal-Suen/DCT-Watermark-Camera.git
cd DCT-Watermark-Camera
flutter pub get
flutter run                    # 或：flutter build apk --release
flutter test                   # 11 项测试：往返、干净图判定、JPEG 鲁棒性、UI 冒烟
```

要求 Flutter 3.29+；Android 构建需 JDK 17、NDK 27.0.12077973、Kotlin 2.2.0。

### 使用流程

1. 启动 App——全屏相机取景。不用默认算法的话，点底部控制条右侧的设置键选择算法。
2. 按快门——App 采集时间/定位/设备ID，嵌入水印，保存到相册（Toast 显示嵌入内容）；底部左侧缩略图更新为最新拍摄。
3. 点底部左侧缩略图打开验水印面板，从相册选那张照片，点**提取水印**——结构化字段（算法、拍摄时间、坐标、设备）即被展示。

### 项目结构

```
DCT-Watermark-Camera/
├── lib/
│   ├── main.dart               # 应用入口：iPhone 相机布局、拍摄管线、验水印面板
│   ├── evidence_collector.dart # 取证证据模型：水印串组装与解析
│   ├── image_codec.dart        # ui.Image 与位图互转、PNG 编解码、缩略图
│   └── algorithm/              # 水印算法库（纯 Dart，无 UI 依赖）
│       ├── algorithm.dart      # 导出入口 + defaultAlgorithms() 注册表
│       ├── dct_qim_color.dart  # QIM-DCT 保色（默认）
│       ├── dwt_algorithm.dart  # DWT 域
│       ├── spread_spectrum.dart# 扩频
│       ├── color_space.dart    # RGB 与 YCoCg 可逆变换
│       ├── reedsolomon.dart    # RS 纠错
│       └── ...                 # 旧版兼容算法、数学工具
├── test/                       # 单元与端到端测试（含 JPEG 鲁棒性回归）
├── bin/                        # 诊断脚本（dart run bin/...）
└── android/ ios/ web/ windows/ # 各平台工程
```

---
