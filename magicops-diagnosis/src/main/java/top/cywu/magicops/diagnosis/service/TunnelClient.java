package top.cywu.magicops.diagnosis.service;

import java.util.function.Consumer;

/**
 * Arthas Tunnel 客户端抽象。负责经 Arthas Tunnel Server 将诊断命令下发到目标
 * JVM 的 Arthas Agent，并以流式方式回传输出。
 *
 * <p>实现需处理：WebSocket 连接管理、按 agentId 路由、命令下发、流式输出接收、
 * 命令完成检测、超时与连接异常传播，以及在未配置/不可达时的模拟降级。
 */
public interface TunnelClient {

    /**
     * 在目标 Agent 上执行命令，输出帧通过 {@code outputConsumer} 流式回传。
     *
     * @param agentId        目标 Arthas Agent 在 Tunnel Server 的标识
     * @param command        已解析的 Arthas 命令
     * @param outputConsumer 接收输出帧（每帧为一段文本，调用方负责脱敏与转发）
     * @return 执行结果（含成功标志与是否模拟降级）
     */
    TunnelResult execute(String agentId, String command, Consumer<String> outputConsumer);

    /**
     * 是否已配置并启用真实 Tunnel 连接。
     */
    boolean isRealTunnelEnabled();
}
