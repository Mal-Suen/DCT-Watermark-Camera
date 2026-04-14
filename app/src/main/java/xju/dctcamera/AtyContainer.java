package xju.dctcamera;

import android.app.Activity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Belikovvv on 2017/7/26.
 */

public class AtyContainer {

    private AtyContainer() {
    }

    private static AtyContainer instance = new AtyContainer();
    private static List<Activity> activityStack = new ArrayList<Activity>();

    public static AtyContainer getInstance() {
        return instance;
    }

    public void addActivity(Activity aty) {
        activityStack.add(aty);
    }

    public void removeActivity(Activity aty) {
        activityStack.remove(aty);
    }

    /**
     * 获取当前Activity（栈顶）
     */
    public Activity getCurrentActivity() {
        if (activityStack.isEmpty()) {
            return null;
        }
        return activityStack.get(activityStack.size() - 1);
    }

    /**
     * 结束所有Activity
     */
    public void finishAllActivity() {
        for (int i = 0, size = activityStack.size(); i < size; i++) {
            if (null != activityStack.get(i)) {
                activityStack.get(i).finish();
            }
        }
        activityStack.clear();
    }
}

