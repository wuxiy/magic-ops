package top.cywu.magicops.console.publish;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.cywu.magicops.console.entity.ApprovalEntity;
import top.cywu.magicops.console.entity.ScriptEntity;
import top.cywu.magicops.console.entity.ScriptVersionEntity;
import top.cywu.magicops.console.repository.ApprovalRepository;
import top.cywu.magicops.console.repository.ScriptRepository;
import top.cywu.magicops.console.repository.ScriptVersionRepository;
import top.cywu.magicops.core.model.ApprovalDecision;
import top.cywu.magicops.core.model.RiskLevel;
import top.cywu.magicops.core.model.ScriptStatus;
import top.cywu.magicops.core.model.ScriptType;
import top.cywu.magicops.sign.SigningService;
import top.cywu.magicops.sign.key.KeyProvider;
import top.cywu.magicops.sign.model.PublishPackage;
import top.cywu.magicops.sqlguard.SqlGuardService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 切片 30：发布包构建治理凭据测试。
 *
 * <p>覆盖关闭标准：
 * <ul>
 *   <li>APPROVED 审批凭据嵌入 metadata 且随包签名可验证</li>
 *   <li>SQL 引用表提取为表级授权清单（datasourcePermissions）</li>
 *   <li>缺少审批记录、审批被拒绝、SQL 无法解析均 fail-closed 拒绝打包</li>
 *   <li>HTTP_ADAPTER 类型不做表提取</li>
 * </ul>
 */
class PackageBuildServiceTest {

    private ScriptRepository scriptRepository;
    private ScriptVersionRepository versionRepository;
    private ApprovalRepository approvalRepository;
    private SigningService signingService;
    private KeyProvider keyProvider;
    private PackageBuildService service;

    @BeforeEach
    void setUp() {
        scriptRepository = mock(ScriptRepository.class);
        versionRepository = mock(ScriptVersionRepository.class);
        approvalRepository = mock(ApprovalRepository.class);
        signingService = new SigningService();
        keyProvider = new KeyProvider();
        service = new PackageBuildService(scriptRepository, versionRepository,
                approvalRepository, signingService, keyProvider, new SqlGuardService());
    }

    private ScriptEntity script(Long id, ScriptType type, String sql) {
        ScriptEntity script = new ScriptEntity("script-" + id, "hospital-a", type, "dev1");
        script.setId(id);
        script.setStatus(ScriptStatus.APPROVED);
        script.setCurrentVersionId(id * 10);
        return script;
    }

    private ScriptVersionEntity version(Long scriptId, String content) {
        ScriptVersionEntity v = new ScriptVersionEntity();
        v.setId(scriptId * 10);
        v.setScriptId(scriptId);
        v.setVersion("1.0.0");
        v.setContent(content);
        v.setRoutePath("/api/test-" + scriptId);
        v.setRouteMethod("GET");
        v.setRiskLevel(RiskLevel.LOW);
        v.setCreatedAt(Instant.now());
        return v;
    }

    private ApprovalEntity approval(Long versionId, ApprovalDecision decision) {
        ApprovalEntity a = new ApprovalEntity(versionId, "dev1");
        a.setId(versionId + 100);
        a.setDecision(decision);
        a.setDecidedBy("approver1");
        a.setDecidedAt(Instant.now());
        return a;
    }

