package xju.dctcamera;

import android.app.Activity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Activity 容器管理类
 * <p>
 * 使用 WeakReference 防止 Activity 内存泄漏
 * 提供线程安全的 Activity 管理方法
 * </p>
 *
 * Created by Belikovvv on 2017/7/26.
 */
public class AtyContainer {

    private static final String TAG = "AtyContainer";

    private static volatile AtyContainer instance;
    private static final List<WeakReference<Activity>> activityStack = new ArrayList<>();

    private AtyContainer() {
    }

    /**
     * 获取单例实例
     */
    public static AtyContainer getInstance() {
        if (instance == null) {
            synchronized (AtyContainer.class) {
                if (instance == null) {
                    instance = new AtyContainer();
                }
            }
        }
        return instance;
    }

    /**
     * 添加 Activity 到栈
     *
     * @param activity 要添加的 Activity
     */
    public synchronized void addActivity(Activity activity) {
        if (activity != null) {
            activityStack.add(new WeakReference<>(activity));
            cleanRecycledActivities();
        }
    }

    /**
     * 从栈中移除 Activity
     *
     * @param activity 要移除的 Activity
     */
    public synchronized void removeActivity(Activity activity) {
        if (activity != null) {
            Iterator<WeakReference<Activity>> iterator = activityStack.iterator();
            while (iterator.hasNext()) {
                WeakReference<Activity> ref = iterator.next();
                Activity activityFromRef = ref.get();
                if (activityFromRef == null || activityFromRef == activity) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * 获取当前 Activity（栈顶）
     *
     * @return 当前 Activity，如果不存在则返回 null
     */
    public synchronized Activity getCurrentActivity() {
        cleanRecycledActivities();
        if (activityStack.isEmpty()) {
            return null;
        }
        WeakReference<Activity> ref = activityStack.get(activityStack.size() - 1);
        return ref.get();
    }

    /**
     * 结束所有 Activity
     */
    public synchronized void finishAllActivity() {
        for (WeakReference<Activity> ref : activityStack) {
            Activity activity = ref.get();
            if (activity != null && !activity.isFinishing()) {
                activity.finish();
            }
        }
        activityStack.clear();
    }

    /**
     * 清理已被 GC 回收的 Activity 引用
     */
    private synchronized void cleanRecycledActivities() {
        activityStack.removeIf(ref -> ref.get() == null || ref.get().isFinishing());
    }

    /**
     * 获取当前存活的 Activity 数量
     */
    public synchronized int getAliveActivityCount() {
        cleanRecycledActivities();
        return activityStack.size();
    }
}
