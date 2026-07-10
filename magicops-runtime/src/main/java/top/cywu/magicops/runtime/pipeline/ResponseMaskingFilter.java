package top.cywu.magicops.runtime.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 响应脱敏过滤器。对 JSON 响应中的敏感字段进行遮蔽。
 *
 * <p>脱敏规则：
 * <ul>
 *   <li>包含 password/token/secret/key 的字段值替换为 ***</li>
 *   <li>手机号：138****1234</li>
 *   <li>身份证号：110101********1234</li>
 * </ul>
 */
@Component
@Order(10)
public class ResponseMaskingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ResponseMaskingFilter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(\\d{3})\\d{4}(\\d{4})(?!\\d)");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)(\\d{6})\\d{8}(\\d{4})(?!\\d)");
    private static final String[] SENSITIVE_KEYS = {
            "password", "token", "secret", "privatekey", "private_key",
            "apikey", "api_key", "credential"
    };

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(response instanceof HttpServletResponse httpResp)) {
            chain.doFilter(request, response);
            return;
        }

        // 包装 response 以捕获输出
        CharResponseWrapper wrapper = new CharResponseWrapper(httpResp);
        chain.doFilter(request, wrapper);

        String contentType = wrapper.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            String original = wrapper.getOutput();
            String masked = maskJson(original);
            byte[] maskedBytes = masked.getBytes(StandardCharsets.UTF_8);
            httpResp.setContentLength(maskedBytes.length);
            httpResp.getOutputStream().write(maskedBytes);
        } else {
            httpResp.getOutputStream().write(wrapper.getOutputBytes());
        }
    }

    @SuppressWarnings("unchecked")
    private String maskJson(String json) {
        if (json == null || json.isBlank()) return json;
        try {
            Object obj = MAPPER.readValue(json, Object.class);
            Object masked = maskObject(obj);
            return MAPPER.writeValueAsString(masked);
        } catch (Exception e) {
            // 非标准 JSON，做字符串级脱敏
            return maskString(json);
        }
    }

    @SuppressWarnings("unchecked")
    private Object maskObject(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey().toString();
                Object value = entry.getValue();
                if (isSensitiveKey(key)) {
                    result.put(key, "***");
                } else {
                    result.put(key, maskObject(value));
                }
            }
            return result;
        }
        if (obj instanceof java.util.List<?> list) {
            return list.stream().map(this::maskObject).toList();
        }
        if (obj instanceof String str) {
            return maskString(str);
        }
        return obj;
    }

    private String maskString(String text) {
        String result = ID_CARD_PATTERN.matcher(text).replaceAll("$1********$2");
        result = PHONE_PATTERN.matcher(result).replaceAll("$1****$2");
        return result;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        for (String sensitive : SENSITIVE_KEYS) {
            if (lower.contains(sensitive)) return true;
        }
        return false;
    }

    /**
     * Response 包装器，捕获字符输出。
     */
    private static class CharResponseWrapper extends HttpServletResponseWrapper {
        private final StringWriter output = new StringWriter();
        private final PrintWriter writer = new PrintWriter(output);

        public CharResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public PrintWriter getWriter() {
            return writer;
        }

        public String getOutput() {
            writer.flush();
            return output.toString();
        }

        public byte[] getOutputBytes() {
            return getOutput().getBytes(StandardCharsets.UTF_8);
        }
    }
}
