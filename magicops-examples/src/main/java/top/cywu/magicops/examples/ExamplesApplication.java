package top.cywu.magicops.examples;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MagicOps 示例应用。
 *
 * <p>基于 magic-api-spring-boot-starter 启动，提供动态 API 能力。
 *
 * <p>启动后访问：
 * <ul>
 *   <li>Web 编辑器：http://localhost:9999/magic/web</li>
 *   <li>H2 控制台：http://localhost:9999/h2-console</li>
 *   <li>示例 API：http://localhost:9999/api/users</li>
 * </ul>
 */
@SpringBootApplication
public class ExamplesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamplesApplication.class, args);
    }
}
