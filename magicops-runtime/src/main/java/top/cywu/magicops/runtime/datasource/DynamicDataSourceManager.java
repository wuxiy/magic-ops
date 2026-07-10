package top.cywu.magicops.runtime.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages dynamic data sources for Runtime query execution.
 * Supports registering named data sources and executing queries against them.
 */
@Component
public class DynamicDataSourceManager {

    private static final Logger log = LoggerFactory.getLogger(DynamicDataSourceManager.class);

    private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();
    private DataSource defaultDataSource;

    public DynamicDataSourceManager() {
        // Create default H2 in-memory data source for testing
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:magicops_query;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(5);
        config.setPoolName("magicops-default-pool");
        this.defaultDataSource = new HikariDataSource(config);
        dataSources.put("default", defaultDataSource);
        log.info("Initialized default H2 data source for query execution");
    }

    public void registerDataSource(String name, String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(5);
        config.setPoolName("magicops-" + name + "-pool");
        DataSource ds = new HikariDataSource(config);
        dataSources.put(name, ds);
        log.info("Registered data source: {}", name);
    }

    public DataSource getDataSource(String name) {
        DataSource ds = dataSources.get(name);
        if (ds == null) {
            throw new IllegalArgumentException("Unknown data source: " + name);
        }
        return ds;
    }

    public DataSource getDefaultDataSource() {
        return defaultDataSource;
    }

    /**
     * Execute a SELECT query and return results as a list of maps.
     *
     * @param dataSourceName the data source to query against
     * @param sql            the SELECT SQL
     * @param params         query parameters
     * @param maxRows        maximum number of rows to return
     * @return query results
     */
    public QueryResult executeQuery(String dataSourceName, String sql, List<Object> params, int maxRows) {
        DataSource ds = getDataSource(dataSourceName);
        long startTime = System.currentTimeMillis();

        try (Connection conn = ds.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setMaxRows(maxRows);
            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }
            }

            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();

                while (rs.next() && rows.size() < maxRows) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }

                long duration = System.currentTimeMillis() - startTime;
                return new QueryResult(rows, rows.size(), duration, true, null);
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Query execution failed: {}", e.getMessage());
            return new QueryResult(List.of(), 0, duration, false, e.getMessage());
        }
    }

    /**
     * Execute an UPDATE/INSERT/DELETE statement and return affected row count.
     *
     * @param dataSourceName the data source to execute against
     * @param sql            the UPDATE/INSERT/DELETE SQL
     * @param params         statement parameters
     * @return update result
     */
    public QueryResult executeUpdate(String dataSourceName, String sql, List<Object> params) {
        DataSource ds = getDataSource(dataSourceName);
        long startTime = System.currentTimeMillis();

        try (Connection conn = ds.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }
            }

            int affectedRows = stmt.executeUpdate();
            long duration = System.currentTimeMillis() - startTime;
            return new QueryResult(List.of(), affectedRows, duration, true, null);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Update execution failed: {}", e.getMessage());
            return new QueryResult(List.of(), 0, duration, false, e.getMessage());
        }
    }

    public record QueryResult(
            List<Map<String, Object>> rows,
            int rowCount,
            long durationMs,
            boolean success,
            String errorMessage
    ) {}
}
