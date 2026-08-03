# 灵动学习 API 接口设计

**版本**：V1.0（设计基线草案）  
**状态**：待评审  
**接口风格**：HTTPS + REST + JSON；OpenAPI 3 作为机器可读契约  
**关联设计**：[FSD](01-功能详细设计-FSD-V1.0.md)、[数据库设计](04-数据库设计-V1.0.md)、[安全设计](07-权限与安全设计-V1.0.md)

## 1. 契约规则

### 1.1 版本与路径

1. 内部 Web 和小程序统一使用 `/api/v1` 前缀，例如 `/api/v1/organizations`。
2. 对外提供的受控接口使用独立 `/open-api/v1` 前缀，与内部登录会话、菜单权限和管理接口隔离。
3. 非兼容变更必须新建 API 主版本；字段可选扩展不改变已存在字段语义。
4. OpenAPI 文档、后端 Controller、前端类型定义和集成测试必须由同一版本契约驱动，不能只维护手写接口清单。

### 1.2 统一响应与错误

```json
{
  "code": "0",
  "message": "OK",
  "data": {},
  "traceId": "request-trace-id"
}
```

| 场景 | 建议错误码 | HTTP 状态 | 客户端处理 |
|---|---|---|---|
| 未登录/会话失效 | `AUTH_REQUIRED` | 401 | 清理失效会话并进入登录。 |
| 无功能或操作权限 | `ACCESS_DENIED` | 403 | 隐藏入口；直达请求显示不可操作。 |
| 功能关闭 | `FEATURE_DISABLED` | 409 | 不展示操作页面，禁止继续业务。 |
| 学生认证失败 | `STUDENT_AUTH_FAILED` | 401 | 统一提示账号或登录码错误，不泄露账号存在性。 |
| 需要图形验证码 | `CAPTCHA_REQUIRED` | 428 | 展示验证码并重新提交。 |
| 学生账号锁定 | `STUDENT_ACCOUNT_LOCKED` | 423 | 使用 `lockedUntil` 展示剩余锁定时间。 |
| 登录或验证码限流 | `RATE_LIMITED` | 429 | 停止自动重试，稍后再试。 |
| 登录保护存储不可用 | `AUTH_PROTECTION_UNAVAILABLE` | 503 | 失败关闭，不绕过验证码和限流。 |
| 无数据范围 | `DATA_SCOPE_DENIED` | 403 | 不泄露对象是否存在或其详情。 |
| 资源不存在或不可见 | `RESOURCE_NOT_FOUND` | 404 | 显示不可访问/已不存在。 |
| 参数校验失败 | `VALIDATION_ERROR` | 400 | 返回字段级错误，不清空表单。 |
| 状态冲突/重复提交 | `STATE_CONFLICT` | 409 | 刷新对象状态后按当前有效操作处理。 |
| 幂等重复请求 | `IDEMPOTENCY_CONFLICT` | 409 | 返回已存在的处理结果或提示重试状态。 |
| 系统异常 | `INTERNAL_ERROR` | 500 | 返回 `traceId`，不输出堆栈、密钥或个人信息。 |

错误响应中的 `message` 只用于用户可见的中性描述；详细异常、SQL、凭证和第三方原始报文只进入受控日志。

当前已实现的认证成功响应直接返回认证 DTO，认证失败和拒绝访问响应返回 `code`、`message`、`traceId`，尚未接入本节定义的统一成功响应信封。后续新增业务 API 前必须统一契约，并同步调整认证 API，不能让不同模块各自定义响应外层结构。

### 1.3 通用请求规则

| 项目 | 规则 |
|---|---|
| 鉴权 | 内部接口使用认证凭证；后端从凭证得到用户、角色、客户端类型和会话状态，不信任前端传来的用户 ID。 |
| 请求追踪 | 客户端可传 `X-Request-Id`，服务端缺失时生成；响应返回 `traceId`。 |
| 幂等 | 创建任务、打卡、审核、兑换、导出、系统任务提交、绑定邀请等写操作支持 `Idempotency-Key`。 |
| 分页 | 统一使用 `pageNo`、`pageSize`、`sortBy`、`sortOrder`；`pageSize` 上限为 100。 |
| 时间 | 请求与响应使用 ISO 8601；统计口径使用中国标准时间。 |
| 标识 | 长整型业务 ID 在 JSON 中以字符串表达，避免 JavaScript 精度丢失。 |
| 枚举 | 请求提交稳定编码，页面展示名称由字典返回；禁用字典项不能用于新建/编辑。 |
| 写操作 | 先校验功能开关、权限、数据范围和对象状态，再执行事务；成功后写审计/事件记录。 |

