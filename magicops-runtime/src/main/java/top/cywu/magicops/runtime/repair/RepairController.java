package top.cywu.magicops.runtime.repair;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.core.model.RiskLevel;
import top.cywu.magicops.runtime.packageMgmt.PackageRejectedException;
import top.cywu.magicops.runtime.packageMgmt.PackageVerificationService;
import top.cywu.magicops.runtime.packageMgmt.ResolvedScript;
import top.cywu.magicops.runtime.packageMgmt.ScriptResolver;
import top.cywu.magicops.sign.model.PublishPackage;

import java.util.Map;

/**
 * 数据修复执行 API。
 *
 * <p>切片 34 起改为脚本引用模式：dry-run 与 execute 均按 {@code scriptId} 从激活包解析 SQL，
 * 拒绝裸 SQL 调用。
 */
@RestController
@RequestMapping("/api/repair")
public class RepairController {

    private final RepairExecutionService repairService;
    private final DryRunService dryRunService;
    private final PackageVerificationService verificationService;
    private final ScriptResolver scriptResolver;

    public RepairController(RepairExecutionService repairService,
                            DryRunService dryRunService,
                            PackageVerificationService verificationService,
                            ScriptResolver scriptResolver) {
        this.repairService = repairService;
        this.dryRunService = dryRunService;
        this.verificationService = verificationService;
        this.scriptResolver = scriptResolver;
    }

    /**
     * 执行 dry-run 分析。按 scriptId 从激活包解析 SQL。
     */
    @PostMapping("/dry-run")
    public ResponseEntity<?> dryRun(@RequestBody Map<String, String> request) {
        PublishPackage activePackage = verificationService.getActivePackage();
        if (activePackage == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "没有已激活的发布包"));
        }

        ResolvedScript resolved;
        try {
            resolved = scriptResolver.resolve(activePackage, request.get("scriptId"));
        } catch (PackageRejectedException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        DryRunReport report = dryRunService.analyze(resolved.entry().scriptId(), resolved.contentAsString());
        return ResponseEntity.ok(report);
    }

    /**
     * 执行数据修复。需要已激活的发布包，按 scriptId 解析经审批的 SQL。
     */
    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody Map<String, String> request) {
        PublishPackage activePackage = verificationService.getActivePackage();
        if (activePackage == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "没有已激活的发布包"));
        }

        ResolvedScript resolved;
        try {
            resolved = scriptResolver.resolve(activePackage, request.get("scriptId"));
        } catch (PackageRejectedException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        String sql = resolved.contentAsString();
        String scriptId = resolved.entry().scriptId();

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
