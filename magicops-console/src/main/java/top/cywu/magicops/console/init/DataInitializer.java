package top.cywu.magicops.console.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import top.cywu.magicops.console.entity.config.ProjectEntity;
import top.cywu.magicops.console.repository.config.ProjectRepository;
import top.cywu.magicops.console.repository.security.UserRepository;
import top.cywu.magicops.console.service.security.UserService;

/**
 * 数据初始化器。首次启动时创建默认管理员用户和默认项目。
 *
 * <p>默认管理员：admin / magicops-admin（可通过环境变量覆盖）。
 * <p>仅在用户表为空时创建用户，不会覆盖已有用户。
 * <p>默认项目（code=default）幂等创建，供 HTTP 目标等项目级资源引用。
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserService userService;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;

    public DataInitializer(UserService userService, UserRepository userRepository,
                           ProjectRepository projectRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureDefaultProject();

        if (userRepository.count() > 0) {
            log.info("数据库已有用户，跳过初始化");
            return;
        }

        String adminPassword = System.getenv().getOrDefault(
                "MAGICOPS_ADMIN_PASSWORD", "magicops-admin");

        var admin = userService.createUser("admin", adminPassword, "系统管理员", "admin@magicops.local");
        userService.assignRole(admin.getId(), "PLATFORM_ADMIN", "system");

        // 创建测试用户
        var developer = userService.createUser("developer", "dev123", "开发测试", "dev@magicops.local");
        userService.assignRole(developer.getId(), "DEVELOPER", "system");

        var approver = userService.createUser("approver", "appr123", "审批测试", "appr@magicops.local");
        userService.assignRole(approver.getId(), "APPROVER", "system");

        log.info("初始化完成：admin (PLATFORM_ADMIN), developer (DEVELOPER), approver (APPROVER)");
    }

    private void ensureDefaultProject() {
        if (projectRepository.existsByCode("default")) {
            return;
        }
        projectRepository.save(new ProjectEntity("default", "默认项目", "system"));
        log.info("已创建默认项目 code=default");
    }
}
