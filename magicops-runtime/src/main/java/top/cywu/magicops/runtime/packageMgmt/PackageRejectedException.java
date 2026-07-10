package top.cywu.magicops.runtime.packageMgmt;

/**
 * 发布包拒绝异常。Runtime 验证失败时抛出。
 */
public class PackageRejectedException extends RuntimeException {

    public PackageRejectedException(String reason) {
        super("发布包被拒绝: " + reason);
    }
}
