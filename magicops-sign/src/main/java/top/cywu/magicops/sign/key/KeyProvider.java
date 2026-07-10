package top.cywu.magicops.sign.key;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA 密钥对提供者。第一版从环境变量加载。
 *
 * <p>环境变量：
 * <ul>
 *   <li>{@code MAGICOPS_PRIVATE_KEY}：Base64 编码的 PKCS8 私钥</li>
 *   <li>{@code MAGICOPS_PUBLIC_KEY}：Base64 编码的 X509 公钥</li>
 * </ul>
 *
 * <p>如果环境变量不存在，自动生成临时密钥对（仅用于开发/测试）。
 */
@Component
public class KeyProvider {

    private static final Logger log = LoggerFactory.getLogger(KeyProvider.class);
    private static final String ENV_PRIVATE_KEY = "MAGICOPS_PRIVATE_KEY";
    private static final String ENV_PUBLIC_KEY = "MAGICOPS_PUBLIC_KEY";
    private static final String DEFAULT_KEY_ID = "magicops-default";

    private final KeyPair keyPair;
    private final String keyId;

    public KeyProvider() {
        String privateKeyB64 = System.getenv(ENV_PRIVATE_KEY);
        String publicKeyB64 = System.getenv(ENV_PUBLIC_KEY);

        if (privateKeyB64 != null && publicKeyB64 != null) {
            try {
                PrivateKey privateKey = loadPrivateKey(privateKeyB64);
                PublicKey publicKey = loadPublicKey(publicKeyB64);
                this.keyPair = new KeyPair(publicKey, privateKey);
                this.keyId = System.getenv().getOrDefault("MAGICOPS_KEY_ID", DEFAULT_KEY_ID);
                log.info("key_loaded_from_env keyId={}", keyId);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("无法加载环境变量中的密钥", e);
            }
        } else {
            this.keyPair = generateKeyPair();
            this.keyId = DEFAULT_KEY_ID;
            log.warn("generated_temporary_keypair keyId={} (NOT FOR PRODUCTION)", keyId);
        }
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
     */
    public boolean isTrustedKeyId(String keyId) {
        return this.keyId.equals(keyId);
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
