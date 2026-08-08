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
| `GET /api/v1/public/capabilities?client=WEB|MINIAPP` | 客户端启动前读取非敏感能力摘要 | 公开；只接受 `WEB` 或 `MINIAPP`。 | V26 返回任务、积分查询和客户端纠错能力；`MINIAPP` 另返回有效的学生账号登录能力，纠错能力固定为否。 |
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
| `POST /api/v1/learning-tasks/{id}/recurrence/stop` | V30 已实现；停止当前用户可管理的活动每日固定计划。 |
| `POST /api/v1/learning-tasks/batch-publish` | V22 已实现；最多 100 个不重复任务标识，逐项独立事务并返回部分失败明细。 |
| `GET /api/v1/learning-task-options/organizations` | V22 已实现；按显式 `sourceType` 返回当前角色可选组织。 |
| `GET /api/v1/learning-task-options/students` | V22 已实现；按显式 `sourceType` 和可选组织返回脱敏学生候选。 |
| `GET /api/v1/learning-task-options/teachers` | V22 已实现；机构管理员按数据范围返回教师及其班级标识。 |
| `GET /api/v1/students/{studentId}/previous-day-task-copy/preview` | V33 已实现；主家长预览昨日候选、今日同名和既有批次。 |
| `POST /api/v1/students/{studentId}/previous-day-task-copy` | V33 已实现；按学生创建或恢复当日唯一复制批次。 |
| `POST /api/v1/task-copy-batches/{batchId}/items/{itemId}/retry` | V33 已实现；主家长显式重试本人批次中的失败条目。 |
| `GET/POST /api/v1/task-templates` | V34 已实现；Web 家长查询系统加本人模板或新增本人个人模板。 |
| `PATCH/DELETE /api/v1/task-templates/{templateId}` | V34 已实现；按版本编辑或逻辑删除本人个人模板。 |
| `PUT /api/v1/task-templates/personal-order` | V34 已实现；提交本人全部活动个人模板及版本并原子排序。 |
| `GET /api/v1/task-assignments` | V23 已实现学生本人列表；支持来源、计划日期和分页，返回基础状态与有效状态。 |
| `GET /api/v1/task-assignments/{id}` | V23 已实现学生本人详情；返回活动暂停和最近打卡，不返回其他学生。 |
| `POST /api/v1/task-assignments/{id}/claim` | V23 已实现；学生认领本人待认领任务。 |
| `POST /api/v1/task-assignments/{id}/pause` | V23 已实现；暂停类型为 `EMOTION` 或 `DIFFICULTY`，时长 1 至 120 分钟。 |
| `POST /api/v1/task-assignments/{id}/resume` | V23 已实现；结束本人有效暂停并继续任务。 |
| `POST /api/v1/task-assignments/{id}/abandon` | V23 已实现；学生放弃后进入待优化，不扣分。 |
| `POST /api/v1/task-assignments/{id}/check-ins` | V31 已扩展；学生提交最长 1000 字文字和/或最多 9 个已上传图片标识。 |
| `GET /api/v1/task-reviews` | V23 已实现；当前审核人待审核分页。 |
| `GET /api/v1/task-reviews/{id}` | V23 已实现；仅当前审核人读取待办详情。 |
| `GET /api/v1/task-reviews/{id}/reviewer-options` | V23 已实现；返回服务端裁剪的审核候选人。 |
| `POST /api/v1/task-reviews/{id}/reject` | V23 已实现；驳回意见必填且最长 500 字。 |
| `POST /api/v1/task-reviews/{id}/transfer` | V23 已实现；转交原因必填，目标必须属于候选范围。 |
| `POST /api/v1/managed-task-assignments/{id}/exempt` | V23 已实现；授权角色按家庭或组织范围设置免执行。 |
| `POST /api/v1/task-reviews/{id}/approve` | V24 已实现；仅当前审核人可操作，请求体为空，按服务端基础积分完成任务并原子入账。 |
| `GET /api/v1/managed-task-assignments` | V32 已实现；Web 管理角色分页查询授权范围内可顺延任务。 |
| `POST /api/v1/managed-task-assignments/{id}/defer` | V32 已实现；按未来 1 至 7 天规则手动顺延。 |

