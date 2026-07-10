package top.cywu.magicops.diagnosis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.cywu.magicops.diagnosis.model.CommandTemplate;
import top.cywu.magicops.diagnosis.model.CommandTemplate.RiskLevel;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * 诊断命令模板注册中心。管理可执行的 Arthas 命令模板。
 *
 * <p>规则：
 * <ul>
 *   <li>只能执行已注册的命令模板</li>
 *   <li>自由命令被拒绝</li>
 *   <li>模板参数必须满足约束（正则白名单）</li>
 *   <li>高风险命令需要额外审批</li>
 * </ul>
 */
@Service
public class CommandTemplateRegistry {

    private static final Logger log = LoggerFactory.getLogger(CommandTemplateRegistry.class);

    private final Map<Long, CommandTemplate> templates = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    /**
     * 注册命令模板。
     */
    public CommandTemplate register(String name, String command, String parameterConstraints,
                                    RiskLevel riskLevel, boolean requiresApproval, String description) {
        long id = idGenerator.getAndIncrement();
        CommandTemplate template = new CommandTemplate(id, name, command, parameterConstraints,
                riskLevel, requiresApproval, description);
        templates.put(id, template);
        log.info("template_registered id={} name={} riskLevel={}", id, name, riskLevel);
        return template;
    }

    /**
     * 解析命令模板，将参数替换到命令中。
     *
     * @throws IllegalArgumentException 参数不满足约束
     */
    public String resolve(Long templateId, Map<String, String> parameters) {
        CommandTemplate template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("命令模板不存在: " + templateId);
        }

        String resolved = template.command();
        if (parameters != null) {
            for (Map.Entry<String, String> param : parameters.entrySet()) {
                String value = param.getValue();
                // 校验参数约束
                if (template.parameterConstraints() != null && !template.parameterConstraints().isBlank()) {
                    Pattern pattern = Pattern.compile(template.parameterConstraints());
                    if (!pattern.matcher(value).matches()) {
                        throw new IllegalArgumentException(
                                "参数 " + param.getKey() + " 不满足约束: " + template.parameterConstraints());
                    }
                }
                resolved = resolved.replace("{" + param.getKey() + "}", value);
            }
        }
        return resolved;
    }

    /**
     * 获取模板。
     */
    public Optional<CommandTemplate> getTemplate(Long id) {
        return Optional.ofNullable(templates.get(id));
    }

    /**
     * 按名称查找模板。
     */
    public Optional<CommandTemplate> findByName(String name) {
        return templates.values().stream()
                .filter(t -> t.name().equals(name))
                .findFirst();
    }

    /**
     * 获取所有模板。
     */
    public List<CommandTemplate> getAll() {
        return new ArrayList<>(templates.values());
    }

    /**
     * 获取指定风险等级的模板。
     */
    public List<CommandTemplate> getByRiskLevel(RiskLevel riskLevel) {
        return templates.values().stream()
                .filter(t -> t.riskLevel() == riskLevel)
                .toList();
    }
}
