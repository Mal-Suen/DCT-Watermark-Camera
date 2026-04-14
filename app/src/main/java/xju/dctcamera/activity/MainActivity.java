package xju.dctcamera.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import xju.dctcamera.AtyContainer;
import xju.dctcamera.R;
import xju.dctcamera.helper.CapturePhotoHelper;
import xju.dctcamera.manager.FolderManager;
import xju.dctcamera.onekeyshare.OnekeyShare;
import xju.dctcamera.utils.MD5Util;

/**
 * 主界面 Activity
 * <p>
 * 提供核心功能入口：拍照、相册、设置
 * 以及辅助功能：反馈、帮助、分享、退出
 * </p>
 */
public class MainActivity extends AppCompatActivity {

    /**
     * 日志标签
     */
    private static final String TAG = "MainActivity";

    /**
     * 保存的照片文件键
     */
    private static final String EXTRA_RESTORE_PHOTO = "extra_restore_photo";

    /**
     * 权限请求码
     */
    private static final int PERMISSION_REQUEST_CODE = 0x1;

    /**
     * 反馈邮箱
     */
    private static final String FEEDBACK_EMAIL = "malcolmsuen@gmail.com";

    /**
     * 分享标题
     */
    private static final String SHARE_TITLE = "天山印象";

    /**
     * 分享描述
     */
    private static final String SHARE_TEXT = "独乐乐不如众乐乐，发现了一个有趣的相机应用，快来下载吧～";

    /**
     * 分享链接
     */
    private static final String SHARE_URL = "http://sharesdk.cn";

    /**
     * 拍照帮助
     */
    private CapturePhotoHelper capturePhotoHelper;

    /**
     * 恢复的照片文件
     */
    private File restorePhotoFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 注册 Activity
        AtyContainer.getInstance().addActivity(this);

