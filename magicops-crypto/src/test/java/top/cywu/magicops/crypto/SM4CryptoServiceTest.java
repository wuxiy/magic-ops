package top.cywu.magicops.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.cywu.magicops.crypto.service.SM4CryptoService;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SM4 国密算法测试。
 */
class SM4CryptoServiceTest {

    private SM4CryptoService sm4Service;
    private byte[] key;
    private byte[] iv;

    @BeforeEach
    void setUp() {
        sm4Service = new SM4CryptoService();
        key = new byte[16]; // SM4 密钥 128 位
        iv = new byte[16];  // SM4 IV 128 位
        new SecureRandom().nextBytes(key);
        new SecureRandom().nextBytes(iv);
    }

    @Test
    void encryptDecrypt_roundTrip() {
        String plaintext = "Hello MagicOps SM4 国密测试";
        String encrypted = sm4Service.encrypt(key, iv, plaintext);
        String decrypted = sm4Service.decrypt(key, iv, encrypted);

        assertEquals(plaintext, decrypted);
        assertNotEquals(plaintext, encrypted);
    }

    @Test
    void encryptDecrypt_emptyString() {
        String encrypted = sm4Service.encrypt(key, iv, "");
        String decrypted = sm4Service.decrypt(key, iv, encrypted);
        assertEquals("", decrypted);
    }

    @Test
    void encryptDecrypt_longText() {
        String longText = "a".repeat(10000);
        String encrypted = sm4Service.encrypt(key, iv, longText);
        String decrypted = sm4Service.decrypt(key, iv, encrypted);
        assertEquals(longText, decrypted);
    }

    @Test
    void decrypt_wrongKey_fails() {
        String encrypted = sm4Service.encrypt(key, iv, "secret data");

        byte[] wrongKey = new byte[16];
        new SecureRandom().nextBytes(wrongKey);

        assertThrows(Exception.class, () -> sm4Service.decrypt(wrongKey, iv, encrypted));
    }

    @Test
    void encrypt_differentIV_differentCiphertext() {
        byte[] iv2 = new byte[16];
        new SecureRandom().nextBytes(iv2);

        String encrypted1 = sm4Service.encrypt(key, iv, "same data");
        String encrypted2 = sm4Service.encrypt(key, iv2, "same data");

        assertNotEquals(encrypted1, encrypted2, "不同 IV 应产生不同密文");
    }
}
