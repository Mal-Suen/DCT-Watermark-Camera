package xju.dctcamera.utils.bitmap;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Bitmap 工具类
 * <p>
 * 提供 Bitmap 的解码、压缩、旋转、保存、展示等常用操作。
 * </p>
 *
 * @author Belikovvv
 * @since 2017
 */
public final class BitmapUtils {

    private static final String TAG = BitmapUtils.class.getSimpleName();

    /**
     * JPEG 文件后缀
     */
    public static final String JPG_SUFFIX = ".jpg";

    /**
     * 时间戳格式
     */
    private static final String TIME_FORMAT = "yyyyMMddHHmmss";

    /**
     * 媒体扫描广播 Action
     */
    private static final String ACTION_MEDIA_SCANNER_SCAN_FILE = "android.intent.action.MEDIA_SCANNER_SCAN_FILE";

    /**
     * JPEG 压缩质量（100 表示无损）
     */
    private static final int JPEG_QUALITY_HIGH = 100;

    /**
     * Exif 方向标记：正常
     */
    private static final int EXIF_ORIENTATION_NORMAL = 1;

    /**
     * Exif 方向标记：旋转 180 度
     */
    private static final int EXIF_ORIENTATION_ROTATE_180 = 3;

    /**
     * Exif 方向标记：旋转 90 度
     */
    private static final int EXIF_ORIENTATION_ROTATE_90 = 6;

    /**
     * Exif 方向标记：旋转 270 度
     */
    private static final int EXIF_ORIENTATION_ROTATE_270 = 8;

    /**
     * Exif 图片长度标签
     */
    private static final String EXIF_TAG_IMAGE_LENGTH = "ImageLength";

    /**
     * Exif 图片宽度标签
     */
    private static final String EXIF_TAG_IMAGE_WIDTH = "ImageWidth";

    /**
     * Exif 方向标签
     */
    private static final String EXIF_TAG_ORIENTATION = "Orientation";

    /**
     * 默认图片尺寸（当 requestWidth 或 requestHeight 为 0 时使用）
     */
    private static final int DEFAULT_IMAGE_SIZE = 0;

    /**
     * 像素容量倍数（用于计算采样率）
     */
    private static final int TOTAL_PIXELS_MULTIPLIER = 2;

    /**
     * 采样率基数
     */
    private static final int IN_SAMPLE_SIZE_BASE = 1;

    /**
     * 半值基数（用于计算 inSampleSize）
     */
    private static final int HALF_DIVISOR = 2;

