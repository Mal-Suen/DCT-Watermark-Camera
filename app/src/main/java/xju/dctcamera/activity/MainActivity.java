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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String EXTRA_RESTORE_PHOTO = "extra_restore_photo";
    private static final int PERMISSION_REQUEST_CODE = 0x1;

    private CapturePhotoHelper mCapturePhotoHelper;
    private File mRestorePhotoFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AtyContainer.getInstance().addActivity(this);

        initViews();
        displayDeviceInfo();
        setupClickListeners();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.tool_bar);
        setSupportActionBar(toolbar);
    }

    /**
     * 设置所有按钮的点击事件
     */
    private void setupClickListeners() {
        // 核心功能按钮
        findViewById(R.id.card_camera).setOnClickListener(v -> checkCameraPermission());
        findViewById(R.id.card_gallery).setOnClickListener(v -> {
            startActivity(new Intent(this, ListViewActivity.class));
        });
        findViewById(R.id.card_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingActivity.class));
        });

        // 菜单按钮
        findViewById(R.id.btn_feedback).setOnClickListener(v -> sendFeedbackEmail());
        findViewById(R.id.btn_help).setOnClickListener(v -> showHelpDialog());
        findViewById(R.id.btn_share).setOnClickListener(v -> showShare());
        findViewById(R.id.btn_exit).setOnClickListener(v -> {
            AtyContainer.getInstance().finishAllActivity();
        });
    }

    /**
     * 显示本机信息 (注意：Android 10+ 无法直接获取 IMEI)
     */
    private void displayDeviceInfo() {
        try {
            TextView infoTv = findViewById(R.id.manualMain);
            
            // Android 10+ 无法获取 IMEI/电话号码，使用安全的替代方案
            String androidId = android.provider.Settings.Secure.getString(
                getContentResolver(), 
                android.provider.Settings.Secure.ANDROID_ID
            );
            
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            String wlanMac = "Unavailable";
            if (wm != null && wm.getConnectionInfo() != null) {
                wlanMac = wm.getConnectionInfo().getMacAddress();
                if (wlanMac == null || wlanMac.equals("02:00:00:00:00:00")) {
                    wlanMac = "Unavailable (Android 6+)";
                }
            }

            String deviceModel = Build.MODEL;
            String sdkVersion = Build.VERSION.RELEASE;
            
            // 计算简单 MD5
            String rawInfo = (androidId == null ? "" : androidId) + deviceModel + sdkVersion;
            String md5 = MD5Util.getMD5(rawInfo);

            String infoDisplay = String.format(
                "Android ID: %s\nDevice: %s\nSDK: %s\nWLAN MAC: %s\nMD5: %s", 
                androidId != null ? androidId : "N/A",
                deviceModel,
                sdkVersion,
                wlanMac,
                md5
            );
            infoTv.setText(infoDisplay);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get device info", e);
            TextView infoTv = findViewById(R.id.manualMain);
            infoTv.setText("无法获取设备信息\n" + e.getMessage());
        }
    }

    private void sendFeedbackEmail() {
        Intent intent = new Intent(Intent.ACTION_SENDTO); // 使用 SENDTO 过滤掉非邮件应用
        intent.setData(Uri.parse("mailto:malcolmsuen@gmail.com"));
        intent.putExtra(Intent.EXTRA_TEXT, "您的建议：");
        intent.putExtra(Intent.EXTRA_SUBJECT, "反馈：天山印象");
        try {
            startActivity(Intent.createChooser(intent, "请选择邮件应用"));
        } catch (Exception e) {
            Toast.makeText(this, "未找到邮件客户端", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            turnOnCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }

    public void turnOnCamera() {
        if (mCapturePhotoHelper == null) {
            mCapturePhotoHelper = new CapturePhotoHelper(this, FolderManager.getPhotoFolder());
        }
        mCapturePhotoHelper.capture();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                turnOnCamera();
            } else {
                showMissingPermissionDialog();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CapturePhotoHelper.CAPTURE_PHOTO_REQUEST_CODE) {
            File photoFile = mCapturePhotoHelper != null ? mCapturePhotoHelper.getPhoto() : null;
            if (photoFile != null) {
                if (resultCode == RESULT_OK) {
                    PhotoPreviewActivity.preview(this, photoFile);
                    finish(); // 预览后关闭主界面
                } else if (photoFile.exists()) {
                    photoFile.delete();
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    // 状态恢复逻辑保持...
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mCapturePhotoHelper != null && mCapturePhotoHelper.getPhoto() != null) {
            outState.putSerializable(EXTRA_RESTORE_PHOTO, mCapturePhotoHelper.getPhoto());
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mRestorePhotoFile = (File) savedInstanceState.getSerializable(EXTRA_RESTORE_PHOTO);
        if (mRestorePhotoFile != null && mCapturePhotoHelper != null) {
            mCapturePhotoHelper.setPhoto(mRestorePhotoFile);
        }
    }

    private void showMissingPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.help)
                .setMessage(R.string.help_content)
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    Toast.makeText(this, R.string.camera_open_error, Toast.LENGTH_LONG).show();
                    finish();
                })
                .setPositiveButton(R.string.settings, (dialog, which) -> turnOnSettings())
                .show();
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.help)
                .setMessage(R.string.help_content)
                .setPositiveButton(R.string.cancel, null)
                .show();
    }

    private void turnOnSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void showShare() {
        OnekeyShare oks = new OnekeyShare();
        oks.disableSSOWhenAuthorize();
        oks.setTitle("天山印象");
        oks.setTitleUrl("http://sharesdk.cn");
        oks.setText("独乐乐不如众乐乐，发现了一个有趣的相机应用，快来下载吧～");
        oks.setSite(getString(R.string.app_name));
        oks.setSiteUrl("http://sharesdk.cn");
        oks.show(MainActivity.this);
    }
}