/*
 * Copyright 2012 by Christoph Gaffga licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */

package xju.dctcamera.utils.dct.net.watermark;


import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

import xju.dctcamera.utils.dct.com.google.zxing.common.reedsolomon.ReedSolomonException;
import xju.dctcamera.utils.dct.net.util.Bits;

import static android.graphics.Color.red;


public class Watermark {

    /**
     * 可用支付列表
     */
    public final static String VALID_CHARS = " abcdefghijklmnopqrstuvwxyz0123456789.-,:/()?!\"'#*+_%$&=<>[];@§\n";
//    public final static String VALID_CHARS = " abcdefghijklmnopqrstuvwxyz0123456789:\n";

    /**
     * 写raw 弃用
     * @param filename
     * @param data
     * @throws IOException
     */

    public static final void writeRaw(final String filename, final int[][] data) throws IOException {
        final FileOutputStream fos = new FileOutputStream(filename);
        final OutputStream os = new BufferedOutputStream(fos, 1024);
        final DataOutputStream dos = new DataOutputStream(os);
        for (final int[] element : data) {
            for (final int element2 : element) {
                dos.writeByte(element2);
            }
        }
        dos.close();
    }


    /** The width and height of our quantization box in pixels (n-times n pixel per bit). */
    /**
     *量化像素的盒模型大小
     */
    int bitBoxSize = 10;

    /** Number of bytes used for Reed-Solomon error correction. No error correction if zero. */
    /**
     *纠删码的位数，没有错误就是0
     */
    int byteLenErrorCorrection = 6;

    /** Number of bits that could be stored, total, including error correction bits. */
    /**
     * 存储字节的数量
     */
    int maxBitsTotal;

    /** Number of bits for data (excluding error correction). */
    /**
     * 数据字节的大小
     */
    int maxBitsData;

    /** Maximal length in of characters for text messages. */
    /**
     * 文本消息的最大长度
     */
    int maxTextLen;

    /** Opacity of the marks when added to the image. */
    /**
     * 增加水印的透明度
     */
    double opacity = 1.0; /* 1.0 is strongest watermark */

    /** Seed for randomization of the watermark. */
    private long randomizeWatermarkSeed = 19;

    /** Seed for randomization of the embedding. */
    private long randomizeEmbeddingSeed = 24;

    /** Enable some debugging output. */
    /**
     * 是否开启debug
     */
    public static boolean debug = false;

    public Watermark() {
        calculateSizes();
    }

    /**
     *
     * @param boxSize
     * @param errorCorrectionBytes
     * @param opacity
     */

    public Watermark(final int boxSize, final int errorCorrectionBytes, final double opacity) {
        this.bitBoxSize = boxSize;
        this.byteLenErrorCorrection = errorCorrectionBytes;
        this.opacity = opacity;
        calculateSizes();
    }

    /**
     *
     * @param boxSize
     * @param errorCorrectionBytes
     * @param opacity
     * @param seed1
     * @param seed2
     */
    public Watermark(final int boxSize, final int errorCorrectionBytes, final double opacity, final long seed1,
            final long seed2) {
        this.bitBoxSize = boxSize;
        this.byteLenErrorCorrection = errorCorrectionBytes;
        this.opacity = opacity;
        this.randomizeEmbeddingSeed = seed1;
        this.randomizeWatermarkSeed = seed2;
        calculateSizes();
    }

    /**
     *
     * @param seed1
     * @param seed2
     */
    public Watermark(final long seed1, final long seed2) {
        this.randomizeEmbeddingSeed = seed1;
        this.randomizeWatermarkSeed = seed2;
        calculateSizes();
    }

    /**
     *
     * @param bits
     * @return
     */
    private String bits2String(final Bits bits) {
        final StringBuilder buf = new StringBuilder();
        {
            for (int i = 0; i < this.maxTextLen; i++) {
                final int c = (int) bits.getValue(i * 6, 6);
                buf.append(VALID_CHARS.charAt(c));
            }
        }
        return buf.toString();
    }

