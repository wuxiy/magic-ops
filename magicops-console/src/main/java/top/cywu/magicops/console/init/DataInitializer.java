package top.cywu.magicops.console.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import top.cywu.magicops.console.entity.config.ProjectEntity;
import top.cywu.magicops.console.repository.config.ProjectRepository;
import top.cywu.magicops.console.repository.security.UserRepository;
import top.cywu.magicops.console.service.security.UserService;

import java.util.Arrays;

/**
 * 数据初始化器。首次启动时创建默认管理员用户和默认项目。
 *
 * <p>默认管理员：admin（密码通过环境变量 {@code MAGICOPS_ADMIN_PASSWORD}
 * 或属性 {@code magicops.admin.password} 提供；dev 环境缺省 magicops-admin）。
 * <p>仅在用户表为空时创建用户，不会覆盖已有用户。
 * <p>默认项目（code=default）幂等创建，供 HTTP 目标等项目级资源引用。
 *
 * <p>切片 31 profile 约束：
 * <ul>
 *   <li>prod：仅当显式提供管理员密码时初始化 admin，永不创建 developer/approver 测试账号</li>
 *   <li>非 prod：维持 admin + developer + approver 三角色初始化（开发/测试便利）</li>
 * </ul>
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String PROD_PROFILE = "prod";
    private static final String DEFAULT_DEV_ADMIN_PASSWORD = "magicops-admin";

    private final UserService userService;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final Environment environment;
    private final String adminPasswordOverride;

    public DataInitializer(UserService userService, UserRepository userRepository,
                           ProjectRepository projectRepository, Environment environment,
                           @Value("${magicops.admin.password:}") String adminPasswordOverride) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.environment = environment;
        this.adminPasswordOverride = adminPasswordOverride;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureDefaultProject();

        if (userRepository.count() > 0) {
            log.info("数据库已有用户，跳过初始化");
            return;
        }

        if (isProd()) {
            if (adminPasswordOverride == null || adminPasswordOverride.isBlank()) {
                log.warn("生产环境未显式提供 MAGICOPS_ADMIN_PASSWORD（magicops.admin.password），"
                        + "跳过默认用户初始化");
                return;
            }
            var admin = userService.createUser(
                    "admin", adminPasswordOverride, "系统管理员", "admin@magicops.local");
            userService.assignRole(admin.getId(), "PLATFORM_ADMIN", "system");
            log.info("生产环境初始化完成：仅创建 admin (PLATFORM_ADMIN)，未创建测试账号");
            return;
        }

        String adminPassword = (adminPasswordOverride == null || adminPasswordOverride.isBlank())
                ? DEFAULT_DEV_ADMIN_PASSWORD : adminPasswordOverride;

        var admin = userService.createUser("admin", adminPassword, "系统管理员", "admin@magicops.local");
        userService.assignRole(admin.getId(), "PLATFORM_ADMIN", "system");

        // 仅非生产环境创建测试用户
        var developer = userService.createUser("developer", "dev123", "开发测试", "dev@magicops.local");
        userService.assignRole(developer.getId(), "DEVELOPER", "system");

        var approver = userService.createUser("approver", "appr123", "审批测试", "appr@magicops.local");
        userService.assignRole(approver.getId(), "APPROVER", "system");

        log.info("初始化完成：admin (PLATFORM_ADMIN), developer (DEVELOPER), approver (APPROVER)");
    }

    private boolean isProd() {
        return Arrays.asList(environment.getActiveProfiles()).contains(PROD_PROFILE);
    }

    private void ensureDefaultProject() {
        if (projectRepository.existsByCode("default")) {
            return;
        }
        projectRepository.save(new ProjectEntity("default", "默认项目", "system"));
        log.info("已创建默认项目 code=default");
    }
}
