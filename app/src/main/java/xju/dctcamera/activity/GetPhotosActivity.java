package xju.dctcamera.activity;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;

import xju.dctcamera.AtyContainer;
import xju.dctcamera.R;

/**
 * 图片浏览 Activity（已废弃）
 * <p>
 * 用于浏览和查看存储在外部目录中的图片。
 * 注意：此类使用旧的文件路径，建议迁移到使用 FolderManager 的新路径。
 * </p>
 *
 * @author Belikovvv
 * @since 2017/5/3
 * @deprecated 建议使用 ListViewActivity 替代
 */
@Deprecated
public class GetPhotosActivity extends Activity implements MediaScannerConnection.MediaScannerConnectionClient {

    private static final String TAG = "GetPhotosActivity";

    /**
     * 旧版图片目录路径
     */
    private static final String LEGACY_PHOTO_PATH = "/xju.digitalwatermark/DCTphoto";

    /**
     * 媒体扫描文件类型
     */
    private static final String FILE_TYPE_IMAGE = "image/*";

    /**
     * 所有文件列表
     */
    private File[] allFiles;

    /**
     * 要扫描的文件路径
     */
    private String scanFilePath;

    /**
     * 媒体扫描连接
     */
    private MediaScannerConnection mediaScannerConnection;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_get);
        AtyContainer.getInstance().addActivity(this);

        loadFiles();
        setupScanButton();
    }

    /**
     * 加载文件列表
     */
    private void loadFiles() {
        File folder = new File(Environment.getExternalStorageDirectory().getPath() + LEGACY_PHOTO_PATH);
        if (!folder.exists() || !folder.isDirectory()) {
            showToast("图片目录不存在");
            allFiles = new File[0];
            return;
        }

        File[] files = folder.listFiles();
        allFiles = files != null ? files : new File[0];

        for (int i = 0; i < allFiles.length; i++) {
            Log.d(TAG, "File " + i + ": " + allFiles[i].getAbsolutePath());
        }
    }

    /**
     * 设置扫描按钮
     */
    private void setupScanButton() {
        Button scanButton = findViewById(R.id.get_photo_button);
        scanButton.setOnClickListener(v -> startMediaScan());
    }

    /**
     * 开始媒体扫描
     */
    private void startMediaScan() {
        if (allFiles == null || allFiles.length == 0) {
            showToast("没有可扫描的文件");
            return;
        }

        if (mediaScannerConnection != null) {
            mediaScannerConnection.disconnect();
        }

        scanFilePath = allFiles[0].getAbsolutePath();
        mediaScannerConnection = new MediaScannerConnection(this, this);
        mediaScannerConnection.connect();
        Log.d(TAG, "Connecting to MediaScanner");
    }

    @Override
    public void onMediaScannerConnected() {
        Log.d(TAG, "MediaScanner connected, scanning: " + scanFilePath);
        mediaScannerConnection.scanFile(scanFilePath, FILE_TYPE_IMAGE);
    }

    @Override
    public void onScanCompleted(String path, Uri uri) {
        try {
            Log.d(TAG, "Scan completed: " + uri);
            if (uri != null) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(uri);
                startActivity(intent);
            }
        } finally {
            disconnectMediaScanner();
        }
    }

    /**
     * 断开媒体扫描连接
     */
    private void disconnectMediaScanner() {
        if (mediaScannerConnection != null) {
            mediaScannerConnection.disconnect();
            mediaScannerConnection = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectMediaScanner();
    }

    /**
     * 显示 Toast 提示
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
