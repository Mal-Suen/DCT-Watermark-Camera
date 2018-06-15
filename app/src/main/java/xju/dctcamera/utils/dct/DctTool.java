package xju.dctcamera.utils.dct;

import android.graphics.Bitmap;

import xju.dctcamera.utils.dct.net.watermark.Watermark;


/**
 * DctTool工具类
 *直接电影工具类方法，即可实现dct算法
 *
 */
public class DctTool {
//测试之后的参数，请测试后改动。
    /**
     * 量化像素的盒模型大小
     */
    private static int boxSize = 7;
    /**
     * 纠删码的位数，没有错误就是0
     */
    private static int errorCorrection = 0;
    /**
     * 增加水印的透明度
     */
    private static double opacity = 0.5;
    /**
     * 水印随机取样
     */
    private static long seed1 = 10;
    /**
     * 嵌入随机取样
     */
    private static long seed2 = 15;


    /**
     * 对bitmap加bitmap水印
     *
     * @param imageSrc
     * @param watermark
     */
    public static void dctImage(Bitmap imageSrc, Bitmap watermark) {
        Watermark water = new Watermark(boxSize, errorCorrection, opacity, seed1, seed2);

    }

    /**
     * 对bitmap加string水印
     *
     * @param Bitmap
     * @param watermark
     * @return
     */
    public static Bitmap dctString(Bitmap Bitmap, String watermark) {
        Watermark water = new Watermark(boxSize, errorCorrection, opacity, seed1, seed2);
        Bitmap dstImage = water.embed(Bitmap, watermark);
        return dstImage;
    }

    /**
     * 对bitmap取sting水印
     *
     * @param imageDst
     * @return
     */
    public static String unDctString(Bitmap imageDst) {
        Watermark water = new Watermark(boxSize, errorCorrection, opacity, seed1, seed2);
        try {
            String message = water.extractText(imageDst);
            return message;
        } catch (Exception e) {
            e.getMessage();
        }
        return "取水印error，请重新尝试";

    }

    /**
     * 对bitmap 取bitmap水印
     *
     * @param imageDst
     */
    public static void unDctImage(Bitmap imageDst) {
        Watermark water = new Watermark(boxSize, errorCorrection, opacity, seed1, seed2);
        try {
            String message = water.extractText(imageDst);
        } catch (Exception e) {
            e.getMessage();
        }

    }
}

