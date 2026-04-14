package xju.dctcamera.manager;

// TODO: 需要添加 BoomMenu 库依赖后才能使用完整功能
// import com.nightonke.boommenu.*;

/**
 * BuilderManager - 临时空壳版本
 * 完整功能需要添加 BoomMenu 依赖
 */
public class BuilderManager {

    private static BuilderManager ourInstance = new BuilderManager();

    public static BuilderManager getInstance() {
        return ourInstance;
    }

    private BuilderManager() {
    }
}
