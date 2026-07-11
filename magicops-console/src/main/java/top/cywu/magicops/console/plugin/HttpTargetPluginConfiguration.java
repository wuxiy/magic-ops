package top.cywu.magicops.console.plugin;

import org.ssssssss.magicapi.core.config.MagicPluginConfiguration;
import org.ssssssss.magicapi.core.model.Plugin;
import org.springframework.stereotype.Component;

/**
 * HTTP 目标管理插件。在 magic-editor 中注册 HTTP 目标资源类型。
 *
 * <p>前端插件 JS 负责渲染资源树节点和 CRUD 面板。
 * 后端通过 magic-api 的 MagicControllerRegister 注册 REST 端点。
 */
@Component
public class HttpTargetPluginConfiguration implements MagicPluginConfiguration {

    @Override
    public Plugin plugin() {
        return new Plugin("HTTP目标管理", "MagicHttpTarget", "magic-httptarget.1.0.0.iife.js");
    }
}
