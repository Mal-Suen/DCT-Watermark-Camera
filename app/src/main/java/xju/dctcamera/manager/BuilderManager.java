package xju.dctcamera.manager;

/**
 * 构建器管理器（已废弃）
 * <p>
 * 原计划用于管理 BoomMenu 构建器，但由于依赖未安装，
 * 此功能已废弃。如需使用菜单功能，请添加 BoomMenu 依赖后重新实现。
 * </p>
 *
 * @deprecated 此功能已废弃，BoomMenu 依赖未安装。
 */
@Deprecated
public final class BuilderManager {

    private static final BuilderManager INSTANCE = new BuilderManager();

    /**
     * 获取单例实例
     *
     * @return BuilderManager 单例
     */
    public static BuilderManager getInstance() {
        return INSTANCE;
    }

    /**
     * 私有构造函数，防止实例化
     */
    private BuilderManager() {
        // TODO: 如需使用 BoomMenu，请添加依赖后在此处初始化
    }
}
