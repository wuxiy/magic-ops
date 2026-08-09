package top.cywu.magicops.runtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MagicOps Runtime 应用入口。
 *
 * <p>负责接收已签名发布包、验签、加载、执行动态 API、SQL Guard 和执行审计。
 *
 * <p>切片 33 起，Runtime 装配审计持久化：扫描 audit 模块的 JPA 仓储与实体，
 * 执行审计落共享 PostgreSQL（开发态由 Hibernate 建表，生产态 validate Console 建好的 schema），
 * 不再走内存回退。Console 与 Runtime 共享同一 PostgreSQL；Console 经 Flyway 拥有生产 schema，
 * Runtime 以 {@code ddl-auto=validate} 校验，不自建迁移，避免 schema 双写漂移。
 */
@SpringBootApplication
@ComponentScan(basePackages = "top.cywu.magicops")
@EntityScan(basePackages = {"top.cywu.magicops.audit.entity", "top.cywu.magicops.runtime"})
@EnableJpaRepositories(basePackages = {"top.cywu.magicops.audit.repository", "top.cywu.magicops.runtime"})
@EnableScheduling
public class RuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuntimeApplication.class, args);
    }
}
