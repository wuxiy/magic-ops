package top.cywu.magicops.crypto.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 加解密服务。第一阶段支持 AES、HMAC 和脱敏。
 *
 * <p>脚本必须使用 keyId 引用密钥，不能直接使用原始密钥。
 */
@Service
public class CryptoService {

    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    /** key ID → 原始密钥（第一版内存存储，后续接入密钥管理服务）。 */
    private final Map<String, byte[]> keyStore = new ConcurrentHashMap<>();

    /** 脱敏规则。 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(\\d{3})\\d{4}(\\d{4})(?!\\d)");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)(\\d{6})\\d{8}(\\d{4})(?!\\d)");

    /**
     * 注册密钥（第一版内存存储）。
     */
    public void registerKey(String keyId, byte[] keyBytes) {
        keyStore.put(keyId, keyBytes);
        log.info("key_registered keyId={}", keyId);
    }

    /**
     * 使用 keyId 对应的密钥进行 AES-GCM 加密。
     */
    public String encrypt(String keyId, String plaintext) {
        byte[] key = getKeyOrThrow(keyId);
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 拼接 IV + 密文
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用 keyId 对应的密钥进行 AES-GCM 解密。
     */
    public String decrypt(String keyId, String ciphertext) {
        byte[] key = getKeyOrThrow(keyId);
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES 解密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用 keyId 对应的密钥计算 HMAC-SHA256。
     */
    public String hmacSha256(String keyId, String data) {
        byte[] key = getKeyOrThrow(keyId);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证 HMAC-SHA256。
     */
    public boolean verifyHmac(String keyId, String data, String expectedHmac) {
        String actual = hmacSha256(keyId, data);
        return actual.equals(expectedHmac);
    }

    /**
     * 脱敏处理。对敏感信息进行遮蔽。先匹配身份证（18位），再匹配手机号（11位）。
     */
    public String mask(String text) {
        if (text == null) return null;

        // 身份证脱敏（先匹配更长的模式）：110101********1234
        String result = ID_CARD_PATTERN.matcher(text).replaceAll("$1********$2");

        // 手机号脱敏：138****1234
        result = PHONE_PATTERN.matcher(result).replaceAll("$1****$2");

        return result;
    }

    /**
     * 检查 keyId 是否已注册。
     */
    public boolean hasKey(String keyId) {
        return keyStore.containsKey(keyId);
    }

    private byte[] getKeyOrThrow(String keyId) {
        byte[] key = keyStore.get(keyId);
        if (key == null) {
            throw new IllegalArgumentException("未注册的密钥 ID: " + keyId);
        }
        return key;
    }
}
