#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
批量替换 Support Library 为 AndroidX
"""

import os
import re

# 替换规则
replacements = [
    ('android.support.v7.app.AppCompatActivity', 'androidx.appcompat.app.AppCompatActivity'),
    ('android.support.v7.app.AlertDialog', 'androidx.appcompat.app.AlertDialog'),
    ('android.support.v7.widget.Toolbar', 'androidx.appcompat.widget.Toolbar'),
    ('android.support.v4.app.ActivityCompat', 'androidx.core.app.ActivityCompat'),
    ('android.support.v4.content.ContextCompat', 'androidx.core.content.ContextCompat'),
    ('android.support.annotation.NonNull', 'androidx.annotation.NonNull'),
    ('android.support.design.widget', 'com.google.android.material.widget'),
]

# 遍历所有 Java 文件
java_dir = r'app\src\main\java'
count = 0

for root, dirs, files in os.walk(java_dir):
    for file in files:
        if file.endswith('.java'):
            filepath = os.path.join(root, file)
            try:
                with open(filepath, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                original = content
                for old, new in replacements:
                    content = content.replace(old, new)
                
                if content != original:
                    with open(filepath, 'w', encoding='utf-8') as f:
                        f.write(content)
                    count += 1
                    print(f'✓ {filepath}')
            except Exception as e:
                print(f'✗ {filepath}: {e}')

print(f'\n总计修改: {count} 个文件')
