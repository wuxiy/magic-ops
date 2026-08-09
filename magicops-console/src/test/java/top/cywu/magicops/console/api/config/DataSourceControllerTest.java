package top.cywu.magicops.console.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据源管理 API 测试（切片 36）。
 *
 * <p>覆盖关闭标准：admin 可创建/查询数据源；权限隔离生效。
 */
@SpringBootTest
@AutoConfigureMockMvc
class DataSourceControllerTest {

    private static final String BASIC = "Basic "
            + java.util.Base64.getEncoder().encodeToString("admin:magicops-admin".getBytes());

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createAndListDataSource_asAdmin() throws Exception {
        String body = "{\"name\":\"test-ds-controller\","
                + "\"driverClass\":\"org.h2.Driver\","
                + "\"jdbcUrl\":\"jdbc:h2:mem:ds_ctrl_test\","
                + "\"username\":\"sa\"}";
        mockMvc.perform(post("/api/datasources")
                        .header("Authorization", BASIC)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("test-ds-controller"));

        mockMvc.perform(get("/api/datasources")
                        .header("Authorization", BASIC))
                .andExpect(status().isOk());
    }

    @Test
    void listWithoutAuth_unauthorized() throws Exception {
        mockMvc.perform(get("/api/datasources"))
                .andExpect(status().isUnauthorized());
    }
}
