package top.cywu.magicops.runtime.packageMgmt;

import top.cywu.magicops.sign.model.PackageManifest;

import java.nio.charset.StandardCharsets;

/**
 * 脚本解析结果（切片 34）。
 *
 * @param entry   manifest 中的脚本条目（含 scriptId、path、contentHash 等）
 * @param content 脚本内容字节（来自激活包，已校验 contentHash）
 */
public record ResolvedScript(PackageManifest.ScriptEntry entry, byte[] content) {

    /** 脚本内容按 UTF-8 解码为字符串（SQL 或适配定义）。 */
    public String contentAsString() {
        return new String(content, StandardCharsets.UTF_8);
    }
}