#### 2.5.1 V22 已实现学习任务契约

创建和更新请求包含 `sourceType`、可选 `sourceOrganizationId`、`title`、`difficultyLevel`、`durationMinutes`、`scheduledDate`、可选 `categoryCode`、`tagCodes`、可选 `remark`、可选 `reviewerUserId` 和非空 `targets`。目标元素只包含 `targetType` 与 `targetId`。基础积分、创建人、状态、发布时间和审核超时均由服务端生成；19 位标识按 JSON 字符串收发。

管理列表可选参数为 `sourceType`、`status`、`scheduledDate`、`keyword`、`page`、`pageSize`，固定按计划日期、创建时间和主键倒序；页大小为 1 至 100。学生列表不接受学生标识，服务端从 `MINIAPP` Bearer 会话反查学生档案；固定按计划日期、创建时间和实例主键倒序。

任务不存在或不在当前家庭、组织或班级范围统一返回 `404 RESOURCE_NOT_FOUND`；角色/权限或客户端不符返回 `403 ACCESS_DENIED`；字段、字典、重复批量标识返回 `400 VALIDATION_ERROR`；重复发布、编辑已发布任务、零有效学生等返回 `409 STATE_CONFLICT`；功能关闭返回 `409 FEATURE_DISABLED`。批量发布失败项使用“任务不可发布，请检查状态或数据范围”中性原因，不暴露任务是否存在或归属他人。

#### 2.5.2 V23-V24 已实现执行、审核与任务奖励积分契约

学生执行接口必须使用 `MINIAPP` 会话和 `TASK_ASSIGNMENT_EXECUTE_SELF`，服务端只从会话反查学生档案。所有写操作先锁定本人任务实例；跨学生标识返回 `404`，重复点击或非法状态返回 `409`。响应沿用学生任务详情，并增加 `effectiveStatus`、可空 `activePause` 和可空 `latestCheckIn`，其中所有 19 位标识序列化为字符串。

审核接口必须使用 `WEB` 会话和 `TASK_ASSIGNMENT_REVIEW`。待办与详情只查询 `current_reviewer_id` 等于当前用户且状态为待审核的数据。驳回在同一事务内把最近打卡标为 `REJECTED`、保存意见、将任务退回进行中并写事件；再次打卡生成递增提交序号，不覆盖历史。转交同时更新当前审核人、写入转交历史和审计事件。免执行需要 `TASK_ASSIGNMENT_EXEMPT`，并继续校验家庭主关系、创建教师或机构组织数据范围。

V24 审核通过请求不接受积分、学生、来源或余额字段。服务端锁定当前审核人的任务实例、最新待审核打卡和学生积分账户，使用任务基础积分完成打卡与任务、增加累计及可用积分、写入唯一任务奖励台账和审核通过事件。成功响应包含 `assignmentId`、`currentStatus=COMPLETED`、`checkInId`、`checkInStatus=APPROVED`、`awardedPoints`、`totalPoints`、`availablePoints`、`ledgerId`；所有标识均为字符串。非当前审核人返回 404，重复或并发旧请求返回 409。

#### 2.5.3 V30 每日固定任务契约

创建和更新任务增加 `recurrenceEnabled` 与可空 `recurrenceEndDate`。未传启用标识按 `false`；关闭时不得携带结束日；启用时结束日不得早于 `scheduledDate`。详情和管理列表返回 `recurrenceEnabled`、可空 `recurrenceEndDate` 及可空 `recurrenceStatus=ACTIVE|COMPLETED|STOPPED`。

