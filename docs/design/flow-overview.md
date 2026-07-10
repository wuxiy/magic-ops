# 流程概览

## 脚本生命周期

动态查询接口、受控数据修复和 HTTP 接口适配共用一套脚本生命周期：

```text
DRAFT
  -> DEBUGGED
  -> SUBMITTED
  -> REVIEWING
  -> APPROVED
  -> SIGNED
  -> PUSHING
  -> PUBLISHED
  -> DISABLED / ROLLED_BACK
```

失败或分支状态：

```text
REVIEWING -> REJECTED
PUSHING -> PUBLISH_FAILED
```

## 动态查询 API 流程

```text
create draft
  -> write query script
  -> debug in controlled environment
  -> submit
  -> approve
  -> sign
  -> push to Runtime
  -> Runtime verifies package
  -> production query execution
  -> execution audit
```

规则：

- SQL 默认只读；
- 查询结果大小必须受限；
- 请求和响应审计必须按策略脱敏。

## 受控数据修复流程

```text
create repair draft
  -> write repair script
  -> dry-run
  -> generate impact report
  -> submit
  -> approve high-risk operation
  -> sign
  -> push to Runtime
  -> Runtime verifies package
  -> execute repair
  -> record affected rows
  -> generate execution report
  -> retain rollback record
```

规则：

- 数据修复共用脚本生命周期，但不能按普通查询执行处理；
- 写操作必须有风险等级和审批策略；
- dry-run 是强制步骤；
- 必须记录回滚意图；
- 高风险审计写入失败时阻断执行。

## HTTP 接口适配流程

```text
register HTTP target
  -> configure path allowlist and crypto/signing policy
  -> write adapter script
  -> debug
  -> approve if target or data risk requires it
  -> sign
  -> push to Runtime
  -> Runtime executes adapter
  -> external call audit
```

规则：

- 脚本不能调用任意 URL；
- 目标系统必须注册，并被纳入已签名元数据；
- trace ID 必须透传；
- 请求和响应日志必须脱敏。

## 第二阶段诊断流程

Arthas 诊断属于第二阶段：

```text
select app instance
  -> create diagnosis session
  -> choose diagnosis template
  -> permission check / approval
  -> execute command through diagnosis center
  -> capture output
  -> generate diagnosis report
  -> audit archive
```

该流程有意从第一阶段实现中延后。