## 2. 接口目录

以下为 V1 资源目录和操作名称。平台认证、V21 学生登录、2.2 中明确标注“已实现”的 IAM 接口及 2.5 中明确标注“V22 已实现”的学习任务接口已有 Controller 和自动化测试，但尚未生成 OpenAPI 文件；其他目录仍是设计清单，不表示已有可联调接口。字段定义以 FSD 与数据库设计为基础，OpenAPI 落地时必须补齐字段类型、必填性、示例、错误码和权限码。

### 2.1 认证、账号和设备

| 方法与路径 | 用途 | 权限/前置 | 当前状态 |
|---|---|---|---|
| `POST /api/v1/auth/sessions/password` | 平台账号密码登录 | 公开；仅启用的 `UserType.PLATFORM` 账号，客户端固定为 `WEB`。 | 已实现。 |
| `POST /api/v1/auth/sessions/refresh` | 轮换访问凭证和刷新凭证 | 公开；请求体刷新凭证对应的会话为 `ACTIVE` 且未过期。 | 已实现。 |
| `DELETE /api/v1/auth/sessions/current` | 退出当前会话 | 有效 Bearer 访问凭证。 | 已实现，返回 204。 |
| `GET /api/v1/auth/me` | 当前用户、角色代码和会话摘要 | 有效 Bearer 访问凭证。 | 已实现，不返回菜单或能力摘要。 |
| `GET /api/v1/auth/devices` | 当前用户的活动设备会话 | 有效 Bearer 访问凭证。 | 已实现，不返回令牌或其摘要。 |
| `DELETE /api/v1/auth/devices/{sessionId}` | 下线指定自身设备会话 | 有效 Bearer 访问凭证，且会话归属当前用户。 | 已实现，返回 204。 |
| `POST /api/v1/auth/devices/sign-out-all` | 撤销当前用户所有活动会话 | 有效 Bearer 访问凭证。 | 已实现，返回 204。 |
| `POST /api/v1/auth/sms-codes` | 发送家长验证码 | 频率限制与图形/风控校验。 | 未实现。 |
| `POST /api/v1/auth/sessions/sms` | 验证码登录 | 验证码有效且未超限。 | 未实现。 |
| `POST /api/v1/auth/sessions/wechat` | 微信登录/会话换取 | 小程序微信授权；绑定与回退规则由账号用例执行。 | 未实现。 |
| `GET /api/v1/public/capabilities?client=WEB|MINIAPP` | 客户端启动前读取非敏感能力摘要 | 公开；只接受 `WEB` 或 `MINIAPP`。 | V22 已扩展，返回 `learningTaskManagementEnabled`；`MINIAPP` 另返回有效的 `studentCodeLoginEnabled`。 |
| `POST /api/v1/auth/student-captchas` | 申请学生登录图形验证码 | 公开；`STUDENT_CODE_LOGIN` 启用，账号与设备标识非空。 | V21 已实现，返回挑战标识、Base64 图片和到期时间。 |
| `POST /api/v1/auth/student-sessions/code` | 学生登录码登录 | 公开；功能启用，校验限流、锁定、按需图形验证码与登录码。 | V21 已实现，只创建 `MINIAPP` 会话。 |
| `POST /api/v1/auth/student-sessions/scan` | 学生扫码登录 | 校验二维码有效期与绑定关系。 | 未实现。 |

### 2.2 组织、用户、角色和权限