    /**
     *
     */
    private void calculateSizes() {
        this.maxBitsTotal = 128 / this.bitBoxSize * (128 / this.bitBoxSize);
        this.maxBitsData = this.maxBitsTotal - this.byteLenErrorCorrection * 8;
        this.maxTextLen = this.maxBitsData / 6;

    }

    /**
     * 嵌入方法
     * @param image
     * @param data
     * @return
     */

    public Bitmap embed(final Bitmap  image, final Bits data) {
        Bits bits;
        // make the size fit...
        if (data.size() > this.maxBitsData) {
            bits = new Bits(data.getBits(0, this.maxBitsData));
        } else {
            bits = new Bits(data);
            while (bits.size() < this.maxBitsData) {
                bits.addBit(false);
            }
        }

        // add error correction...
        if (this.byteLenErrorCorrection > 0) {
            bits = Bits.bitsReedSolomonEncode(bits, this.byteLenErrorCorrection);
        }

        // create watermark image...
        final int[][] watermarkBitmap = new int[128][128];
        for (int y = 0; y < 128 / this.bitBoxSize * this.bitBoxSize; y++) {
            for (int x = 0; x < 128 / this.bitBoxSize * this.bitBoxSize; x++) {
                if (bits.size() > x / this.bitBoxSize + y / this.bitBoxSize * (128 / this.bitBoxSize)) {
                    watermarkBitmap[y][x] = bits.getBit(x / this.bitBoxSize + y / this.bitBoxSize
                            * (128 / this.bitBoxSize)) ? 255 : 0;
                }
            }
        }

        if (debug) {
            try {
                writeRaw("water1.raw", watermarkBitmap);
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }

        // embedding...
        int[][] grey = embed(image, watermarkBitmap);

        int imageWidth =image.getWidth();
        int imageHeight = image.getHeight();
//创建新的bitmap
        Bitmap bmp = Bitmap.createBitmap(imageWidth,imageHeight, Bitmap.Config.ARGB_8888);

        // added computed data to original image...
        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
//                final Color color = new Color(image.getRGB(x, y));
//                final float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
                int imagePixel =image.getPixel(x,y);
                int red = Color.red(imagePixel);
                int green = Color.green(imagePixel);
                int blue = Color.blue(imagePixel);
                float[] hsb = RGBtoHSB(red, green, blue, null);
                // adjust brightness of the pixel...
                hsb[2] = (float) (hsb[2] * (1.0 - this.opacity) + grey[y][x] * this.opacity / 255.0);
//                final Color colorNew = new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
//                image.setRGB(x, y, colorNew.getRGB());
                int color = HSBtoRGB(hsb[0], hsb[1], hsb[2]);

                int redDst = Color.red(color);
                int greenDst = Color.green(color);
                int blueDst = Color.blue(color);


                try {
                    bmp.setPixel(x, y, color);
                } catch (Exception e) {
                    e.getMessage();
                }


            }
        }
        return bmp;
    }

    /**
     * 嵌入方法
     * @param src
     * @param water1
     * @return
     */

    private int[][] embed(final Bitmap  src, final int[][] water1) {
        final int width = (src.getWidth() + 7) / 8 * 8;
        final int height = (src.getHeight() + 7) / 8 * 8;

        // original image process
        final int N = 8;
        final int buff1[][] = new int[height][width]; // Original image
        final int buff2[][] = new int[height][width]; // DCT Original image coefficients
        final int buff3[][] = new int[height][width]; // IDCT Original image coefficients

        final int b1[][] = new int[N][N]; // DCT input
        final int b2[][] = new int[N][N]; // DCT output

        final int b3[][] = new int[N][N]; // IDCT input
        final int b4[][] = new int[N][N]; // IDCT output

        // watermark image process
        final int W = 4;
        final int water2[][] = new int[128][128]; // random watermark image
        final int water3[][] = new int[128][128]; // DCT watermark image coefficients

        final int w1[][] = new int[W][W]; // DCT input
        final int w2[][] = new int[W][W]; // DCT output
        final int w3[][] = new int[W][W]; // quantization output
        final int mfbuff1[][] = new int[128][128]; // embed coefficients
        final int mfbuff2[] = new int[width * height]; // 2 to 1

        // random process...
        int a, b, c;
        final int tmp[] = new int[128 * 128];

        // random embed...
        int c1;
        int cc = 0;
        final int tmp1[] = new int[128 * 128];

        // divide 8x8 block...
        int k = 0, l = 0;

        // init buf1 from src image..

        int xTime= 0;
        int yTime =0;

        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
//                final Color color = new Color(src.getRGB(x, y));
//                final float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
//                int R =123;
//                int G = 12;
//                int B =45;
                int R = red(src.getPixel(x,y));
                int G = Color.green(src.getPixel(x,y));
                int B =Color.blue(src.getPixel(x,y));
                final float[] hsb = RGBtoHSB(R, G, B, null);
                // use brightness of the pixel...
                buff1[y][x] = (int) (hsb[2] * 255.0);
                xTime++;
            }
            yTime++;
        }

        // 將 Original image 切成 8*8 的 block 作 DCT 轉換
        // System.out.println("Original image         ---> FDCT");
        for (int y = 0; y < height; y += N) {
            for (int x = 0; x < width; x += N) {
                for (int i = y; i < y + N; i++) {
                    for (int j = x; j < x + N; j++) {
                        b1[k][l] = buff1[i][j];
                        l++;
                    }
                    l = 0;
                    k++;
                }
                k = 0;
                final DCT o1 = new DCT(); // 宣告 DCT 物件
                o1.ForwardDCT(b1, b2); // 引用 DCT class 中,ForwardDCT的方法

                for (int p = y; p < y + N; p++) {
                    for (int q = x; q < x + N; q++) {
                        buff2[p][q] = b2[k][l];
                        l++;
                    }
                    l = 0;
                    k++;
                }
                k = 0;
            }
        }
        // System.out.println("                       OK!      ");

        // watermark image 作 random 處理
        // System.out.println("Watermark image        ---> Random");
        final Random r = new Random(this.randomizeWatermarkSeed); // 設定亂數產生器的seed
        for (int i = 0; i < 128; i++) {
            for (int j = 0; j < 128; j++) {
                while (true) {
                    c = r.nextInt(128 * 128);
                    if (tmp[c] == 0) {
                        break;
                    }
                }
                a = c / 128;
                b = c % 128;
                water2[i][j] = water1[a][b];
                tmp[c] = 1;
            }
        }
        // System.out.println("                       OK!      ");

        // 將 watermark image 切成 4x4 的 block 作 DCT 轉換與quantization
        k = 0;
        l = 0;
        // System.out.println("Watermark image        ---> FDCT & Quantization");
        for (int y = 0; y < 128; y += W) {
            for (int x = 0; x < 128; x += W) {
                for (int i = y; i < y + W; i++) {
                    for (int j = x; j < x + W; j++) {
                        w1[k][l] = water2[i][j];
                        l++;
                    }
                    l = 0;
                    k++;
                }
                k = 0;

                // 宣告 DCT2 物件
                final DCT2 wm1 = new DCT2();

                // 引用DCT2 class 中,ForwardDCT的方法
                wm1.ForwardDCT(w1, w2);

                final Qt qw1 = new Qt(); // 宣告 Qt 物件
                qw1.WaterQt(w2, w3); // 引用Qt class 中,WaterQt的方法

                for (int p = y; p < y + W; p++) {
                    for (int q = x; q < x + W; q++) {
                        water3[p][q] = w3[k][l];
                        l++;
                    }
                    l = 0;
                    k++;
                }
                k = 0;
            }
        }
        // System.out.println("                       OK!      ");

        // Embedding Watermark water3[128][128] -->buff2[512][512]
        // System.out.println("Watermarked image      ---> Embedding");

        // Random Embedding
        final Random r1 = new Random(this.randomizeEmbeddingSeed); // 設定亂數產生器的seed
        for (int i = 0; i < 128; i++) {
            for (int j = 0; j < 128; j++) {
                while (true) {
                    c1 = r1.nextInt(128 * 128);
                    if (tmp1[c1] == 0) {
                        break;
                    }
                }
                a = c1 / 128;
                b = c1 % 128;
                mfbuff1[i][j] = water3[a][b];
                tmp1[c1] = 1;
            }
        }

        // 二維 轉 一維
        ZigZag scan = new ZigZag();
        scan.two2one(mfbuff1, mfbuff2);

        // WriteBack coefficients
        for (int i = 0; i < height; i += N) {
            for (int j = 0; j < width; j += N) {
                buff2[i + 1][j + 4] = mfbuff2[cc];
                cc++;
                buff2[i + 2][j + 3] = mfbuff2[cc];
                cc++;
                buff2[i + 3][j + 2] = mfbuff2[cc];
                cc++;
                buff2[i + 4][j + 1] = mfbuff2[cc];
                cc++;
            }
        }
        cc = 0;
        // System.out.println("                       OK!      ");

        // 將 Watermarked image 切成 8*8 的 block 作 IDCT 轉換
        // System.out.println("Watermarked image      ---> IDCT");
        k = 0;
        l = 0;
        for (int y = 0; y < height; y += N) {
            for (int x = 0; x < width; x += N) {
                for (int i = y; i < y + N; i++) {
                    for (int j = x; j < x + N; j++) {
                        b3[k][l] = buff2[i][j];
                        l++;
                    }
                    l = 0;
                    k++;
                }
                k = 0;

                DCT o2 = new DCT(); // 宣告 DCT 物件
                o2.InverseDCT(b3, b4); // 引用DCT class 中,InverseDCT的方法

                for (int p = y; p < y + N; p++) {
                    for (int q = x; q < x + N; q++) {
                        buff3[p][q] = b4[k][l];
                        l++;
                    }
                    l = 0;
                    k++;
                }
                k = 0;
            }
        }
        // System.out.println("                       OK!      ");

        return buff3;
    }

    /**
     * 嵌入
     * @param image
     * @param data
     * @return
     */

    public Bitmap embed(final Bitmap  image, final String data) {
       return embed(image, string2Bits(data));
    }

    public Bits extractData(final Bitmap  image) throws ReedSolomonException {
        final int[][] extracted = extractRaw(image);

        if (debug) {
            try {
                writeRaw("water2.raw", extracted);
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }

        // black/white the extracted result...
        for (int y = 0; y < 128 / this.bitBoxSize * this.bitBoxSize; y += this.bitBoxSize) {
            for (int x = 0; x < 128 / this.bitBoxSize * this.bitBoxSize; x += this.bitBoxSize) {
                int sum = 0;
                for (int y2 = y; y2 < y + this.bitBoxSize; y2++) {
                    for (int x2 = x; x2 < x + this.bitBoxSize; x2++) {
                        sum += extracted[y2][x2];
                    }
                }
                sum = sum / (this.bitBoxSize * this.bitBoxSize);
                for (int y2 = y; y2 < y + this.bitBoxSize; y2++) {
                    for (int x2 = x; x2 < x + this.bitBoxSize; x2++) {
                        extracted[y2][x2] = sum > 127 ? 255 : 0;
                    }
                }
            }
        }

        if (debug) {
            try {
                writeRaw("water3.raw", extracted);
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }

        // reconstruct bits...
        Bits bits = new Bits();
        for (int y = 0; y < 128 / this.bitBoxSize * this.bitBoxSize; y += this.bitBoxSize) {
            for (int x = 0; x < 128 / this.bitBoxSize * this.bitBoxSize; x += this.bitBoxSize) {
                bits.addBit(extracted[y][x] > 127);
            }
        }
        bits = new Bits(bits.getBits(0, this.maxBitsTotal));

        // if debugging, copy original before error correction...
        Bits bitsBeforeCorrection = null;
        if (debug) {
            bitsBeforeCorrection = new Bits(bits.getBits(0, this.maxBitsData));
        }

        // apply error correction...
        if (this.byteLenErrorCorrection > 0) {
            bits = Bits.bitsReedSolomonDecode(bits, this.byteLenErrorCorrection);
        }

        if (debug) {// count errors (faulty bits)...
            int errors = 0;
            for (int i = 0; i < bitsBeforeCorrection.size(); i++) {
                if (bitsBeforeCorrection.getBit(i) != bits.getBit(i)) {
                    errors++;
                }
            }
            System.out.println("Error Correction:\n" + errors + " bits of " + bitsBeforeCorrection.size()
                    + " are faulty");
        }

        return bits;
    }

    private int[][] extractRaw(final Bitmap  src) {
        final int width = (src.getWidth() + 7) / 8 * 8;
        final int height = (src.getHeight() + 7) / 8 * 8;

        // original image
        final int N = 8;
        final int buff1[][] = new int[height][width]; // watermarked image
        final int buff2[][] = new int[height][width]; // DCT watermarked image coefficients
        final int b1[][] = new int[N][N]; // DCT input
        final int b2[][] = new int[N][N]; // DCT output

        // watermark
        final int W = 4;
        final int water1[][] = new int[128][128]; // extract watermark image
        final int water2[][] = new int[128][128]; // DCT watermark image coefficients
        final int water3[][] = new int[128][128]; // random watermark image

        final int w1[][] = new int[W][W]; // dequantization output
        final int w2[][] = new int[W][W]; // DCT input
        final int w3[][] = new int[W][W]; // DCT output

        // random process
        int a, b, c, c1;
        final int tmp[] = new int[128 * 128];
        final int tmp1[] = new int[128 * 128];
        int cc = 0;

        // middle frequency coefficients
        // final int mfbuff1[] = new int[128 * 128];
        final int mfbuff1[] = new int[width * height];
        final int mfbuff2[][] = new int[128][128]; // 1 to 2

        // divide 8x8 block
        int k = 0, l = 0;

        // init buf1 from watermarked image src...
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
//                final Color color = new Color(src.getRGB(x, y));
//                final float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);

                final float[] hsb = RGBtoHSB(red(src.getPixel(x,y)), Color.green(src.getPixel(x,y)), Color.blue(src.getPixel(x,y)), null);
                // use brightness of the pixel...
                buff1[y][x] = (int) (hsb[2] * 255.0);
            }
        }

        // 將 watermark image 切成 8x8 的 block 作 DCT 轉換
        // System.out.println("Watermarked image         ---> FDCT");
        for (int y = 0; y < height; y += N) {
            for (int x = 0; x < width; x += N) {
                for (int i = y; i < y + N; i++) {
                    for (int j = x; j < x + N; j++) {
                        b1[k][l] = buff1[i][j];
                        l++;
                    }
                    l = 0;
                    k++;
                }
                k = 0;

                final DCT o1 = new DCT(); // 宣告 DCT 物件
                o1.ForwardDCT(b1, b2); // 引用DCT class 中,ForwardDCT的方法

                for (int p = y; p < y + N; p++) {
                    for (int q = x; q < x + N; q++) {
                        buff2[p][q] = b2[k][l];
                        l++;
                    }
                    l = 0;
                    k++;
                }
                k = 0;
            }
        }
        // System.out.println("                       OK!      ");

        // extract middle frequency coefficients...
        // System.out.println("watermark image       ---> Extracting");
        for (int i = 0; i < height; i += N) {
            for (int j = 0; j < width; j += N) {
                mfbuff1[cc] = buff2[i + 1][j + 4];
                cc++;
                mfbuff1[cc] = buff2[i + 2][j + 3];
                cc++;
                mfbuff1[cc] = buff2[i + 3][j + 2];
                cc++;
                mfbuff1[cc] = buff2[i + 4][j + 1];
                cc++;
            }
        }
        cc = 0;

        // 一維 轉 二維
        final ZigZag scan = new ZigZag(); // 宣告 zigZag 物件
        scan.one2two(mfbuff1, mfbuff2); // 引用zigZag class 中,one2two的方法

        // random extracting
        final Random r1 = new Random(this.randomizeEmbeddingSeed);
        for (int i = 0; i < 128; i++) {
            for (int j = 0; j < 128; j++) {
                while (true) {
                    c1 = r1.nextInt(128 * 128);
                    if (tmp1[c1] == 0) {
                        break;
                    }
                }
                a = c1 / 128;
                b = c1 % 128;
                water1[a][b] = mfbuff2[i][j];
                tmp1[c1] = 1;
            }
        }
        // System.out.println("                       OK!      ");

        k = 0;
        l = 0;
        // System.out.println("Watermark image       ---> Dequantization & IDCT");
        for (int y = 0; y < 128; y += W) {
            for (int x = 0; x < 128; x += W) {

                for (int i = y; i < y + W; i++) {
                    for (int j = x; j < x + W; j++) {
                        w1[k][l] = water1[i][j];
                        l++;
                    }
                    l = 0;
                    k++;
                }
                k = 0;

                final Qt qw2 = new Qt(); // 宣告 Qt 物件
                qw2.WaterDeQt(w1, w2); // 引用Qt class 中,WaterDeQt的方法

                final DCT2 wm2 = new DCT2(); // 宣告 DCT2 物件
                wm2.InverseDCT(w2, w3); // 引用DCT2 class 中,InverseDCT的方法
                for (int p = y; p < y + W; p++) {
                    for (int q = x; q < x + W; q++) {
                        water2[p][q] = w3[k][l];
                        l++;
                    }
                    l = 0;
                    k++;
                }
                k = 0;
            }
        }
        // System.out.println("                       OK!      ");

        // System.out.println("Watermark image       ---> Re Random");
        final Random r = new Random(this.randomizeWatermarkSeed); // 設定亂數產生器的seed
        for (int i = 0; i < 128; i++) {
            for (int j = 0; j < 128; j++) {
                while (true) {
                    c = r.nextInt(128 * 128);
                    if (tmp[c] == 0) {
                        break;
                    }
                }
                a = c / 128;
                b = c % 128;
                water3[a][b] = water2[i][j];
                tmp[c] = 1;
            }
        }
        // System.out.println("                       OK!      ");

        return water3;
    }

    public String extractText(final Bitmap  image) throws ReedSolomonException {
        return bits2String(extractData(image)).trim();
    }

    public int getBitBoxSize() {
        return this.bitBoxSize;
    }

    public int getByteLenErrorCorrection() {
        return this.byteLenErrorCorrection;
    }

    public int getMaxBitsData() {
        return this.maxBitsData;
    }

    public int getMaxBitsTotal() {
        return this.maxBitsTotal;
    }

    public int getMaxTextLen() {
        return this.maxTextLen;
    }

    public double getOpacity() {
        return this.opacity;
    }

    public long getRandomizeEmbeddingSeed() {
        return this.randomizeEmbeddingSeed;
    }

    public long getRandomizeWatermarkSeed() {
        return this.randomizeWatermarkSeed;
    }

    /**
     * 字符转bits
     * @param s
     * @return
     */

    private Bits string2Bits(String s) {
        final Bits bits = new Bits();

        Log.d("Android: ", "s长度 " + s.length());

        // remove invalid characters...
        s = s.toLowerCase();
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);     //获取字符串中的下标为i的字符
            if (VALID_CHARS.indexOf(c) < 0) {
                s = s.substring(0, i) + s.substring(i + 1);
                i--;
            }
        }

        // shorten if needed...
        if (s.length() > this.maxTextLen) {
            s = s.substring(0, this.maxTextLen);
        }
        // padding if needed...
        while (s.length() < this.maxTextLen) {
            s += " ";
        }

        // create watermark bits...
        for (int j = 0; j < s.length(); j++) {
            bits.addValue(VALID_CHARS.indexOf(s.charAt(j)), 6);
        }

        return bits;
    }

    /**
     * RGB转HSB
     * @param r
     * @param g
     * @param b
     * @param hsbvals
     * @return
     */

    public  float[] RGBtoHSB(int r, int g, int b, float[] hsbvals) {
        float hue, saturation, brightness;
        if (hsbvals == null) {
            hsbvals = new float[3];
        }
        int cmax = (r > g) ? r : g;
        if (b > cmax) cmax = b;
        int cmin = (r < g) ? r : g;
        if (b < cmin) cmin = b;

        brightness = ((float) cmax) / 255.0f;
        if (cmax != 0)
            saturation = ((float) (cmax - cmin)) / ((float) cmax);
        else
            saturation = 0;
        if (saturation == 0)
            hue = 0;
        else {
            float redc = ((float) (cmax - r)) / ((float) (cmax - cmin));
            float greenc = ((float) (cmax - g)) / ((float) (cmax - cmin));
            float bluec = ((float) (cmax - b)) / ((float) (cmax - cmin));
            if (r == cmax)
                hue = bluec - greenc;
            else if (g == cmax)
                hue = 2.0f + redc - bluec;
            else
                hue = 4.0f + greenc - redc;
            hue = hue / 6.0f;
            if (hue < 0)
                hue = hue + 1.0f;
        }
        hsbvals[0] = hue;
        hsbvals[1] = saturation;
        hsbvals[2] = brightness;
        return hsbvals;
    }

    /**
     * HSB转RGB
     * @param hue
     * @param saturation
     * @param brightness
     * @return
     */
    public  int HSBtoRGB(float hue, float saturation, float brightness) {
        int r = 0, g = 0, b = 0;
        if (saturation == 0) {
            r = g = b = (int) (brightness * 255.0f + 0.5f);
        } else {
            float h = (hue - (float)Math.floor(hue)) * 6.0f;
            float f = h - (float) Math.floor(h);
            float p = brightness * (1.0f - saturation);
            float q = brightness * (1.0f - saturation * f);
            float t = brightness * (1.0f - (saturation * (1.0f - f)));
            switch ((int) h) {
                case 0:
                    r = (int) (brightness * 255.0f + 0.5f);
                    g = (int) (t * 255.0f + 0.5f);
                    b = (int) (p * 255.0f + 0.5f);
                    break;
                case 1:
                    r = (int) (q * 255.0f + 0.5f);
                    g = (int) (brightness * 255.0f + 0.5f);
                    b = (int) (p * 255.0f + 0.5f);
                    break;
                case 2:
                    r = (int) (p * 255.0f + 0.5f);
                    g = (int) (brightness * 255.0f + 0.5f);
                    b = (int) (t * 255.0f + 0.5f);
                    break;
                case 3:
                    r = (int) (p * 255.0f + 0.5f);
                    g = (int) (q * 255.0f + 0.5f);
                    b = (int) (brightness * 255.0f + 0.5f);
                    break;
                case 4:
                    r = (int) (t * 255.0f + 0.5f);
                    g = (int) (p * 255.0f + 0.5f);
                    b = (int) (brightness * 255.0f + 0.5f);
                    break;
                case 5:
                    r = (int) (brightness * 255.0f + 0.5f);
                    g = (int) (p * 255.0f + 0.5f);
                    b = (int) (q * 255.0f + 0.5f);
                    break;
            }
        }
        return 0xff000000 | (r << 16) | (g << 8) | (b << 0);
    }

}
