package xju.dctcamera.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import xju.dctcamera.AtyContainer;
import xju.dctcamera.R;
import xju.dctcamera.utils.dct.DctTool;

/**
 * Created by Belikovvv on 2017/5/4.
 */

public class PhotoDedctActivity extends AppCompatActivity implements View.OnClickListener {

    private final static float RATIO = 1.1f;

    private final static String EXTRA_PHOTO = "extra_photo";

    private ImageView mPhotoPreview;
    private File dctPhotoFile;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_dedct);
        AtyContainer.getInstance().addActivity(this);

        mPhotoPreview = (ImageView)findViewById(R.id.iv_preview_photo);

        //新页面接收数据
        Bundle bundle = this.getIntent().getExtras();
        //接收name值
        String path = bundle.getString("Path");
        Log.i("获取到的path值为",path);

        Bitmap bitmap = BitmapFactory.decodeFile(path);
        Log.i("bitmap",bitmap.toString());

        //dct解密

        String s = DctTool.unDctString(bitmap);

        Log.i("String DCT   ",s);

        TextView textView=(TextView)findViewById(R.id.dedctText);
                textView.setText("嵌入的水印内容为："+s);

        mPhotoPreview.setImageBitmap(bitmap);
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(PhotoDedctActivity.this,"水印提取成功！", Toast.LENGTH_LONG).show();
            }
        });

    }
    @Override
    protected  void onResume(){
        super.onResume();

    }
    @Override
    public void onClick(View v) {
        int viewId = v.getId();
        if (viewId == R.id.btn_add_DCT) {

        }
    }


}
