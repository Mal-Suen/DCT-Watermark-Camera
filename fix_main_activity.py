#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
修复 MainActivity.java - 删除所有 BoomMenu 代码
"""

filepath = 'app/src/main/java/xju/dctcamera/activity/MainActivity.java'

try:
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    # 过滤掉所有 BoomMenu 相关的行
    filtered = []
    removed_count = 0
    skip_keywords = [
        'bmb', 'bmb2', 'BoomMenu', 'BuilderManager',
        'ButtonEnum', 'PiecePlaceEnum', 'ButtonPlaceEnum',
        'OnBoomListener', 'BoomButton', 'BoomMenuButton'
    ]
    
    for line in lines:
        skip = False
        for kw in skip_keywords:
            if kw in line:
                skip = True
                removed_count += 1
                break
        if not skip:
            filtered.append(line)
    
    with open(filepath, 'w', encoding='utf-8', newline='\n') as f:
        f.writelines(filtered)
    
    print(f'✓ Fixed: {filepath}')
    print(f'  Removed {removed_count} lines')
    print(f'  Remaining: {len(filtered)} lines')
    
except Exception as e:
    print(f'✗ Error: {e}')
