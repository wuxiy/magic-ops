package top.cywu.magicops.runtime.query;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.runtime.packageMgmt.PackageRejectedException;
import top.cywu.magicops.runtime.packageMgmt.PackageVerificationService;
import top.cywu.magicops.runtime.packageMgmt.ResolvedScript;
import top.cywu.magicops.runtime.packageMgmt.ScriptResolver;
import top.cywu.magicops.sign.model.PublishPackage;

import java.util.Map;
import java.util.UUID;

/**
 * Runtime 查询执行 API。
 *
 * <p>切片 34 起改为脚本引用模式：请求携带 {@code scriptId}，执行内容为激活包中的脚本，
 * 拒绝裸 SQL 调用。
 */
@RestController
@RequestMapping("/api/query")
public class QueryController {

    private final QueryExecutionService queryExecutionService;
    private final PackageVerificationService verificationService;
    private final ScriptResolver scriptResolver;

    public QueryController(QueryExecutionService queryExecutionService,
                           PackageVerificationService verificationService,
                           ScriptResolver scriptResolver) {
        this.queryExecutionService = queryExecutionService;
        this.verificationService = verificationService;
        this.scriptResolver = scriptResolver;
    }

    /**
     * 执行查询。需要当前有已激活的发布包，且请求引用其中某个脚本。
     *
     * @param request 查询请求，包含 scriptId（必需）与 traceId（可选）
     * @return 执行结果
     */
    @PostMapping
    public ResponseEntity<?> execute(@RequestBody Map<String, String> request) {
        PublishPackage activePackage = verificationService.getActivePackage();
        if (activePackage == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "没有已激活的发布包"));
        }

        String scriptId = request.get("scriptId");
        if (scriptId == null || scriptId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "scriptId 参数不能为空（切片 34 起拒绝裸 SQL 调用）"));
        }

        String traceId = request.getOrDefault("traceId", UUID.randomUUID().toString());

        ResolvedScript resolved;
        try {
            resolved = scriptResolver.resolve(activePackage, scriptId);
        } catch (PackageRejectedException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        QueryExecutionResult result = queryExecutionService.executeQuery(
                activePackage, resolved.contentAsString(), traceId, scriptId, true);
        return ResponseEntity.ok(result);
    }
}
