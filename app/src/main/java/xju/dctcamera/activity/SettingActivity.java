package xju.dctcamera.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import xju.dctcamera.AtyContainer;
import xju.dctcamera.R;
import xju.dctcamera.utils.MD5Util;

/**
 * 设置 Activity
 * <p>
 * 用于配置水印信息，支持 IMEI、MAC 地址、SIM 序列号、组合 MD5 及自定义文本。
 * </p>
 *
 * @author Belikovvv
 * @since 2017/5/23
 */
public class SettingActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "SettingActivity";
    private static final String SP_NAME = "watermark";
    private static final String KEY_WATERMARK = "watermark";

    private RadioButton radioImei;
    private RadioButton radioMac;
    private RadioButton radioSim;
    private RadioButton radioAll;
    private RadioButton radioCustom;
    private TextView customTextInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        AtyContainer.getInstance().addActivity(this);

        initViews();
    }

    /**
     * 初始化视图并设置点击监听器
     */
    private void initViews() {
        radioImei = findViewById(R.id.RadioButton1);
        radioMac = findViewById(R.id.RadioButton2);
        radioSim = findViewById(R.id.RadioButton3);
        radioAll = findViewById(R.id.RadioButton4);
        radioCustom = findViewById(R.id.RadioButton5);
        customTextInput = findViewById(R.id.customText);

        findViewById(R.id.set_button).setOnClickListener(this);
        findViewById(R.id.back_button).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.set_button) {
            handleSaveSetting();
        } else if (id == R.id.back_button) {
            // 使用 finish() 而不是重新开启一个 Activity 栈
            finish();
        }
    }

    /**
     * 处理保存设置的逻辑
     */
    private void handleSaveSetting() {
        String watermarkValue;
        String toastLabel;

        // 获取系统服务
        TelephonyManager telephonyManager = getTelephonyManager();
        String macAddress = getMacAddress();

        // 统一处理逻辑，减少冗余代码
        if (radioImei.isChecked()) {
            watermarkValue = getSafeImei(telephonyManager);
            toastLabel = "IMEI";
        } else if (radioMac.isChecked()) {
            watermarkValue = macAddress;
            toastLabel = "WLAN MAC";
        } else if (radioSim.isChecked()) {
            watermarkValue = getSafeSimSerial(telephonyManager);
            toastLabel = "Sim Serial";
        } else if (radioAll.isChecked()) {
            String imei = getSafeImei(telephonyManager);
            String sim = getSafeSimSerial(telephonyManager);
            watermarkValue = MD5Util.getMD5(imei + macAddress + sim);
            toastLabel = "MD5 Filter";
        } else if (radioCustom.isChecked()) {
            watermarkValue = customTextInput.getText().toString();
            toastLabel = "自定义内容";
        } else {
            // 默认使用自定义内容
            watermarkValue = customTextInput.getText().toString();
            toastLabel = "自定义内容";
        }

        // 执行保存
        saveToPreferences(watermarkValue);
        displayToast("设置成功！" + toastLabel + "：" + watermarkValue);

        // 跳转回主页
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    /**
     * 获取 IMEI（安全方式，兼容 Android 10+）
     */
    private String getSafeImei(TelephonyManager tm) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                String id = tm.getDeviceId();
                return id != null ? id : "UnknownIMEI";
            }
        } catch (SecurityException e) {
            Log.e(TAG, "No permission to get IMEI", e);
        }
        return "Unavailable";
    }

    /**
     * 获取 MAC 地址
     */
    private String getMacAddress() {
        android.net.wifi.WifiManager wifiManager =
                (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && wifiManager.getConnectionInfo() != null) {
            String mac = wifiManager.getConnectionInfo().getMacAddress();
            return mac != null ? mac : "02:00:00:00:00:00";
        }
        return "02:00:00:00:00:00";
    }

    /**
     * 获取 SIM 序列号（安全方式，兼容 Android 10+）
     */
    private String getSafeSimSerial(TelephonyManager tm) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                String sn = tm.getSimSerialNumber();
                return sn != null ? sn : "UnknownSIM";
            }
        } catch (SecurityException e) {
            Log.e(TAG, "No permission to get SIM Serial", e);
        }
        return "Unavailable";
    }

    /**
     * 保存水印到 SharedPreferences
     */
    private void saveToPreferences(String value) {
        SharedPreferences sp = getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_WATERMARK, value).apply();
        Log.d(TAG, "Saved Watermark: " + value);
    }

    /**
     * 获取 TelephonyManager
     */
    private TelephonyManager getTelephonyManager() {
        return (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
    }

    /**
     * 显示 Toast 提示
     */
    public void displayToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}