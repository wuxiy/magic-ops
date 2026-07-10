package top.cywu.magicops.audit.masking;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 审计脱敏服务。对敏感数据进行遮蔽后再写入审计存储。
 *
 * <p>脱敏规则：
 * <ul>
 *   <li>身份证号：110101********1234</li>
 *   <li>手机号：138****1234</li>
 *   <li>密码/token/secret/privateKey：全部替换为 ***</li>
 *   <li>数据库连接串：隐藏密码部分</li>
 * </ul>
 */
@Service
public class AuditMaskingService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(\\d{3})\\d{4}(\\d{4})(?!\\d)");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)(\\d{6})\\d{8}(\\d{4})(?!\\d)");
    private static final Pattern DB_URL_PASSWORD = Pattern.compile("password=[^&;\\s]*", Pattern.CASE_INSENSITIVE);

    /** 敏感 key 名称（小写匹配）。 */
    private static final String[] SENSITIVE_KEYS = {
            "password", "token", "secret", "privatekey", "private_key",
            "apikey", "api_key", "credential", "authorization"
    };

    /**
     * 对文本进行脱敏。
     */
    public String mask(String text) {
        if (text == null) return null;
        String result = ID_CARD_PATTERN.matcher(text).replaceAll("$1********$2");
        result = PHONE_PATTERN.matcher(result).replaceAll("$1****$2");
        result = DB_URL_PASSWORD.matcher(result).replaceAll("password=***");
        return result;
    }

    /**
     * 对 Map 中的敏感值进行脱敏。敏感 key 的值全部替换为 ***。
     */
    public Map<String, Object> maskDetails(Map<String, Object> details) {
        if (details == null) return null;
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (isSensitiveKey(key)) {
                masked.put(key, "***");
            } else if (value instanceof String strValue) {
                masked.put(key, mask(strValue));
            } else {
                masked.put(key, value);
            }
        }
        return masked;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        for (String sensitive : SENSITIVE_KEYS) {
            if (lower.contains(sensitive)) return true;
        }
        return false;
    }
}
