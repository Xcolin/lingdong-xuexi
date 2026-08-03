# 灵动学习后台管理权限与 IAM 接口设计

## 1. 目标与范围

本次以已完成的平台账号 Web 认证为前提，建立首批后台管理权限目录和通用权限拦截器，并把已存在的用户、角色、权限、用户数据权限和组织管理员应用服务以真实 REST API 提供给 Web 管理端。

本次只覆盖已有应用服务能够保证业务规则的动作：查询用户/角色/权限目录，创建用户、创建自定义角色、关联用户与组织、授予用户角色、设置平台账号密码、创建权限、授予角色权限、配置用户明确允许/禁止权限、配置角色自定义组织范围，以及配置组织管理员。

本次不实现用户或角色编辑/停用/删除、撤销组织关联或角色授予、批量导入、菜单树、前端工程、OpenAPI 文件生成、分页查询、功能开关与对象数据范围的业务接口拦截。这些能力没有对应的完整应用服务或业务状态规则，必须在后续独立设计后实现。

## 2. 授权方案

Flyway `V16` 建立首批 Web 后台管理操作权限目录，并将这些权限授予内置 `SYS_ADMIN` 角色。权限均为 `OPERATION` 资源类型和 `WEB` 客户端类型，使用固定的 19 位雪花 `BIGINT id`。

| 权限编码 | 受控动作 |
|---|---|
| `IAM_USER_READ` | 查询指定用户。 |
| `IAM_USER_CREATE` | 创建用户。 |
| `IAM_USER_ORGANIZATION_ASSIGN` | 建立用户与组织关联。 |
| `IAM_USER_ROLE_ASSIGN` | 向用户授予角色。 |
| `IAM_USER_PASSWORD_SET` | 为平台账号设置或重置密码。 |
| `IAM_ROLE_READ` | 查询角色目录。 |
| `IAM_ROLE_CREATE` | 创建自定义角色。 |
| `IAM_PERMISSION_READ` | 查询权限目录。 |
| `IAM_PERMISSION_CREATE` | 创建权限目录项。 |
| `IAM_ROLE_PERMISSION_GRANT` | 向角色授予权限。 |
| `IAM_USER_PERMISSION_CONFIGURE` | 配置用户明确允许或禁止权限。 |
| `IAM_DATA_SCOPE_CONFIGURE` | 配置角色自定义组织范围和组织管理员。 |

新增 `@RequirePermission` 方法注解与 Spring MVC 拦截器。请求已通过 Bearer 会话认证后，拦截器从当前身份取得用户标识，调用既有 `PermissionDecisionService`，按“用户明确禁止优先 -> 角色允许 -> 用户补充允许”的已有规则做判断。无对应权限或存在明确禁止时，拦截器返回 HTTP 403、`ACCESS_DENIED` 与请求标识，不执行业务控制器。

该通用权限判断不能绕过已有应用服务中的额外业务约束：密码设置、权限目录变更和数据范围配置仍只允许 `SYS_ADMIN`，因为这些动作属于系统级安全控制。系统管理员可将用户、角色等较低风险管理权限授予自定义运维角色；但该角色不能凭借路由权限突破上述系统管理员限制。

## 3. API 与流程

所有接口位于 `/api/v1`，必须携带有效 Bearer 访问凭证。响应中的业务 ID 使用字符串形式的 19 位雪花标识；请求中的 ID 也以字符串传递。成功响应沿用当前认证接口的直接 DTO 形式，错误响应沿用 `code`、`message`、`traceId` 结构。

