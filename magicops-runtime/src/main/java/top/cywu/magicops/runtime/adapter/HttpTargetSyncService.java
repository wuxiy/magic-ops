package top.cywu.magicops.runtime.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.cywu.magicops.http.model.HttpTarget;
import top.cywu.magicops.http.registry.HttpTargetRegistry;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 从 {@code http_targets} 表同步 HTTP 目标到 Runtime 注册表（切片 33-c）。
 *
 * <p>打通 Console CRUD -> Runtime 生效链路：Console 在 {@code http_targets} 表写入/启停目标，
 * Runtime 启动时加载并定时刷新（默认每 60 秒）。只有 enabled=true 的目标进入注册表；
 * 注册表中已被禁用或删除的目标会被移除。
 *
 * <p>轻量实现：直接 JDBC 读取共享 PostgreSQL，不引入重复 Entity，不向 magicops-http 共享库添加 JPA 依赖。
 */
@Component
public class HttpTargetSyncService {

    private static final Logger log = LoggerFactory.getLogger(HttpTargetSyncService.class);

    private final HttpTargetRegistry registry;
    private final JdbcTemplate jdbc;

    public HttpTargetSyncService(HttpTargetRegistry registry, DataSource dataSource) {
        this.registry = registry;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 应用就绪后立即同步一次，避免首请求命中空注册表。 */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        syncNow();
    }

    /** 定时刷新，默认每 60 秒。 */
    @Scheduled(fixedDelayString = "${magicops.runtime.http-target-refresh-ms:60000}", initialDelayString = "${magicops.runtime.http-target-refresh-ms:60000}")
    public void scheduledSync() {
        syncNow();
    }

    /**
     * 执行一次同步。先重建注册表：读取 DB 中全部 enabled 目标，与当前注册表比对，增量注册/注销。
     */
    public void syncNow() {
        List<HttpTarget> targets;
        try {
            targets = loadEnabledTargets();
        } catch (Exception e) {
            log.warn("http_target_sync_failed reason={}", e.getMessage());
            return;
        }

        Set<String> activeIds = new HashSet<>();
        for (HttpTarget t : targets) {
            activeIds.add(t.id());
            registry.register(t);
        }

        // 移除 DB 中已不存在或已禁用的目标
        for (String registeredId : registry.ids()) {
            if (!activeIds.contains(registeredId)) {
                registry.unregister(registeredId);
            }
        }
        log.info("http_target_synced active={} total_registered={}", activeIds.size(), registry.size());
    }

    private List<HttpTarget> loadEnabledTargets() {
        return jdbc.query(
                "SELECT target_id, name, base_url, allowed_paths, auth_type, "
                        + "require_encryption, crypto_key_id FROM http_targets WHERE enabled = TRUE",
                (rs, rowNum) -> {
                    String allowedPaths = rs.getString("allowed_paths");
                    List<String> paths = parsePaths(allowedPaths);
                    return new HttpTarget(
                            rs.getString("target_id"),
                            rs.getString("name"),
                            rs.getString("base_url"),
                            paths,
                            rs.getString("auth_type"),
                            java.util.Map.of(),
                            rs.getBoolean("require_encryption"),
                            rs.getString("crypto_key_id")
                    );
                });
    }

    private List<String> parsePaths(String allowedPaths) {
        if (allowedPaths == null || allowedPaths.isBlank()) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        for (String p : allowedPaths.split(",")) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                paths.add(trimmed);
            }
        }
        return paths;
    }
}
