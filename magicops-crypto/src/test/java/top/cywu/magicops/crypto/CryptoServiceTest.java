package top.cywu.magicops.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.cywu.magicops.crypto.service.CryptoService;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CryptoService AES-GCM 和 HMAC-SHA256 测试。
 */
class CryptoServiceTest {

    private CryptoService cryptoService;

    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService();
        // 注册 AES-256 密钥
        byte[] aesKey = new byte[32];
        new SecureRandom().nextBytes(aesKey);
        cryptoService.registerKey("aes-key-001", aesKey);

        // 注册 HMAC 密钥
        byte[] hmacKey = new byte[32];
        new SecureRandom().nextBytes(hmacKey);
        cryptoService.registerKey("hmac-key-001", hmacKey);
    }

    // ---- AES-GCM 测试 ----

    @Test
    void aesEncryptDecrypt_roundTrip() {
        String plaintext = "Hello MagicOps AES-GCM";
        String encrypted = cryptoService.encrypt("aes-key-001", plaintext);
        String decrypted = cryptoService.decrypt("aes-key-001", encrypted);

        assertEquals(plaintext, decrypted);
        assertNotEquals(plaintext, encrypted);
    }

    @Test
    void aesEncrypt_emptyString() {
        String encrypted = cryptoService.encrypt("aes-key-001", "");
        String decrypted = cryptoService.decrypt("aes-key-001", encrypted);
        assertEquals("", decrypted);
    }

    @Test
    void aesEncrypt_longText() {
        String longText = "a".repeat(10000);
        String encrypted = cryptoService.encrypt("aes-key-001", longText);
        String decrypted = cryptoService.decrypt("aes-key-001", encrypted);
        assertEquals(longText, decrypted);
    }

    @Test
    void aesEncrypt_differentCiphertextEachTime() {
        String plaintext = "same input";
        String enc1 = cryptoService.encrypt("aes-key-001", plaintext);
        String enc2 = cryptoService.encrypt("aes-key-001", plaintext);
        // GCM uses random IV, so ciphertexts should differ
        assertNotEquals(enc1, enc2);
    }

    @Test
    void aesDecrypt_wrongKey_fails() {
        byte[] wrongKey = new byte[32];
        new SecureRandom().nextBytes(wrongKey);
        cryptoService.registerKey("wrong-key", wrongKey);

        String encrypted = cryptoService.encrypt("aes-key-001", "secret data");
        assertThrows(Exception.class, () -> cryptoService.decrypt("wrong-key", encrypted));
    }

    @Test
    void aesEncrypt_unknownKeyId_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> cryptoService.encrypt("nonexistent-key", "data"));
    }

    // ---- HMAC-SHA256 测试 ----

    @Test
    void hmacSign_verify() {
        String data = "request payload for signing";
        String hmac = cryptoService.hmacSha256("hmac-key-001", data);

        assertTrue(cryptoService.verifyHmac("hmac-key-001", data, hmac));
    }

    @Test
    void hmacSign_tamperedData_fails() {
        String data = "original data";
        String hmac = cryptoService.hmacSha256("hmac-key-001", data);

        assertFalse(cryptoService.verifyHmac("hmac-key-001", "tampered data", hmac));
    }

    @Test
    void hmacSign_differentKeys_differentHmac() {
        byte[] otherKey = new byte[32];
        new SecureRandom().nextBytes(otherKey);
        cryptoService.registerKey("hmac-key-002", otherKey);

        String data = "same data";
        String hmac1 = cryptoService.hmacSha256("hmac-key-001", data);
        String hmac2 = cryptoService.hmacSha256("hmac-key-002", data);

        assertNotEquals(hmac1, hmac2);
    }

    @Test
    void hmacSign_deterministic() {
        String data = "deterministic test";
        String hmac1 = cryptoService.hmacSha256("hmac-key-001", data);
        String hmac2 = cryptoService.hmacSha256("hmac-key-001", data);

        assertEquals(hmac1, hmac2, "相同密钥和数据应产生相同 HMAC");
    }

    // ---- Key 管理测试 ----

    @Test
    void hasKey_registered() {
        assertTrue(cryptoService.hasKey("aes-key-001"));
        assertTrue(cryptoService.hasKey("hmac-key-001"));
    }

    @Test
    void hasKey_unregistered() {
        assertFalse(cryptoService.hasKey("nonexistent"));
    }
}
