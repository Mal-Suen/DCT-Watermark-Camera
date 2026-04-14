# DCT-Watermark-Camera 关键修复指南

## 已自动修复的问题

### ✅ 1. Qt.java - 量化方法名修复
- **文件**: `app/src/main/java/xju/dctcamera/utils/dct/net/watermark/Qt.java`
- **修复内容**:
  - 添加新方法名 `quantize()` 和 `dequantize()` 替代原有混淆的方法名
  - 保留旧方法名作为 @Deprecated 以保持向后兼容
  - 将 N 常量改为 `static final`

### ✅ 2. .gitignore 配置
- **文件**: `.gitignore`
- **修复内容**: 排除 APK、build、IDE配置、性能分析文件等

## 需要手动修复的关键问题

### 🔧 3. Watermark.java - 死循环修复 (Critical)

**文件位置**: `app/src/main/java/xju/dctcamera/utils/dct/net/watermark/Watermark.java`

**问题行**: 366, 425, 676, 728

**修复方法**: 
在以下4个位置的 `while(true)` 循环中添加最大尝试次数保护:

#### 位置 1: 第366行附近 (水印嵌入随机化)
```java
// 原代码:
while (true) {
    c1 = r1.nextInt(128 * 128);
    if (tmp1[c1] == 0) {
        break;
    }
}

// 修改为:
int maxAttempts = 128 * 128;
int attempts = 0;
while (attempts < maxAttempts) {
    attempts++;
    c1 = r1.nextInt(128 * 128);
    if (tmp1[c1] == 0) {
        break;
    }
}
if (attempts >= maxAttempts) {
    Log.e("Watermark", "Watermark embedding failed: no available positions");
    return null;
}
```

#### 位置 2: 第425行附近 (提取水印随机化)
同样的修复方式,添加最大尝试次数

#### 位置 3: 第676行 (字符串验证循环)
```java
// 原代码:
while (true) {
    // 查找字符
}

// 修改为:
for (int idx = 0; idx < s.length(); idx++) {
    // 使用 for 循环替代 while(true)
}
```

#### 位置 4: 第728行
同样的修复方式

### 🔧 4. DCT.java 和 DCT2.java - 静态缓存优化 (Critical)

**文件位置**: 
- `app/src/main/java/xju/dctcamera/utils/dct/net/watermark/DCT.java`
- `app/src/main/java/xju/dctcamera/utils/dct/net/watermark/DCT2.java`

**问题**: 每次创建 DCT 对象都会重新计算 64 次三角函数

**修复方法**:

#### DCT.java 修改:
```java
public class DCT {
    static int N = 8;
    static float C[][];  // 修改为静态
    static float Ct[][]; // 修改为静态
    
    // 添加静态初始化块
    static {
        C = new float[N][N];
        Ct = new float[N][N];
        for (int i = 0; i < N; i++) {
            for (int k = 0; k < N; k++) {
                if (i == 0)
                    C[i][k] = (float) Math.sqrt(1.0 / N);
                else
                    C[i][k] = (float) Math.sqrt(2.0 / N) * Math.cos(i * Math.PI * k / (2 * N));
                Ct[k][i] = C[i][k];
            }
        }
    }
    
    // 构造函数不再重复计算
    DCT() {}
}
```

#### DCT2.java 同样修改:
```java
public class DCT2 {
    static int N = 4;
    static float C[][];
    static float Ct[][];
    
    static {
        // 与 DCT.java 相同的静态初始化逻辑
    }
    
    DCT2() {}
}
```

### 🔧 5. FolderManager.java - 内存泄漏修复 (Critical)

**文件位置**: `app/src/main/java/xju/dctcamera/manager/FolderManager.java`

**修复方法**:
```java
// 原代码 (第17行附近):
private static FolderManager instance;
private Context context;

public static synchronized FolderManager getInstance(Context context) {
    if (instance == null) {
        instance = new FolderManager(context);
    }
    return instance;
}

// 修改为:
private static FolderManager instance;
private Context applicationContext;  // 使用 ApplicationContext

public static synchronized FolderManager getInstance(Context context) {
    if (instance == null) {
        instance = new FolderManager(context.getApplicationContext());
    }
    return instance;
}
```

### 🔧 6. 权限检查添加 (Critical)

**需要添加运行时权限的 Activity**:
- `GetPhotosActivity.java`
- `PhotoPreviewActivity.java`
- `MainActivity.java`

**修复方法** - 在读取/写入存储前添加:
```java
// 在 Activity 开头添加权限检查
private void checkStoragePermission() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this,
            new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 
            REQUEST_CODE_STORAGE);
    }
}

@Override
public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_CODE_STORAGE) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // 权限已授予,继续操作
        } else {
            Toast.makeText(this, "需要存储权限才能访问照片", Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
```

### 🔧 7. Bitmap 内存管理 (Critical)

**文件**: `PhotoPreviewActivity.java`

**在切换图片时添加**:
```java
private Bitmap currentBitmap;

private void loadImage(String path) {
    // 释放旧 Bitmap
    if (currentBitmap != null && !currentBitmap.isRecycled()) {
        currentBitmap.recycle();
        currentBitmap = null;
    }
    
    // 加载新图片
    currentBitmap = BitmapFactory.decodeFile(path);
    imageView.setImageBitmap(currentBitmap);
}

// 在 onDestroy 中清理
@Override
protected void onDestroy() {
    super.onDestroy();
    if (currentBitmap != null && !currentBitmap.isRecycled()) {
        currentBitmap.recycle();
        currentBitmap = null;
    }
}
```

## 建议的后续优化

### 性能优化 (可选)
1. 使用 `Bitmap.copyPixelsToBuffer()` 替代逐像素操作
2. 考虑使用 RenderScript 或 NDK 进行 DCT 计算
3. 添加图片尺寸限制,避免处理超大图片

### 代码质量优化
1. 将 `StringBuffer` 替换为 `StringBuilder`
2. 使用泛型替代 raw type Vector
3. 添加异常日志记录而不是静默吞没

### 架构优化
1. 移动 `apshare` 和 `wxapi` 到 `share/` 包
2. 将 ShareSDK 相关代码隔离到独立模块
3. 添加核心算法的单元测试