        // 初始化视图
        initToolbar();
        displayDeviceInfo();
        setupClickListeners();
    }

    /**
     * 初始化 Toolbar
     */
    private void initToolbar() {
        Toolbar toolbar = findViewById(R.id.tool_bar);
        setSupportActionBar(toolbar);
    }

    /**
     * 设置所有按钮的点击事件
     */
    private void setupClickListeners() {
        // 核心功能按钮
        findViewById(R.id.card_camera).setOnClickListener(v -> checkCameraPermission());
        findViewById(R.id.card_gallery).setOnClickListener(v -> openPhotoList());
        findViewById(R.id.card_settings).setOnClickListener(v -> openSettings());

        // 菜单按钮
        findViewById(R.id.btn_feedback).setOnClickListener(v -> sendFeedbackEmail());
        findViewById(R.id.btn_help).setOnClickListener(v -> showHelpDialog());
        findViewById(R.id.btn_share).setOnClickListener(v -> showShareDialog());
        findViewById(R.id.btn_exit).setOnClickListener(v -> exitApp());
    }

    /**
     * 显示设备信息
     */
    private void displayDeviceInfo() {
        try {
            String androidId = getAndroidId();
            String wlanMac = getWlanMac();
            String deviceModel = Build.MODEL;
            String sdkVersion = Build.VERSION.RELEASE;

            String rawInfo = (androidId == null ? "" : androidId) + deviceModel + sdkVersion;
            String md5 = MD5Util.getMD5(rawInfo);

            String infoText = buildDeviceInfoText(androidId, deviceModel, sdkVersion, wlanMac, md5);
            TextView infoTv = findViewById(R.id.manualMain);
            infoTv.setText(infoText);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get device info", e);
            showDeviceInfoError(e);
        }
    }

    /**
     * 构建设备信息文本
     */
    private String buildDeviceInfoText(String androidId, String deviceModel,
                                       String sdkVersion, String wlanMac, String md5) {
        return String.format(
                "Android ID: %s\nDevice: %s\nSDK: %s\nWLAN MAC: %s\nMD5: %s",
                androidId != null ? androidId : "N/A",
                deviceModel,
                sdkVersion,
                wlanMac,
                md5
        );
    }

    /**
     * 获取 Android ID
     */
    private String getAndroidId() {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    /**
     * 获取 WLAN MAC 地址
     */
    private String getWlanMac() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && wifiManager.getConnectionInfo() != null) {
            String mac = wifiManager.getConnectionInfo().getMacAddress();
            if (mac != null && !mac.equals("02:00:00:00:00:00")) {
                return mac;
            }
        }
        return "Unavailable (Android 6+)";
    }

    /**
     * 显示设备信息错误
     */
    private void showDeviceInfoError(Exception e) {
        TextView infoTv = findViewById(R.id.manualMain);
        infoTv.setText("无法获取设备信息\n" + e.getMessage());
    }

    /**
     * 打开照片列表
     */
    private void openPhotoList() {
        startActivity(new Intent(this, ListViewActivity.class));
    }

    /**
     * 打开设置
     */
    private void openSettings() {
        startActivity(new Intent(this, SettingActivity.class));
    }

    /**
     * 退出应用
     */
    private void exitApp() {
        AtyContainer.getInstance().finishAllActivity();
    }

    /**
     * 发送反馈邮件
     */
    private void sendFeedbackEmail() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + FEEDBACK_EMAIL));
        intent.putExtra(Intent.EXTRA_TEXT, "您的建议：");
        intent.putExtra(Intent.EXTRA_SUBJECT, "反馈：" + SHARE_TITLE);

        try {
            startActivity(Intent.createChooser(intent, "请选择邮件应用"));
        } catch (Exception e) {
            Toast.makeText(this, "未找到邮件客户端", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示帮助对话框
     */
    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.help)
                .setMessage(R.string.help_content)
                .setPositiveButton(R.string.cancel, null)
                .show();
    }

    /**
     * 显示分享对话框
     */
    private void showShareDialog() {
        OnekeyShare oks = new OnekeyShare();
        oks.disableSSOWhenAuthorize();
        oks.setTitle(SHARE_TITLE);
        oks.setTitleUrl(SHARE_URL);
        oks.setText(SHARE_TEXT);
        oks.setSite(getString(R.string.app_name));
        oks.setSiteUrl(SHARE_URL);
        oks.show(this);
    }

    /**
     * 检查相机权限
     */
    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE
            );
        }
    }

    /**
     * 启动相机
     */
    private void startCamera() {
        if (capturePhotoHelper == null) {
            capturePhotoHelper = new CapturePhotoHelper(this, FolderManager.getInstance().getPhotoFolder());
        }
        capturePhotoHelper.capture();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                showMissingPermissionDialog();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CapturePhotoHelper.CAPTURE_PHOTO_REQUEST_CODE) {
            handleCameraResult(resultCode);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * 处理相机结果
     */
    private void handleCameraResult(int resultCode) {
        File photoFile = capturePhotoHelper != null ? capturePhotoHelper.getPhoto() : null;
        if (photoFile != null) {
            if (resultCode == RESULT_OK) {
                PhotoPreviewActivity.preview(this, photoFile);
                finish();
            } else if (photoFile.exists()) {
                photoFile.delete();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (capturePhotoHelper != null && capturePhotoHelper.getPhoto() != null) {
            outState.putSerializable(EXTRA_RESTORE_PHOTO, capturePhotoHelper.getPhoto());
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        restorePhotoFile = (File) savedInstanceState.getSerializable(EXTRA_RESTORE_PHOTO);
        if (restorePhotoFile != null && capturePhotoHelper != null) {
            capturePhotoHelper.setPhoto(restorePhotoFile);
        }
    }

    /**
     * 显示缺少权限的对话框
     */
    private void showMissingPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.help)
                .setMessage(R.string.help_content)
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    Toast.makeText(this, R.string.camera_open_error, Toast.LENGTH_LONG).show();
                    finish();
                })
                .setPositiveButton(R.string.settings, (dialog, which) -> openAppSettings())
                .show();
    }

    /**
     * 打开应用设置
     */
    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }
}
