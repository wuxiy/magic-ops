package top.cywu.magicops.runtime.query;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.sign.model.PublishPackage;
import top.cywu.magicops.runtime.packageMgmt.PackageVerificationService;

import java.util.Map;
import java.util.UUID;

/**
 * Runtime 查询执行 API。
 *
 * <p>接收已发布的查询请求，执行并返回结果。
 */
@RestController
@RequestMapping("/api/query")
public class QueryController {

    private final QueryExecutionService queryExecutionService;
    private final PackageVerificationService verificationService;

    public QueryController(QueryExecutionService queryExecutionService,
                           PackageVerificationService verificationService) {
        this.queryExecutionService = queryExecutionService;
        this.verificationService = verificationService;
    }

    /**
     * 执行查询。需要当前有已激活的发布包。
     *
     * @param request 查询请求，包含 SQL
     * @return 执行结果
     */
    @PostMapping
    public ResponseEntity<?> execute(@RequestBody Map<String, String> request) {
        PublishPackage activePackage = verificationService.getActivePackage();
        if (activePackage == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "没有已激活的发布包"));
        }

        String sql = request.get("sql");
        if (sql == null || sql.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "sql 参数不能为空"));
        }

        String traceId = request.getOrDefault("traceId", UUID.randomUUID().toString());
        QueryExecutionResult result = queryExecutionService.executeQuery(activePackage, sql, traceId);

        return ResponseEntity.ok(result);
    }
}