    private void stubApprovedScript(Long id, ScriptType type, String sql) {
        ScriptEntity script = script(id, type, sql);
        ScriptVersionEntity v = version(id, sql);
        when(scriptRepository.findById(id)).thenReturn(Optional.of(script));
        when(versionRepository.findById(id * 10)).thenReturn(Optional.of(v));
        when(approvalRepository.findByScriptVersionId(id * 10))
                .thenReturn(Optional.of(approval(id * 10, ApprovalDecision.APPROVED)));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> approvals(PublishPackage pkg) {
        return (List<Map<String, Object>>) pkg.metadata().get("approvals");
    }

    @SuppressWarnings("unchecked")
    private List<String> allowedTables(PublishPackage pkg) {
        List<Map<String, Object>> perms =
                (List<Map<String, Object>>) pkg.metadata().get("datasourcePermissions");
        return (List<String>) perms.get(0).get("tables");
    }

    @Test
    void buildAndSign_embedsApprovalProofAndTablePermissions() {
        stubApprovedScript(1L, ScriptType.DYNAMIC_QUERY,
                "SELECT id, name FROM patients WHERE id = 1");

        PublishPackage pkg = service.buildAndSign(List.of(1L), "development", "admin");

        // 审批凭据
        List<Map<String, Object>> proofs = approvals(pkg);
        assertEquals(1, proofs.size());
        Map<String, Object> proof = proofs.get(0);
        assertEquals("1", proof.get("scriptId"));
        assertEquals("1.0.0", proof.get("version"));
        assertEquals("APPROVED", proof.get("decision"));
        assertEquals("approver1", proof.get("decidedBy"));
        assertNotNull(proof.get("approvalId"));
        assertNotNull(proof.get("decidedAt"));

        // 表级授权
        assertEquals(List.of("patients"), allowedTables(pkg));

        // 治理凭据随包签名：验签通过证明 metadata 未被篡改
        assertTrue(signingService.verifyPackage(pkg, keyProvider.getPublicKey()),
                "含治理凭据的发布包应通过验签");
    }

    @Test
    void buildAndSign_multipleScripts_tablesUnionedAndSorted() {
        stubApprovedScript(1L, ScriptType.DYNAMIC_QUERY, "SELECT * FROM patients");
        stubApprovedScript(2L, ScriptType.DATA_REPAIR,
                "UPDATE orders SET status = 'fixed' WHERE id = 9");

        PublishPackage pkg = service.buildAndSign(List.of(1L, 2L), "development", "admin");

        assertEquals(2, approvals(pkg).size());
        assertEquals(List.of("orders", "patients"), allowedTables(pkg));
        assertTrue(signingService.verifyPackage(pkg, keyProvider.getPublicKey()));
    }

    @Test
    void buildAndSign_missingApproval_rejected() {
        ScriptEntity script = script(1L, ScriptType.DYNAMIC_QUERY, "SELECT 1 FROM patients");
        ScriptVersionEntity v = version(1L, "SELECT 1 FROM patients");
        when(scriptRepository.findById(1L)).thenReturn(Optional.of(script));
        when(versionRepository.findById(10L)).thenReturn(Optional.of(v));
        when(approvalRepository.findByScriptVersionId(10L)).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.buildAndSign(List.of(1L), "development", "admin"));
        assertTrue(ex.getMessage().contains("缺少审批记录"));
    }

    @Test
    void buildAndSign_rejectedApproval_rejected() {
        ScriptEntity script = script(1L, ScriptType.DYNAMIC_QUERY, "SELECT 1 FROM patients");
        ScriptVersionEntity v = version(1L, "SELECT 1 FROM patients");
        when(scriptRepository.findById(1L)).thenReturn(Optional.of(script));
        when(versionRepository.findById(10L)).thenReturn(Optional.of(v));
        when(approvalRepository.findByScriptVersionId(10L))
                .thenReturn(Optional.of(approval(10L, ApprovalDecision.REJECTED)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.buildAndSign(List.of(1L), "development", "admin"));
        assertTrue(ex.getMessage().contains("REJECTED"));
    }

    @Test
    void buildAndSign_unparseableSql_rejected() {
        String garbage = "THIS IS NOT VALID SQL @@@ ###";
        ScriptEntity script = script(1L, ScriptType.DYNAMIC_QUERY, garbage);
        ScriptVersionEntity v = version(1L, garbage);
        when(scriptRepository.findById(1L)).thenReturn(Optional.of(script));
        when(versionRepository.findById(10L)).thenReturn(Optional.of(v));
        when(approvalRepository.findByScriptVersionId(10L))
                .thenReturn(Optional.of(approval(10L, ApprovalDecision.APPROVED)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.buildAndSign(List.of(1L), "development", "admin"));
        assertTrue(ex.getMessage().contains("无法解析"));
    }

    @Test
    void buildAndSign_httpAdapter_skipsTableExtraction() {
        String adapterConfig = "{\"target\":\"his-system\",\"path\":\"/api/orders\"}";
        stubApprovedScript(1L, ScriptType.HTTP_ADAPTER, adapterConfig);

        PublishPackage pkg = service.buildAndSign(List.of(1L), "development", "admin");

        assertEquals(1, approvals(pkg).size());
        assertTrue(allowedTables(pkg).isEmpty(),
                "HTTP_ADAPTER 不做表提取，授权表清单应为空");
        assertTrue(signingService.verifyPackage(pkg, keyProvider.getPublicKey()));
    }

    @Test
    void buildAndSign_scriptNotApproved_rejected() {
        ScriptEntity script = script(1L, ScriptType.DYNAMIC_QUERY, "SELECT 1 FROM patients");
        script.setStatus(ScriptStatus.DRAFT);
        when(scriptRepository.findById(1L)).thenReturn(Optional.of(script));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.buildAndSign(List.of(1L), "development", "admin"));
        assertTrue(ex.getMessage().contains("状态不允许发布"));
    }

    @Test
    void buildAndSign_placeholderScript_tablesExtracted() {
        // magic-api 风格占位符 SQL 也应能提取表（打包前由 SqlGuardService 规范化）
        stubApprovedScript(1L, ScriptType.DYNAMIC_QUERY,
                "SELECT id, name FROM patients WHERE id = #{id}");

        PublishPackage pkg = service.buildAndSign(List.of(1L), "development", "admin");

        assertEquals(List.of("patients"), allowedTables(pkg));
    }
}
