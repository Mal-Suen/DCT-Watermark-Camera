package xju.dctcamera.utils.common;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.TypedValue;

/**
 * 屏幕尺寸与单位转换工具类
 * <p>
 * 提供屏幕宽高获取、DP/SP 到 PX 的单位转换功能。
 * </p>
 *
 * @author Belikovvv
 * @since 2017/5/2
 */
public final class RuleUtils {

    /**
     * TypedValue.COMPLEX_UNIT_DIP 的整数值
     */
    private static final int UNIT_DIP = 1;

    /**
     * TypedValue.COMPLEX_UNIT_SP 的整数值
     */
    private static final int UNIT_SP = 2;

    /**
     * 私有构造函数，防止实例化
     */
    private RuleUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 获取屏幕宽度（像素）
     *
     * @param context 上下文对象，不可为 null
     * @return 屏幕宽度像素值
     */
    public static int getScreenWidth(Context context) {
        DisplayMetrics displayMetrics = getDisplayMetrics(context);
        return displayMetrics.widthPixels;
    }

    /**
     * 获取屏幕高度（像素）
     *
     * @param context 上下文对象，不可为 null
     * @return 屏幕高度像素值
     */
    public static int getScreenHeight(Context context) {
        DisplayMetrics displayMetrics = getDisplayMetrics(context);
        return displayMetrics.heightPixels;
    }

    /**
     * 将 DP 转换为 PX
     *
     * @param context 上下文对象，不可为 null
     * @param dp      DP 值
     * @return 对应的 PX 值
     */
    public static float convertDpToPx(Context context, int dp) {
        DisplayMetrics metrics = getDisplayMetrics(context);
        return TypedValue.applyDimension(UNIT_DIP, dp, metrics);
    }

    /**
     * 将 SP 转换为 PX
     *
     * @param context 上下文对象，不可为 null
     * @param sp      SP 值
     * @return 对应的 PX 值
     */
    public static float convertSpToPx(Context context, int sp) {
        DisplayMetrics metrics = getDisplayMetrics(context);
        return TypedValue.applyDimension(UNIT_SP, sp, metrics);
    }

    /**
     * 获取 DisplayMetrics 对象
     *
     * @param context 上下文对象
     * @return DisplayMetrics 实例
     */
    private static DisplayMetrics getDisplayMetrics(Context context) {
        return context.getResources().getDisplayMetrics();
    }
}