| 接口 | 所需权限 | 处理流程 |
|---|---|---|
| `GET /users/{id}` | `IAM_USER_READ` | 查询用户基础信息，不返回密码散列、会话或令牌。 |
| `POST /users` | `IAM_USER_CREATE` | 校验账号、名称、手机号和用户类型，创建用户；不会自动创建密码或授予角色。 |
| `POST /users/{id}/organizations` | `IAM_USER_ORGANIZATION_ASSIGN` | 校验用户与启用组织存在且未重复关联，保存人员组织关系。 |
| `POST /users/{id}/roles` | `IAM_USER_ROLE_ASSIGN` | 校验用户、角色和可选组织范围；组织范围角色必须先关联同一组织。 |
| `POST /users/{id}/password` | `IAM_USER_PASSWORD_SET` | 将当前身份作为操作者，复用认证服务校验系统管理员、平台账号和密码复杂度后保存 BCrypt 散列。 |
| `GET /roles` | `IAM_ROLE_READ` | 查询角色目录与数据范围，不返回角色权限明细。 |
| `POST /roles` | `IAM_ROLE_CREATE` | 创建符合编码规则的自定义角色，不自动授予权限或组织范围。 |
| `GET /permissions` | `IAM_PERMISSION_READ` | 查询已启用和停用的权限目录基础信息。 |
| `POST /permissions` | `IAM_PERMISSION_CREATE` | 将当前身份作为操作者，复用系统管理员约束创建权限目录项。 |
| `POST /roles/{roleId}/permissions` | `IAM_ROLE_PERMISSION_GRANT` | 将当前身份作为操作者，校验角色和权限存在且未重复后建立授权关系。 |
| `PUT /users/{userId}/permissions/{permissionId}` | `IAM_USER_PERMISSION_CONFIGURE` | 将当前身份作为操作者，写入或更新用户级 `ALLOW`/`DENY` 权限效果。 |
| `POST /roles/{roleId}/data-scopes` | `IAM_DATA_SCOPE_CONFIGURE` | 将当前身份作为操作者；仅自定义范围角色可追加启用组织根节点。 |
| `POST /organization-admins` | `IAM_DATA_SCOPE_CONFIGURE` | 将当前身份作为操作者；用户必须先关联目标组织，才可成为该组织管理员。 |

参数格式、字段长度和枚举由请求 DTO 的 Bean Validation 与既有应用服务共同校验。新增统一 API 异常处理：请求未通过字段校验时返回 HTTP 400、`VALIDATION_ERROR`；明确查询不到的用户、角色、权限或组织返回 HTTP 404、`RESOURCE_NOT_FOUND`；重复关联、重复授权或状态不满足时返回 HTTP 409、`STATE_CONFLICT`；业务服务判定为仅系统管理员可操作时返回 HTTP 403、`ACCESS_DENIED`。为保持既有应用服务测试语义，资源不存在异常继承 `IllegalArgumentException`，但由异常处理器优先映射为 404；系统管理员限制异常继承 `IllegalStateException`，但优先映射为 403。

## 4. 持久化与分层

V16 只新增基础权限数据和内置 `SYS_ADMIN` 的角色权限关联，不新增业务表。所有种子 ID 使用预生成的 19 位雪花常量。角色、权限、用户与关联数据继续复用既有 MyBatis XML 映射；为查询接口补充 `findAll` 查询，不在 Controller 内编写 SQL。

控制器只负责 HTTP 参数、当前身份和响应 DTO。新增 IAM 查询应用服务负责从 Mapper 读取用户、角色和权限目录；既有写应用服务继续处理创建、关联、角色授予、权限配置、数据范围和密码规则。通用权限拦截器只做路由权限判定，不承担组织数据范围或具体业务对象授权。

## 5. 测试与验收

自动化测试至少覆盖：

1. Flyway 从空库执行到 V16 后，12 个管理权限、内置系统管理员授权以及所有雪花主键约束存在。
2. 具有内置系统管理员角色的登录用户可访问受保护的用户、角色和权限接口。
3. 只有角色代码但没有相应权限的用户被拦截为 403；用户级 `DENY` 覆盖角色允许。
4. 自定义运维角色被授予 `IAM_USER_CREATE` 后可以创建用户，但没有 `IAM_USER_ROLE_ASSIGN` 时不能授予角色。
5. 用户组织关联、组织范围角色授予、平台密码设置、角色权限授予、用户允许/禁止权限、自定义范围和组织管理员配置均从当前认证身份取得操作者，拒绝客户端伪造操作者标识。
6. 响应不泄露 `password_hash`、原始令牌或令牌摘要，所有输出 ID 为 JSON 字符串。
7. 本次仅在本地 H2/Flyway 测试环境执行，不代表共享测试、预生产或生产 MySQL 8 环境已验证。
