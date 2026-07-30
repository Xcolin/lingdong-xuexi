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
| 功能关闭 | `FEATURE_DISABLED` | 403 | 不展示操作页面，禁止继续业务。 |
| 无数据范围 | `DATA_SCOPE_DENIED` | 403 | 不泄露对象是否存在或其详情。 |
| 资源不存在或不可见 | `RESOURCE_NOT_FOUND` | 404 | 显示不可访问/已不存在。 |
| 参数校验失败 | `VALIDATION_ERROR` | 400 | 返回字段级错误，不清空表单。 |
| 状态冲突/重复提交 | `STATE_CONFLICT` | 409 | 刷新对象状态后按当前有效操作处理。 |
| 幂等重复请求 | `IDEMPOTENCY_CONFLICT` | 409 | 返回已存在的处理结果或提示重试状态。 |
| 系统异常 | `INTERNAL_ERROR` | 500 | 返回 `traceId`，不输出堆栈、密钥或个人信息。 |

错误响应中的 `message` 只用于用户可见的中性描述；详细异常、SQL、凭证和第三方原始报文只进入受控日志。

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

以下为 V1 资源目录和操作名称。字段定义以 FSD 与数据库设计为基础，OpenAPI 落地时必须补齐字段类型、必填性、示例、错误码和权限码。

### 2.1 认证、账号和设备

| 方法与路径 | 用途 | 权限/前置 |
|---|---|---|
| `POST /api/v1/auth/sms-codes` | 发送家长验证码 | 频率限制与图形/风控校验。 |
| `POST /api/v1/auth/sessions/password` | 密码登录 | 家长/管理角色等允许密码登录的账号。 |
| `POST /api/v1/auth/sessions/sms` | 验证码登录 | 验证码有效且未超限。 |
| `POST /api/v1/auth/sessions/wechat` | 微信登录/会话换取 | 小程序微信授权；绑定与回退规则由账号用例执行。 |
| `POST /api/v1/auth/student-sessions/code` | 学生登录码登录 | 校验锁定、图形验证码与登录码。 |
| `POST /api/v1/auth/student-sessions/scan` | 学生扫码登录 | 校验二维码有效期与绑定关系。 |
| `POST /api/v1/auth/sessions/refresh` | 刷新会话 | 仅刷新有效会话。 |
| `DELETE /api/v1/auth/sessions/current` | 退出当前会话 | 已登录用户。 |
| `GET /api/v1/auth/devices` | 已登录设备列表 | 当前用户。 |
| `DELETE /api/v1/auth/devices/{deviceId}` | 下线指定设备 | 当前用户且设备归属本人。 |
| `POST /api/v1/auth/devices/sign-out-all` | 一键下线 | 当前用户。 |
| `GET /api/v1/auth/me` | 当前用户、角色、菜单与能力摘要 | 已登录用户；响应按端应用裁剪。 |

### 2.2 组织、用户、角色和权限

| 方法与路径 | 用途 |
|---|---|
| `GET/POST /api/v1/organization-types` | 查询/新增组织类型。 |
| `PATCH /api/v1/organization-types/{id}` | 修改组织类型、排序或启停。 |
| `GET/POST /api/v1/organizations` | 查询组织树/新增组织节点。 |
| `GET/PATCH /api/v1/organizations/{id}` | 查询/编辑组织节点。 |
| `POST /api/v1/organizations/{id}/disable-requests` | 创建重要组织停用系统任务。 |
| `GET/POST /api/v1/organization-admins` | 查询/配置组织管理员。 |
| `GET/POST /api/v1/users` | 用户列表/创建用户。 |
| `GET/PATCH /api/v1/users/{id}` | 用户详情、启停、基础资料维护。 |
| `PUT /api/v1/users/{id}/organizations` | 覆盖维护用户组织关联。 |
| `PUT /api/v1/users/{id}/roles` | 覆盖或增量维护用户角色与组织范围。 |
| `GET/POST /api/v1/roles` | 角色列表/创建自定义角色。 |
| `PATCH /api/v1/roles/{id}` | 修改自定义角色状态、描述和数据范围。 |
| `PUT /api/v1/roles/{id}/permissions` | 配置角色权限。 |
| `PUT /api/v1/roles/{id}/data-scopes` | 配置自定义组织数据范围。 |
| `PUT /api/v1/users/{id}/permissions` | 配置用户明确允许或禁止权限。 |
| `GET /api/v1/access-decisions` | 查询当前用户或被授权用户的权限结果摘要。 |

所有上述写接口只允许系统管理员或被显式授权的组织管理员在其范围内使用；接口层不可用 URL 中的组织 ID 替代服务端的数据范围计算。

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

| 方法与路径 | 用途 |
|---|---|
| `GET/POST /api/v1/students` | 查询/创建学生。 |
| `GET/PATCH /api/v1/students/{id}` | 学生详情/允许的基础资料修改。 |
| `POST /api/v1/students/{id}/login-code-resets` | 重置学生登录码。 |
| `POST /api/v1/students/{id}/parent-invitations` | 机构或主家长发起家长绑定邀请。 |
| `POST /api/v1/parent-invitations/{id}/accept` | 家长确认邀请。 |
| `POST /api/v1/parent-invitations/{id}/reject` | 家长拒绝邀请。 |
| `POST /api/v1/students/{id}/parent-relations` | 主家长邀请副家长。 |
| `PATCH /api/v1/students/{id}/parent-relations/{relationId}` | 解绑或处理主副家长关系变更。 |
| `PUT /api/v1/students/{id}/organizations` | 维护入校、班级、转班转学关系。 |
| `POST /api/v1/students/{id}/cancellations` | 发起学生注销，服务端验证前置条件。 |

### 2.5 任务、打卡和审核

| 方法与路径 | 用途 |
|---|---|
| `GET/POST /api/v1/learning-tasks` | 按授权范围查询/创建家庭、机构、教师任务。 |
| `GET/PATCH /api/v1/learning-tasks/{id}` | 查询/编辑允许编辑的任务定义。 |
| `POST /api/v1/learning-tasks/{id}/publish` | 下发并生成学生任务实例。 |
| `POST /api/v1/learning-tasks/{id}/copy-previous-day` | 按学生复制昨日任务，服务端限制每日一次。 |
| `GET /api/v1/task-assignments` | 当前角色授权范围内的学生任务实例列表。 |
| `GET /api/v1/task-assignments/{id}` | 任务实例详情、来源、状态、审核历史。 |
| `POST /api/v1/task-assignments/{id}/claim` | 学生认领待认领任务。 |
| `POST /api/v1/task-assignments/{id}/check-ins` | 学生提交打卡。 |
| `POST /api/v1/task-assignments/{id}/reviews/approve` | 当前有效审核人通过打卡。 |
| `POST /api/v1/task-assignments/{id}/reviews/reject` | 当前有效审核人驳回，意见必填。 |
| `POST /api/v1/task-assignments/{id}/pauses` | 学生发起情绪暂停或难题搁置。 |
| `POST /api/v1/task-assignments/{id}/resume` | 结束暂停并回到进行中。 |
| `POST /api/v1/task-assignments/{id}/abandon` | 学生放弃，进入待优化。 |
| `POST /api/v1/task-assignments/{id}/defer` | 按规则顺延。 |
| `POST /api/v1/task-assignments/{id}/exempt` | 授权角色设置免执行。 |

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

### 3.1 创建系统任务

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

### 3.2 创建学习任务

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

### 3.3 任务打卡与审核

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
