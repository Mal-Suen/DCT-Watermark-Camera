package xju.dctcamera.activity;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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
 * 图片预览 Activity
 * <p>
 * 用于预览拍摄的照片并添加 DCT 水印。
 * </p>
 *
 * @author Belikovvv
 * @since 2017/5/4
 */
public class PhotoPreviewActivity extends AppCompatActivity implements ProgressGenerator.OnCompleteListener {

    private static final String TAG = "PhotoPreviewActivity";

    /**
     * Bitmap 缩放比例系数
     */
    private static final float SCALE_RATIO = 1.1f;

    /**
     * Intent 传递的照片文件参数
     */
    private static final String EXTRA_PHOTO = "extra_photo";

    /**
     * SharedPreferences 水印键名
     */
    private static final String SP_WATERMARK_NAME = "watermark";
    private static final String SP_KEY_WATERMARK = "watermark";

    /**
     * 水印未设置的默认值
     */
    private static final String WATERMARK_DEFAULT_VALUE = "null";

    /**
     * Bitmap 压缩质量
     */
    private static final int BITMAP_COMPRESS_QUALITY = 100;

    /**
     * 水印添加状态标记（0=未添加，1=已添加）
     */
    private static final int WATERMARK_NOT_ADDED = 0;
    private static final int WATERMARK_ADDED = 1;

    private ImageView photoPreview;
    private Button backButton;
    private Button addDctButton;
    private File photoFile;
    private int watermarkFlag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_preview);
        AtyContainer.getInstance().addActivity(this);

        photoFile = (File) getIntent().getSerializableExtra(EXTRA_PHOTO);
        if (photoFile == null) {
            showToast("图片文件无效");
            finish();
            return;
        }

        watermarkFlag = WATERMARK_NOT_ADDED;
        initViews();
        displayPhoto();
        setupAddWatermarkButton();
    }

    /**
     * 初始化视图
     */
    private void initViews() {
        photoPreview = findViewById(R.id.iv_preview_photo);
        backButton = findViewById(R.id.btn_back);
        addDctButton = findViewById(R.id.btn_add_DCT);

        backButton.setOnClickListener(v -> navigateToMain());
    }

    /**
     * 显示照片
     */
    private void displayPhoto() {
        int requestWidth = (int) (RuleUtils.getScreenWidth(this) * SCALE_RATIO);
        int requestHeight = (int) (RuleUtils.getScreenHeight(this) * SCALE_RATIO);

        Bitmap bitmap = BitmapUtils.getSmallBitmap(
                photoFile.getPath(), BITMAP_COMPRESS_QUALITY, requestWidth, requestHeight);

        if (bitmap != null) {
            int degree = BitmapUtils.getBitmapDegree(photoFile.getAbsolutePath());
            if (degree != 0) {
                bitmap = BitmapUtils.rotateBitmapByDegree(bitmap, degree);
            }
            photoPreview.setImageBitmap(bitmap);
        }
    }

    /**
     * 设置添加水印按钮的点击事件
     */
    private void setupAddWatermarkButton() {
        ProgressGenerator progressGenerator = new ProgressGenerator(this);

        addDctButton.setOnClickListener(v -> {
            if (watermarkFlag == WATERMARK_NOT_ADDED) {
                addWatermark(progressGenerator);
            } else {
                addDctButton.setEnabled(true);
            }
        });
    }

    /**
     * 添加水印
     */
    private void addWatermark(ProgressGenerator progressGenerator) {
        String watermark = getWatermarkFromPreferences();
        if (WATERMARK_DEFAULT_VALUE.equals(watermark)) {
            showMissingSettingDialog();
            return;
        }

        progressGenerator.start(addDctButton);

        int requestWidth = (int) (RuleUtils.getScreenWidth(this) * SCALE_RATIO);
        int requestHeight = (int) (RuleUtils.getScreenHeight(this) * SCALE_RATIO);

        Bitmap bitmap = BitmapUtils.getSmallBitmap(
                photoFile.getPath(), BITMAP_COMPRESS_QUALITY, requestWidth, requestHeight);

        if (bitmap == null) {
            showToast("图片解码失败");
            return;
        }

        // 添加 DCT 水印
        Bitmap watermarkedImage = DctTool.dctString(bitmap, watermark);
        Log.d(TAG, "DCT watermark applied: " + watermark);

        photoPreview.setImageBitmap(watermarkedImage);

        // 保存到文件
        File savedFile = BitmapUtils.saveToFile(watermarkedImage, FolderManager.getInstance().getPhotoDctFolder());
        Log.d(TAG, "Watermarked image saved to: " + savedFile);

        // 删除缓存文件
        deleteCacheFile();
        watermarkFlag = WATERMARK_ADDED;
    }

    /**
     * 从 SharedPreferences 获取水印
     */
    private String getWatermarkFromPreferences() {
        SharedPreferences sharedPreferences = getSharedPreferences(SP_WATERMARK_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getString(SP_KEY_WATERMARK, WATERMARK_DEFAULT_VALUE);
    }

    /**
     * 删除缓存照片文件
     */
    private void deleteCacheFile() {
        if (photoFile != null && photoFile.exists()) {
            boolean deleted = photoFile.delete();
            if (!deleted) {
                Log.w(TAG, "Failed to delete cache file: " + photoFile.getAbsolutePath());
            }
        }
    }

    /**
     * 导航到主页面
     */
    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    /**
     * 显示未设置水印的提示对话框
     */
    private void showMissingSettingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.warning);
        builder.setMessage("尚未设置水印信息，请先设置水印信息！");
        builder.setPositiveButton("确定", (dialog, which) -> {
            Intent intent = new Intent(PhotoPreviewActivity.this, SettingActivity.class);
            startActivity(intent);
        });
        builder.show();
    }

    /**
     * 静态方法：启动预览页面
     *
     * @param activity 当前 Activity
     * @param file     要预览的照片文件
     */
    public static void preview(Activity activity, File file) {
        if (activity == null || file == null) {
            return;
        }
        Intent previewIntent = new Intent(activity, PhotoPreviewActivity.class);
        previewIntent.putExtra(EXTRA_PHOTO, file);
        activity.startActivity(previewIntent);
    }

    @Override
    public void onComplete() {
        showToast(R.string.Processing_Complete);
    }

    /**
     * 显示 Toast 提示
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /**
     * 显示 Toast 提示（字符串资源）
     */
    private void showToast(int stringResId) {
        Toast.makeText(this, stringResId, Toast.LENGTH_LONG).show();
    }
}