| 方法与路径 | 所需权限 | 用途与当前状态 |
|---|---|---|
| `POST /api/v1/organization-admins` | `IAM_DATA_SCOPE_CONFIGURE` | 配置组织管理员；已实现。用户必须已关联目标组织，且应用服务仍要求 `SYS_ADMIN`。 |
| `POST /api/v1/users` | `IAM_USER_CREATE` | 创建用户；已实现，不自动设置密码、关联组织或授予角色。 |
| `GET /api/v1/users` | `IAM_USER_LIST` | 按关键字、用户类型、账号状态分页查询用户目录；已实现。当前仅向内置 `SYS_ADMIN` 种子授权，未接入组织数据范围 SQL。 |
| `GET /api/v1/users/{id}` | `IAM_USER_READ` | 查询单个用户；已实现，不返回密码散列、会话或令牌。 |
| `PATCH /api/v1/users/{id}/status` | `IAM_USER_STATUS_CHANGE` | 变更为 `ENABLED`、`DISABLED` 或 `LOCKED`；已实现。停用或锁定会撤销该用户全部活动设备会话。 |
| `POST /api/v1/users/{id}/organizations` | `IAM_USER_ORGANIZATION_ASSIGN` | 增量关联一个组织；已实现。 |
| `POST /api/v1/users/{id}/roles` | `IAM_USER_ROLE_ASSIGN` | 增量授予一个角色及可选组织范围；已实现。 |
| `POST /api/v1/users/{id}/password` | `IAM_USER_PASSWORD_SET` | 设置平台用户密码；已实现，应用服务仍要求 `SYS_ADMIN`。 |
| `PUT /api/v1/users/{userId}/permissions/{permissionId}` | `IAM_USER_PERMISSION_CONFIGURE` | 写入或更新用户级 `ALLOW`/`DENY`；已实现，应用服务仍要求 `SYS_ADMIN`。 |
| `GET /api/v1/roles` | `IAM_ROLE_READ` | 查询角色目录；已实现，不包含角色权限明细。 |
| `POST /api/v1/roles` | `IAM_ROLE_CREATE` | 创建自定义角色；已实现，不自动授予权限或组织范围。 |
| `POST /api/v1/roles/{roleId}/permissions` | `IAM_ROLE_PERMISSION_GRANT` | 向角色增加一个权限；已实现，应用服务仍要求 `SYS_ADMIN`。 |
| `POST /api/v1/roles/{roleId}/data-scopes` | `IAM_DATA_SCOPE_CONFIGURE` | 为 `CUSTOM` 范围角色增加一个组织根节点；已实现，应用服务仍要求 `SYS_ADMIN`。 |
| `GET /api/v1/permissions` | `IAM_PERMISSION_READ` | 查询权限目录；已实现。 |
| `POST /api/v1/permissions` | `IAM_PERMISSION_CREATE` | 创建权限目录项；已实现，应用服务仍要求 `SYS_ADMIN`。 |
| `GET /api/v1/organization-types` | `ORG_TYPE_READ` | 查询组织类型目录；已实现。全量目录当前仍限 `SYS_ADMIN`。 |
| `POST /api/v1/organization-types` | `ORG_TYPE_CREATE` | 创建自定义组织类型；已实现，当前仍限 `SYS_ADMIN`。 |
| `GET /api/v1/organizations` | `ORG_NODE_READ` | 查询嵌套组织树；已实现，当前仍限 `SYS_ADMIN`，未接入组织数据范围 SQL 过滤。 |
| `POST /api/v1/organizations` | `ORG_NODE_CREATE` | 创建区域、学校等组织节点；已实现，当前仍限 `SYS_ADMIN`。 |
| `PATCH /api/v1/organization-types/{id}`、`GET/PATCH /api/v1/organizations/{id}`、组织停用任务等其余组织管理接口 | 待定义 | 仍是设计清单，尚未实现。 |

所有已实现 IAM 与组织管理接口均要求 Bearer 会话，方法级 `@RequirePermission` 由服务端动态读取角色权限和用户级 `ALLOW`/`DENY` 决策；用户明确禁止优先。请求及响应中的 19 位业务 ID 均以 JSON 字符串表达。当前 IAM 接口已提供用户目录分页和账号状态变更，但不提供用户资料编辑、删除、撤销组织/角色关联、批量导入、菜单树、对象数据范围拦截和功能开关拦截。用户目录尚未接入组织数据范围 SQL，不得向机构管理员、教师、家长或学生授予全局目录读取权限；组织树接口同样不向非系统管理员提供全量查询，直到组织数据范围 SQL 条件落地。

