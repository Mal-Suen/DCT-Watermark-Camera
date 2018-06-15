package xju.dctcamera.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import xju.dctcamera.AtyContainer;
import xju.dctcamera.R;
import xju.dctcamera.utils.MD5Util;

/**
 * Created by Belikovvv on 2017/5/23.
 */

public class SettingActivity extends AppCompatActivity implements View.OnClickListener{
    RadioGroup m_RadioGroup;
    RadioButton m_IMEI, m_MAC, m_SIM, m_All,m_custom;
    Button set_Button,back_Button;
    TextView customText;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        AtyContainer.getInstance().addActivity(this);

        m_RadioGroup = (RadioGroup) findViewById(R.id.RadioGroup);
        m_IMEI = (RadioButton) findViewById(R.id.RadioButton1);
        m_MAC = (RadioButton) findViewById(R.id.RadioButton2);
        m_SIM = (RadioButton) findViewById(R.id.RadioButton3);
        m_All = (RadioButton) findViewById(R.id.RadioButton4);
        m_custom = (RadioButton) findViewById(R.id.RadioButton5);
        customText = (TextView) findViewById(R.id.customText);
        set_Button = (Button) findViewById(R.id.set_button);
        back_Button = (Button) findViewById(R.id.back_button);
        set_Button.setOnClickListener(this);
        back_Button.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.set_button:
                if(m_IMEI.isChecked()){
                    TelephonyManager TelephonyMgr = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
                    String imei = TelephonyMgr.getDeviceId();
                    SharedPreferences sharedPreferences = getSharedPreferences("watermark", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    editor.putString("watermark",imei);
                    Log.d("Android: ", "IMEI: " + imei);
                    editor.apply();
                    DisplayToast("设置成功！IMEI："+imei);
                    Intent intent = new Intent(SettingActivity.this, MainActivity.class);
                    startActivity(intent);
                }
                else if(m_MAC.isChecked()){
                    WifiManager wm = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    String WLANMAC = wm.getConnectionInfo().getMacAddress();
                    SharedPreferences sharedPreferences = getSharedPreferences("watermark", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    editor.putString("watermark",WLANMAC);
                    Log.d("Android: ", "WLANMAC: " + WLANMAC);
                    editor.apply();
                    DisplayToast("设置成功！WLAN MAC Address："+WLANMAC);
                    Intent intent = new Intent(SettingActivity.this, MainActivity.class);
                    startActivity(intent);
                }
                else if(m_SIM.isChecked()){
                    TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
                    String SimSerialNumber = tm.getSimSerialNumber();
                    SharedPreferences sharedPreferences = getSharedPreferences("watermark", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    editor.putString("watermark",SimSerialNumber);
                    Log.d("Android: ", "SimSerialNumber: " + SimSerialNumber);
                    editor.apply();
                    DisplayToast("设置成功！Sim Serial Number："+SimSerialNumber);
                    Intent intent = new Intent(SettingActivity.this, MainActivity.class);
                    startActivity(intent);
                }
                else if(m_All.isChecked()){
                    TelephonyManager TelephonyMgr = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
                    String imei = TelephonyMgr.getDeviceId();
                    WifiManager wm = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    String WLANMAC = wm.getConnectionInfo().getMacAddress();
                    TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
                    String SimSerialNumber = tm.getSimSerialNumber();
                    String md5= MD5Util.getMD5(imei+WLANMAC+SimSerialNumber);

                    SharedPreferences sharedPreferences = getSharedPreferences("watermark", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    editor.putString("watermark",md5);
                    Log.d("Android: ", "MD5: " + md5);
                    editor.apply();
                    DisplayToast("设置成功！IMEI+MAC+SSN MD5："+md5);
                    Intent intent = new Intent(SettingActivity.this, MainActivity.class);
                    startActivity(intent);
                }
                else if(m_custom.isChecked()){
                    SharedPreferences sharedPreferences = getSharedPreferences("watermark", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    editor.putString("watermark",customText.getText().toString());
                    Log.d("Android: ", "custom: " + customText.getText().toString());
                    editor.apply();
                    DisplayToast("设置成功！自定义内容为："+customText.getText().toString());
                    Intent intent = new Intent(SettingActivity.this, MainActivity.class);
                    startActivity(intent);
                }
                break;
            case R.id.back_button:
                Intent intent = new Intent(SettingActivity.this, MainActivity.class);
                startActivity(intent);
                break;
        }

    }

    /* 显示Toast  */
    public void DisplayToast(String str)
    {
        Toast toast = Toast.makeText(this, str, Toast.LENGTH_LONG);
        //设置toast显示的位置
//        toast.setGravity(Gravity.TOP, 0, 220);
        //显示该Toast
        toast.show();
    }

}
