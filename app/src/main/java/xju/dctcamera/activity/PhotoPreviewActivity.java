package xju.dctcamera.activity;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.dd.processbutton.iml.SubmitProcessButton;

import java.io.File;

import xju.dctcamera.AtyContainer;
import xju.dctcamera.R;
import xju.dctcamera.helper.CapturePhotoHelper;
import xju.dctcamera.manager.FolderManager;
import xju.dctcamera.utils.ProgressGenerator;
import xju.dctcamera.utils.bitmap.BitmapUtils;
import xju.dctcamera.utils.common.RuleUtils;
import xju.dctcamera.utils.dct.DctTool;

/**
 * 预览图片界面
 *
 * Created by Belikovvv on 2017/5/4.
 */
public class PhotoPreviewActivity extends AppCompatActivity implements ProgressGenerator.OnCompleteListener {


    /**
     * 压缩图片参数，最直接的决定算法效率
     */
    private final static float RATIO = 1.1f;
    private int m_ButtonFlag=0;
    private final static String EXTRA_PHOTO = "extra_photo";
    Button btn_back;
    private ImageView m_PhotoPreview;
    private File m_PhotoFile;
    private CapturePhotoHelper mCapturePhotoHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_preview);
        AtyContainer.getInstance().addActivity(this);

        m_PhotoPreview = (ImageView) findViewById(R.id.iv_preview_photo);
        btn_back = (Button) findViewById(R.id.btn_back);
        m_PhotoFile = (File) getIntent().getSerializableExtra(EXTRA_PHOTO);
        int requestWidth = (int) (RuleUtils.getScreenWidth(this) * RATIO);
        int requestHeight = (int) (RuleUtils.getScreenHeight(this) * RATIO);
        Bitmap bitmap = BitmapUtils.getSmallBitmap(m_PhotoFile.getPath(), 100, requestWidth, requestHeight);//缩放

        if (bitmap != null) {
            int degree = BitmapUtils.getBitmapDegree(m_PhotoFile.getAbsolutePath());//检查是否有被旋转，并进行纠正
            if (degree != 0) {
                bitmap = BitmapUtils.rotateBitmapByDegree(bitmap, degree);
            }
            m_PhotoPreview.setImageBitmap(bitmap);
        }
        final ProgressGenerator progressGenerator = new ProgressGenerator(this);
        final SubmitProcessButton btn_add_dct = (SubmitProcessButton) findViewById(R.id.btn_add_DCT);

        btn_add_dct.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (m_ButtonFlag == 0) {
                    int requestWidth = (int) (RuleUtils.getScreenWidth(PhotoPreviewActivity.this) * RATIO);
                    int requestHeight = (int) (RuleUtils.getScreenHeight(PhotoPreviewActivity.this) * RATIO);
                    final Bitmap bitmap = BitmapUtils.getSmallBitmap(m_PhotoFile.getPath(), 100, requestWidth, requestHeight);//缩放
                    SharedPreferences sharedPreferences = getSharedPreferences("watermark", Context.MODE_PRIVATE);
                    String watermark = sharedPreferences.getString("watermark", "null");
                    //getString()第二个参数为缺省值，如果preference中不存在该key，将返回缺省值
                    // TODO Auto-generated method stub
                    if (watermark.equals("null")) {
                        showMissingSettingDialog();
                    } else {
                        progressGenerator.start(btn_add_dct);

                        Bitmap dstImage = DctTool.dctString(bitmap, watermark);// 添加字符水印
                        Log.d("Android: ", "Dctthread start " + watermark);

                        m_PhotoPreview.setImageBitmap(dstImage);
                        BitmapUtils.saveToFile(dstImage, FolderManager.getPhotoDCTFolder());
                        Log.d("Android: ", "Dctthread over");
                        //删除缓存文件
                        File tmp = new File(m_PhotoFile.getPath());
                        tmp.delete();
                        m_ButtonFlag++;
                    }

                } else {
                    btn_add_dct.setEnabled(true);
                }
            }
        });
        btn_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(PhotoPreviewActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    protected  void onResume(){
        super.onResume();

    }

    public static void preview(Activity activity, File file) {
        Intent previewIntent = new Intent(activity, PhotoPreviewActivity.class);
        previewIntent.putExtra(EXTRA_PHOTO, file);
        activity.startActivity(previewIntent);
    }

    /**
     * 显示打开水印提示的对话框
     */
    private void showMissingSettingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.warning);
        builder.setMessage("尚未设置水印信息，请先设置水印信息！");

        builder.setPositiveButton("确定", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                Intent intent = new Intent(PhotoPreviewActivity.this, SettingActivity.class);
                startActivity(intent);
            }
        });

        builder.show();
    }

    @Override
    public void onComplete() {
        Toast.makeText(this, R.string.Processing_Complete, Toast.LENGTH_LONG).show();
    }


}