#### 2.2.1 已实现用户目录契约

`GET /api/v1/users` 的可选参数为 `keyword`、`type`、`status`、`page`、`pageSize`。`keyword` 去除首尾空格后最长 64 个字符；`page` 从 1 开始，`pageSize` 取值范围为 1 至 100。结果固定按 `created_at DESC, id DESC` 排序，响应结构为 `items`、`page`、`pageSize`、`total`。每个用户项仅返回 `id`、`username`、`displayName`、脱敏 `mobile`、`type`、`status`、`createdAt`、`updatedAt`；不返回密码散列、会话、访问令牌或刷新令牌。手机号正常长度仅保留前三位、后四位，中间以 `****` 替代；长度不足 7 位时只返回 `****`。

`PATCH /api/v1/users/{id}/status` 的请求体为 `{"status":"ENABLED|DISABLED|LOCKED"}`。目标用户不存在时返回 `404 RESOURCE_NOT_FOUND`；状态值为空或非法时返回 `400 VALIDATION_ERROR`。重复提交目标状态返回当前用户数据而不报错。目标状态为 `DISABLED` 或 `LOCKED` 时，在同一事务内撤销该用户全部 `ACTIVE` 设备会话，已签发访问令牌后续访问受保护接口返回 `401 AUTH_REQUIRED`；重新启用账号不恢复旧会话，用户必须重新登录。

### 2.3 系统任务、配置与功能开关

| 方法与路径 | 用途 |
|---|---|
| `GET/POST /api/v1/system-tasks` | 查询任务/创建草稿。 |
| `GET/PATCH /api/v1/system-tasks/{id}` | 查看或编辑本人草稿。 |
| `POST /api/v1/system-tasks/{id}/submit` | 系统管理员提交任务。 |
| `POST /api/v1/system-tasks/{id}/approve` | 系统审核员审批通过。 |
| `POST /api/v1/system-tasks/{id}/reject` | 系统审核员驳回，审批意见必填。 |
| `GET /api/v1/feature-toggles` | 查询生效开关及范围。 |
| `POST /api/v1/feature-toggle-changes` | 创建全局开关调整任务。 |
| `GET/POST /api/v1/dictionaries/types` | 查询/维护字典类型。 |
| `GET/POST /api/v1/dictionaries/types/{typeCode}/items` | 查询/维护字典项。 |
| `GET/POST /api/v1/attachment-rules` | 查询/维护附件规则。 |
| `GET/POST /api/v1/import-export-templates` | 查询/维护模板。 |
| `GET/POST /api/v1/interface-services` | 查询/登记接口服务。 |
| `POST /api/v1/cache-operations` | 发起缓存刷新/清除，必要时创建系统任务。 |

系统审核接口只暴露系统任务资源；任务、积分、兑换和考勤的审核端点不在该目录下，也不得复用系统审核员角色。

### 2.4 学生、亲子关系和机构关系

