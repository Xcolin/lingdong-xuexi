# 灵动学习接口服务管理设计

## 1. 目标与边界

接口服务管理用于登记第三方接入和对外服务的受控元数据：服务名称、方向、用途、调用方、授权范围、责任人和启停状态。它不保存访问令牌、签名密钥、供应商地址、原始请求或原始响应，也不实现未指定供应商的真实调用协议。

当前实现只提供受控登记、系统任务审批、生效执行校验和调用结果台账。后续微信、地图、短信、对象存储或开放接口适配器必须在调用前校验服务已登记且为启用状态，并通过本模块记录结果。

## 2. 数据模型

Flyway `V12__create_interface_service_tables.sql` 新建三张表，全部使用应用层生成的 19 位雪花 `BIGINT id` 主键：

| 表 | 关键字段 | 约束 |
| --- | --- | --- |
| `sys_interface_service` | service_name、direction、purpose、caller_name、authorization_scope、authorization_scope_value、owner_id、status | 责任人关联系统用户；只允许 `ENABLED`、`DISABLED`。 |
| `sys_interface_service_change` | task_id、service_id、change_type、拟生效的服务字段 | `task_id` 唯一；创建变更没有 `service_id`，停用和授权范围变更必须关联既有服务。 |
| `sys_interface_call_log` | service_id、caller_name、result、error_summary、trace_id、occurred_at | 只记录结果与异常摘要，不存密钥、令牌、完整报文或位置数据。 |

服务方向固定为 `INBOUND`、`OUTBOUND`；用途固定为 `WECHAT`、`MAP`、`SMS`、`SCHOOL`、`DATA_SYNC`、`OTHER`；授权范围固定为 `GLOBAL`、`REGION`、`SCHOOL`、`INSTITUTION`、`SPECIFIED_CALLER`。除 `GLOBAL` 外必须给出范围值。范围变更统一按高风险授权变更审核，避免在组织层级和调用方范围尚未有完整外部客户端模型时错误推断“是否扩大”。

## 3. 审批与生效流程

```mermaid
flowchart LR
    A[系统管理员创建变更草稿] --> B[创建 INTERFACE_SERVICE_CHANGE 系统任务]
    B --> C[提交审核]
    C --> D[系统审核员审批]
    D --> E[执行新增 停用或范围变更]
    E --> F[记录实际生效]
```

1. 新增、停用和授权范围变更均由系统管理员发起，并关联一个 `INTERFACE_SERVICE_CHANGE` 任务。
2. 系统审核员只能审核系统任务，不直接编辑接口服务记录；不得审核本人提交的任务，沿用既有系统任务规则。
3. 审批通过后执行具体变更。只有插入、停用或授权范围更新成功，任务才标记为 `EFFECTIVE`；执行异常时任务保留 `APPROVED`，不伪造生效结果。
4. 新建服务在实际生效前不存在可调用的服务记录；已停用或未登记的服务不允许记录为可执行调用。

## 4. 调用台账规则

`recordCall` 是给未来内部适配器使用的应用服务入口。它先读取服务：不存在时拒绝，已停用时拒绝；启用服务可写入 `SUCCEEDED` 或 `FAILED` 结果。调用方最长 100 字符，异常摘要最长 1000 字符，追踪标识最长 64 字符。所有字段仅保存受控摘要。

## 5. 权限、API 与测试边界

当前没有认证上下文和业务 REST Controller，因此只实现后端应用服务与持久化，不创建绕过鉴权的临时 HTTP 接口。应用服务内所有登记和审批发起操作均校验 `SYS_ADMIN`；系统审核员权限由 `SystemTaskApplicationService` 统一校验。

测试覆盖：V12 建表及雪花主键、未登记或停用服务拒绝调用、新增服务必须经审批后才能出现、停用和授权范围变更必须经审批后才生效、调用成功/失败只记录摘要字段。全量回归仍使用 H2，正式 MySQL 8 发布前必须再次执行空库和升级库验证。
