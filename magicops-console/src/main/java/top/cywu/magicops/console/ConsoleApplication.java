package top.cywu.magicops.console;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * MagicOps Console 应用入口。
 *
 * <p>负责脚本编辑、调试、审批、签名、发布推送和审计查询。
 */
@SpringBootApplication(scanBasePackages = "top.cywu.magicops")
@EntityScan(basePackages = {"top.cywu.magicops.console.entity", "top.cywu.magicops.audit.entity"})
@EnableJpaRepositories(basePackages = {"top.cywu.magicops.console.repository", "top.cywu.magicops.audit.repository"})
public class ConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsoleApplication.class, args);
    }
}
