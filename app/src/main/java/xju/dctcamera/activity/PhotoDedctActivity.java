package xju.dctcamera.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import xju.dctcamera.AtyContainer;
import xju.dctcamera.R;
import xju.dctcamera.utils.dct.DctTool;

/**
 * 水印提取 Activity
 * <p>
 * 用于从已嵌入 DCT 水印的图片中提取水印文本。
 * </p>
 *
 * @author Belikovvv
 * @since 2017/5/4
 */
public class PhotoDedctActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "PhotoDedctActivity";

    /**
     * Intent 传递的图片路径参数
     */
    private static final String EXTRA_IMAGE_PATH = "Path";

    private ImageView photoPreview;
    private TextView watermarkResultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_dedct);
        AtyContainer.getInstance().addActivity(this);

        initViews();
        extractWatermark();
    }

    /**
     * 初始化视图
     */
    private void initViews() {
        photoPreview = findViewById(R.id.iv_preview_photo);
        watermarkResultText = findViewById(R.id.dedctText);
        findViewById(R.id.btn_add_DCT).setOnClickListener(this);
    }

    /**
     * 从图片中提取水印
     */
    private void extractWatermark() {
        String imagePath = getImagePathFromIntent();
        if (imagePath == null) {
            showToast("图片路径无效");
            finish();
            return;
        }

        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap == null) {
            showToast("图片解码失败");
            finish();
            return;
        }

        // DCT 解密
        String watermarkText = DctTool.unDctString(bitmap);
        Log.i(TAG, "Extracted watermark: " + watermarkText);

        photoPreview.setImageBitmap(bitmap);
        watermarkResultText.setText("嵌入的水印内容为：" + watermarkText);

        showToast("水印提取成功！");
    }

    /**
     * 从 Intent 中获取图片路径
     */
    private String getImagePathFromIntent() {
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            return null;
        }
        return extras.getString(EXTRA_IMAGE_PATH);
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();
        if (viewId == R.id.btn_add_DCT) {
            // TODO: 实现添加 DCT 水印的逻辑
        }
    }

    /**
     * 显示 Toast 提示
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
