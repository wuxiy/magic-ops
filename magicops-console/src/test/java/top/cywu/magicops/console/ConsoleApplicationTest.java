package top.cywu.magicops.console;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ConsoleApplicationTest {

    @Test
    void mainClassExists() {
        assertDoesNotThrow(() -> Class.forName("top.cywu.magicops.console.ConsoleApplication"));
    }
}