发布固定任务时，首日学生实例和活动计划在同一事务创建，下一生成日为首日加一天。停止接口请求体为空，成功返回字符串 `taskId`、字符串 `recurrenceId`、`status=STOPPED`、字符串 `stoppedByUserId` 和 `stoppedAt`。越权任务返回 404；普通任务、重复停止、已完成计划和并发版本冲突返回 409；`LEARNING_TASK_MANAGEMENT` 关闭返回 `409 FEATURE_DISABLED`。

#### 2.5.4 V31 图片打卡附件契约

`POST /api/v1/attachments/uploads` 使用 `multipart/form-data`，字段为 `moduleCode=LEARNING_TASK_CHECKIN`、`fileCategory=IMAGE` 和 `file`。仅接受小程序学生上传的 JPG/JPEG/PNG，单文件不超过 10 MB；成功返回字符串文件标识、原始文件名、归一化内容类型、大小、模块和分类，不返回存储键或路径。

`GET /api/v1/attachments/{id}` 返回安全元数据；`GET /api/v1/attachments/{id}/content` 返回认证后的图片字节；`DELETE /api/v1/attachments/{id}` 仅允许上传人删除尚未关联的临时文件，成功返回 204。上传学生本人及当前有效审核人可读取已关联图片，其他用户与不存在文件统一返回 404。

任务详情和审核详情的 `latestCheckIn` 增加 `attachments`，每项包含字符串 `id`、`originalName`、`contentType` 和 `fileSizeBytes`。提交打卡时 `content` 可空，`fileIds` 可空或最多 9 项，但两者不能同时为空；文件必须由当前学生上传、属于指定模块和分类、未删除且未关联。服务端在打卡事务内完成关联。

#### 2.5.5 V32 待优化与顺延契约

`GET /api/v1/managed-task-assignments` 仅允许 Web 端具有 `TASK_ASSIGNMENT_DEFER` 权限的家长、教师或机构管理员调用，接收 `page`、`pageSize`，返回 `items`、`page`、`pageSize`、`total`。列表项包含字符串 `assignmentId`、任务标题、字符串 `studentId`、学生名称、来源类型、可空来源组织名称、计划日期、当前状态、可空最近顺延类型和隔夜迁移标记。

`POST /api/v1/managed-task-assignments/{id}/defer` 请求体仅包含 `targetDate`。目标日期必须为上海时区当前日期之后 1 至 7 天；家庭任务限活动主家长，教师任务限创建教师，机构任务限授权组织范围。成功返回字符串 `assignmentId`、字符串 `targetTaskId`、`status=PENDING_CLAIM`、目标日期、`deferType=MANUAL` 和隔夜迁移标记。

服务端每日 23:59 将到期且无活动暂停的 `IN_PROGRESS` 实例转为 `NEEDS_IMPROVEMENT`，每日 00:00 将昨日未手动处理的待优化实例自动顺延到当日。顺延复制任务定义与标签快照、关闭周期属性、迁移同一学生实例并写入不可变历史。手动顺延优先于自动顺延；自动顺延后仅在实例仍为待认领时允许再次手动改期。状态冲突或日期越界返回 409，越权对象统一返回 404，功能停用返回 `409 FEATURE_DISABLED`。

#### 2.5.6 V33 按学生复制昨日任务契约

预览与复制接口仅允许 `WEB` 会话、`PARENT` 角色、`LEARNING_TASK_COPY_PREVIOUS_DAY` 权限和活动主家长关系。服务端固定使用上海业务日计算昨日与今日；预览返回字符串学生标识、学生名称、源日期、目标日期、候选数量、同名标题、是否已复制及可空既有批次。

复制请求体仅包含 `confirmDuplicateTitles`。存在今日同名标题且未确认时返回状态冲突；无候选时不创建批次。成功返回字符串批次标识、学生标识、日期、批次状态、总数、成功数、失败数和条目列表；条目标识、源任务和目标任务标识均为字符串。部分失败返回同一成功响应结构，由条目状态表达，不回滚已成功任务。

