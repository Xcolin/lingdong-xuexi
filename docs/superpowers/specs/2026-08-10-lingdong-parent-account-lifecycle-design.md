# 灵动伴随 V42 家长手机号换绑与账号注销前置设计

## 1. 目标与需求依据

V42 落地家长自助更换手机号，以及家长账号注销的安全前置状态机。依据原始素材：常规换号必须验证原手机号和新手机号；换号后微信、亲子关系、副家长关系及历史数据保持不变；家长注销前必须解除全部学生绑定，申请后进入 7 天冷静期且可撤销。

本工作包不实现原号码失效时的机构人工核验，不执行最终账号禁用、个人数据匿名化或删除。到期申请只进入“待执行注销”状态，后续工作包在数据留存和匿名化清单确认后完成不可逆处理。

## 2. 业务边界

### 2.1 手机号换绑

1. 仅已登录、启用且具有 `PARENT` 角色的家长可操作，Web 和小程序均支持。
2. 第一步向当前账号手机号发送 `CHANGE_MOBILE_CURRENT` 验证码；校验成功后签发 5 分钟一次性随机票据，服务端只保存摘要。
3. 第二步携带未消费票据和新手机号申请 `CHANGE_MOBILE_NEW` 验证码；新手机号必须格式正确且未被其他账号占用。
4. 最终提交同时校验并消费换号票据与新手机号验证码，锁定当前用户，再次检查当前号码和新号码唯一性。
5. `sys_user.mobile` 更新为新号码；若 `username` 等于旧手机号则同步更新为新手机号，其他自定义用户名不改。
6. 微信绑定、家长档案、协议、亲子关系、任务、积分、奖励、复盘和安全历史均继续按 `user_id` 关联，不迁移、不复制、不删除。
7. 成功后撤销该用户全部活动设备会话，客户端清除本地会话并回到家长登录页。
8. 审计只保存新旧手机号 HMAC 摘要，不保存完整手机号、验证码或随机票据。

### 2.2 注销前置

1. 家长必须不存在任何活动主家长或副家长关系，才能申请注销。
2. 申请前向当前手机号发送 `ACCOUNT_CANCELLATION` 验证码；提交时还必须输入固定确认文本“确认注销”。
3. 成功申请后创建一条 `COOLING_OFF` 记录，冷静期固定 7 天；重复申请返回当前记录，不创建重复活动申请。
4. 冷静期内允许本人撤销，状态变为 `REVOKED`，历史记录保留。
5. 当前时间达到 `cooling_ends_at` 后，对外状态计算为 `READY_FOR_FINALIZATION`；V42 不停用用户、不撤销会话、不匿名化数据。
6. 若冷静期内重新建立任何活动亲子关系，后续最终执行必须重新检查并拒绝；V42 状态接口明确返回当前活动学生关系数量。

## 3. 数据模型与 Flyway

新增 `V42__add_parent_account_lifecycle.sql`，不修改 V1-V41。

### 3.1 `auth_parent_mobile_change`

- `id BIGINT`：19 位雪花主键，非自增。
- `user_id BIGINT`、`old_mobile_digest CHAR(64)`、`new_mobile_digest CHAR(64)`。
- `client_type WEB|MINIAPP`、`changed_at`、`created_at`。
- 仅提供插入和本人倒序查询，不提供更新或删除。

### 3.2 `auth_parent_account_cancellation`

- `id BIGINT`：19 位雪花主键，非自增。
- `user_id BIGINT`、`status COOLING_OFF|REVOKED|FINALIZED`。
- `active_scope_key`：活动记录固定 `ACTIVE`，撤销后改为唯一关闭值，用唯一约束保证每个用户最多一条活动申请。
- `requested_at`、`cooling_ends_at`、`revoked_at`、`finalized_at`、审计时间。
- V42 不写入 `FINALIZED`，该枚举为后续前向实现预留的受约束状态，不预建空迁移。

新增默认启用功能开关 `PARENT_ACCOUNT_LIFECYCLE` 和 `BOTH` 权限 `PARENT_ACCOUNT_LIFECYCLE_MANAGE`，最小授权给内置家长角色。

## 4. 服务与安全

1. `ParentAccountLifecycleService` 是换号和注销前置事务边界，先校验功能开关和家长身份，再锁定用户。
2. 换号票据使用 Redis 保存摘要、用户标识、客户端、当前手机号摘要和 5 分钟有效期；测试 profile 使用内存实现。
3. 票据只能由签发用户、签发客户端和未变化的当前手机号消费，重放、过期和跨端使用统一失败。
4. 公共验证码端点不得接受 V42 三种用途；V42 使用认证端点发送，防止匿名请求骚扰任意手机号。
5. 数据库唯一冲突映射为业务冲突，不返回“手机号属于哪个账号”等枚举信息。
6. 所有响应中的雪花标识使用 JSON 字符串；手机号只返回掩码。

## 5. API

- `GET /api/v1/auth/parent-account-lifecycle`：当前脱敏手机号、活动学生关系数、注销申请状态和冷静期时间。
- `POST /api/v1/auth/parent-mobile-change/current-codes`：向当前手机号发送验证码。
- `POST /api/v1/auth/parent-mobile-change-tickets`：验证当前号码并返回一次性票据。
- `POST /api/v1/auth/parent-mobile-change/new-codes`：校验票据后向新手机号发送验证码。
- `POST /api/v1/auth/parent-mobile-changes`：完成换号并撤销全部会话，返回 204。
- `POST /api/v1/auth/parent-account-cancellation-codes`：向当前手机号发送注销验证码。
- `POST /api/v1/auth/parent-account-cancellations`：校验无活动学生关系、验证码和确认文本后创建冷静期申请。
- `DELETE /api/v1/auth/parent-account-cancellations/current`：冷静期内撤销当前申请，返回 204。

全部接口需要家长会话、`PARENT_ACCOUNT_LIFECYCLE_MANAGE`、匹配客户端和功能开关。功能停用后 Web/小程序入口隐藏，后端直达统一拒绝。

## 6. 前端

Web 在账号安全工作台为家长显示“更换手机号”和“账号注销”操作；uni-app 在家长首页进入独立 `parent-account-lifecycle` 页面。两端分别实现页面代码，共享后端契约但不共享会话存储。换号成功后立即清除本地会话；注销申请成功后账号在冷静期内仍可登录和撤销。

## 7. 验收

1. V1-V42 空库迁移通过，新增两张表均为显式非自增 `BIGINT` 主键。
2. 覆盖当前号码错误、新号码占用、票据过期/重放/跨用户/跨客户端、并发换号仅一次成功。
3. 覆盖换号后微信绑定、主副家长关系和历史数据行不变，旧手机号不能登录，全部旧会话失效。
4. 覆盖存在任一活动学生关系时禁止申请注销；零关系时创建 7 天冷静期、重复申请幂等、冷静期撤销成功、到期状态为待执行。
5. 覆盖功能停用的入口隐藏和服务端拒绝、完整手机号与验证码不进入审计、日志或前端持久化。
6. 后端全量测试、Web 全量测试与构建、uni-app 类型检查及 H5/微信小程序构建全部通过。
