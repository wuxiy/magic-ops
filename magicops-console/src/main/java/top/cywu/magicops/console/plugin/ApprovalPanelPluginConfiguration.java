package top.cywu.magicops.console.plugin;

import org.ssssssss.magicapi.core.config.MagicPluginConfiguration;
import org.ssssssss.magicapi.core.model.Plugin;
import org.springframework.stereotype.Component;

/**
 * 审批状态面板插件。在 magic-editor 脚本工具栏中显示审批状态。
 *
 * <p>前端插件 JS 负责在脚本编辑器底部面板显示审批时间线。
 * 后端通过 magic-api 的 MagicControllerRegister 注册查询端点。
 */
@Component
public class ApprovalPanelPluginConfiguration implements MagicPluginConfiguration {

    @Override
    public Plugin plugin() {
        return new Plugin("审批状态", "MagicApproval", "magic-approval.1.0.0.iife.js");
    }
}
