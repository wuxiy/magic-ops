package top.cywu.magicops.sign.key;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.security.*;
import java.security.cert.Certificate;
import java.util.*;

/**
 * Java KeyStore (JKS) 密钥提供者。支持从 JKS 文件加载密钥对。
 *
 * <p>支持多个 keyId 并存，支持密钥轮换。
 * <p>KeyStore 类型支持 JDK 标准的 JKS 与 PKCS12，通过构造参数指定。
 */
public class KeyStoreKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(KeyStoreKeyProvider.class);
    public static final String DEFAULT_TYPE = "JKS";

    private final Map<String, KeyPair> keyPairs = new LinkedHashMap<>();
    private String activeKeyId;

    /**
     * 从默认类型（JKS）的 KeyStore 文件加载所有密钥对。
     */
    public KeyStoreKeyProvider(String keystorePath, String keystorePassword) {
        this(keystorePath, keystorePassword, DEFAULT_TYPE);
    }

    /**
     * 从 KeyStore 文件加载所有密钥对。
     *
     * @param keystoreType KeyStore 类型（JKS 或 PKCS12，均为 JDK 标准实现）
     */
    public KeyStoreKeyProvider(String keystorePath, String keystorePassword, String keystoreType) {
        try {
            KeyStore ks = KeyStore.getInstance(keystoreType);
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                ks.load(fis, keystorePassword.toCharArray());
            }

            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (ks.isKeyEntry(alias)) {
                    PrivateKey privateKey = (PrivateKey) ks.getKey(alias, keystorePassword.toCharArray());
                    Certificate cert = ks.getCertificate(alias);
                    if (cert != null && privateKey != null) {
                        keyPairs.put(alias, new KeyPair(cert.getPublicKey(), privateKey));
                        log.info("key_loaded keyId={} from={}", alias, keystorePath);
                    }
                }
            }

            if (keyPairs.isEmpty()) {
                throw new IllegalStateException("KeyStore 文件中未找到密钥对: " + keystorePath);
            }

            // 第一个加载的 keyId 作为活跃 keyId
            this.activeKeyId = keyPairs.keySet().iterator().next();
            log.info("active_key_id={}", activeKeyId);

        } catch (Exception e) {
            throw new IllegalStateException("无法加载 JKS 文件: " + keystorePath, e);
        }
    }

    public String getActiveKeyId() { return activeKeyId; }
    public void setActiveKeyId(String keyId) { this.activeKeyId = keyId; }

    public PrivateKey getPrivateKey(String keyId) {
        KeyPair kp = keyPairs.get(keyId);
        if (kp == null) throw new IllegalArgumentException("未知的 keyId: " + keyId);
        return kp.getPrivate();
    }

    public PublicKey getPublicKey(String keyId) {
        KeyPair kp = keyPairs.get(keyId);
        if (kp == null) throw new IllegalArgumentException("未知的 keyId: " + keyId);
        return kp.getPublic();
    }

    public PrivateKey getActivePrivateKey() { return getPrivateKey(activeKeyId); }
    public PublicKey getActivePublicKey() { return getPublicKey(activeKeyId); }

    /**
     * 检查 keyId 是否被信任（所有已加载的 keyId 都是可信的）。
     */
    public boolean isTrustedKeyId(String keyId) {
        return keyPairs.containsKey(keyId);
    }

    public Set<String> getAllKeyIds() {
        return Collections.unmodifiableSet(keyPairs.keySet());
    }
}
