package top.cywu.magicops.runtime.packageMgmt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import top.cywu.magicops.core.constants.PushProtocol;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 收包端点共享密钥认证的集成测试（切片 32）。
 * 通过真实 Spring Security 过滤器链验证 401 拒绝与放行行为。
 */
@SpringBootTest(properties = "magicops.runtime.push-secret=it-push-secret")
@AutoConfigureMockMvc
class PackageReceiveAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pushWithoutSecret_rejectedWith401() throws Exception {
        mockMvc.perform(post("/api/packages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("unauthorized"));
    }

    @Test
    void pushWithWrongSecret_rejectedWith401() throws Exception {
        mockMvc.perform(post("/api/packages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PushProtocol.PUSH_SECRET_HEADER, "wrong")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pushWithCorrectSecret_passesAuthentication() throws Exception {
        // 通过认证后进入业务校验：空包体会被反序列化/验签拒绝，但不应是 401
        int status = mockMvc.perform(post("/api/packages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PushProtocol.PUSH_SECRET_HEADER, "it-push-secret")
                        .content("{}"))
                .andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertNotEquals(401, status,
                "携带正确共享密钥不应被认证层拒绝");
    }

    @Test
    void activeQueryEndpoint_openWithoutSecret() throws Exception {
        mockMvc.perform(get("/api/packages/active"))
                .andExpect(status().isOk());
    }
}
