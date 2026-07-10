package top.cywu.magicops.diagnosis.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 诊断输出脱敏服务。对 Arthas 诊断输出中的敏感信息进行遮蔽。
 *
 * <p>脱敏规则：
 * <ul>
 *   <li>密码/密钥/token 替换为 ***</li>
 *   <li>数据库连接串隐藏密码部分</li>
 *   <li>手机号、身份证号脱敏</li>
 * </ul>
 */
@Service
public class OutputMaskingService {

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("(?i)(password|passwd|pwd)\\s*[=:]\\s*[^\\s,;\\]]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRET_PATTERN =
            Pattern.compile("(?i)(secret|token|apikey|api_key|private_key|credential)\\s*[=:]\\s*[^\\s,;\\]]*",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern DB_URL_PASSWORD =
            Pattern.compile("password=[^&;\\s]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\d)(\\d{3})\\d{4}(\\d{4})(?!\\d)");
    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile("(?<!\\d)(\\d{6})\\d{8}(\\d{4})(?!\\d)");

    /**
     * 对诊断输出文本进行脱敏。
     */
    public String mask(String output) {
        if (output == null || output.isBlank()) return output;

        String result = output;
        result = PASSWORD_PATTERN.matcher(result).replaceAll("$1=***");
        result = SECRET_PATTERN.matcher(result).replaceAll("$1=***");
        result = DB_URL_PASSWORD.matcher(result).replaceAll("password=***");
        result = ID_CARD_PATTERN.matcher(result).replaceAll("$1********$2");
        result = PHONE_PATTERN.matcher(result).replaceAll("$1****$2");
        return result;
    }
}