| 方法与路径 | 所需权限/前置 | 用途与当前状态 |
|---|---|---|
| `POST /api/v1/students` | `STUDENT_CREATE`；家长不带组织标识，或机构管理员指定其直接管理的启用组织。 | V21 已扩展：同事务创建学生账号、角色和登录凭证，初始登录码只在创建响应出现一次。 |
| `GET /api/v1/students` | `STUDENT_READ`；按当前角色关系过滤。 | 已实现，系统管理员读取全量；家长、机构管理员读取各自直接关系范围。 |
| `GET /api/v1/students/{id}` | `STUDENT_READ`；对象位于当前读取范围。 | 已实现；跨范围与不存在统一返回 `404 RESOURCE_NOT_FOUND`。 |
| `PATCH /api/v1/students/{id}` | 待定义 | 学生基础资料修改尚未实现。 |
| `POST /api/v1/students/{id}/credentials/initialize` | `STUDENT_CREDENTIAL_INITIALIZE`；活动主家长或学生当前组织的直接机构管理员。 | V21 已实现；只用于未关联学生用户的历史学生。 |
| `POST /api/v1/students/{id}/login-code-resets` | `STUDENT_LOGIN_CODE_RESET`；活动主家长或学生当前组织的直接机构管理员。 | V21 已实现；覆盖凭证摘要并撤销该学生全部活动会话。 |
| `PUT /api/v1/students/{studentId}/class` | `STUDENT_CLASS_ASSIGN`；机构管理员组织数据范围。 | V22 已实现；原子停用旧活动班级并配置唯一当前班级。 |
| `PUT /api/v1/teachers/{teacherUserId}/classes/{classId}` | `TEACHER_CLASS_ASSIGN`；机构管理员组织数据范围。 | V22 已实现；新增或重新启用教师班级关系。 |
| `DELETE /api/v1/teachers/{teacherUserId}/classes/{classId}` | `TEACHER_CLASS_ASSIGN`；机构管理员组织数据范围。 | V22 已实现；将关系置为失效，不物理删除。 |
| `GET /api/v1/teachers/{teacherUserId}/classes` | `TEACHER_CLASS_ASSIGN`；机构管理员可查范围或教师本人。 | V22 已实现；只返回活动关系。 |
| `POST /api/v1/students/{id}/parent-invitations` | 已实现；机构管理员为其直接管理组织的学生发起家长绑定邀请。 |
| `POST /api/v1/parent-invitations/{id}/accept` | 已实现；既有家长账号确认邀请。 |
| `POST /api/v1/parent-invitations/{id}/reject` | 已实现；既有家长账号拒绝邀请。 |
| `POST /api/v1/students/{id}/parent-relations` | 主家长邀请副家长。 |
| `PATCH /api/v1/students/{id}/parent-relations/{relationId}` | 解绑或处理主副家长关系变更。 |
| `PUT /api/v1/students/{id}/organizations` | 维护入校、班级、转班转学关系。 |
| `POST /api/v1/students/{id}/cancellations` | 发起学生注销，服务端验证前置条件。 |

#### 2.4.1 V19 已实现学生接口契约

`POST /api/v1/students` 请求体为 `{"studentName":"学生姓名","gradeCode":"可选年级编码","organizationId":"可选组织标识"}`。`studentName` 去除首尾空格后不能为空且最长 64 个字符；`gradeCode` 可为空且最长 64 个字符。请求体不接收家长、机构管理员、学生账号、状态或关系角色，操作者均从 Bearer 会话取得。家长请求不得提供 `organizationId`；机构管理员必须提供其直接管理且处于启用状态的组织标识。V21 响应在既有字段外增加 `studentAccount` 和 `initialLoginCode`；标识为字符串形式的 19 位雪花值，初始登录码只允许在当前成功响应展示一次。

#### 2.4.2 V20 已实现机构家长绑定邀请接口契约

创建接口为 `POST /api/v1/students/{studentId}/parent-invitations`，需要 `STUDENT_PARENT_INVITE_CREATE`。请求体仅为 `{"organizationId":"机构标识"}`；后端从 Bearer 会话获取机构管理员，验证该组织处于启用状态、当前用户是其直接管理员、学生存在且与该组织存在活动直接关系，并验证学生尚无主家长。成功返回 `201`，字段为字符串 `id`、字符串 `studentId`、字符串 `organizationId`、`status`、`expiresAt`、`createdAt` 和 `acceptToken`。`acceptToken` 仅在这一创建响应返回一次，不得写入客户端持久化存储或日志；数据库只保存其 SHA-256 摘要。

接受、拒绝接口分别为 `POST /api/v1/parent-invitations/{id}/accept` 与 `POST /api/v1/parent-invitations/{id}/reject`，均需要 `STUDENT_PARENT_INVITE_RESPOND`。请求体仅含非空、最长 128 字符的 `acceptToken`；后端从 Bearer 会话取得既有 `PARENT` 账号。令牌错误、当前用户没有家长角色或机构范围不符返回 `403 ACCESS_DENIED`；邀请不存在返回 `404 RESOURCE_NOT_FOUND`；已响应、已过期、已有主家长或存在未过期待处理邀请返回 `409 STATE_CONFLICT`。两个响应接口成功均返回 `204`，接受在同一事务内建立唯一主家长关系，拒绝只关闭邀请。

