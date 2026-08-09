package top.cywu.magicops.runtime.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;

/**
 * 从 {@code data_sources} 表同步业务数据源到 {@link DynamicDataSourceManager}（切片 36）。
 *
 * <p>打通 Console 数据源管理 -> Runtime 生效链路：Console 在 {@code data_sources} 表写入/启停数据源，
 * Runtime 启动时加载并定时刷新（默认每 60 秒），使只读灰度查询可指向真实业务库。
 *
 * <p>凭据安全：{@code password_ref} 不落明文密码，按 {@code env:VARNAME} 前缀从 Runtime 环境变量解析真实密码；
 * 未带前缀则视为环境变量名；为空则空密码（仅限无密码库如开发 H2）。
 *
 * <p>轻量实现：直接 JDBC 读取共享 PostgreSQL，不引入重复 Entity。
 */
@Component
public class DataSourceSyncService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceSyncService.class);
    private static final String ENV_PREFIX = "env:";

    private final DynamicDataSourceManager dataSourceManager;
    private final JdbcTemplate jdbc;

    public DataSourceSyncService(DynamicDataSourceManager dataSourceManager, DataSource dataSource) {
        this.dataSourceManager = dataSourceManager;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        syncNow();
    }

    @Scheduled(fixedDelayString = "${magicops.runtime.datasource-refresh-ms:60000}",
            initialDelayString = "${magicops.runtime.datasource-refresh-ms:60000}")
    public void scheduledSync() {
        syncNow();
    }

    /**
     * 执行一次同步：读取 enabled 数据源行，注册到 DynamicDataSourceManager。
     */
    public void syncNow() {
        List<DataSourceRow> rows;
        try {
            rows = loadEnabledDataSources();
        } catch (Exception e) {
            log.warn("datasource_sync_failed reason={}", e.getMessage());
            return;
        }

        for (DataSourceRow row : rows) {
            try {
                String password = resolvePassword(row.passwordRef());
                dataSourceManager.registerDataSource(row.name(), row.jdbcUrl(), row.username(), password);
                log.info("datasource_synced name={} url={} readOnly={}", row.name(), maskUrl(row.jdbcUrl()), row.readOnly());
            } catch (Exception e) {
                log.warn("datasource_register_failed name={} reason={}", row.name(), e.getMessage());
            }
        }
        log.info("datasource_sync_complete count={}", rows.size());
    }

    private List<DataSourceRow> loadEnabledDataSources() {
        return jdbc.query(
                "SELECT name, jdbc_url, username, password_ref, read_only FROM data_sources WHERE enabled = TRUE",
                (rs, rowNum) -> new DataSourceRow(
                        rs.getString("name"),
                        rs.getString("jdbc_url"),
                        rs.getString("username"),
                        rs.getString("password_ref"),
                        rs.getBoolean("read_only")));
    }

    /** 按 env:VARNAME 解析密码；未带前缀则视为环境变量名；为空返回空串。 */
    private String resolvePassword(String passwordRef) {
        if (passwordRef == null || passwordRef.isBlank()) {
            return "";
        }
        String varName = passwordRef.startsWith(ENV_PREFIX)
                ? passwordRef.substring(ENV_PREFIX.length())
                : passwordRef;
        String value = System.getenv(varName);
        return value != null ? value : "";
    }

    /** 日志中脱敏 jdbcUrl 凭据段。 */
    private String maskUrl(String url) {
        if (url == null) return null;
        return url.replaceAll("password=[^&;]*", "password=***");
    }

    private record DataSourceRow(String name, String jdbcUrl, String username,
                                 String passwordRef, boolean readOnly) {}
}
