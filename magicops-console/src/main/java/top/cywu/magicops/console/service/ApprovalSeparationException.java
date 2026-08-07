package top.cywu.magicops.console.service;

/**
 * 审批分离违规异常。审批决策人同时是提交人时抛出，映射为 HTTP 403。
 *
 * <p>切片 31：提交人与审批人必须分离，防止自审自批。
 */
public class ApprovalSeparationException extends RuntimeException {

    public ApprovalSeparationException(String message) {
        super(message);
    }
}
