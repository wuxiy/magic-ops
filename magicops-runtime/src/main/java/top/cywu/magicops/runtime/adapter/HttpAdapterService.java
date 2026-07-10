package top.cywu.magicops.runtime.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.cywu.magicops.audit.model.AuditEventType;
import top.cywu.magicops.audit.model.AuditRecord;
import top.cywu.magicops.audit.service.AuditService;
import top.cywu.magicops.audit.service.AuditWriteException;
import top.cywu.magicops.crypto.service.CryptoService;
import top.cywu.magicops.http.HttpClientService;
import top.cywu.magicops.http.model.HttpTarget;
import top.cywu.magicops.http.registry.HttpTargetRegistry;
import top.cywu.magicops.sign.model.PublishPackage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP 接口适配服务。通过已注册目标系统执行适配脚本。
 *
 * <p>规则：
 * <ul>
 *   <li>脚本只能调用已注册目标</li>
 *   <li>非 allowlist 路径被拒绝</li>
 *   <li>请求可使用配置签名/加密</li>
 *   <li>trace ID 可透传</li>
 *   <li>外部写/推送类调用审计失败时阻断</li>
 * </ul>
 */
@Service
public class HttpAdapterService {

    private static final Logger log = LoggerFactory.getLogger(HttpAdapterService.class);

    private final HttpTargetRegistry targetRegistry;
    private final HttpClientService httpClientService;
    private final CryptoService cryptoService;
    private final AuditService auditService;

    public HttpAdapterService(HttpTargetRegistry targetRegistry,
                              HttpClientService httpClientService,
                              CryptoService cryptoService,
                              AuditService auditService) {
        this.targetRegistry = targetRegistry;
        this.httpClientService = httpClientService;
        this.cryptoService = cryptoService;
        this.auditService = auditService;
    }

    /**
     * 执行 HTTP 适配调用。
     *
     * @param pkg       已验签的发布包
     * @param targetId  目标系统 ID
     * @param path      请求路径
     * @param method    HTTP 方法
     * @param body      请求体
     * @param traceId   追踪 ID
     * @return 适配结果
     */
    public AdapterResult execute(PublishPackage pkg, String targetId, String path,
                                 String method, String body, String traceId) {
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }

        // 1. 检查目标是否已注册
        HttpTarget target = targetRegistry.getTarget(targetId).orElse(null);
        if (target == null) {
            return AdapterResult.failure(traceId, targetId, path, "目标未注册: " + targetId);
        }

        // 2. 检查路径是否在 allowlist 内
        if (!target.isPathAllowed(path)) {
            return AdapterResult.failure(traceId, targetId, path, "路径不在 allowlist 内: " + path);
        }

        // 3. 处理请求体加密（如果目标配置了加密）
        String processedBody = body;
        if (target.requireEncryption() && body != null && target.cryptoKeyId() != null) {
            if (cryptoService.hasKey(target.cryptoKeyId())) {
                processedBody = cryptoService.encrypt(target.cryptoKeyId(), body);
            }
        }

        // 4. 构建请求头（包含 trace ID 透传）
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Trace-Id", traceId);

        // 5. 执行 HTTP 请求
        HttpClientService.HttpResponseResult httpResult =
                httpClientService.execute(targetId, path, method, processedBody, headers, traceId);

        // 6. 处理响应解密
        String responseBody = httpResult.body();
        if (httpResult.success() && target.requireEncryption() && responseBody != null
                && target.cryptoKeyId() != null && cryptoService.hasKey(target.cryptoKeyId())) {
            try {
                responseBody = cryptoService.decrypt(target.cryptoKeyId(), responseBody);
            } catch (Exception e) {
                log.warn("响应解密失败，使用原始响应: {}", e.getMessage());
            }
        }

        AdapterResult result = httpResult.success()
                ? AdapterResult.success(traceId, targetId, path, httpResult.statusCode(),
                    responseBody, httpResult.durationMs())
                : AdapterResult.failure(traceId, targetId, path, httpResult.errorMessage());

        // 7. 写入外部调用审计
        writeExternalAudit(pkg, result, method);

        return result;
    }

    private void writeExternalAudit(PublishPackage pkg, AdapterResult result, String method) {
        boolean isWrite = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("traceId", result.traceId());
        details.put("targetId", result.targetId());
        details.put("path", result.path());
        details.put("success", result.success());
        details.put("durationMs", result.durationMs());
        if (result.statusCode() > 0) {
            details.put("statusCode", result.statusCode());
        }
        if (result.errorMessage() != null) {
            details.put("errorMessage", result.errorMessage());
        }
        if (pkg != null) {
            details.put("environment", pkg.manifest().environment());
        }

        AuditRecord record = isWrite
                ? AuditRecord.critical(AuditEventType.SCRIPT_EXECUTED, "HttpAdapter",
                    result.targetId(), pkg != null ? pkg.manifest().publishedBy() : "system", details)
                : AuditRecord.of(AuditEventType.SCRIPT_EXECUTED, "HttpAdapter",
                    result.targetId(), pkg != null ? pkg.manifest().publishedBy() : "system", details);

        auditService.write(record);
    }
}
