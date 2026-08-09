package top.cywu.magicops.runtime.packageMgmt;

import org.springframework.stereotype.Component;
import top.cywu.magicops.sign.SigningService;
import top.cywu.magicops.sign.model.PackageManifest;
import top.cywu.magicops.sign.model.PublishPackage;

/**
 * 脚本引用解析器（切片 34）。
 *
 * <p>Runtime 执行端点改为脚本引用模式：请求只携带 {@code scriptId}，
 * 由本解析器从激活发布包中解析出脚本内容。执行内容来自已签名发布包，
 * 拒绝裸 SQL/裸目标调用。
 *
 * <p>解析流程：
 * <ol>
 *   <li>按 scriptId 在 manifest.scripts() 中查找 ScriptEntry；</li>
 *   <li>从 pkg.scripts() 按 entry.path() 取脚本字节；</li>
 *   <li>校验内容 contentHash 与 manifest 声明一致（防御纵深，签名验签时已校验）。</li>
 * </ol>
 *
 * <p>查找失败或 hash 不匹配抛 {@link PackageRejectedException}。
 */
@Component
public class ScriptResolver {

    private final SigningService signingService;

    public ScriptResolver(SigningService signingService) {
        this.signingService = signingService;
    }

    /**
     * 按 scriptId 解析激活包中的脚本。
     */
    public ResolvedScript resolve(PublishPackage pkg, String scriptId) {
        if (scriptId == null || scriptId.isBlank()) {
            throw new PackageRejectedException("请求未引用脚本（scriptId 缺失），拒绝裸调用");
        }

        PackageManifest.ScriptEntry entry = pkg.manifest().scripts().stream()
                .filter(s -> scriptId.equals(s.scriptId()))
                .findFirst()
                .orElseThrow(() -> new PackageRejectedException("激活包中不存在脚本: " + scriptId));

        byte[] content = pkg.scripts().get(entry.path());
        if (content == null) {
            throw new PackageRejectedException("脚本内容缺失: " + entry.path());
        }

        String actualHash = signingService.sha256Hex(content);
        if (!actualHash.equals(entry.contentHash())) {
            throw new PackageRejectedException(
                    "脚本内容 hash 不匹配: " + scriptId);
        }

        return new ResolvedScript(entry, content);
    }
}
