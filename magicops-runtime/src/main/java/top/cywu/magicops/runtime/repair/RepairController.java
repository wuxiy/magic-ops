package top.cywu.magicops.runtime.repair;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.runtime.packageMgmt.PackageVerificationService;
import top.cywu.magicops.sign.model.PublishPackage;
import top.cywu.magicops.core.model.RiskLevel;

import java.util.Map;

/**
 * 数据修复执行 API。
 */
@RestController
@RequestMapping("/api/repair")
public class RepairController {

    private final RepairExecutionService repairService;
    private final DryRunService dryRunService;
    private final PackageVerificationService verificationService;

    public RepairController(RepairExecutionService repairService,
                            DryRunService dryRunService,
                            PackageVerificationService verificationService) {
        this.repairService = repairService;
        this.dryRunService = dryRunService;
        this.verificationService = verificationService;
    }

    /**
     * 执行 dry-run 分析。
     */
    @PostMapping("/dry-run")
    public ResponseEntity<DryRunReport> dryRun(@RequestBody Map<String, String> request) {
        String scriptId = request.getOrDefault("scriptId", "unknown");
        String sql = request.get("sql");
        if (sql == null || sql.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        DryRunReport report = dryRunService.analyze(scriptId, sql);
        return ResponseEntity.ok(report);
    }

    /**
     * 执行数据修复。需要已激活的发布包和有效的 dry-run 报告。
     */
    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody Map<String, String> request) {
        PublishPackage activePackage = verificationService.getActivePackage();
        if (activePackage == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "没有已激活的发布包"));
        }

        String scriptId = request.getOrDefault("scriptId", "unknown");
        String sql = request.get("sql");
        if (sql == null || sql.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sql 参数不能为空"));
        }

        // 执行 dry-run
        DryRunReport report = dryRunService.analyze(scriptId, sql);
        if (!report.safe()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "dry-run 失败: " + report.reason(), "report", report));
        }

        // 构建风险声明
        RepairDeclaration declaration = new RepairDeclaration(
                scriptId,
                RiskLevel.MEDIUM,
                report.affectedTables().isEmpty() ? "unknown" : report.affectedTables().get(0),
                report.sqlType(),
                true,
                request.getOrDefault("rollbackStrategy", "manual")
        );

        RepairResult result = repairService.execute(activePackage, sql, report, declaration);
        return ResponseEntity.ok(result);
    }
}