每个学生目标日期唯一批次保证重复 POST 幂等。已有批次若仍含 `PENDING` 条目，重复请求只恢复这些条目；`SUCCESS` 与 `FAILED` 不自动重放。重试接口只接受 `FAILED` 条目，成功项、越权批次和失效主关系分别按状态冲突或不可访问资源处理。两个功能开关任一关闭时后端返回 `409 FEATURE_DISABLED`；公共能力仅对 Web 返回 `previousDayTaskCopyEnabled=true`，小程序固定为 false。

#### 2.5.7 V34 任务模板契约

模板接口只允许 `WEB` 会话和 `PARENT` 角色。读取同时要求 `LEARNING_TASK_TEMPLATE_READ`，新增、编辑、删除和排序要求 `LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL`；`LEARNING_TASK_MANAGEMENT` 与 `LEARNING_TASK_TEMPLATE` 任一停用均返回 `409 FEATURE_DISABLED`。

模板输入包含 `templateName`、`taskTitle`、`difficultyLevel`、`durationMinutes`、可空 `categoryCode`、`tagCodes` 和可空 `remark`，不接受来源、组织、学生、日期、审核人、积分、周期或任务状态。更新请求额外包含整数 `versionNo`；删除使用查询参数 `versionNo`；排序请求为当前家长全部活动个人模板组成的 `items[{templateId,versionNo}]`。

响应中的 `id` 为字符串雪花标识，另含范围、可复用字段、排序、版本和审计时间。系统模板仅允许读取和选用；跨家长或系统模板管理统一返回不可访问资源。个人名称冲突、100 个上限、版本或排序集合变化返回 409。公共能力仅在 Web 双开关启用时返回 `learningTaskTemplateEnabled=true`，小程序固定为 false。

