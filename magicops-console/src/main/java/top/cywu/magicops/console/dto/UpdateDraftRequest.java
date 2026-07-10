package top.cywu.magicops.console.dto;

/**
 * 更新草稿请求。
 */
public record UpdateDraftRequest(
        String content,
        String routePath,
        String routeMethod
) {}
