package top.cywu.magicops.runtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MagicOps Runtime 应用入口。
 *
 * <p>负责接收已签名发布包、验签、加载、执行动态 API、SQL Guard 和执行审计。
 */
@SpringBootApplication(scanBasePackages = "top.cywu.magicops")
public class RuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuntimeApplication.class, args);
    }
}
