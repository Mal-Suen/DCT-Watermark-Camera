package xju.dctcamera.helper;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import xju.dctcamera.manager.FolderManager;

/**
 * 拍照帮助类
 * <p>
 * 处理拍照逻辑，包括文件创建、Intent 启动、结果处理
 * </p>
 */
public class CapturePhotoHelper {

    /**
     * 拍照请求码
     */
    public static final int CAPTURE_PHOTO_REQUEST_CODE = 0x1;

    /**
     * 照片文件名前缀
     */
    private static final String PHOTO_FILE_PREFIX = "IMG_";

    /**
     * 照片文件名后缀
     */
    private static final String PHOTO_FILE_SUFFIX = ".jpg";

    /**
     * 日期格式（用于文件名）
     */
    private static final String DATE_FORMAT_PATTERN = "yyyyMMdd_HHmmss";

    /**
     * Activity 实例
     */
    private final Activity activity;

    /**
     * 照片存储目录
     */
    private final File photoFolder;

    /**
     * 当前拍摄的照片文件
     */
    private File currentPhotoFile;

    /**
     * 构造函数
     *
     * @param activity    Activity 实例
     * @param photoFolder 照片存储目录
     */
    public CapturePhotoHelper(Activity activity, File photoFolder) {
        if (activity == null) {
            throw new IllegalArgumentException("Activity cannot be null");
        }
        this.activity = activity;
        this.photoFolder = photoFolder;
    }

    /**
     * 启动拍照
     *
     * @return 如果成功启动返回 true，否则返回 false
     */
    public boolean capture() {
        if (!isCameraAvailable()) {
            showToast("无法打开相机");
            return false;
        }

        // 创建照片文件
        currentPhotoFile = createPhotoFile();
        if (currentPhotoFile == null) {
            showToast("无法创建照片文件");
            return false;
        }

        // 启动拍照 Intent
        Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        Uri photoUri = Uri.fromFile(currentPhotoFile);
        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);

        try {
            activity.startActivityForResult(captureIntent, CAPTURE_PHOTO_REQUEST_CODE);
            return true;
        } catch (Exception e) {
            showToast("启动相机失败");
            return false;
        }
    }

    /**
     * 获取当前照片文件
     *
     * @return 当前照片文件
     */
    public File getPhoto() {
        return currentPhotoFile;
    }

    /**
     * 设置当前照片文件（用于状态恢复）
     *
     * @param photoFile 照片文件
     */
    public void setPhoto(File photoFile) {
        this.currentPhotoFile = photoFile;
    }

    /**
     * 检查相机是否可用
     *
     * @return 如果相机可用返回 true
     */
    private boolean isCameraAvailable() {
        PackageManager packageManager = activity.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    /**
     * 创建照片文件
     *
     * @return 创建的照片文件，失败返回 null
     */
    private File createPhotoFile() {
        if (photoFolder == null) {
            return null;
        }

        // 确保目录存在
        if (!photoFolder.exists() && !photoFolder.mkdirs()) {
            return null;
        }

        // 生成文件名
        String timeStamp = new SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.getDefault())
                .format(new Date());
        String fileName = PHOTO_FILE_PREFIX + timeStamp + PHOTO_FILE_SUFFIX;

        return new File(photoFolder, fileName);
    }

    /**
     * 显示 Toast 提示
     *
     * @param message 提示消息
     */
    private void showToast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
