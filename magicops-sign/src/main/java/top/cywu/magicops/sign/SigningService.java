package top.cywu.magicops.sign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.cywu.magicops.sign.canonical.CanonicalJson;
import top.cywu.magicops.sign.model.PackageManifest;
import top.cywu.magicops.sign.model.PublishPackage;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/**
 * 签名与验签服务。
 *
 * <p>签名算法：SHA256withRSA。
 * <p>签名输入：canonical manifest（不含 signature 字段）+ canonical metadata + normalized scripts bytes。
 * <p>签名输入必须形成稳定字节序列，并由测试覆盖。
 */
@Service
public class SigningService {

    private static final Logger log = LoggerFactory.getLogger(SigningService.class);
    private static final String SIGN_ALGORITHM = "SHA256withRSA";
    private static final String HASH_ALGORITHM = "SHA-256";

    /**
     * 计算 SHA-256 hash。
     */
    public String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(data);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 计算脚本内容的 hash（规范化后）。
     */
    public String hashScriptContent(String content) {
        byte[] normalized = CanonicalJson.normalizeScript(content);
        return sha256Hex(normalized);
    }

    /**
     * 计算 metadata 的 canonical hash。
     */
    public String hashMetadata(Map<String, Object> metadata) {
        byte[] canonical = CanonicalJson.toCanonicalBytes(metadata);
        return sha256Hex(canonical);
    }

    /**
     * 构建签名输入字节。
     *
     * <p>签名输入 = canonical manifest bytes + canonical metadata bytes + sorted normalized scripts bytes
     */
    public byte[] buildSigningInput(PackageManifest manifest, Map<String, Object> metadata,
                                    Map<String, byte[]> scripts) {
        byte[] manifestBytes = CanonicalJson.toCanonicalBytes(manifest.toSignableMap());
        byte[] metadataBytes = CanonicalJson.toCanonicalBytes(metadata);

        // 按文件路径字典序排列脚本
        List<String> sortedPaths = new ArrayList<>(scripts.keySet());
        Collections.sort(sortedPaths);

        var buffer = new java.io.ByteArrayOutputStream();
        try {
            buffer.write(manifestBytes);
            buffer.write(metadataBytes);
            for (String path : sortedPaths) {
                buffer.write(scripts.get(path));
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("构建签名输入失败", e);
        }
        return buffer.toByteArray();
    }

    /**
     * 使用私钥签名。
     */
    public byte[] sign(byte[] signingInput, PrivateKey privateKey) {
        try {
            Signature sig = Signature.getInstance(SIGN_ALGORITHM);
            sig.initSign(privateKey);
            sig.update(signingInput);
            byte[] signature = sig.sign();
            log.debug("signed {} bytes -> {} bytes signature", signingInput.length, signature.length);
            return signature;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("签名失败", e);
        }
    }

    /**
     * 使用公钥验签。
     */
    public boolean verify(byte[] signingInput, byte[] signature, PublicKey publicKey) {
        try {
            Signature sig = Signature.getInstance(SIGN_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(signingInput);
            return sig.verify(signature);
        } catch (GeneralSecurityException e) {
            log.warn("verification_error", e);
            return false;
        }
    }

    /**
     * 验证发布包完整性：签名 + hash。
     */
    public boolean verifyPackage(PublishPackage pkg, PublicKey publicKey) {
        // 1. 重新构建签名输入
        byte[] signingInput = buildSigningInput(pkg.manifest(), pkg.metadata(), pkg.scripts());

        // 2. 验签
        if (!verify(signingInput, pkg.signature(), publicKey)) {
            log.warn("package_signature_verification_failed keyId={}", pkg.manifest().keyId());
            return false;
        }

        // 3. 验证 metadata hash
        String actualMetadataHash = hashMetadata(pkg.metadata());
        if (!actualMetadataHash.equals(pkg.manifest().metadataHash())) {
            log.warn("metadata_hash_mismatch expected={} actual={}",
                    pkg.manifest().metadataHash(), actualMetadataHash);
            return false;
        }

        // 4. 验证每个脚本的 content hash
        for (PackageManifest.ScriptEntry entry : pkg.manifest().scripts()) {
            byte[] scriptBytes = pkg.scripts().get(entry.path());
            if (scriptBytes == null) {
                log.warn("script_missing path={}", entry.path());
                return false;
            }
            String actualHash = sha256Hex(scriptBytes);
            if (!actualHash.equals(entry.contentHash())) {
                log.warn("script_hash_mismatch path={} expected={} actual={}",
                        entry.path(), entry.contentHash(), actualHash);
                return false;
            }
        }

        return true;
    }

    private static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
