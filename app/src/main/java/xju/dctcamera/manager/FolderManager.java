package xju.dctcamera.manager;

import android.content.Context;
import android.os.Environment;

import java.io.File;

/**
 * 目录管理器
 * <p>
 * 管理应用相关的文件目录
 * 优先使用应用专属目录（无需权限）
 * </p>
 *
 * Created by Clock on 2016/5/27.
 */
public final class FolderManager {

    /**
     * 应用程序主目录名称
     */
    private static final String APP_FOLDER_NAME = "xju.digitalwatermark";

    /**
     * 照片目录名称
     */
    private static final String PHOTO_FOLDER_NAME = "photo";

    /**
     * DCT 处理后照片目录名称
     */
    private static final String PHOTO_DCT_FOLDER_NAME = "DCTphoto";

    /**
     * 闪退日志目录名称
     */
    private static final String CRASH_LOG_FOLDER_NAME = "crash";

    /**
     * 单例实例
     */
    private static volatile FolderManager instance;

    /**
     * 应用上下文（用于获取外部存储目录）
     */
    private Context applicationContext;

    private FolderManager() {
    }

    /**
     * 获取单例实例
     *
     * @return FolderManager 实例
     */
    public static FolderManager getInstance() {
        if (instance == null) {
            synchronized (FolderManager.class) {
                if (instance == null) {
                    instance = new FolderManager();
                }
            }
        }
        return instance;
    }

    /**
     * 初始化（应在 Application 或 MainActivity 中调用）
     *
     * @param context 应用上下文
     */
    public void initialize(Context context) {
        if (context != null) {
            this.applicationContext = context.getApplicationContext();
        }
    }

    /**
     * 获取应用主目录
     * <p>
     * 优先使用应用专属目录（无需存储权限）
     * </p>
     *
     * @return 应用主目录，失败返回 null
     */
    public File getAppFolder() {
        // 优先使用应用专属目录
        if (applicationContext != null) {
            File appFolder = applicationContext.getExternalFilesDir(null);
            if (appFolder != null) {
                return ensureDirectoryExists(appFolder);
            }
        }

        // 备用方案：使用公共图片目录
        if (isExternalStorageAvailable()) {
            File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File appFolder = new File(picturesDir, APP_FOLDER_NAME);
            return ensureDirectoryExists(appFolder);
        }

        return null;
    }

    /**
     * 获取照片存储目录
     *
     * @return 照片目录，失败返回 null
     */
    public File getPhotoFolder() {
        return createSubDirectory(getAppFolder(), PHOTO_FOLDER_NAME);
    }

    /**
     * 获取 DCT 处理后照片存储目录
     *
     * @return DCT 照片目录，失败返回 null
     */
    public File getPhotoDctFolder() {
        return createSubDirectory(getAppFolder(), PHOTO_DCT_FOLDER_NAME);
    }

    /**
     * 获取闪退日志存储目录
     *
     * @return 日志目录，失败返回 null
     */
    public File getCrashLogFolder() {
        return createSubDirectory(getAppFolder(), CRASH_LOG_FOLDER_NAME);
    }

    /**
     * 在指定父目录下创建子目录
     *
     * @param parentDir   父目录
     * @param subDirName  子目录名称
     * @return 创建后的子目录 File 对象，失败返回 null
     */
    private File createSubDirectory(File parentDir, String subDirName) {
        if (parentDir == null || subDirName == null || subDirName.isEmpty()) {
            return null;
        }

        File subDir = new File(parentDir, subDirName);
        return ensureDirectoryExists(subDir);
    }

    /**
     * 确保目录存在，如果不存在则创建
     *
     * @param folder 目录路径
     * @return 如果目录存在或创建成功则返回 File 对象，否则返回 null
     */
    private static File ensureDirectoryExists(File folder) {
        if (folder == null) {
            return null;
        }

        if (!folder.exists()) {
            boolean created = folder.mkdirs();
            if (!created && !folder.exists()) {
                return null;
            }
        }

        return folder;
    }

    /**
     * 检查外部存储是否可用
     *
     * @return 如果外部存储已挂载则返回 true
     */
    private boolean isExternalStorageAvailable() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }
}
