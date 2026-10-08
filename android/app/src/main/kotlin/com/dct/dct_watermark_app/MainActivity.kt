package com.dct.dct_watermark_app

import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    companion object {
        const val CHANNEL = "dct_watermark_app/device_id"
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        // 修复（2026-10-08 代码审查 C2）：device_info_plus 的 info.id 是 Build.ID
        // （OS 构建号，同 ROM 设备共享），不是 ANDROID_ID。取证水印的设备标识
        // 需要每设备唯一值，这里通过 platform channel 暴露 Settings.Secure.ANDROID_ID。
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                if (call.method == "getAndroidId") {
                    try {
                        val id = Settings.Secure.getString(
                            contentResolver, Settings.Secure.ANDROID_ID
                        )
                        result.success(id)
                    } catch (e: Exception) {
                        result.error("ANDROID_ID_ERROR", e.message, null)
                    }
                } else {
                    result.notImplemented()
                }
            }
    }
}
