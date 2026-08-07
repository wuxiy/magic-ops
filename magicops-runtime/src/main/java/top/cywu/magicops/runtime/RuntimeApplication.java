package top.cywu.magicops.runtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import top.cywu.magicops.audit.api.AuditController;

/**
 * MagicOps Runtime 应用入口。
 *
 * <p>负责接收已签名发布包、验签、加载、执行动态 API、SQL Guard 和执行审计。
 *
 * <p>注意：共享 audit 模块的 {@link AuditController} 依赖 JPA 仓储，
 * Runtime 当前尚未装配审计持久化（见第五阶段切片 33），
 * 扫描时予以排除，审计走 {@code AuditService} 内存回退模式。
 */
@SpringBootApplication
@ComponentScan(basePackages = "top.cywu.magicops",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = AuditController.class))
public class RuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuntimeApplication.class, args);
    }
}
