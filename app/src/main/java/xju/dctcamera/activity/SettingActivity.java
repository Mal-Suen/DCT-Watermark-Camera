package xju.dctcamera.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import xju.dctcamera.AtyContainer;
import xju.dctcamera.R;
import xju.dctcamera.utils.MD5Util;

/**
 * 设置页面 Activity
 * <p>
 * 用于配置水印类型，支持多种水印来源选择
 * </p>
 *
 * @author Belikovvv
 * @since 2017/5/23
 */
public class SettingActivity extends AppCompatActivity {

    private static final String TAG = "SettingActivity";
    private static final String SP_NAME = "watermark_settings";
    private static final String KEY_WATERMARK = "watermark_content";
    private static final String KEY_WATERMARK_TYPE = "watermark_type";

    /**
     * 水印类型常量
     */
    private static final int TYPE_IMEI = 0;
    private static final int TYPE_MAC = 1;
    private static final int TYPE_SIM = 2;
    private static final int TYPE_ALL = 3;
    private static final int TYPE_CUSTOM = 4;

    private RadioGroup watermarkTypeGroup;
    private RadioButton radioImei;
    private RadioButton radioMac;
    private RadioButton radioSim;
    private RadioButton radioAll;
    private RadioButton radioCustom;
    private MaterialCardView customInputCard;
    private TextInputEditText customWatermarkInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        AtyContainer.getInstance().addActivity(this);

        initViews();
        setupListeners();
        loadSavedSettings();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        watermarkTypeGroup = findViewById(R.id.watermark_type_group);
        radioImei = findViewById(R.id.radio_imei);
        radioMac = findViewById(R.id.radio_mac);
        radioSim = findViewById(R.id.radio_sim);
        radioAll = findViewById(R.id.radio_all);
        radioCustom = findViewById(R.id.radio_custom);
        customInputCard = findViewById(R.id.custom_input_card);
        customWatermarkInput = findViewById(R.id.custom_watermark_input);

        findViewById(R.id.save_button).setOnClickListener(v -> handleSaveSetting());
        findViewById(R.id.back_button).setOnClickListener(v -> finish());
    }

    private void setupListeners() {
        watermarkTypeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            // 显示/隐藏自定义输入卡片
            if (checkedId == R.id.radio_custom) {
                customInputCard.setVisibility(View.VISIBLE);
            } else {
                customInputCard.setVisibility(View.GONE);
            }
        });
    }

    /**
     * 加载已保存的设置
     */
    private void loadSavedSettings() {
        SharedPreferences sp = getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        int savedType = sp.getInt(KEY_WATERMARK_TYPE, TYPE_ALL);
        String savedCustom = sp.getString(KEY_WATERMARK, "");

        // 设置对应 RadioButton
        switch (savedType) {
            case TYPE_IMEI:
                radioImei.setChecked(true);
                break;
            case TYPE_MAC:
                radioMac.setChecked(true);
                break;
            case TYPE_SIM:
                radioSim.setChecked(true);
                break;
            case TYPE_ALL:
                radioAll.setChecked(true);
                break;
            case TYPE_CUSTOM:
                radioCustom.setChecked(true);
                customInputCard.setVisibility(View.VISIBLE);
                if (!savedCustom.isEmpty()) {
                    customWatermarkInput.setText(savedCustom);
                }
                break;
        }
    }

    /**
     * 处理保存设置
     */
    private void handleSaveSetting() {
        String watermarkValue;
        int selectedType;

        int checkedId = watermarkTypeGroup.getCheckedRadioButtonId();

        if (checkedId == R.id.radio_imei) {
            watermarkValue = getDeviceIdentifier();
            selectedType = TYPE_IMEI;
        } else if (checkedId == R.id.radio_mac) {
            watermarkValue = getMacAddress();
            selectedType = TYPE_MAC;
        } else if (checkedId == R.id.radio_sim) {
            watermarkValue = getSimSerial();
            selectedType = TYPE_SIM;
        } else if (checkedId == R.id.radio_all) {
            watermarkValue = getAllDeviceInfo();
            selectedType = TYPE_ALL;
        } else if (checkedId == R.id.radio_custom) {
            String customText = customWatermarkInput.getText() != null
                    ? customWatermarkInput.getText().toString().trim()
                    : "";
            if (customText.isEmpty()) {
                Toast.makeText(this, "请输入自定义水印内容", Toast.LENGTH_SHORT).show();
                return;
            }
            watermarkValue = customText;
            selectedType = TYPE_CUSTOM;
        } else {
            // 默认使用全部信息
            watermarkValue = getAllDeviceInfo();
            selectedType = TYPE_ALL;
        }

        saveSettings(watermarkValue, selectedType);

        Toast.makeText(this, "水印设置已保存！", Toast.LENGTH_SHORT).show();

        // 返回主页面
        finish();
    }

    /**
     * 获取设备标识 (Android ID)
     */
    private String getDeviceIdentifier() {
        // Android 10+ 无法获取 IMEI，使用 Android ID
        String androidId = android.provider.Settings.Secure.getString(
                getContentResolver(),
                android.provider.Settings.Secure.ANDROID_ID
        );
        return androidId != null ? androidId : "Unknown";
    }

    /**
     * 获取 MAC 地址
     */
    private String getMacAddress() {
        android.net.wifi.WifiManager wifiManager = (android.net.wifi.WifiManager)
                getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && wifiManager.getConnectionInfo() != null) {
            String mac = wifiManager.getConnectionInfo().getMacAddress();
            if (mac != null && !mac.equals("02:00:00:00:00:00")) {
                return mac;
            }
        }
        return "Unavailable";
    }

    /**
     * 获取 SIM 序列号
     */
    private String getSimSerial() {
        // Android 10+ 无法获取 SIM 序列号
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) {
                    String serial = tm.getSimSerialNumber();
                    return serial != null ? serial : "Unknown";
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied for SIM serial", e);
            }
        }
        return "Unavailable";
    }

    /**
     * 获取全部设备信息并计算 MD5
     */
    private String getAllDeviceInfo() {
        String androidId = getDeviceIdentifier();
        String mac = getMacAddress();
        String sim = getSimSerial();

        String rawInfo = androidId + mac + sim;
        return MD5Util.getMD5(rawInfo);
    }

    /**
     * 保存设置到 SharedPreferences
     */
    private void saveSettings(String watermarkValue, int type) {
        SharedPreferences sp = getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putString(KEY_WATERMARK, watermarkValue)
                .putInt(KEY_WATERMARK_TYPE, type)
                .apply();

        Log.d(TAG, "保存水印: " + watermarkValue + ", 类型: " + type);
    }
}