package top.cywu.magicops.core.constants;

/**
 * Console 到 Runtime 的发布包推送协议常量（切片 32）。
 *
 * <p>放在 core 模块供 Console（发送方）与 Runtime（接收方）共享，
 * 避免跨应用模块直接依赖。
 */
public final class PushProtocol {

    /** 推送共享密钥请求头。值由环境变量 MAGICOPS_RUNTIME_SHARED_SECRET 提供。 */
    public static final String PUSH_SECRET_HEADER = "X-MagicOps-Push-Secret";

    /** Runtime 收包端点路径。 */
    public static final String PACKAGE_RECEIVE_PATH = "/api/packages";

    /** Runtime 下线端点路径（切片 33-d）。Console 推送下线指令到此路径。 */
    public static final String PACKAGE_DEACTIVATE_PATH = "/api/packages/deactivate";

    private PushProtocol() {
    }
}
