package xju.dctcamera.utils;

import android.os.Handler;
import android.os.Looper;
import android.widget.Button;

import java.util.Random;

/**
 * 进度生成器
 * <p>
 * 模拟进度条效果，用于按钮展示处理进度
 * </p>
 */
public class ProgressGenerator {

    /**
     * 进度增量（每次增加 10%）
     */
    private static final int PROGRESS_INCREMENT = 10;

    /**
     * 完成进度值
     */
    private static final int PROGRESS_COMPLETE = 100;

    /**
     * 最大延迟（毫秒）
     */
    private static final int MAX_DELAY_MS = 1000;

    /**
     * 完成监听器
     */
    public interface OnCompleteListener {
        /**
         * 进度完成时回调
         */
        void onComplete();
    }

    /**
     * 完成监听器
     */
    private final OnCompleteListener onCompleteListener;

    /**
     * 当前进度
     */
    private int currentProgress;

    /**
     * 随机数生成器
     */
    private final Random randomGenerator;

    /**
     * 构造函数
     *
     * @param listener 完成监听器
     */
    public ProgressGenerator(OnCompleteListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        this.onCompleteListener = listener;
        this.randomGenerator = new Random();
    }

    /**
     * 开始进度（使用标准 Android Button）
     *
     * @param button 要显示进度的按钮
     */
    public void start(final Button button) {
        if (button == null) {
            throw new IllegalArgumentException("Button cannot be null");
        }

        currentProgress = 0;
        final Handler handler = new Handler(Looper.getMainLooper());

        handler.post(new Runnable() {
            @Override
            public void run() {
                currentProgress += PROGRESS_INCREMENT;
                button.setText(currentProgress + "%");

                if (currentProgress < PROGRESS_COMPLETE) {
                    handler.postDelayed(this, generateRandomDelay());
                } else {
                    onCompleteListener.onComplete();
                }
            }
        });
    }

    /**
     * 生成随机延迟时间
     *
     * @return 延迟时间（毫秒）
     */
    private int generateRandomDelay() {
        return randomGenerator.nextInt(MAX_DELAY_MS);
    }
}
