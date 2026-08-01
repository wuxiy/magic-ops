package top.cywu.magicops.console.plugin;

import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.config.MagicPluginConfiguration;
import org.ssssssss.magicapi.core.model.Plugin;

/**
 * 审批动态插件。在 magic-editor 资源右键菜单中提供「审批动态」面板。
 *
 * <p>前端 IIFE bundle（magic-approval.1.0.0.iife.js）位于
 * {@code classpath:/magicops-editor-plugins/}，由 {@link PluginResourceController}
 * 以 {@code /magic/web/plugins/} 路径提供。面板通过同源 Session 调用
 * {@code /api/approvals} 展示最近审批记录。
 */
@Component
public class ApprovalPanelPluginConfiguration implements MagicPluginConfiguration {

    @Override
    public Plugin plugin() {
        return new Plugin("审批状态", "MagicApproval", "magic-approval.1.0.0.iife.js");
    }
}
