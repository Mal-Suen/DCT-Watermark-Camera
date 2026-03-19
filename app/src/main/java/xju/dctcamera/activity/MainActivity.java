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
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.nightonke.boommenu.BoomButtons.BoomButton;
import com.nightonke.boommenu.BoomButtons.ButtonPlaceEnum;
import com.nightonke.boommenu.BoomMenuButton;
import com.nightonke.boommenu.ButtonEnum;
import com.nightonke.boommenu.OnBoomListenerAdapter;
import com.nightonke.boommenu.Piece.PiecePlaceEnum;

import java.io.File;

import xju.dctcamera.AtyContainer;
import xju.dctcamera.R;
import xju.dctcamera.helper.CapturePhotoHelper;
import xju.dctcamera.manager.BuilderManager;
import xju.dctcamera.manager.FolderManager;
import xju.dctcamera.onekeyshare.OnekeyShare;
import xju.dctcamera.utils.MD5Util;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String EXTRA_RESTORE_PHOTO = "extra_restore_photo";
    private static final int PERMISSION_REQUEST_CODE = 0x1;

    private BoomMenuButton bmb, bmb2;
    private CapturePhotoHelper mCapturePhotoHelper;
    private File mRestorePhotoFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AtyContainer.getInstance().addActivity(this);

        initViews();
        displayDeviceInfo();
        initBoomMenus();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.tool_bar);
        setSupportActionBar(toolbar);
        bmb = findViewById(R.id.bmb);
        bmb2 = findViewById(R.id.bmb2);
    }

    /**
     * 显示本机信息 (注意：Android 10+ 无法直接获取 IMEI)
     */
    private void displayDeviceInfo() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            // 润色：在高版本中 getDeviceId 可能抛出异常或返回空，需做简单兼容处理
            String imei = (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) ? tm.getDeviceId() : "Unavailable";
            
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            String wlanMac = (wm != null && wm.getConnectionInfo() != null) ? wm.getConnectionInfo().getMacAddress() : "02:00:00:00:00:00";
            
            String simSerial = (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) ? tm.getSimSerialNumber() : "Unavailable";
            
            String rawInfo = (imei == null ? "" : imei) + (wlanMac == null ? "" : wlanMac) + (simSerial == null ? "" : simSerial);
            String md5 = MD5Util.getMD5(rawInfo);

            TextView infoTv = findViewById(R.id.manualMain);
            String infoDisplay = String.format("IMEI:%s\nWLANMAC:%s\nSimSerial:%s\nMD5:%s\n", imei, wlanMac, simSerial, md5);
            infoTv.setText(infoDisplay);
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for device info", e);
        }
    }

    private void initBoomMenus() {
        // 配置第一个 BMB (菜单功能)
        bmb.setButtonEnum(ButtonEnum.Ham);
        bmb.addBuilder(BuilderManager.getHamButtonBuilderWithDifferentPieceColor_Comment());
        bmb.addBuilder(BuilderManager.getHamButtonBuilderWithDifferentPieceColor_Help());
        bmb.addBuilder(BuilderManager.getHamButtonBuilderWithDifferentPieceColor_Share());
        bmb.addBuilder(BuilderManager.getHamButtonBuilderWithDifferentPieceColor_Exit());

        bmb.setOnBoomListener(new OnBoomListenerAdapter() {
            @Override
            public void onClicked(int index, BoomButton boomButton) {
                handleBmbClick(index);
            }
        });

        // 配置第二个 BMB (核心操作)
        bmb2.setButtonEnum(ButtonEnum.TextOutsideCircle);
        bmb2.setPiecePlaceEnum(PiecePlaceEnum.DOT_3_1);
        bmb2.setButtonPlaceEnum(ButtonPlaceEnum.SC_3_1);
        bmb2.addBuilder(BuilderManager.getTextOutsideCircleButtonBuilder_Gallery());
        bmb2.addBuilder(BuilderManager.getTextOutsideCircleButtonBuilder_Camera());
        bmb2.addBuilder(BuilderManager.getTextOutsideCircleButtonBuilder_Set());

        bmb2.setOnBoomListener(new OnBoomListenerAdapter() {
            @Override
            public void onClicked(int index, BoomButton boomButton) {
                handleBmb2Click(index);
            }
        });
    }

    private void handleBmbClick(int index) {
        switch (index) {
            case 0: // 反馈
                sendFeedbackEmail();
                break;
            case 1: // 帮助 (此处原代码 index 1 和 3 都是退出，建议根据业务调整)
            case 3: // 退出
                AtyContainer.getInstance().finishAllActivity();
                break;
            case 2: // 分享
                showShare();
                break;
        }
    }

    private void handleBmb2Click(int index) {
        switch (index) {
            case 0: // 相册
                startActivity(new Intent(this, ListViewActivity.class));
                break;
            case 1: // 相机
                checkCameraPermission();
                break;
            case 2: // 设置
                startActivity(new Intent(this, SettingActivity.class));
                break;
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
        oks.show(this);
    }
}