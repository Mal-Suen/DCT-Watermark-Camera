# DCT-Watermark-Camera

<div align="center">

**A forensic watermark camera — the moment you press the shutter, time, place and device are written into the photo itself**

**DCT 取证水印相机：按下快门的那一刻，时间、地点、设备已被写入照片本身**

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

### Overview

**DCT-Watermark-Camera** is a cross-platform Flutter rewrite of the original Android DCT watermark camera, repositioned as a **pure forensic watermark camera**: it only watermarks photos captured in-app, and the watermark content is assembled from objective facts of the capture moment — algorithm id, timestamp, GPS coordinates, and a per-device identifier. Photos stay traceable to their origin and can be verified later by anyone holding the app.

### Core Features

| Feature | What it does |
|---------|--------------|
| **Forensic capture** | Live camera view; the shutter automatically collects timestamp + GPS + device ID, embeds them as an invisible watermark, and saves the photo to the system gallery |
| **Verification tab** | Pick any image from the gallery and extract its watermark; structured display of algorithm / time / coordinates / device; tries every registered algorithm automatically — no manual matching |
| **Three lossless algorithms** | Switchable in the UI (see table below); all preserve full color (chroma untouched, PSNR 47-55 dB) |
| **Reed-Solomon error correction** | Fail-closed decoding: a clean or corrupted image returns an empty string instead of garbage — "no watermark" is a reliable verdict |
| **Non-blocking UI** | Embedding and extraction run in background isolates |

### Watermark Algorithms

| Algorithm | Strengths |
|-----------|-----------|
| **QIM-DCT (color-preserving, default)** | Highest fidelity (PSNR ~55 dB, zero chroma loss), large capacity |
| **DWT (Haar)** | Strongest noise tolerance (noise ≤ 10), fastest |
| **Spread spectrum** | Most robust (noise ≤ 15), smaller capacity |

### How It Works & Honest Limitations

- **The watermark lives in frequency-domain coefficients** (8×8 DCT blocks / Haar subbands). It survives the app's own lossless PNG round-trip — which is exactly why the app saves PNG. Re-compression by third-party apps (JPEG re-encode, chat-app transfer) can damage it.
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
flutter test                   # 9 tests: round-trips, clean-image verdict, UI smoke
```

Requires Flutter 3.29+; Android builds need JDK 17, NDK 27.0.12077973, Kotlin 2.2.0.

### Usage

1. Open the **取证拍照** (capture) tab — the camera preview starts; pick an algorithm if you don't want the default.
2. Press the shutter — the app collects time/position/device, embeds the watermark, and saves the photo to the gallery (a toast shows the embedded string).
3. Later, open the **验水印** (verify) tab, pick that photo from the gallery, tap **提取水印** — the structured fields (algorithm, capture time, coordinates, device) are displayed.

### Project Structure

```
DCT-Watermark-Camera/
├── lib/
│   ├── main.dart               # App entry: capture/verify dual-tab UI, camera, gallery
│   ├── evidence_collector.dart # Evidence model: assemble & parse the watermark string
│   ├── image_codec.dart        # ui.Image <-> bitmap bridging, PNG codec
│   └── algorithm/              # Watermark algorithms (pure Dart, no UI deps)
│       ├── algorithm.dart      # Barrel export + defaultAlgorithms() registry
│       ├── dct_qim_color.dart  # QIM-DCT color-preserving (default)
│       ├── dwt_algorithm.dart  # DWT domain
│       ├── spread_spectrum.dart# Spread spectrum
│       ├── color_space.dart    # Reversible RGB <-> YCoCg transform
│       ├── reedsolomon.dart    # RS error correction
│       └── ...                 # Legacy compat algorithm, math utilities
├── test/                       # Unit & end-to-end round-trip tests
├── bin/                        # Diagnostic scripts (dart run bin/...)
└── android/ ios/ web/ windows/ # Platform projects
```

---

<a name="中文"></a>
## 中文

### 概述

**DCT-Watermark-Camera** 是原 Android DCT 水印相机的 Flutter 跨平台重构版，定位为**纯取证水印相机**：只给现场新拍摄的照片加水印，水印内容由拍摄时刻的客观事实构成——算法标识、时间戳、GPS 经纬度、设备唯一标识。照片来源可追溯，任何人可事后验证。

### 核心功能

| 功能 | 说明 |
|------|------|
| **取证拍照** | 相机实时取景；快门自动采集 时间+定位+设备ID，嵌入不可见水印，保存到系统相册 |
| **验水印** | 从相册选图提取水印；结构化展示 算法/时间/坐标/设备；自动遍历全部注册算法，无需手动匹配 |
| **三种无损算法** | UI 可切换（见下表）；全部保留完整色彩（色度不动，PSNR 47-55 dB） |
| **Reed-Solomon 纠错** | fail-closed 解码：干净图/损坏图返回空串而非乱码——"无水印"是可靠判定 |
| **UI 不阻塞** | 嵌入与提取均在后台 isolate 执行 |

### 水印算法

| 算法 | 特点 |
|------|------|
| **QIM-DCT 保色（默认）** | 保真度最高（PSNR 约 55 dB、色彩零损失），容量大 |
| **DWT 域** | 抗噪最强（noise≤10），速度最快 |
| **扩频** | 鲁棒性最强（noise≤15），容量较小 |

### 工作原理与诚实声明

- **水印承载在频域系数**（8×8 DCT 块 / Haar 子带）。它能完好通过本 App 自身的无损 PNG 往返——这正是 App 存 PNG 的原因。第三方重压缩（JPEG 再编码、聊天工具传输）可能损坏水印。
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
flutter test                   # 9 项测试：往返、干净图判定、UI 冒烟
```

要求 Flutter 3.29+；Android 构建需 JDK 17、NDK 27.0.12077973、Kotlin 2.2.0。

### 使用流程

1. 打开**取证拍照** Tab——相机取景启动；不用默认算法的话先在下拉里选一个。
2. 按快门——App 采集时间/定位/设备ID，嵌入水印，保存到相册（Toast 显示嵌入内容）。
3. 事后打开**验水印** Tab，从相册选那张照片，点**提取水印**——结构化字段（算法、拍摄时间、坐标、设备）即被展示。

### 项目结构

```
DCT-Watermark-Camera/
├── lib/
│   ├── main.dart               # 应用入口：取证拍照/验水印双 Tab、相机、相册
│   ├── evidence_collector.dart # 取证证据模型：水印串组装与解析
│   ├── image_codec.dart        # ui.Image 与位图互转、PNG 编解码
│   └── algorithm/              # 水印算法库（纯 Dart，无 UI 依赖）
│       ├── algorithm.dart      # 导出入口 + defaultAlgorithms() 注册表
│       ├── dct_qim_color.dart  # QIM-DCT 保色（默认）
│       ├── dwt_algorithm.dart  # DWT 域
│       ├── spread_spectrum.dart# 扩频
│       ├── color_space.dart    # RGB 与 YCoCg 可逆变换
│       ├── reedsolomon.dart    # RS 纠错
│       └── ...                 # 旧版兼容算法、数学工具
├── test/                       # 单元与端到端往返测试
├── bin/                        # 诊断脚本（dart run bin/...）
└── android/ ios/ web/ windows/ # 各平台工程
```

---
