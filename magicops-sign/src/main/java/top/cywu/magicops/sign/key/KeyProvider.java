package top.cywu.magicops.sign.key;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Set;

/**
 * RSA 密钥对提供者。切片 32 起为配置驱动，加载优先级：
 *
 * <ol>
 *   <li>KeyStore 持久化密钥（JDK 标准 {@link java.security.KeyStore}，JKS/PKCS12）：
 *       {@code magicops.sign.keystore.path/password/type}，支持多 keyId 与轮换；</li>
 *   <li>环境变量密钥：{@code MAGICOPS_PRIVATE_KEY}（Base64 PKCS8）与
 *       {@code MAGICOPS_PUBLIC_KEY}（Base64 X509）；</li>
 *   <li>临时密钥对：仅当 {@code magicops.sign.allow-ephemeral-keys=true}
 *       （prod 配置为 false，缺失密钥时启动 fail-fast）。</li>
 * </ol>
 *
 * <p>全部密钥机制均基于 JDK 标准加密 API，不引入自研密钥格式。
 */
@Component
public class KeyProvider {

    private static final Logger log = LoggerFactory.getLogger(KeyProvider.class);
    private static final String DEFAULT_KEY_ID = "magicops-default";

    private final KeyPair keyPair;
    private final String keyId;
    /** 受信任的 keyId 集合（KeyStore 模式下为全部已加载别名）。 */
    private final Set<String> trustedKeyIds;
    /** KeyStore 模式下持有全部密钥对，支持按 keyId 查询。 */
    private final KeyStoreKeyProvider keystoreProvider;

    /**
     * 开发/测试兼容构造器：生成临时密钥对。生产环境不得使用（Spring 注入走配置化构造器）。
     */
    public KeyProvider() {
        this.keyPair = generateKeyPair();
        this.keyId = DEFAULT_KEY_ID;
        this.trustedKeyIds = Set.of(DEFAULT_KEY_ID);
        this.keystoreProvider = null;
        log.warn("generated_temporary_keypair keyId={} (NOT FOR PRODUCTION)", keyId);
    }

    /**
     * 配置化构造器。按 KeyStore、环境变量、临时密钥的顺序装配；
     * 不允许临时密钥且无任何持久化密钥时抛出异常使应用启动失败（fail-fast）。
     */
    @Autowired
    public KeyProvider(
            @Value("${MAGICOPS_PRIVATE_KEY:}") String privateKeyB64,
            @Value("${MAGICOPS_PUBLIC_KEY:}") String publicKeyB64,
            @Value("${magicops.sign.keystore.path:}") String keystorePath,
            @Value("${magicops.sign.keystore.password:}") String keystorePassword,
            @Value("${magicops.sign.keystore.type:JKS}") String keystoreType,
            @Value("${MAGICOPS_KEY_ID:}") String keyIdOverride,
            @Value("${magicops.sign.allow-ephemeral-keys:true}") boolean allowEphemeralKeys) {

        if (keystorePath != null && !keystorePath.isBlank()) {
            // 1. KeyStore 持久化密钥（JDK 标准 KeyStore，多 keyId）
            this.keystoreProvider = new KeyStoreKeyProvider(keystorePath, keystorePassword, keystoreType);
            if (keyIdOverride != null && !keyIdOverride.isBlank()
                    && keystoreProvider.isTrustedKeyId(keyIdOverride)) {
                keystoreProvider.setActiveKeyId(keyIdOverride);
            }
            this.keyPair = new KeyPair(
                    keystoreProvider.getActivePublicKey(), keystoreProvider.getActivePrivateKey());
            this.keyId = keystoreProvider.getActiveKeyId();
            this.trustedKeyIds = keystoreProvider.getAllKeyIds();
            log.info("key_loaded_from_keystore keyId={} trustedKeyIds={} type={}",
                    keyId, trustedKeyIds, keystoreType);
            return;
        }

        if (privateKeyB64 != null && !privateKeyB64.isBlank()
                && publicKeyB64 != null && !publicKeyB64.isBlank()) {
            // 2. 环境变量密钥
            try {
                PrivateKey privateKey = loadPrivateKey(privateKeyB64);
                PublicKey publicKey = loadPublicKey(publicKeyB64);
                this.keyPair = new KeyPair(publicKey, privateKey);
                this.keyId = (keyIdOverride != null && !keyIdOverride.isBlank())
                        ? keyIdOverride : DEFAULT_KEY_ID;
                this.trustedKeyIds = Set.of(this.keyId);
                this.keystoreProvider = null;
                log.info("key_loaded_from_env keyId={}", keyId);
                return;
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                throw new IllegalStateException("无法加载环境变量中的签名密钥（MAGICOPS_PRIVATE_KEY/PUBLIC_KEY）", e);
            }
        }

        if (allowEphemeralKeys) {
            // 3. 临时密钥对（仅开发/测试）
            this.keyPair = generateKeyPair();
            this.keyId = DEFAULT_KEY_ID;
            this.trustedKeyIds = Set.of(DEFAULT_KEY_ID);
            this.keystoreProvider = null;
            log.warn("generated_temporary_keypair keyId={} (NOT FOR PRODUCTION)", keyId);
            return;
        }

        // 4. fail-fast：生产环境必须提供密钥
        throw new IllegalStateException(
                "签名密钥缺失，生产环境禁止使用临时密钥，启动终止。请配置以下任一项："
                        + "① magicops.sign.keystore.path/password（JKS/PKCS12 文件）；"
                        + "② 环境变量 MAGICOPS_PRIVATE_KEY 与 MAGICOPS_PUBLIC_KEY"
                        + "（可用 top.cywu.magicops.sign.key.KeyPairGeneratorUtil 生成）。");
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }

    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }

    public String getKeyId() {
        return keyId;
    }

    /**
     * 验证给定的 keyId 是否被当前 Runtime 信任。
     * KeyStore 模式下所有已加载的 keyId 均可信（支持轮换过渡期）。
     */
    public boolean isTrustedKeyId(String keyId) {
        return trustedKeyIds.contains(keyId);
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA 不可用", e);
        }
    }

    private static PrivateKey loadPrivateKey(String base64) throws GeneralSecurityException {
        byte[] decoded = Base64.getDecoder().decode(base64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private static PublicKey loadPublicKey(String base64) throws GeneralSecurityException {
        byte[] decoded = Base64.getDecoder().decode(base64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}