    /**
     * 私有构造函数，防止实例化
     */
    private BitmapUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 将图片添加到系统图库并通知媒体扫描
     *
     * @param context   上下文对象
     * @param photoFile 图片文件
     */
    public static void displayToGallery(Context context, File photoFile) {
        if (photoFile == null || !photoFile.exists()) {
            return;
        }

        String photoPath = photoFile.getAbsolutePath();
        String photoName = photoFile.getName();

        try {
            ContentResolver contentResolver = context.getContentResolver();
            MediaStore.Images.Media.insertImage(contentResolver, photoPath, photoName, null);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Failed to insert image to gallery", e);
        }

        // 通知媒体扫描器更新
        Intent scanIntent = new Intent(ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + photoPath));
        context.sendBroadcast(scanIntent);
    }

    /**
     * 将 Bitmap 保存到文件（使用时间戳命名）
     *
     * @param bitmap Bitmap 对象
     * @param folder 保存目录
     * @return 保存的文件，失败时返回 null
     */
    public static File saveToFile(Bitmap bitmap, File folder) {
        if (bitmap == null) {
            return null;
        }
        String fileName = new SimpleDateFormat(TIME_FORMAT, Locale.getDefault()).format(new Date());
        return saveToFile(bitmap, folder, fileName);
    }

    /**
     * 将 Bitmap 保存到文件
     *
     * @param bitmap   Bitmap 对象
     * @param folder   保存目录
     * @param fileName 文件名（不含扩展名）
     * @return 保存的文件，失败时返回 null
     */
    public static File saveToFile(Bitmap bitmap, File folder, String fileName) {
        if (bitmap == null || folder == null || fileName == null) {
            return null;
        }

        if (!folder.exists()) {
            boolean created = folder.mkdirs();
            if (!created) {
                Log.e(TAG, "Failed to create folder: " + folder.getAbsolutePath());
                return null;
            }
        }

        File file = new File(folder, fileName + JPG_SUFFIX);
        if (file.exists()) {
            boolean deleted = file.delete();
            if (!deleted) {
                Log.e(TAG, "Failed to delete existing file: " + file.getAbsolutePath());
            }
        }

        BufferedOutputStream outputStream = null;
        try {
            boolean created = file.createNewFile();
            if (!created) {
                return null;
            }
            outputStream = new BufferedOutputStream(new FileOutputStream(file));
            bitmap.compress(CompressFormat.JPEG, JPEG_QUALITY_HIGH, outputStream);
            outputStream.flush();
            return file;
        } catch (IOException e) {
            Log.e(TAG, "Failed to save bitmap to file", e);
            return null;
        } finally {
            closeStream(outputStream);
        }
    }

    /**
     * 获取图片的旋转角度
     *
     * @param path 图片文件路径
     * @return 旋转角度（0、90、180、270）
     */
    public static int getBitmapDegree(String path) {
        if (TextUtils.isEmpty(path)) {
            return 0;
        }

        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(EXIF_TAG_ORIENTATION, EXIF_ORIENTATION_NORMAL);
            switch (orientation) {
                case EXIF_ORIENTATION_ROTATE_180:
                    return 180;
                case EXIF_ORIENTATION_ROTATE_90:
                    return 90;
                case EXIF_ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to get bitmap degree", e);
            return 0;
        }
    }

    /**
     * 按角度旋转 Bitmap
     *
     * @param bitmap 原始 Bitmap
     * @param degree 旋转角度
     * @return 旋转后的 Bitmap
     */
    public static Bitmap rotateBitmapByDegree(Bitmap bitmap, int degree) {
        if (bitmap == null) {
            return null;
        }
        if (degree == 0) {
            return bitmap;
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        // 回收原始 Bitmap（如果未回收）
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }

        return rotatedBitmap;
    }

    /**
     * 从文件解码 Bitmap（按指定尺寸缩放）
     *
     * @param imageFile     图片文件
     * @param requestWidth  目标宽度
     * @param requestHeight 目标高度
     * @return 解码后的 Bitmap
     */
    public static Bitmap decodeBitmapFromFile(File imageFile, int requestWidth, int requestHeight) {
        if (imageFile == null) {
            return null;
        }
        return decodeBitmapFromFile(imageFile.getAbsolutePath(), requestWidth, requestHeight);
    }

    /**
     * 从文件路径解码 Bitmap（按指定尺寸缩放）
     *
     * @param imagePath     图片文件路径
     * @param requestWidth  目标宽度
     * @param requestHeight 目标高度
     * @return 解码后的 Bitmap
     */
    public static Bitmap decodeBitmapFromFile(String imagePath, int requestWidth, int requestHeight) {
        if (TextUtils.isEmpty(imagePath)) {
            return null;
        }

        Log.i(TAG, "requestWidth: " + requestWidth);
        Log.i(TAG, "requestHeight: " + requestHeight);

        if (requestWidth > DEFAULT_IMAGE_SIZE && requestHeight > DEFAULT_IMAGE_SIZE) {
            Options options = createBitmapOptions(imagePath);
            options.inSampleSize = calculateInSampleSize(options, requestWidth, requestHeight);
            Log.i(TAG, "inSampleSize: " + options.inSampleSize);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(imagePath, options);
        } else {
            return BitmapFactory.decodeFile(imagePath);
        }
    }

    /**
     * 从资源解码 Bitmap（按指定尺寸缩放）
     *
     * @param res       资源对象
     * @param resId     资源 ID
     * @param reqWidth  目标宽度
     * @param reqHeight 目标高度
     * @return 解码后的 Bitmap
     */
    public static Bitmap decodeBitmapFromResource(Resources res, int resId, int reqWidth, int reqHeight) {
        Options options = new Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    /**
     * 从文件描述符解码 Bitmap（按指定尺寸缩放）
     *
     * @param fileDescriptor 文件描述符
     * @param reqWidth       目标宽度
     * @param reqHeight      目标高度
     * @return 解码后的 Bitmap
     */
    public static Bitmap decodeBitmapFromDescriptor(FileDescriptor fileDescriptor, int reqWidth, int reqHeight) {
        Options options = new Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);
    }

    /**
     * 计算采样率（用于 Bitmap 压缩）
     *
     * @param options    Bitmap 选项
     * @param reqWidth   目标宽度
     * @param reqHeight  目标高度
     * @return 采样率值
     */
    public static int calculateInSampleSize(Options options, int reqWidth, int reqHeight) {
        if (options == null) {
            return IN_SAMPLE_SIZE_BASE;
        }

        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = IN_SAMPLE_SIZE_BASE;

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / HALF_DIVISOR;
            int halfWidth = width / HALF_DIVISOR;

            while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= HALF_DIVISOR;
            }

            long totalPixels = (long) (width * height / inSampleSize);
            long totalReqPixelsCap = (long) reqWidth * reqHeight * TOTAL_PIXELS_MULTIPLIER;

            while (totalPixels > totalReqPixelsCap) {
                inSampleSize *= HALF_DIVISOR;
                totalPixels /= HALF_DIVISOR;
            }
        }

        return inSampleSize;
    }

    /**
     * 压缩 Bitmap
     *
     * @param filePath 图片文件路径
     * @param rate     压缩质量（1-100）
     * @param width    目标宽度
     * @param height   目标高度
     * @return 压缩后的 Bitmap
     */
    public static Bitmap getSmallBitmap(String filePath, int rate, int width, int height) {
        if (TextUtils.isEmpty(filePath)) {
            return null;
        }

        Options options = new Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);

        options.inSampleSize = calculateInSampleSize(options, width, height);
        options.inJustDecodeBounds = false;

        Bitmap bitmap = BitmapFactory.decodeFile(filePath, options);
        if (bitmap == null) {
            return null;
        }

        // 注意：此方法仅用于质量压缩，不会减小 Bitmap 内存占用
        // 如果需要减小内存占用，请使用 decodeBitmapFromFile 方法
        ByteArrayOutputStream baos = null;
        try {
            baos = new ByteArrayOutputStream();
            bitmap.compress(CompressFormat.JPEG, rate, baos);
        } catch (Exception e) {
            Log.e(TAG, "Failed to compress bitmap", e);
        } finally {
            closeStream(baos);
        }
        return bitmap;
    }

    /**
     * 创建 Bitmap 解码选项并读取图片边界
     *
     * @param imagePath 图片路径
     * @return Bitmap 选项
     */
    private static Options createBitmapOptions(String imagePath) {
        Options options = new Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imagePath, options);

        Log.i(TAG, "original height: " + options.outHeight);
        Log.i(TAG, "original width: " + options.outWidth);

        // 如果无法从文件头获取尺寸，尝试从 EXIF 中读取
        if (options.outHeight == -1 || options.outWidth == -1) {
            readExifDimensions(options, imagePath);
        }

        return options;
    }

    /**
     * 从 EXIF 中读取图片尺寸
     *
     * @param options   Bitmap 选项
     * @param imagePath 图片路径
     */
    private static void readExifDimensions(Options options, String imagePath) {
        try {
            ExifInterface exifInterface = new ExifInterface(imagePath);
            int height = exifInterface.getAttributeInt(EXIF_TAG_IMAGE_LENGTH, -1);
            int width = exifInterface.getAttributeInt(EXIF_TAG_IMAGE_WIDTH, -1);
            Log.i(TAG, "exif height: " + height);
            Log.i(TAG, "exif width: " + width);
            options.outWidth = width;
            options.outHeight = height;
        } catch (IOException e) {
            Log.e(TAG, "Failed to read EXIF dimensions", e);
        }
    }

    /**
     * 安全关闭流
     *
     * @param stream 要关闭的流
     */
    private static void closeStream(ByteArrayOutputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close stream", e);
            }
        }
    }

    /**
     * 安全关闭流
     *
     * @param stream 要关闭的流
     */
    private static void closeStream(BufferedOutputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close stream", e);
            }
        }
    }
}
