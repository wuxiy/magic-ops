package top.cywu.magicops.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.cywu.magicops.http.model.HttpTarget;
import top.cywu.magicops.http.registry.HttpTargetRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTTP 目标注册中心和客户端测试。
 */
class HttpTargetRegistryTest {

    private HttpTargetRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new HttpTargetRegistry();
    }

    @Test
    void register_andRetrieve() {
        HttpTarget target = new HttpTarget("his-001", "HIS System",
                "http://his.local:8080", List.of("/api/patients", "/api/orders"),
                "basic", Map.of(), false, null);
        registry.register(target);

        assertTrue(registry.isRegistered("his-001"));
        assertEquals(1, registry.size());

        var found = registry.getTarget("his-001");
        assertTrue(found.isPresent());
        assertEquals("HIS System", found.get().name());
    }

    @Test
    void unregister_removesTarget() {
        HttpTarget target = new HttpTarget("temp-001", "Temp",
                "http://temp.local", List.of("/api/test"),
                null, Map.of(), false, null);
        registry.register(target);
        assertTrue(registry.isRegistered("temp-001"));

        registry.unregister("temp-001");
        assertFalse(registry.isRegistered("temp-001"));
    }

    @Test
    void getTarget_unknown_returnsEmpty() {
        assertTrue(registry.getTarget("nonexistent").isEmpty());
    }

    @Test
    void isUrlAllowed_matchingPath() {
        HttpTarget target = new HttpTarget("sys-001", "System",
                "http://sys.local", List.of("/api/v1", "/api/v2"),
                null, Map.of(), false, null);
        registry.register(target);

        assertTrue(registry.isUrlAllowed("sys-001", "/api/v1/patients"));
        assertTrue(registry.isUrlAllowed("sys-001", "/api/v2/orders"));
    }

    @Test
    void isUrlAllowed_nonMatchingPath() {
        HttpTarget target = new HttpTarget("sys-002", "System",
                "http://sys.local", List.of("/api/v1"),
                null, Map.of(), false, null);
        registry.register(target);

        assertFalse(registry.isUrlAllowed("sys-002", "/api/admin/delete"));
        assertFalse(registry.isUrlAllowed("sys-002", "/api/v3/new"));
    }

    @Test
    void isUrlAllowed_unknownTarget() {
        assertFalse(registry.isUrlAllowed("unknown", "/api/test"));
    }

    @Test
    void register_nullId_throws() {
        HttpTarget target = new HttpTarget(null, "Bad", "http://bad", List.of(), null, Map.of(), false, null);
        assertThrows(IllegalArgumentException.class, () -> registry.register(target));
    }

    @Test
    void isPathAllowed_emptyAllowlist() {
        HttpTarget target = new HttpTarget("empty-001", "Empty",
                "http://empty.local", List.of(),
                null, Map.of(), false, null);
        registry.register(target);

        assertFalse(registry.isUrlAllowed("empty-001", "/api/test"));
    }
}
