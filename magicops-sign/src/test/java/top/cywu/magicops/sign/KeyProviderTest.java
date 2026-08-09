package top.cywu.magicops.sign;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.cywu.magicops.sign.key.KeyProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KeyProvider 配置化装配测试（切片 32）。覆盖：
 * <ul>
 *   <li>prod fail-fast：无持久化密钥且禁用临时密钥时启动失败</li>
 *   <li>环境变量密钥加载（Base64 PKCS8/X509）</li>
 *   <li>KeyStore（JKS，JDK 标准 keytool 生成）多 keyId 装配与轮换信任</li>
 *   <li>临时密钥仅限显式允许（dev/test）</li>
 * </ul>
 */
class KeyProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void failFast_whenNoKeysAndEphemeralDisabled() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new KeyProvider("", "", "", "", "JKS", "", false));
        assertTrue(ex.getMessage().contains("签名密钥缺失"), "应给出明确的密钥缺失错误: " + ex.getMessage());
    }

    @Test
    void ephemeralKeys_generatedOnlyWhenAllowed() {
        KeyProvider provider = new KeyProvider("", "", "", "", "JKS", "", true);
        assertEquals("magicops-default", provider.getKeyId());
        assertNotNull(provider.getPrivateKey());
        assertTrue(provider.isTrustedKeyId("magicops-default"));
    }

    @Test
    void legacyNoArgConstructor_generatesEphemeralKeys() {
        KeyProvider provider = new KeyProvider();
        assertNotNull(provider.getKeyPair());
        assertTrue(provider.isTrustedKeyId(provider.getKeyId()));
    }

    @Test
    void envKeys_loadedWithKeyIdOverride() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        String priv = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        String pub = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());

        KeyProvider provider = new KeyProvider(priv, pub, "", "", "JKS", "env-key-01", true);
        assertEquals("env-key-01", provider.getKeyId());
        assertTrue(provider.isTrustedKeyId("env-key-01"));
        assertFalse(provider.isTrustedKeyId("other-key"));

        // 签名/验签 roundtrip 验证密钥可用
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(provider.getPrivateKey());
        sig.update("payload".getBytes());
        byte[] signature = sig.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(provider.getPublicKey());
        verifier.update("payload".getBytes());
        assertTrue(verifier.verify(signature));
    }

    @Test
    void envKeys_invalidBase64_failFast() {
        assertThrows(IllegalStateException.class,
                () -> new KeyProvider("not-a-key", "also-not-a-key", "", "", "JKS", "", true));
    }

    @Test
    void keystoreLoaded_multipleKeyIds_allTrusted() throws Exception {
        Path ks = tempDir.resolve("magicops.jks");
        generateKeytoolKeystore(ks, "changeit", "key-a", "key-b");

        KeyProvider provider = new KeyProvider("", "", ks.toString(), "changeit", "JKS", "", true);
        assertTrue(provider.isTrustedKeyId("key-a"), "应信任 key-a");
        assertTrue(provider.isTrustedKeyId("key-b"), "应信任 key-b");
        assertFalse(provider.isTrustedKeyId("key-c"));
        assertNotNull(provider.getPrivateKey());

        // 签名 roundtrip（活跃密钥可用）
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(provider.getPrivateKey());
        sig.update("package-bytes".getBytes());
        byte[] signature = sig.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(provider.getPublicKey());
        verifier.update("package-bytes".getBytes());
        assertTrue(verifier.verify(signature));
    }

    @Test
    void keystoreLoaded_keyIdOverrideSelectsActiveKey() throws Exception {
        Path ks = tempDir.resolve("magicops-override.jks");
        generateKeytoolKeystore(ks, "changeit", "key-a", "key-b");

        KeyProvider provider = new KeyProvider("", "", ks.toString(), "changeit", "JKS", "key-b", true);
        assertEquals("key-b", provider.getKeyId(), "指定 keyId 存在时应作为活跃密钥");
    }

    @Test
    void keystoreLoaded_unknownOverrideFallsBackToFirstAlias() throws Exception {
        Path ks = tempDir.resolve("magicops-fallback.jks");
        generateKeytoolKeystore(ks, "changeit", "key-a");

        KeyProvider provider = new KeyProvider("", "", ks.toString(), "changeit", "JKS", "not-in-store", true);
        assertEquals("key-a", provider.getKeyId(), "指定 keyId 不存在时应回退到已加载别名");
    }

    @Test
    void keystoreMissingFile_failFast() {
        assertThrows(IllegalStateException.class,
                () -> new KeyProvider("", "", tempDir.resolve("missing.jks").toString(), "changeit", "JKS", "", true));
    }

    @Test
    void keystoreWrongPassword_failFast() throws Exception {
        Path ks = tempDir.resolve("magicops-wrongpw.jks");
        generateKeytoolKeystore(ks, "changeit", "key-a");

        assertThrows(IllegalStateException.class,
                () -> new KeyProvider("", "", ks.toString(), "wrong-password", "JKS", "", true));
    }

    /**
     * 使用 JDK 标准 keytool 生成含多个私钥别名的 JKS（测试不引入第三方证书库）。
     */
    private void generateKeytoolKeystore(Path keystoreFile, String password, String... aliases) throws Exception {
        for (String alias : aliases) {
            ProcessBuilder pb = new ProcessBuilder(
                    "keytool", "-genkeypair",
                    "-alias", alias,
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-storetype", "JKS",
                    "-keystore", keystoreFile.toString(),
                    "-storepass", password,
                    "-keypass", password,
                    "-dname", "CN=MagicOps Test",
                    "-validity", "365");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exit = process.waitFor();
            assertEquals(0, exit, "keytool 生成 " + alias + " 失败: " + output);
        }
        assertTrue(Files.exists(keystoreFile));
    }
}
