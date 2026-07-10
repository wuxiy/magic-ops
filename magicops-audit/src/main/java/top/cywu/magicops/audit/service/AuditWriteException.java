package top.cywu.magicops.audit.service;

/**
 * 关键审计写入失败异常。抛出时执行应被阻断。
 */
public class AuditWriteException extends RuntimeException {

    public AuditWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
