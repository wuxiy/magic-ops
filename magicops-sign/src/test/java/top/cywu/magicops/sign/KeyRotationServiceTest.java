package top.cywu.magicops.sign;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.cywu.magicops.sign.key.KeyRotationService;

import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 密钥轮换测试。覆盖生成、轮换、共存、移除。
 */
class KeyRotationServiceTest {

    private KeyRotationService rotationService;

    @BeforeEach
    void setUp() {
        rotationService = new KeyRotationService();
    }

    @Test
    void initialState_hasDefaultKey() {
        assertNotNull(rotationService.getActiveKeyId());
        assertEquals(1, rotationService.getAllKeyIds().size());
        assertEquals(KeyRotationService.KeyStatus.ACTIVE,
                rotationService.getKeyStatus(rotationService.getActiveKeyId()));
    }

    @Test
    void rotate_createsNewKey_andDeprecatesOld() {
        String oldKeyId = rotationService.getActiveKeyId();
        String newKeyId = rotationService.rotate();

        assertNotEquals(oldKeyId, newKeyId);
        assertEquals(newKeyId, rotationService.getActiveKeyId());
        assertEquals(2, rotationService.getAllKeyIds().size());

        // 旧密钥标记为 deprecated
        assertEquals(KeyRotationService.KeyStatus.DEPRECATED,
                rotationService.getKeyStatus(oldKeyId));
        // 新密钥为 active
        assertEquals(KeyRotationService.KeyStatus.ACTIVE,
                rotationService.getKeyStatus(newKeyId));
    }

    @Test
    void rotate_multipleRotations_allDeprecated() {
        String key1 = rotationService.getActiveKeyId();
        String key2 = rotationService.rotate();
        String key3 = rotationService.rotate();

        assertEquals(key3, rotationService.getActiveKeyId());
        assertEquals(3, rotationService.getAllKeyIds().size());
        assertEquals(2, rotationService.getDeprecatedKeyIds().size());

        assertTrue(rotationService.isTrustedKeyId(key1));
        assertTrue(rotationService.isTrustedKeyId(key2));
        assertTrue(rotationService.isTrustedKeyId(key3));
    }

    @Test
    void deprecatedKey_stillUsable_forVerification() {
        String oldKeyId = rotationService.getActiveKeyId();
        KeyPair oldKeyPair = rotationService.getKeyPair(oldKeyId);
        assertNotNull(oldKeyPair.getPublic());
        assertNotNull(oldKeyPair.getPrivate());

        rotationService.rotate();

        // 旧密钥仍可获取
        KeyPair stillAvailable = rotationService.getKeyPair(oldKeyId);
        assertEquals(oldKeyPair.getPublic(), stillAvailable.getPublic());
    }

    @Test
    void removeDeprecated_succeeds() {
        String oldKeyId = rotationService.getActiveKeyId();
        rotationService.rotate();

        rotationService.removeDeprecated(oldKeyId);
        assertFalse(rotationService.isTrustedKeyId(oldKeyId));
        assertEquals(1, rotationService.getAllKeyIds().size());
    }

    @Test
    void removeActive_throwsException() {
        String activeKeyId = rotationService.getActiveKeyId();
        assertThrows(IllegalArgumentException.class,
                () -> rotationService.removeDeprecated(activeKeyId));
    }

    @Test
    void removeUnknown_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> rotationService.removeDeprecated("nonexistent-key"));
    }
}
