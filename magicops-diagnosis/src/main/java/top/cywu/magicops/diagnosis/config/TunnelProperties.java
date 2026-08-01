package top.cywu.magicops.diagnosis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Arthas Tunnel 连接配置。
 *
 * <p>配置前缀 {@code magicops.diagnosis.tunnel}。未启用或未配置 serverUrl 时，
 * TunnelClient 按 {@code simulateFallback} 决定是否回退到模拟输出。
 */
@ConfigurationProperties(prefix = "magicops.diagnosis.tunnel")
public class TunnelProperties {

    /** 是否启用真实 Tunnel 连接。 */
    private boolean enabled = false;

    /** Arthas Tunnel Server 客户端接入地址，如 ws://tunnel-server:7777/ws/client。 */
    private String serverUrl;

    /** 连接到 Tunnel Server 时使用的客户端名称。 */
    private String clientName = "magicops";

    /** 可选认证 Token（Tunnel Server 密码/API Key）。 */
    private String authToken;

    /** WebSocket 连接超时（毫秒）。 */
    private long connectTimeoutMs = 5000;

    /** 单条命令等待输出的超时（毫秒）。 */
    private long commandTimeoutMs = 30000;

    /** 未启用/连接失败时是否回退到模拟输出。 */
    private boolean simulateFallback = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }

    public long getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(long connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public long getCommandTimeoutMs() { return commandTimeoutMs; }
    public void setCommandTimeoutMs(long commandTimeoutMs) { this.commandTimeoutMs = commandTimeoutMs; }

    public boolean isSimulateFallback() { return simulateFallback; }
    public void setSimulateFallback(boolean simulateFallback) { this.simulateFallback = simulateFallback; }
}