#### 2.4.3 V21 已实现学生登录凭证接口契约

学生登录请求体为 `studentAccount`、`loginCode`、`deviceId`、`deviceName` 及按需提供的 `captchaChallengeId`、`captchaAnswer`。账号必须为 8 位数字、登录码必须为 4 位数字；无效格式、未知账号、非学生账号、停用用户、停用学生或登录码错误均统一返回 `401 STUDENT_AUTH_FAILED`。第 5 次失败起返回 `428 CAPTCHA_REQUIRED`，第 10 次返回 `423 STUDENT_ACCOUNT_LOCKED` 并携带 `lockedUntil`；有效验证码一次消费并绑定账号和设备。成功响应沿用会话 DTO，19 位 `sessionId` 以字符串返回，客户端类型固定为 `MINIAPP`。

历史初始化与登录码重置均无请求体，成功返回 `studentAccount`、`loginCode`，登录码明文只在当前响应出现。对象不在当前主家长或直接机构管理员范围时统一返回 `404 RESOURCE_NOT_FOUND`；重复初始化、未初始化即重置或学生状态不允许时返回 `409 STATE_CONFLICT`。普通目录、详情、设备列表和数据库均不得返回登录码明文。

`GET /api/v1/students` 支持可选 `keyword`、`page`、`pageSize` 参数。关键字去除首尾空格后最长 64 个字符；`page` 从 1 开始，`pageSize` 取值范围为 1 至 100；结果固定按 `created_at DESC, id DESC` 排序。响应结构为 `items`、`page`、`pageSize`、`total`，学生标识均以字符串返回。学生详情和目录范围均在服务端 SQL 或对象关系校验中执行，不允许客户端传入范围；普通已认证账号没有相应权限时返回 `403 ACCESS_DENIED`。

### 2.5 任务、打卡和审核

| 方法与路径 | 用途 |
|---|---|
| `GET/POST /api/v1/learning-tasks` | V22 已实现；按授权范围查询或创建家庭、机构、教师任务草稿。 |
| `GET/PATCH /api/v1/learning-tasks/{id}` | V22 已实现；查询可管理详情，或仅编辑 `DRAFT` 任务。 |
| `POST /api/v1/learning-tasks/{id}/publish` | V22 已实现；单事务展开学生实例并将任务置为已发布。 |
| `POST /api/v1/learning-tasks/batch-publish` | V22 已实现；最多 100 个不重复任务标识，逐项独立事务并返回部分失败明细。 |
| `GET /api/v1/learning-task-options/organizations` | V22 已实现；按显式 `sourceType` 返回当前角色可选组织。 |
| `GET /api/v1/learning-task-options/students` | V22 已实现；按显式 `sourceType` 和可选组织返回脱敏学生候选。 |
| `GET /api/v1/learning-task-options/teachers` | V22 已实现；机构管理员按数据范围返回教师及其班级标识。 |
| `POST /api/v1/learning-tasks/{id}/copy-previous-day` | 按学生复制昨日任务，服务端限制每日一次。 |
| `GET /api/v1/task-assignments` | V22 已实现学生本人只读列表；支持来源、计划日期和分页。 |
| `GET /api/v1/task-assignments/{id}` | V22 已实现学生本人只读详情；不返回原始目标或其他学生。 |
| `POST /api/v1/task-assignments/{id}/claim` | 学生认领待认领任务。 |
| `POST /api/v1/task-assignments/{id}/check-ins` | 学生提交打卡。 |
| `POST /api/v1/task-assignments/{id}/reviews/approve` | 当前有效审核人通过打卡。 |
| `POST /api/v1/task-assignments/{id}/reviews/reject` | 当前有效审核人驳回，意见必填。 |
| `POST /api/v1/task-assignments/{id}/pauses` | 学生发起情绪暂停或难题搁置。 |
| `POST /api/v1/task-assignments/{id}/resume` | 结束暂停并回到进行中。 |
| `POST /api/v1/task-assignments/{id}/abandon` | 学生放弃，进入待优化。 |
| `POST /api/v1/task-assignments/{id}/defer` | 按规则顺延。 |
| `POST /api/v1/task-assignments/{id}/exempt` | 授权角色设置免执行。 |

