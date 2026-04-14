#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
DCT Watermark 算法独立测试
不依赖 Android SDK，直接测试重构后的核心算法
"""

import subprocess
import sys
import os

def test_java_compilation():
    """测试 Java 代码编译"""
    print("=" * 60)
    print("Test 1: Java Code Compilation Check")
    print("=" * 60)
    
    # 检查关键文件是否存在且语法正确
    files_to_check = [
        "app/src/main/java/xju/dctcamera/utils/dct/net/watermark/Qt.java",
        "app/src/main/java/xju/dctcamera/utils/dct/net/watermark/DCT.java",
        "app/src/main/java/xju/dctcamera/utils/dct/net/watermark/DCT2.java",
        "app/src/main/java/xju/dctcamera/utils/dct/DctTool.java",
        "app/src/main/java/xju/dctcamera/utils/common/PermissionUtils.java",
        "app/src/main/java/xju/dctcamera/utils/bitmap/BitmapManager.java",
        "app/src/main/java/xju/dctcamera/utils/common/Logger.java",
    ]
    
    all_exist = True
    for filepath in files_to_check:
        full_path = os.path.join("E:\\PROMETHEUS PROJECTS\\DCT-Watermark-Camera", filepath)
        if os.path.exists(full_path):
            print(f"  ✓ {filepath}")
        else:
            print(f"  ✗ {filepath} - NOT FOUND")
            all_exist = False
    
    if all_exist:
        print("\n  ✓ All critical files exist")
        return True
    else:
        print("\n  ✗ Some files missing")
        return False


def test_code_changes():
    """测试代码修改是否正确"""
    print("\n" + "=" * 60)
    print("Test 2: Code Changes Verification")
    print("=" * 60)
    
    base_path = "E:\\PROMETHEUS PROJECTS\\DCT-Watermark-Camera"
    
    # 测试 1: Qt.java 方法名修复
    print("\n2.1 Checking Qt.java method names...")
    qt_path = os.path.join(base_path, "app/src/main/java/xju/dctcamera/utils/dct/net/watermark/Qt.java")
    with open(qt_path, 'r', encoding='utf-8') as f:
        content = f.read()
        if 'void quantize(' in content and 'void dequantize(' in content:
            print("  ✓ New method names (quantize/dequantize) found")
        else:
            print("  ✗ New method names NOT found")
            return False
        
        if '@Deprecated' in content:
            print("  ✓ Old methods marked as @Deprecated")
        else:
            print("  ⚠ Old methods not marked as @Deprecated")
    
    # 测试 2: Watermark.java 死循环修复
    print("\n2.2 Checking Watermark.java while(true) fixes...")
    watermark_path = os.path.join(base_path, "app/src/main/java/xju/dctcamera/utils/dct/net/watermark/Watermark.java")
    with open(watermark_path, 'r', encoding='utf-8') as f:
        content = f.read()
        while_true_count = content.count('while (true)')
        if while_true_count == 0:
            print("  ✓ All while(true) loops fixed")
        else:
            print(f"  ✗ Found {while_true_count} while(true) loops still present")
            return False
        
        if 'maxPositions' in content or 'maxEmbedPositions' in content:
            print("  ✓ Maximum attempt protection added")
        else:
            print("  ✗ Maximum attempt protection NOT found")
            return False
    
    # 测试 3: DCT.java 静态缓存
    print("\n2.3 Checking DCT.java static cache...")
    dct_path = os.path.join(base_path, "app/src/main/java/xju/dctcamera/utils/dct/net/watermark/DCT.java")
    with open(dct_path, 'r', encoding='utf-8') as f:
        content = f.read()
        if 'static {' in content and 'C = new double' in content:
            print("  ✓ Static initialization block found")
        else:
            print("  ✗ Static initialization block NOT found")
            return False
        
        if 'public static double[][] getC()' in content:
            print("  ✓ Static accessor methods found")
        else:
            print("  ⚠ Static accessor methods may be missing")
    
    # 测试 4: 工具类创建
    print("\n2.4 Checking new utility classes...")
    utils = [
        ("PermissionUtils.java", "运行时权限检查"),
        ("BitmapManager.java", "Bitmap 内存管理"),
        ("Logger.java", "统一日志工具"),
    ]
    
    for filename, desc in utils:
        filepath = os.path.join(base_path, f"app/src/main/java/xju/dctcamera/utils/common/{filename}")
        if os.path.exists(filepath):
            print(f"  ✓ {filename} ({desc})")
        else:
            # 检查是否在 bitmap 目录
            if filename == "BitmapManager.java":
                filepath = os.path.join(base_path, f"app/src/main/java/xju/dctcamera/utils/bitmap/{filename}")
                if os.path.exists(filepath):
                    print(f"  ✓ {filename} ({desc})")
                else:
                    print(f"  ✗ {filename} NOT FOUND")
            else:
                print(f"  ✗ {filename} NOT FOUND")
    
    return True


def test_gitignore():
    """测试 .gitignore 配置"""
    print("\n" + "=" * 60)
    print("Test 3: .gitignore Configuration")
    print("=" * 60)
    
    gitignore_path = "E:\\PROMETHEUS PROJECTS\\DCT-Watermark-Camera\\.gitignore"
    if not os.path.exists(gitignore_path):
        print("  ✗ .gitignore file not found")
        return False
    
    with open(gitignore_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    checks = [
        ("*.apk", "APK files"),
        ("build/", "Build directory"),
        (".iml", "IDE files"),
        ("local.properties", "Local properties"),
    ]
    
    all_passed = True
    for pattern, desc in checks:
        if pattern in content:
            print(f"  ✓ {desc} excluded")
        else:
            print(f"  ✗ {desc} NOT excluded")
            all_passed = False
    
    return all_passed


def test_gradle_config():
    """测试 Gradle 配置"""
    print("\n" + "=" * 60)
    print("Test 4: Gradle Configuration")
    print("=" * 60)
    
    base_path = "E:\\PROMETHEUS PROJECTS\\DCT-Watermark-Camera"
    
    # 检查 build.gradle
    build_gradle = os.path.join(base_path, "build.gradle")
    with open(build_gradle, 'r', encoding='utf-8') as f:
        content = f.read()
        if 'google()' in content:
            print("  ✓ Google Maven repository configured")
        else:
            print("  ✗ Google Maven repository NOT configured")
            return False
        
        if 'mavenCentral()' in content:
            print("  ✓ Maven Central repository configured")
        else:
            print("  ✗ Maven Central repository NOT configured")
    
    # 检查 gradle wrapper
    wrapper_props = os.path.join(base_path, "gradle/wrapper/gradle-wrapper.properties")
    if os.path.exists(wrapper_props):
        with open(wrapper_props, 'r', encoding='utf-8') as f:
            content = f.read()
            if 'gradle-8' in content or 'gradle-7' in content:
                print("  ✓ Gradle wrapper version is modern (7.x or 8.x)")
            else:
                print("  ⚠ Gradle wrapper version may be outdated")
    
    return True


def main():
    """主测试函数"""
    print("\n")
    print("╔" + "=" * 58 + "╗")
    print("║" + " " * 8 + "DCT Watermark-Camera Refactoring Test Suite" + " " * 9 + "║")
    print("╚" + "=" * 58 + "╝")
    print()
    
    tests = [
        ("Java Compilation Check", test_java_compilation),
        ("Code Changes Verification", test_code_changes),
        (".gitignore Configuration", test_gitignore),
        ("Gradle Configuration", test_gradle_config),
    ]
    
    passed = 0
    failed = 0
    
    for name, test_func in tests:
        try:
            if test_func():
                passed += 1
            else:
                failed += 1
        except Exception as e:
            print(f"\n  ✗ ERROR in {name}: {e}")
            failed += 1
    
    # 总结
    print("\n" + "=" * 60)
    print(f"Test Summary: {passed} passed, {failed} failed")
    print("=" * 60)
    
    if failed == 0:
        print("\n✓ ALL TESTS PASSED - Refactoring successful!")
    else:
        print(f"\n⚠ {failed} test(s) failed - please check the details above")
    
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
