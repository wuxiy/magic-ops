# Arthas 诊断中心架构设计

## 状态

已设计，待实现（V1.5）。

## 架构概览

```text
┌─────────────────────────────────────────────┐
│                 Console Web                  │
│  ┌─────────────┐  ┌──────────────────────┐  │
│  │ Diagnosis   │  │ Diagnosis Audit      │  │
│  │ Console UI  │  │ Query & Export       │  │
│  └──────┬──────┘  └──────────────────────┘  │
└─────────┼───────────────────────────────────┘
          │ WebSocket + REST
┌─────────┼───────────────────────────────────┐
│   magicops-diagnosis (独立模块)              │
│  ┌──────┴──────┐  ┌──────────────────────┐  │
│  │ Session     │  │ Command Template     │  │
│  │ Manager     │  │ Registry             │  │
│  └──────┬──────┘  └──────────┬───────────┘  │
│         │                    │              │
│  ┌──────┴──────┐  ┌──────────┴───────────┐  │
│  │ Tunnel      │  │ Output Masking &     │  │
│  │ Client      │  │ Archival             │  │
│  └──────┬──────┘  └──────────────────────┘  │
└─────────┼───────────────────────────────────┘
          │ Arthas Tunnel Protocol
┌─────────┼───────────────────────────────────┐
│   Arthas Tunnel Server (独立部署)            │
└─────────┼───────────────────────────────────┘
          │ Arthas Agent Attach
┌─────────┼───────────────────────────────────┐
│   目标应用 JVM (Arthas Agent)                │
└─────────────────────────────────────────────┘
```

## 模块结构

```text
magicops-diagnosis
├── src/main/java/top/cywu/magicops/diagnosis/
│   ├── model/
│   │   ├── DiagnosisSession.java       # 诊断会话模型
│   │   ├── CommandTemplate.java        # 命令模板模型
│   │   ├── CommandExecution.java       # 命令执行记录
│   │   └── DiagnosisReport.java        # 诊断报告模型
│   ├── service/
│   │   ├── SessionManager.java         # 会话生命周期管理
│   │   ├── CommandTemplateRegistry.java # 命令模板注册与校验
│   │   ├── TunnelClient.java           # Arthas Tunnel 通信
│   │   └── OutputMaskingService.java   # 输出脱敏
│   └── api/
│       ├── DiagnosisController.java    # REST API
│       └── DiagnosisWebSocket.java     # WebSocket 实时输出
```

## 数据模型

### DiagnosisSession

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 会话 ID |
| targetApp | String | 目标应用名称 |
| targetHost | String | 目标主机 IP |
| targetPort | int | 目标 Arthas Agent 端口 |
| operatorId | Long | 操作人用户 ID |
| status | Enum | CREATED / ACTIVE / CLOSED / TIMEOUT |
| createdAt | Instant | 创建时间 |
| closedAt | Instant | 关闭时间 |
| timeoutMinutes | int | 超时时间（默认 30） |

### CommandTemplate

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 模板 ID |
| name | String | 模板名称（如 thread-top10） |
| command | String | Arthas 命令（如 thread -n {count}） |
| parameterConstraints | JSON | 参数约束（正则白名单） |
| riskLevel | Enum | LOW / MEDIUM / HIGH / CRITICAL |
| requiresApproval | boolean | 是否需要额外审批 |
| description | String | 模板说明 |

### CommandExecution

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 执行 ID |
| sessionId | Long | 所属会话 |
| templateId | Long | 使用的命令模板 |
| parameters | JSON | 实际参数值 |
| resolvedCommand | String | 解析后的完整命令 |
| output | TEXT | 执行输出（脱敏后） |
| durationMs | Long | 执行耗时 |
| status | Enum | SUCCESS / FAILED / TIMEOUT / REJECTED |
| executedAt | Instant | 执行时间 |

## 安全设计

### 权限控制

- 诊断会话创建：`diagnosis:session:create`（运维人员 + 平台管理员）；
- 命令模板管理：`diagnosis:template:manage`（平台管理员）；
- 高风险命令审批：`diagnosis:command:approve`（平台管理员）；
- 诊断审计查看：`diagnosis:audit:read`（安全审计员 + 平台管理员）。

### 命令安全分级

| 风险等级 | 命令示例 | 控制策略 |
|---|---|---|
| LOW | thread、dashboard、jvm | 直接执行 |
| MEDIUM | trace、watch、monitor | 记录审计，限制执行频率 |
| HIGH | jad（反编译）、sc/sm | 需要额外审批 |
| CRITICAL | retransform、redefine、stop | 需要多人审批，默认拒绝 |

### 输出脱敏

诊断输出在存储和展示前必须经过 `OutputMaskingService` 处理：

- 密码、密钥、token 替换为 `***`；
- 数据库连接串隐藏密码部分；
- 手机号、身份证号脱敏；
- 堆栈中的敏感类名/包名可配置遮蔽。

## 通信协议

### Console ↔ Diagnosis Module

- REST API：会话 CRUD、命令模板管理、审计查询；
- WebSocket：实时命令输出流。

### Diagnosis Module ↔ Tunnel Server

- Arthas Tunnel Protocol（基于 WebSocket）；
- 认证：Tunnel Server 密码 + API Key；
- 加密：内网部署可使用 TLS。

### Tunnel Server ↔ Target JVM

- Arthas Agent Attach API；
- 本地进程通信（Unix Domain Socket 或 TCP）。

## 部署拓扑

```text
管理网                         生产网
┌──────────┐                  ┌──────────────────┐
│ Console  │──── 内网 ────────│ Tunnel Server    │
│ :8080    │                  │ :7777            │
└──────────┘                  └────────┬─────────┘
                                       │
                              ┌────────┴─────────┐
                              │ 目标 JVM + Agent  │
                              │ App1  App2  App3  │
                              └──────────────────┘
```

Tunnel Server 部署在生产网，只接受管理网的连接。不得暴露到公网或互联网。
