package xju.dctcamera.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5 加密工具类
 * <p>
 * 提供字符串到 MD5 哈希的转换
 * </p>
 */
public final class MD5Util {

    /**
     * 字节掩码（用于 byte 转 int）
     */
    private static final int BYTE_MASK = 0xff;

    /**
     * 十六进制字符表
     */
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /**
     * 私有构造函数，防止实例化
     */
    private MD5Util() {
        throw new UnsupportedOperationException("Utility class, do not instantiate");
    }

    /**
     * 计算字符串的 MD5 值
     *
     * @param content 要加密的字符串
     * @return MD5 哈希值（32位十六进制字符串），如果加密失败则返回 null
     */
    public static String getMD5(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] inputBytes = content.getBytes(StandardCharsets.UTF_8);
            byte[] hashBytes = messageDigest.digest(inputBytes);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // MD5 算法在所有 Android 设备上都可用，此处不应发生
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int intVal = b & BYTE_MASK;
            if (intVal < 0x10) {
                hexString.append('0');
            }
            hexString.append(HEX_CHARS[intVal >> 4]);
            hexString.append(HEX_CHARS[intVal & 0x0f]);
        }
        return hexString.toString();
    }
}
