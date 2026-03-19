package xju.dctcamera.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import xju.dctcamera.AtyContainer;
import xju.dctcamera.R;
import xju.dctcamera.utils.MD5Util;

/**
 * Created by Belikovvv on 2017/5/23.
 * Refined for stability and clarity.
 */
public class SettingActivity extends AppCompatActivity implements View.OnClickListener {
    
    private static final String TAG = "SettingActivity";
    private static final String SP_NAME = "watermark";
    private static final String KEY_WATERMARK = "watermark";

    private RadioButton m_IMEI, m_MAC, m_SIM, m_All, m_custom;
    private TextView customText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        AtyContainer.getInstance().addActivity(this);

        initViews();
    }

    private void initViews() {
        m_IMEI = findViewById(R.id.RadioButton1);
        m_MAC = findViewById(R.id.RadioButton2);
        m_SIM = findViewById(R.id.RadioButton3);
        m_All = findViewById(R.id.RadioButton4);
        m_custom = findViewById(R.id.RadioButton5);
        customText = findViewById(R.id.customText);

        findViewById(R.id.set_button).setOnClickListener(this);
        findViewById(R.id.back_button).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.set_button) {
            handleSaveSetting();
        } else if (id == R.id.back_button) {
            finish(); // 优化：返回通常使用 finish() 而不是重新开启一个 Activity 栈
        }
    }

    private void handleSaveSetting() {
        String watermarkValue = "";
        String toastLabel = "";

        // 获取系统服务
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        // 统一处理逻辑，减少冗余代码
        if (m_IMEI.isChecked()) {
            watermarkValue = getSafeImei(tm);
            toastLabel = "IMEI";
        } else if (m_MAC.isChecked()) {
            watermarkValue = getSafeMac(wm);
            toastLabel = "WLAN MAC";
        } else if (m_SIM.isChecked()) {
            watermarkValue = getSafeSimSerial(tm);
            toastLabel = "Sim Serial";
        } else if (m_All.isChecked()) {
            String imei = getSafeImei(tm);
            String mac = getSafeMac(wm);
            String sim = getSafeSimSerial(tm);
            watermarkValue = MD5Util.getMD5(imei + mac + sim);
            toastLabel = "MD5 Filter";
        } else if (m_custom.isChecked()) {
            watermarkValue = customText.getText().toString();
            toastLabel = "自定义内容";
        }

        // 执行保存
        saveToPreferences(watermarkValue);
        displayToast("设置成功！" + toastLabel + "：" + watermarkValue);

        // 跳转回主页
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

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

    private String getSafeMac(WifiManager wm) {
        if (wm != null && wm.getConnectionInfo() != null) {
            String mac = wm.getConnectionInfo().getMacAddress();
            return mac != null ? mac : "02:00:00:00:00:00";
        }
        return "02:00:00:00:00:00";
    }

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

    private void saveToPreferences(String value) {
        SharedPreferences sp = getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_WATERMARK, value).apply();
        Log.d(TAG, "Saved Watermark: " + value);
    }

    public void displayToast(String str) {
        Toast.makeText(this, str, Toast.LENGTH_LONG).show();
    }
}