#### 2.5.1 V22 已实现学习任务契约

创建和更新请求包含 `sourceType`、可选 `sourceOrganizationId`、`title`、`difficultyLevel`、`durationMinutes`、`scheduledDate`、可选 `categoryCode`、`tagCodes`、可选 `remark`、可选 `reviewerUserId` 和非空 `targets`。目标元素只包含 `targetType` 与 `targetId`。基础积分、创建人、状态、发布时间和审核超时均由服务端生成；19 位标识按 JSON 字符串收发。

管理列表可选参数为 `sourceType`、`status`、`scheduledDate`、`keyword`、`page`、`pageSize`，固定按计划日期、创建时间和主键倒序；页大小为 1 至 100。学生列表不接受学生标识，服务端从 `MINIAPP` Bearer 会话反查学生档案；固定按计划日期、创建时间和实例主键倒序。

任务不存在或不在当前家庭、组织或班级范围统一返回 `404 RESOURCE_NOT_FOUND`；角色/权限或客户端不符返回 `403 ACCESS_DENIED`；字段、字典、重复批量标识返回 `400 VALIDATION_ERROR`；重复发布、编辑已发布任务、零有效学生等返回 `409 STATE_CONFLICT`；功能关闭返回 `409 FEATURE_DISABLED`。批量发布失败项使用“任务不可发布，请检查状态或数据范围”中性原因，不暴露任务是否存在或归属他人。

