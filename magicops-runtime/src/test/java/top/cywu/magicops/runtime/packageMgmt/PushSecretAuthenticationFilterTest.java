package top.cywu.magicops.runtime.packageMgmt;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import top.cywu.magicops.core.constants.PushProtocol;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 推送共享密钥认证过滤器单元测试（切片 32）。
 * 覆盖关闭标准：未携带或不匹配共享密钥的包推送被 401 拒绝。
 */
class PushSecretAuthenticationFilterTest {

    @Test
    void missingHeader_rejectedWith401() throws Exception {
        var filter = new PushSecretAuthenticationFilter("s3cret", false);
        var request = post("/api/packages");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("unauthorized"));
    }

    @Test
    void mismatchedHeader_rejectedWith401() throws Exception {
        var filter = new PushSecretAuthenticationFilter("s3cret", false);
        var request = post("/api/packages");
        request.addHeader(PushProtocol.PUSH_SECRET_HEADER, "wrong-secret");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void correctHeader_passesThrough() throws Exception {
        var filter = new PushSecretAuthenticationFilter("s3cret", false);
        var request = post("/api/packages");
        request.addHeader(PushProtocol.PUSH_SECRET_HEADER, "s3cret");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotEquals(401, response.getStatus());
        assertNotNull(chain.getRequest(), "认证通过应继续过滤器链");
    }

    @Test
    void noSecret_prodProfile_failClosedWith401() throws Exception {
        var filter = new PushSecretAuthenticationFilter("", true);
        var request = post("/api/packages");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus(), "prod 未配置共享密钥必须 fail-closed");
    }

    @Test
    void noSecret_nonProd_allowedWithPassThrough() throws Exception {
        var filter = new PushSecretAuthenticationFilter("", false);
        var request = post("/api/packages");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotEquals(401, response.getStatus());
        assertNotNull(chain.getRequest(), "非生产未配置密钥应放行");
    }

    @Test
    void getActiveEndpoint_notAffectedBySecret() throws Exception {
        var filter = new PushSecretAuthenticationFilter("s3cret", true);
        var request = new MockHttpServletRequest("GET", "/api/packages/active");
        request.setRequestURI("/api/packages/active");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotEquals(401, response.getStatus(), "GET 状态查询不受推送密钥约束");
        assertNotNull(chain.getRequest());
    }

    @Test
    void otherPaths_notAffected() throws Exception {
        var filter = new PushSecretAuthenticationFilter("s3cret", true);
        var request = new MockHttpServletRequest("POST", "/api/query");
        request.setRequestURI("/api/query");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest(), "过滤器只作用于收包端点");
    }

    private MockHttpServletRequest post(String uri) {
        var request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        return request;
    }
}