### 2.6 积分、奖励、复盘、机构和考勤

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/growth-points/me/account` | V25 已实现；学生 `MINIAPP` 会话查询本人累计与可用积分。 |
| `GET /api/v1/growth-points/me/ledgers` | V25 已实现；学生查询本人不可变台账，支持 `page`、`pageSize`。 |
| `GET /api/v1/growth-points/students` | V25 已实现；家长 `WEB` 会话查询活动主关系学生选项。 |
| `GET /api/v1/growth-points/students/{studentId}/account` | V25 已实现；主家长查询活动主关系孩子账户。 |
| `GET /api/v1/growth-points/students/{studentId}/ledgers` | V25 已实现；主家长查询活动主关系孩子台账分页。 |
| `POST /api/v1/growth-points/students/{studentId}/corrections` | V26 已实现；主家长在 72 小时内整笔纠正本人误审的家庭任务奖励。 |
| `GET/POST /api/v1/rewards/students/{studentId}` | V27 已实现；主家长分页查询/新增孩子的家庭奖励。 |
| `PATCH/DELETE /api/v1/rewards/{rewardId}` | V27 已实现；主家长编辑、上下架或逻辑删除孩子奖励。 |
| `GET /api/v1/rewards/me` | V27 已实现；学生分页查询本人在线且未到期奖励。 |
| `GET /api/v1/rewards/me/summary` | V27 已实现；学生查询奖励兑换能力内的本人可用积分摘要。 |
| `POST /api/v1/reward-exchanges` | V27 已实现；学生提交本人奖励兑换申请。 |
| `GET /api/v1/reward-exchanges/me` | V27 已实现；学生分页查询本人兑换历史。 |
| `GET /api/v1/reward-exchanges/students/{studentId}` | V27 已实现；主家长分页查询孩子兑换记录。 |
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

#### 2.6.1 V25 积分查询契约

账户响应包含 `studentId`、`studentName`、`totalPoints`、`availablePoints`、`updatedAt`，不返回账户主键和内部版本号。台账响应包含 `id`、`changeType`、`amount`、`availableDelta`、可空来源任务/兑换/类型/组织、任务标题、审核人、`occurredAt` 和可空备注；19 位标识均为字符串。分页范围为页码 1 至 1000000、每页 1 至 100，固定按 `occurredAt DESC, id DESC` 排序。

学生本人端点不接收学生标识；家长端点只接受活动主关系学生。跨关系学生返回 `404 RESOURCE_NOT_FOUND`，无 RBAC 权限返回 `403 ACCESS_DENIED`，`GROWTH_POINT_QUERY` 停用返回 `409 FEATURE_DISABLED`。教师和机构管理员后续只能使用按来源及组织过滤的统计接口，不得复用统一账户端点。

#### 2.6.2 V26 积分纠错契约

纠错请求只包含字符串形式的 `originalLedgerId` 和 1 至 500 字的 `reason`，不接收积分数量、账户余额、任务状态或审核人。成功响应包含学生、任务实例、原台账、纠错台账标识，整笔纠错积分、最新累计/可用积分、`PENDING_REVIEW` 和发生时间；全部 19 位标识以字符串输出。

家长台账响应增加 `correctionOfId`、`correctionLedgerId`、`correctionDeadline` 和 `correctable`。`GROWTH_POINT_CORRECTION` 停用返回 `409 FEATURE_DISABLED`；超时、重复、非本人审核、非家庭奖励或状态已变化返回 `409 STATE_CONFLICT`；跨关系学生和不属于该学生的原台账返回 404。

#### 2.6.3 V27 家庭奖励与兑换契约

奖励新增请求包含 `rewardName`、`requiredPoints`、可空 `description`、可空 `validUntil` 和 `online`；更新沿用同一组业务字段。奖励与兑换查询统一接收 `page`、`pageSize`，页码范围为 1 至 1000000、每页 1 至 100，响应固定为 `items`、`page`、`pageSize`、`total`。家长兑换查询可选 `status`，学生奖励与兑换查询不接受学生标识。

学生申请请求只包含字符串形式的 `rewardId`。服务端从 `MINIAPP` 会话反查学生，保存奖励名称、所需积分和描述快照；主家长同意、驳回和核销分别使用 `/approve`、`/reject`、`/verify`，驳回请求只包含 1 至 500 字原因。奖励、兑换、学生、家长、审核人和台账等 19 位标识全部按 JSON 字符串收发。

奖励兑换能力的账户摘要由 `REWARD_EXCHANGE` 独立控制，不依赖 `GROWTH_POINT_QUERY`。申请时不扣分；同意时只减少可用积分并返回最新兑换状态，累计积分不变。兑换台账返回 `sourceExchangeId`，`amount=0`、`availableDelta=-requiredPointsSnapshot`，备注保留奖励名称用于两端追溯。

功能停用返回 `409 FEATURE_DISABLED`；角色、权限或客户端类型不符返回 `403 ACCESS_DENIED`；跨家庭、跨学生或非当前主家长对象统一返回 `404 RESOURCE_NOT_FOUND`；重复活动申请、积分不足、过期、并发旧请求和非法状态转换返回 `409 STATE_CONFLICT`。

#### 2.6.4 V28 成长复盘契约

| 方法与路径 | 客户端与权限 | 用途 |
|---|---|---|
| `GET /api/v1/growth-reviews/me` | 小程序；`GROWTH_REVIEW_READ_SELF` | 学生查询本人复盘列表，支持 `periodType`、`page`、`pageSize`。 |
| `GET /api/v1/growth-reviews/me/{reviewId}` | 小程序；`GROWTH_REVIEW_READ_SELF` | 查询本人复盘当前快照、分类、趋势和补录。 |
| `POST /api/v1/growth-reviews/me/{reviewId}/supplements` | 小程序；`GROWTH_REVIEW_SUPPLEMENT_SELF` | 学生在日复盘允许时间内追加补录。 |
| `GET /api/v1/growth-reviews/students/{studentId}` | Web；`GROWTH_REVIEW_READ_CHILD` | 活动主家长查询指定孩子复盘列表。 |
| `GET /api/v1/growth-reviews/students/{studentId}/{reviewId}` | Web；`GROWTH_REVIEW_READ_CHILD` | 活动主家长查询指定孩子复盘详情。 |
| `POST /api/v1/growth-reviews/students/{studentId}/{reviewId}/supplements` | Web；`GROWTH_REVIEW_SUPPLEMENT_CHILD` | 活动主家长在日复盘允许时间内追加补录。 |

列表固定按周期开始日和复盘标识倒序分页。详情返回当前快照版本、数据截止时间、任务总数/各状态数量、完成率、累计获取积分、暂停次数、分类统计、每日趋势和追加补录；`reviewId`、`snapshotId`、补录标识、学生标识和编辑人标识均以 JSON 字符串输出。补录请求只接受 `supplementType=INSIGHT|STRENGTH_WEAKNESS|NEXT_PLAN` 和去空白后 1 至 1000 字正文，不接受学生、编辑人、快照版本或时间字段。

每日开关关闭后不生成日复盘且不允许新增日复盘补录；周期开关关闭后不生成周/月复盘。两个开关关闭均不删除历史，已授权用户仍可读取历史记录。跨学生、无活动主关系或复盘不属于目标学生统一返回 `404 RESOURCE_NOT_FOUND`；客户端或角色错误返回 `403 ACCESS_DENIED`；超出日复盘当日和次日窗口、非日复盘补录或功能停用返回 `409` 业务错误。

#### 2.6.5 V29 积分生命周期查询契约

V29 不新增客户端写接口。任务审核通过仍使用既有审核端点，服务端响应中的 `awardedPoints` 改为衰减后的实际发放值；客户端不得提交连续天数、衰减比例、规则标识或实发积分。

学生本人和主家长的既有积分台账响应增加可空字段 `sourceTaskId`、`basePointsSnapshot`、`decayPercent`、`streakDays`、`decayRuleId`。任务奖励记录返回本次审核固化的基础积分、衰减比例和连续天数；历史非任务记录与无法回填的规则标识返回空值。所有 19 位标识继续按 JSON 字符串输出。

`POINT_LIFECYCLE` 停用时，审核仍按任务基础积分全额发放，但不应用衰减规则；沉睡扫描不创建提醒、不清零。沉睡提醒和清零由后台任务执行，没有面向学生或家长的手工触发接口。提醒记录状态只表达 `PENDING` 或 `NO_RECIPIENT`，在实际消息适配完成前不得对外宣称已发送。

### 2.7 文件、消息、导入导出、报表与反馈

| 方法与路径 | 用途 |
|---|---|
| `POST /api/v1/files/upload-sessions` | 申请临时上传授权，先校验附件规则和业务权限。 |
| `POST /api/v1/files/{id}/complete` | 确认上传完成并做服务端元数据校验。 |
| `GET /api/v1/files/{id}/preview` | 按权限生成预览结果。 |
| `GET /api/v1/files/{id}/download` | 按权限生成短时下载结果。 |
| `POST /api/v1/attachments/uploads` | V31 已实现；小程序学生上传 JPG/PNG 任务打卡图片。 |
| `GET /api/v1/attachments/{id}` | V31 已实现；上传学生或当前审核人读取安全元数据。 |
| `GET /api/v1/attachments/{id}/content` | V31 已实现；认证读取图片内容，不返回永久直链。 |
| `DELETE /api/v1/attachments/{id}` | V31 已实现；上传人删除尚未关联的临时文件。 |
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
  "fileIds": ["9000000000000000001"]
}
```

打卡接口只接受当前学生的进行中任务；`content` 去除首尾空格后最长 1000 字，`fileIds` 最多 9 个且不得重复，二者至少提供一项。附件在同一事务内完成统一文件服务的归属、状态、模块、分类和未关联校验。审核驳回请求必须额外包含非空 `comment`。

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
