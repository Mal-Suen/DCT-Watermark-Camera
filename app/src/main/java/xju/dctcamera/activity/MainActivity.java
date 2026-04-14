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

    private static final String TAG = "MainActivity";
    private static final String EXTRA_RESTORE_PHOTO = "extra_restore_photo";

    /**
     * 权限请求码
     */
    private static final int PERMISSION_REQUEST_CODE = 100;

    /**
     * 所有必要权限已授予的标志
     */
    private static final int ALL_PERMISSIONS_GRANTED = 0;

    private static final String FEEDBACK_EMAIL = "malcolmsuen@gmail.com";
    private static final String SHARE_TITLE = "天山印象";
    private static final String SHARE_TEXT = "独乐乐不如众乐乐，发现了一个有趣的相机应用，快来下载吧～";
    private static final String SHARE_URL = "http://sharesdk.cn";

    private CapturePhotoHelper capturePhotoHelper;
    private File restorePhotoFile;
    private boolean isWaitingForPermissionResult = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AtyContainer.getInstance().addActivity(this);
        
        // 初始化 FolderManager
        FolderManager.getInstance().initialize(this);

        initToolbar();
        displayDeviceInfo();
        setupClickListeners();

        // 启动时检查并请求权限
        checkAndRequestPermissions();
    }

    /**
     * 检查并请求必要的权限
     */
    private void checkAndRequestPermissions() {
        String[] requiredPermissions = getRequiredPermissions();
        String[] missingPermissions = getMissingPermissions(requiredPermissions);

        if (missingPermissions.length > 0) {
            Log.d(TAG, "请求缺失的权限: " + missingPermissions.length + " 个");
            ActivityCompat.requestPermissions(this, missingPermissions, PERMISSION_REQUEST_CODE);
            isWaitingForPermissionResult = true;
        } else {
            Log.d(TAG, "所有必要权限已授予");
            isWaitingForPermissionResult = false;
        }
    }

    /**
     * 根据系统版本获取需要请求的权限列表
     */
    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+)
            return new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_MEDIA_IMAGES
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12 (API 30-32)
            return new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        } else {
            // Android 10 及以下 (API <= 29)
            return new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }
    }

    /**
     * 获取尚未授予的权限列表
     */
    private String[] getMissingPermissions(String[] requiredPermissions) {
        java.util.List<String> missingList = new java.util.ArrayList<>();
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missingList.add(permission);
            }
        }
        return missingList.toArray(new String[0]);
    }

    /**
     * 检查是否拥有所有必要权限
     */
    private boolean hasAllPermissions() {
        String[] requiredPermissions = getRequiredPermissions();
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void initToolbar() {
        Toolbar toolbar = findViewById(R.id.tool_bar);
        setSupportActionBar(toolbar);
    }

    private void setupClickListeners() {
        findViewById(R.id.card_camera).setOnClickListener(v -> handleCameraClick());
        findViewById(R.id.card_gallery).setOnClickListener(v -> openPhotoList());
        findViewById(R.id.card_settings).setOnClickListener(v -> openSettings());

        findViewById(R.id.btn_feedback).setOnClickListener(v -> sendFeedbackEmail());
        findViewById(R.id.btn_help).setOnClickListener(v -> showHelpDialog());
        findViewById(R.id.btn_share).setOnClickListener(v -> showShareDialog());
        findViewById(R.id.btn_exit).setOnClickListener(v -> exitApp());
    }

    /**
     * 处理拍照按钮点击
     */
    private void handleCameraClick() {
        if (!hasAllPermissions()) {
            showPermissionRequiredDialog();
        } else {
            startCamera();
        }
    }

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

    private String getAndroidId() {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    private String getWlanMac() {
        WifiManager wifiManager = (WifiManager) getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && wifiManager.getConnectionInfo() != null) {
            String mac = wifiManager.getConnectionInfo().getMacAddress();
            if (mac != null && !mac.equals("02:00:00:00:00:00")) {
                return mac;
            }
        }
        return "Unavailable (Android 6+)";
    }

    private void showDeviceInfoError(Exception e) {
        TextView infoTv = findViewById(R.id.manualMain);
        infoTv.setText("无法获取设备信息\n" + e.getMessage());
    }

    private void openPhotoList() {
        startActivity(new Intent(this, ListViewActivity.class));
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingActivity.class));
    }

    private void exitApp() {
        AtyContainer.getInstance().finishAllActivity();
    }

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

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.help)
                .setMessage(R.string.help_content)
                .setPositiveButton(R.string.cancel, null)
                .show();
    }

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

    private void startCamera() {
        if (capturePhotoHelper == null) {
            capturePhotoHelper = new CapturePhotoHelper(
                    this, 
                    FolderManager.getInstance().getPhotoFolder()
            );
        }
        capturePhotoHelper.capture();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        isWaitingForPermissionResult = false;

        if (requestCode == PERMISSION_REQUEST_CODE) {
            int deniedCount = 0;
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    deniedCount++;
                    Log.w(TAG, "权限被拒绝: " + permissions[i]);
                }
            }

            if (deniedCount == 0) {
                Log.d(TAG, "所有权限已授予");
                Toast.makeText(this, "权限已授予，可以正常使用应用", Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "有 " + deniedCount + " 个权限被拒绝");
                showPermissionDeniedDialog();
            }
        }
    }

    /**
     * 显示权限被拒绝的对话框
     */
    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("权限不足")
                .setMessage("应用需要相机和存储权限才能正常工作。\n\n" +
                        "• 相机权限：用于拍摄照片\n" +
                        "• 存储权限：用于保存和读取照片\n\n" +
                        "请前往设置授予必要的权限。")
                .setNegativeButton("暂不授权", (dialog, which) -> {
                    Toast.makeText(this, "部分功能可能无法正常使用", Toast.LENGTH_LONG).show();
                })
                .setPositiveButton("去设置", (dialog, which) -> openAppSettings())
                .setCancelable(false)
                .show();
    }

    /**
     * 显示需要权限的对话框
     */
    private void showPermissionRequiredDialog() {
        new AlertDialog.Builder(this)
                .setTitle("需要权限")
                .setMessage("拍照功能需要相机和存储权限，请先授予这些权限。")
                .setNegativeButton("取消", null)
                .setPositiveButton("授权", (dialog, which) -> checkAndRequestPermissions())
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == CapturePhotoHelper.CAPTURE_PHOTO_REQUEST_CODE) {
            handleCameraResult(resultCode);
        }
    }

    private void handleCameraResult(int resultCode) {
        File photoFile = capturePhotoHelper != null ? capturePhotoHelper.getPhoto() : null;
        if (photoFile != null) {
            if (resultCode == RESULT_OK) {
                PhotoPreviewActivity.preview(this, photoFile);
                finish();
            } else if (photoFile.exists()) {
                boolean deleted = photoFile.delete();
                if (!deleted) {
                    Log.w(TAG, "Failed to delete photo file");
                }
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

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 如果之前在等待权限结果，返回时重新检查权限状态
        if (!isWaitingForPermissionResult) {
            if (!hasAllPermissions()) {
                Log.d(TAG, "onResume: 权限未授予");
            } else {
                Log.d(TAG, "onResume: 权限已授予");
            }
        }
    }
}