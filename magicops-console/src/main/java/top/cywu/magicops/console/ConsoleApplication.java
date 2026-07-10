package top.cywu.magicops.console;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MagicOps Console 应用入口。
 *
 * <p>负责脚本编辑、调试、审批、签名、发布推送和审计查询。
 */
@SpringBootApplication(scanBasePackages = "top.cywu.magicops")
public class ConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsoleApplication.class, args);
    }
}
