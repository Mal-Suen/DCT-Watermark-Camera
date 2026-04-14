package xju.dctcamera.utils;

import android.os.Handler;

import java.util.Random;

/**
 * ProgressGenerator - 进度生成器
 * 注: 原 ProcessButton 依赖已移除，现在使用标准 Android Button
 */
public class ProgressGenerator {

    public interface OnCompleteListener {
        void onComplete();
    }

    private OnCompleteListener mListener;
    private int mProgress;

    public ProgressGenerator(OnCompleteListener listener) {
        mListener = listener;
    }

    /**
     * 开始进度 (使用标准 Android Button)
     */
    public void start(final android.widget.Button button) {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mProgress += 10;
                // 标准 Button 没有 setProgress，使用 setText 显示进度
                button.setText(mProgress + "%");
                if (mProgress < 100) {
                    handler.postDelayed(this, generateDelay());
                } else {
                    mListener.onComplete();
                }
            }
        }, generateDelay());
    }

    private Random random = new Random();

    private int generateDelay() {
        return random.nextInt(1000);
    }
}
