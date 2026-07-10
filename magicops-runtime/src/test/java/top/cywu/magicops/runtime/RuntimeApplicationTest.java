package top.cywu.magicops.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RuntimeApplicationTest {

    @Test
    void mainClassExists() {
        assertDoesNotThrow(() -> Class.forName("top.cywu.magicops.runtime.RuntimeApplication"));
    }
}
