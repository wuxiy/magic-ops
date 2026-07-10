package top.cywu.magicops.http.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import top.cywu.magicops.http.model.HttpTarget;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP 目标注册中心。管理已注册的 HTTP 目标系统。
 * 脚本只能调用已注册目标，不能调用任意 URL。
 */
@Component
public class HttpTargetRegistry {

    private static final Logger log = LoggerFactory.getLogger(HttpTargetRegistry.class);

    private final Map<String, HttpTarget> targets = new ConcurrentHashMap<>();

    /**
     * 注册 HTTP 目标。
     */
    public void register(HttpTarget target) {
        if (target == null || target.id() == null) {
            throw new IllegalArgumentException("目标及其 ID 不能为空");
        }
        targets.put(target.id(), target);
        log.info("http_target_registered id={} name={} baseUrl={}",
                target.id(), target.name(), target.baseUrl());
    }

    /**
     * 注销 HTTP 目标。
     */
    public void unregister(String targetId) {
        targets.remove(targetId);
        log.info("http_target_unregistered id={}", targetId);
    }

    /**
     * 获取已注册目标。
     */
    public Optional<HttpTarget> getTarget(String targetId) {
        return Optional.ofNullable(targets.get(targetId));
    }

    /**
     * 检查目标是否已注册。
     */
    public boolean isRegistered(String targetId) {
        return targets.containsKey(targetId);
    }

    /**
     * 验证 URL 是否属于已注册目标的 allowlist。
     */
    public boolean isUrlAllowed(String targetId, String path) {
        HttpTarget target = targets.get(targetId);
        if (target == null) return false;
        return target.isPathAllowed(path);
    }

    public int size() {
        return targets.size();
    }
}
