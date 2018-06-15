package xju.dctcamera.activity;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
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

    private BoomMenuButton bmb,bmb2;    //    BMB按钮
    /**
     * 运行时权限申请码
     */
    private final static int RUNTIME_PERMISSION_REQUEST_CODE = 0x1;

    private final static String TAG = MainActivity.class.getSimpleName();
    private final static String EXTRA_RESTORE_PHOTO = "extra_restore_photo";

    private CapturePhotoHelper mCapturePhotoHelper;
    private File mRestorePhotoFile;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AtyContainer.getInstance().addActivity(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.tool_bar);
        setSupportActionBar(toolbar);

        //显示本机信息
        TelephonyManager TelephonyMgr = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
        String imei = TelephonyMgr.getDeviceId();
        WifiManager wm = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        String WLANMAC = wm.getConnectionInfo().getMacAddress();
        TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        String SimSerialNumber = tm.getSimSerialNumber();
        String md5 = MD5Util.getMD5(imei+WLANMAC+SimSerialNumber);
        TextView textView = (TextView) findViewById(R.id.manualMain);
        textView.setText("IMEI:"+imei+"\n"+"WLANMAC:"+WLANMAC+"\n"+"SimSerialNumber:"+SimSerialNumber+"\n"+"MD5:"+md5+"\n");

        bmb = (BoomMenuButton) findViewById(R.id.bmb);
        assert bmb != null;
        bmb.setButtonEnum(ButtonEnum.Ham);
        bmb.addBuilder(BuilderManager.getHamButtonBuilderWithDifferentPieceColor_Comment());
        bmb.addBuilder(BuilderManager.getHamButtonBuilderWithDifferentPieceColor_Help());
        bmb.addBuilder(BuilderManager.getHamButtonBuilderWithDifferentPieceColor_Share());
        bmb.addBuilder(BuilderManager.getHamButtonBuilderWithDifferentPieceColor_Exit());

        bmb.setOnBoomListener(new OnBoomListenerAdapter(){
            /**
             * When one of the boom-button is clicked.
             *
             * @param index index of the clicked boom-button
             * @param boomButton the clicked boom-button
             */
            @Override
            public void onClicked(int index, BoomButton boomButton) {
                super.onClicked(index, boomButton);
                switch (index){
                    case 0:
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        String[] tos={"malcolmsuen@gmail.com"};
//                        intent.setType("text/plain"); //模拟器请使用这行
                        intent.setType("message/rfc822"); // 真机上使用这行
                        intent.putExtra(Intent.EXTRA_EMAIL, tos);
                        intent.putExtra(Intent.EXTRA_TEXT, "您的建议");
                        intent.putExtra(Intent.EXTRA_SUBJECT, "我们很希望能得到您的建议");
                        startActivity(Intent.createChooser(intent, "请选择邮件应用"));
                        break;
                    case 1:
                        AtyContainer.getInstance().finishAllActivity();
                        break;
                    case 2:
                        showShare();
                        break;
                    case 3:
                        AtyContainer.getInstance().finishAllActivity();
                        break;
                }
            }
        });
        bmb2 = (BoomMenuButton) findViewById(R.id.bmb2);
        assert bmb2 != null;
        bmb2.setButtonEnum(ButtonEnum.TextOutsideCircle);
        bmb2.setPiecePlaceEnum(PiecePlaceEnum.DOT_3_1);
        bmb2.setButtonPlaceEnum(ButtonPlaceEnum.SC_3_1);
        bmb2.addBuilder(BuilderManager.getTextOutsideCircleButtonBuilder_Gallery());
        bmb2.addBuilder(BuilderManager.getTextOutsideCircleButtonBuilder_Camera());
        bmb2.addBuilder(BuilderManager.getTextOutsideCircleButtonBuilder_Set());

        bmb2.setOnBoomListener(new OnBoomListenerAdapter(){
            /**
             * When one of the boom-button is clicked.
             *
             * @param index index of the clicked boom-button
             * @param boomButton the clicked boom-button
             */
            @Override
            public void onClicked(int index, BoomButton boomButton) {
                super.onClicked(index, boomButton);
                    switch (index){
                        case 0:
                            Intent intentGallery = new Intent(MainActivity.this, ListViewActivity.class);
                            startActivity(intentGallery);
                            break;
                        case 1:
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { //Android M 处理Runtime Permission
                                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {//检查是否有写入SD卡的授权
                                    Log.i(TAG, "granted permission!");
                                    turnOnCamera();
                                } else {
                                    Log.i(TAG, "denied permission!");
                                    if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                                        Log.i(TAG, "should show request permission rationale!");
                                    }
                                    requestPermission();
                                }
                            } else {
                                turnOnCamera();
                            }
                            break;
                        case 2:
                            Intent intentSet = new Intent(MainActivity.this, SettingActivity.class);
                            startActivity(intentSet);
                            break;
                    }
            }
        });

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.i(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);
        if (mCapturePhotoHelper != null) {
            mRestorePhotoFile = mCapturePhotoHelper.getPhoto();
            Log.i(TAG, "onSaveInstanceState , mRestorePhotoFile: " + mRestorePhotoFile);
            if (mRestorePhotoFile != null) {
                outState.putSerializable(EXTRA_RESTORE_PHOTO, mRestorePhotoFile);
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        Log.i(TAG, "onRestoreInstanceState");
        super.onRestoreInstanceState(savedInstanceState);
        if (mCapturePhotoHelper != null) {
            mRestorePhotoFile = (File) savedInstanceState.getSerializable(EXTRA_RESTORE_PHOTO);
            Log.i(TAG, "onRestoreInstanceState , mRestorePhotoFile: " + mRestorePhotoFile);
            mCapturePhotoHelper.setPhoto(mRestorePhotoFile);
        }
    }

    /**
     * 开启相机
     */
    public void turnOnCamera() {
        if (mCapturePhotoHelper == null) {
            mCapturePhotoHelper = new CapturePhotoHelper(this, FolderManager.getPhotoFolder());
        }
        mCapturePhotoHelper.capture();
    }

    /**
     * 申请写入sd卡的权限
     */
    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, RUNTIME_PERMISSION_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "requestCode: " + requestCode + " resultCode: " + resultCode + " data: " + data);
        if (requestCode == CapturePhotoHelper.CAPTURE_PHOTO_REQUEST_CODE) {
            File photoFile = mCapturePhotoHelper.getPhoto();
            if (photoFile != null) {
                if (resultCode == RESULT_OK) {
                    PhotoPreviewActivity.preview(this, photoFile);
                    finish();
                } else {
                    if (photoFile.exists()) {
                        photoFile.delete();
                    }
                }
            }

        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RUNTIME_PERMISSION_REQUEST_CODE) {
            for (int index = 0; index < permissions.length; index++) {
                String permission = permissions[index];
                if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
                    if (grantResults[index] == PackageManager.PERMISSION_GRANTED) {
                        Log.i(TAG, "onRequestPermissionsResult: permission is granted!");
                        turnOnCamera();

                    } else {
                        showMissingPermissionDialog();

                    }
                }
            }
        }
    }


    /**
     * 显示打开权限提示的对话框
     */
    private void showMissingPermissionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.help);
        builder.setMessage(R.string.help_content);

        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this,R.string.camera_open_error, Toast.LENGTH_LONG).show();
                    }
                });
                finish();
            }
        });

        builder.setPositiveButton(R.string.settings, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                turnOnSettings();
            }
        });

        builder.show();
    }

    /**
     * 启动系统权限设置界面
     */
    private void turnOnSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void showShare() {
        OnekeyShare oks = new OnekeyShare();
        //关闭sso授权
        oks.disableSSOWhenAuthorize();

        // 分享时Notification的图标和文字  2.5.9以后的版本不     调用此方法
        //oks.setNotification(R.drawable.ic_launcher, getString(R.string.app_name));
        // title标题，印象笔记、邮箱、信息、微信、人人网和QQ空间使用
//        oks.setTitle(getString(R.string.share));
        oks.setTitle("天山印象");
        // titleUrl是标题的网络链接，仅在人人网和QQ空间使用
        oks.setTitleUrl("http://sharesdk.cn");
        // text是分享文本，所有平台都需要这个字段
        oks.setText("独乐乐不如众乐乐，我发现了一个非常有趣的应用\"天山印象\"，快来下载吧～");
        // imagePath是图片的本地路径，Linked-In以外的平台都支持此参数
        oks.setImagePath("/sdcard/test.jpg");//确保SDcard下面存在此张图片
        // url仅在微信（包括好友和朋友圈）中使用
        oks.setUrl("http://sharesdk.cn");
        // comment是我对这条分享的评论，仅在人人网和QQ空间使用
        oks.setComment("独乐乐不如众乐乐，我发现了一个非常有趣的应用\"天山印象\"，快来下载吧～");
        // site是分享此内容的网站名称，仅在QQ空间使用
        oks.setSite(getString(R.string.app_name));
        // siteUrl是分享此内容的网站地址，仅在QQ空间使用
        oks.setSiteUrl("http://sharesdk.cn");

        // 启动分享GUI
        oks.show(this);
    }
}
