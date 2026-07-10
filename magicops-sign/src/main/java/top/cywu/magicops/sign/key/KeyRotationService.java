package top.cywu.magicops.sign.key;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 密钥轮换服务。管理多个 keyId 的生命周期，支持新旧 keyId 共存过渡。
 *
 * <p>轮换流程：
 * <ol>
 *   <li>生成新 keyId 和密钥对</li>
 *   <li>标记新 keyId 为 active</li>
 *   <li>旧 keyId 标记为 deprecated 但仍可用于验签</li>
 *   <li>过渡期结束后移除旧 keyId</li>
 * </ol>
 */
@Service
public class KeyRotationService {

    private static final Logger log = LoggerFactory.getLogger(KeyRotationService.class);

    private final Map<String, KeyEntry> keyStore = new ConcurrentHashMap<>();
    private String activeKeyId;

    public KeyRotationService() {
        // 初始化默认密钥对
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair defaultPair = gen.generateKeyPair();

            String defaultKeyId = "default-" + Instant.now().getEpochSecond();
            keyStore.put(defaultKeyId, new KeyEntry(defaultKeyId, defaultPair, KeyStatus.ACTIVE, Instant.now()));
            this.activeKeyId = defaultKeyId;
            log.info("default_key_initialized keyId={}", defaultKeyId);
        } catch (Exception e) {
            throw new IllegalStateException("无法初始化默认密钥", e);
        }
    }

    /**
     * 生成新密钥对并设为活跃。旧密钥标记为 deprecated。
     *
     * @return 新 keyId
     */
    public String rotate() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair newPair = gen.generateKeyPair();

            // 旧密钥标记为 deprecated
            if (activeKeyId != null) {
                KeyEntry oldEntry = keyStore.get(activeKeyId);
                if (oldEntry != null) {
                    keyStore.put(activeKeyId, new KeyEntry(
                            oldEntry.keyId(), oldEntry.keyPair(),
                            KeyStatus.DEPRECATED, oldEntry.createdAt()));
                }
            }

            // 新密钥设为 active
            String newKeyId = "key-" + java.util.UUID.randomUUID().toString().substring(0, 8);
            keyStore.put(newKeyId, new KeyEntry(newKeyId, newPair, KeyStatus.ACTIVE, Instant.now()));
            this.activeKeyId = newKeyId;

            log.info("key_rotated newKeyId={} oldKeyIds={}",
                    newKeyId, getDeprecatedKeyIds());
            return newKeyId;
        } catch (Exception e) {
            throw new IllegalStateException("密钥轮换失败", e);
        }
    }

    /**
     * 移除已废弃的密钥。
     */
    public void removeDeprecated(String keyId) {
        KeyEntry entry = keyStore.get(keyId);
        if (entry != null && entry.status() == KeyStatus.DEPRECATED) {
            keyStore.remove(keyId);
            log.info("deprecated_key_removed keyId={}", keyId);
        } else {
            throw new IllegalArgumentException("只能移除已废弃的密钥: " + keyId);
        }
    }

    public String getActiveKeyId() { return activeKeyId; }

    public KeyPair getKeyPair(String keyId) {
        KeyEntry entry = keyStore.get(keyId);
        if (entry == null) throw new IllegalArgumentException("未知的 keyId: " + keyId);
        return entry.keyPair();
    }

    public boolean isTrustedKeyId(String keyId) {
        return keyStore.containsKey(keyId);
    }

    public List<String> getAllKeyIds() {
        return new ArrayList<>(keyStore.keySet());
    }

    public List<String> getDeprecatedKeyIds() {
        return keyStore.values().stream()
                .filter(e -> e.status() == KeyStatus.DEPRECATED)
                .map(KeyEntry::keyId)
                .toList();
    }

    public KeyStatus getKeyStatus(String keyId) {
        KeyEntry entry = keyStore.get(keyId);
        return entry != null ? entry.status() : null;
    }

    public enum KeyStatus { ACTIVE, DEPRECATED, REVOKED }

    private record KeyEntry(String keyId, KeyPair keyPair, KeyStatus status, Instant createdAt) {}
}
