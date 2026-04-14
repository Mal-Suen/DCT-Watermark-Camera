#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
修复 BoomMenu 和 ProcessButton 相关的代码
"""

import os

files_to_fix = [
    {
        'path': 'app/src/main/java/xju/dctcamera/activity/MainActivity.java',
        'replacements': [
            ('import com.nightonke.boommenu', '// import com.nightonke.boommenu'),
            ('private BoomMenuButton bmb, bmb2;', '// private BoomMenuButton bmb, bmb2;'),
        ]
    },
    {
        'path': 'app/src/main/java/xju/dctcamera/activity/PhotoPreviewActivity.java',
        'replacements': [
            ('import com.dd.processbutton', '// import com.dd.processbutton'),
        ]
    },
    {
        'path': 'app/src/main/java/xju/dctcamera/utils/ProgressGenerator.java',
        'replacements': [
            ('import com.dd.processbutton', '// import com.dd.processbutton'),
        ]
    },
]

count = 0
for file_info in files_to_fix:
    filepath = file_info['path']
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
        
        original = content
        for old, new in file_info['replacements']:
            content = content.replace(old, new)
        
        if content != original:
            with open(filepath, 'w', encoding='utf-8', newline='\n') as f:
                f.write(content)
            count += 1
            print(f'✓ Fixed: {filepath}')
    except Exception as e:
        print(f'✗ Error: {filepath}: {e}')

print(f'\n总计修改: {count} 个文件')
