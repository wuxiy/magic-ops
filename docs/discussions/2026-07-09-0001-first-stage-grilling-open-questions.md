# 第一阶段脚手架与受控闭环拷问记录

## 背景

用户希望完成项目脚手架和第一阶段受控闭环：动态查询、数据修复、接口适配。

本记录保存 `/grilling` 会话中已经确认的决策和仍阻塞实施的问题。

## 已确认决策

- Maven `groupId` 使用 `top.cywu`。
- 第一版采用多应用形态，至少拆分 Console 与 Runtime。
- 最低受控闭环接受 API/测试驱动，不要求第一版 Console 界面漂亮。
- 第一版 UI 复用 `magic-editor`，Console 可先做最小页面/API。
- 数据修复第一版不允许自由 UPDATE/DELETE。
- 数据修复第一版只支持受限 SQL 模板，或单表带主键/WHERE 的写操作。
- 数据修复必须强制 dry-run。
- SQL Guard 和 dry-run 第一版只支持达梦数据库业务数据源。
- Runtime 在线推送使用 REST 协议。
- Console 和 Runtime 第一版理论上部署在同一网段，Console 可直接推送 Runtime。
- 第一版平台签名私钥从项目环境变量读取。
- 第一版认证使用 Spring Security Basic。

## 已闭合的问题

### 1. magic-api fork 方式

确认选择：`git subtree`。

理由：

- 比直接复制源码更容易保留上游来源；
- 比独立上游 fork 更容易在单仓库早期推进；
- 符合第一阶段单仓库方向；
- 后续仍可拆出独立 fork。

### 2. 发布包规范化规则

确认采用：

- 签名输入不直接使用 zip 二进制；
- 使用 canonical manifest + canonical metadata + normalized scripts bytes；
- JSON 使用 UTF-8、字段按字典序、无无意义空白；
- 文件路径按字典序排序；
- 脚本内容统一 LF 换行；
- hash 使用 SHA-256；
- 签名算法第一版使用 SHA256withRSA。

### 3. 审计失败分级

确认采用：

- 数据修复、发布、审批、签名、Runtime 加载：关键审计失败即阻断；
- 动态查询：关键执行审计失败默认阻断，除非后续引入本地可靠队列；
- HTTP 接口适配：涉及外部写/推送类调用时阻断；纯查询类可按风险等级处理。

### 4. 平台资源库是否继续 PostgreSQL

确认：平台元数据库继续使用 PostgreSQL。达梦数据库只作为第一版业务数据源/SQL Guard/dry-run 支持范围。

### 5. 达梦 dry-run 语义

确认：第一版 dry-run 只做解析、权限校验、危险语句拦截和影响范围估算，不承诺事务内执行后 rollback。事务内 rollback 等达梦行为被测试覆盖后再升级。
