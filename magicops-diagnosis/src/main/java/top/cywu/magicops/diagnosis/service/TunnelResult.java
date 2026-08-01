package top.cywu.magicops.diagnosis.service;

/**
 * Tunnel 命令执行结果。
 *
 * @param success   是否成功执行（模拟降级也视为成功）
 * @param simulated 是否为模拟降级输出（未配置或无法连接真实 Tunnel 时）
 * @param message   状态或错误信息
 */
public record TunnelResult(boolean success, boolean simulated, String message) {

    public static TunnelResult ofReal() {
        return new TunnelResult(true, false, "EXECUTED");
    }

    public static TunnelResult ofSimulated() {
        return new TunnelResult(true, true, "SIMULATED");
    }

    public static TunnelResult ofFailure(String message) {
        return new TunnelResult(false, false, message);
    }
}
