package top.cywu.magicops.console.plugin;

import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.config.MagicPluginConfiguration;
import org.ssssssss.magicapi.core.model.Plugin;

/**
 * HTTP 目标管理插件。在 magic-editor 资源右键菜单中提供「HTTP 目标管理」面板。
 *
 * <p>前端 IIFE bundle（magic-httptarget.1.0.0.iife.js）位于
 * {@code classpath:/magicops-editor-plugins/}，由 {@link PluginResourceController}
 * 以 {@code /magic/web/plugins/} 路径提供。面板通过同源 Session 调用
 * {@code /api/httptargets}（见 {@code HttpTargetController}）做增删查。
 */
@Component
public class HttpTargetPluginConfiguration implements MagicPluginConfiguration {

    @Override
    public Plugin plugin() {
        return new Plugin("HTTP目标管理", "MagicHttpTarget", "magic-httptarget.1.0.0.iife.js");
    }
}