### 2.6 积分、奖励、复盘、机构和考勤

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/students/{id}/point-account` | 查询累计与可用积分。 |
| `GET /api/v1/students/{id}/point-ledgers` | 查询积分台账。 |
| `POST /api/v1/point-corrections` | 在规则时限内发起积分纠错。 |
| `GET/POST /api/v1/rewards` | 查询/配置家庭奖励。 |
| `POST /api/v1/reward-exchanges` | 学生提交兑换申请。 |
| `POST /api/v1/reward-exchanges/{id}/approve` | 主家长同意兑换。 |
| `POST /api/v1/reward-exchanges/{id}/reject` | 主家长驳回兑换。 |
| `POST /api/v1/reward-exchanges/{id}/verify` | 主家长核销。 |
| `GET /api/v1/growth-reviews` | 查询日/周/月复盘。 |
| `PATCH /api/v1/growth-report-subscriptions/{studentId}` | 开关周报订阅。 |
| `GET /api/v1/classes/{id}/anonymous-rankings` | 查询有权查看的匿名班级排行。 |
| `GET/POST /api/v1/exception-reports` | 查询/教师提交异常报备。 |
| `GET/POST /api/v1/attendance-geofences` | 查询/维护围栏；开关、审批、授权前置。 |
| `GET /api/v1/attendance-records` | 查询本人或授权范围内考勤结果。 |
| `GET /api/v1/location-tracks` | 仅在开关、授权、角色、组织范围均满足时查询。 |

### 2.7 文件、消息、导入导出、报表与反馈

| 方法与路径 | 用途 |
|---|---|
| `POST /api/v1/files/upload-sessions` | 申请临时上传授权，先校验附件规则和业务权限。 |
| `POST /api/v1/files/{id}/complete` | 确认上传完成并做服务端元数据校验。 |
| `GET /api/v1/files/{id}/preview` | 按权限生成预览结果。 |
| `GET /api/v1/files/{id}/download` | 按权限生成短时下载结果。 |
| `GET /api/v1/messages` | 当前用户消息列表。 |
| `POST /api/v1/messages/{id}/read` | 标记已读。 |
| `POST /api/v1/import-jobs` | 创建导入任务，关联受控模板。 |
| `GET /api/v1/import-jobs/{id}` | 查询逐条校验与处理结果。 |
| `POST /api/v1/export-jobs` | 创建导出任务，执行权限、脱敏、审批校验。 |
| `GET /api/v1/export-jobs/{id}` | 查询导出状态与受控下载结果。 |
| `GET /api/v1/reports/{reportCode}` | 按报表权限、筛选和统计口径查询。 |
| `GET/POST /api/v1/feedback` | 查询本人/提交帮助与意见反馈。 |

## 3. 关键请求对象

### 3.1 已实现的认证接口字段

`POST /api/v1/auth/sessions/password` 请求体包含 `username`、`password`、`deviceId`、`deviceName`，四个字段均必填；最大长度依次为 64、64、128、100。登录成功或刷新成功直接返回 `sessionId`、`accessToken`、`refreshToken`、`accessExpiresAt`、`refreshExpiresAt`。`sessionId` 是字符串形式的 19 位雪花 ID；原始令牌只在这两个成功响应中返回。

`POST /api/v1/auth/sessions/refresh` 请求体仅包含必填的 `refreshToken`。刷新成功后访问凭证和刷新凭证均轮换，旧刷新凭证不可再次使用；访问凭证过期时，仍在有效期内的刷新凭证可以继续刷新会话。

`GET /api/v1/auth/me` 返回 `userId`、`sessionId`、`username`、`displayName`、`clientType`、`roleCodes`。`userId` 与 `sessionId` 均以字符串返回，当前 `clientType` 仅为 `WEB`。`GET /api/v1/auth/devices` 返回当前用户的活动会话数组；每项仅包含 `id`、`clientType`、`deviceId`、`deviceName`、`accessExpiresAt`、`refreshExpiresAt`、`lastActiveAt`，不包含密码、令牌或令牌摘要。

`DELETE /api/v1/auth/sessions/current`、`DELETE /api/v1/auth/devices/{sessionId}`、`POST /api/v1/auth/devices/sign-out-all` 成功均返回 HTTP 204，无响应体。认证失败返回 HTTP 401 和 `AUTH_REQUIRED`；认证成功但无权下线其他用户会话返回 HTTP 403 和 `ACCESS_DENIED`。

### 3.2 创建系统任务

```json
{
  "taskType": "FEATURE_TOGGLE_CHANGE",
  "title": "调整地理位置考勤开关",
  "description": "说明调整原因和影响范围",
  "impactScope": "GLOBAL",
  "featureCode": "GEO_ATTENDANCE",
  "targetStatus": "ENABLED"
}
```

服务端必须验证发起人是系统管理员、开关存在、任务类型与目标一致；审批后才改变全局状态。示例不包含任何位置数据、第三方凭证或内部实现字段。

### 3.3 创建学习任务

```json
{
  "sourceType": "FAMILY",
  "title": "阅读任务",
  "difficultyCode": "MEDIUM",
  "durationMinutes": 30,
  "categoryCode": "READING",
  "tagCodes": ["DAILY"],
  "remark": "可补充任务说明",
  "targetStudentIds": ["10001"]
}
```

服务端根据当前身份决定是否允许 `sourceType`、目标范围和审核人；积分规则、来源组织和审核人不可由学生提交或篡改。

### 3.4 任务打卡与审核

```json
{
  "content": "完成情况说明",
  "fileIds": ["50001"]
}
```

打卡接口只接受当前学生的进行中任务；附件在提交前已经完成统一文件服务的归属和权限校验。审核驳回请求必须额外包含非空 `comment`。

## 4. 对外接口约束

1. 对外接口必须先在接口服务管理中登记服务名称、方向、用途、调用方、授权范围、责任人和启停状态，未登记不得开放。
2. 对外调用使用独立客户端凭证、接口范围和调用日志，不复用普通 Web/小程序用户会话。
3. 新增、停用或扩大授权范围由系统管理员发起系统任务、系统审核员审批后生效。
4. 对外响应执行最小数据原则和脱敏策略；未成年人位置、情绪、家庭私有任务、家庭奖励和家庭积分配置不得作为对外默认数据。

## 5. OpenAPI 落地检查

- [ ] 每个接口在 OpenAPI 中定义路径、方法、权限码、功能开关、请求/响应字段、错误码和示例。
- [ ] 每个写接口定义幂等要求、并发/状态冲突行为和审计事件。
- [ ] 每个列表接口定义授权数据范围、筛选项、排序、分页上限和脱敏字段。
- [ ] 每个文件、导出、对外接口均不返回长期有效的存储凭证或真实密钥。
- [ ] 前端客户端由 OpenAPI 契约生成或校验类型，Web 与小程序不得各自猜测字段或状态。
