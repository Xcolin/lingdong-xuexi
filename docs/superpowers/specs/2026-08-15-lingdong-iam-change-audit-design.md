# 灵动学习身份权限变更审计设计

## 1. 目标与范围

本专项落实 `BRD-3.1-12`，为当前已经存在的用户、角色、权限、数据范围和组织管理员写操作建立统一、不可变、可查询的审计事实。审计用于回答“谁在何时对哪个对象做了什么变更”，不替代业务表，也不记录姓名、手机号、密码、令牌等敏感明文。

纳入范围：

- 用户创建、用户状态变更、用户关联组织、用户授予角色；
- 自定义角色创建、权限创建；
- 角色权限新增或效果变更、角色权限撤销；
- 用户显式权限新增或效果变更、用户显式权限撤销；
- 角色自定义组织范围新增、组织管理员配置；
- 系统管理员在 Web 端按事件、对象、操作者、目标标识和时间范围分页查询。

当前没有业务入口的角色状态变更、权限状态变更、解除组织管理员等操作不虚构接口，待对应业务能力实现时接入同一记录器。

## 2. 核心规则

1. 审计记录与被审计业务操作处于同一数据库事务。业务操作失败或事务回滚时，不得留下成功审计。
2. `sys_iam_change_audit` 只允许应用层插入和查询；Mapper 不提供更新、删除方法。
3. 审计主键由应用层雪花算法生成，为 19 位 `BIGINT`，禁止自增。
4. HTTP 管理入口必须将当前认证用户标识传到应用层。兼容既有内部调用的命令可保留无操作者构造器，此时审计操作者为空，表示系统内部或历史测试调用。
5. `before_value`、`after_value` 只保存枚举状态或权限效果等非敏感短值；关联对象使用独立标识列表达。
6. 查询权限使用新增 `IAM_AUDIT_READ`，默认只授予内置系统管理员角色；后端仍以权限拦截为最终边界。
7. Web 负责复杂筛选和分页；小程序不新增身份权限审计页面。

## 3. 数据模型

表 `sys_iam_change_audit`：

| 字段 | 含义 |
|---|---|
| `id` | 19 位雪花主键 |
| `event_type` | 变更事件类型 |
| `operator_id` | 操作者用户标识，可空 |
| `target_type` | 被变更对象类型 |
| `target_id` | 主要对象标识 |
| `related_id` | 关联对象标识，可空 |
| `organization_id` | 组织边界标识，可空 |
| `before_value` | 变更前非敏感短值，可空 |
| `after_value` | 变更后非敏感短值，可空 |
| `occurred_at` | 事件发生时间 |

事件类型固定为：`USER_CREATE`、`USER_STATUS_CHANGE`、`USER_ORGANIZATION_ASSOCIATE`、`USER_ROLE_ASSIGN`、`ROLE_CREATE`、`PERMISSION_CREATE`、`ROLE_PERMISSION_CONFIGURE`、`ROLE_PERMISSION_REMOVE`、`USER_PERMISSION_CONFIGURE`、`USER_PERMISSION_REMOVE`、`ROLE_DATA_SCOPE_ADD`、`ORGANIZATION_ADMIN_ASSIGN`。

## 4. 查询契约

`GET /api/v1/iam/audits` 支持 `eventType`、`targetType`、`operatorId`、`targetId`、`startedAt`、`endedAt`、`page`、`pageSize`。时间范围两端均包含，开始时间不得晚于结束时间；页码为 1 至 1000000，每页为 1 至 100。

响应包含 `items`、`page`、`pageSize`、`total`。所有 `BIGINT` 标识序列化为字符串，避免 JavaScript 精度丢失。

## 5. 异常与安全

- 非系统管理员或没有 `IAM_AUDIT_READ` 的用户由统一权限拦截拒绝；
- 查询条件非法返回统一参数错误；
- 审计查询不联表返回用户姓名、手机号等个人信息；
- 审计数据按发生时间和主键倒序，保证同一时间戳下顺序稳定；
- 本专项只在本地 H2 MySQL 兼容模式验证，不连接远程 MySQL、Redis 或任何共享环境。

## 6. 验收

- V1-V50 可从空库连续迁移；新表和权限种子满足约束及 19 位主键规则；
- 每类已存在写操作均产生一条对应审计，事务失败不产生记录；
- API 筛选、分页、越权和长整型字符串响应通过测试；
- Web 查询、筛选、分页、空态和异常反馈通过测试与生产构建；
- 后端、Web、小程序全量本地回归通过，中文文档同步。
