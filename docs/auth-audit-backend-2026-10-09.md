# 后端权限审计（2026-10-09）

本报告以当前工作区源码为准，只读审计；不代表线上部署版本。没有读取数据库，没有重启服务或修改业务代码。范围包括全部 Web 控制器权限声明、公共权限决策、菜单授权实现及重点业务服务层。末尾附控制器完整声明索引，供实施逐项对照。

## 1. 必须采用的统一契约

有效操作 = 启用账号 + 正确客户端 + 单一动作权限（显式 DENY 优先）+ 功能开启 + 对象在允许的数据范围 + 合法业务状态。权限授权不应偷偷附加另一个通用 IAM 权限。业务身份、关系、组织范围与审批状态不是可删去的安全边界。

建议以现有 API 大写动作编码为单一标准，菜单 node.code 可保留稳定 UI 标识，permissionCode 显式引用动作编码。纯展开、刷新、取消等 UI 按钮不授予操作权限；多个入口执行同一个动作应共享同一 permissionCode。不要新增一次性的第二套 slug 权限。页面读取可作为声明的依赖或动作派生的只读能力，但必须统一到服务端实际决策，尊重页面 DENY。

MenuApplicationService.java:225-243 当前把 grantable 节点 permissionCode 强制设置为 code；必须先解耦，否则 UI slug 无法引用标准编码。删除菜单目前同时删除权限及全部授权（:79-97），解耦后应改为检查其他菜单/端点引用，避免删 UI 入口导致共享 API 权限消失。

## 2. 已确认的不一致

| 位置 | 当前依赖/结果 | 可实施修正 |
|---|---|---|
| iam/web/OrganizationMembershipController.java:46-61; iam/application/OrganizationMembershipService.java:118-191 | members:batch/revoke 控制器及服务都要求 IAM_USER_ORGANIZATION_ASSIGN；administrator=true 再要求 (IAM_DATA_SCOPE_CONFIGURE AND IAM_USER_ROLE_ASSIGN) OR 对应 slug。仅勾取消管理员 slug 仍被通用权限阻挡；通用权限可绕过按钮 DENY | 分离关联/解绑/设管理员/撤管理员端点，分别 IAM_USER_ORGANIZATION_ASSIGN、IAM_USER_ORGANIZATION_REVOKE、ORG_ADMIN_ASSIGN、ORG_ADMIN_REVOKE；控制器与服务只检验对应动作，禁止 generic OR slug 绕过单动作拒绝 |
| datascope/application/DataScopeAdministrationService.java:109-124 | scoped administrator operation 已加入，但仍有 generic OR slug；不是完整统一 | scoped 方法只使用 ORG_ADMIN_ASSIGN/REVOKE，并保留 memberScopes.requireTarget；原全局数据范围接口使用 IAM_DATA_SCOPE_CONFIGURE |
| OrganizationMembershipController.java:23-45; OrganizationMembershipService.java:49,91 | 查询关联/候选人要求 IAM_USER_LIST；执行角色关联要求 IAM_USER_ROLE_ASSIGN。授权了写动作仍可能打不开选择器 | 选择器使用对应动作权限并只返回范围内候选；组织用户列表独立 IAM_USER_LIST，不隐式放开全局用户列表 |
| menu/application/MenuGrantApplicationService.java:43-117 | grantableNodes 仅看 type/grantable/permissionCode；不看功能、客户端、节点状态、权限状态；V112 把 API/MINIAPP 权限挂进 Web 菜单 | Web 授权树只包含 WEB/BOTH、启用节点与有效动作；功能关闭显示 disabledReason 或隐藏并拒绝新增；服务器保存重新验证同一规则 |
| MenuGrantApplicationService.java:69-95 | 使用 node.code 保存、permissionCode 做删除匹配；现有 code==permissionCode 时成立，解耦后错误 | 输入动作码；查 permissionCode；按动作集合差量保存。目录/多个 UI 入口不重复显示同动作 |
| permission/application/PermissionDecisionService.java:39-99 | 决策执行用户/权限状态、客户端、DENY、角色/用户 ALLOW；未计算功能开关；findAllowedCodes 只特殊派生 ORG_NODE_READ | 输出 grantedActions 与 effectiveActions/disabledReasons；当前菜单、按钮、直接路由、API 用同一有效条件。若保留功能条件在服务层，也须提供前端准确能力信息 |
| permission/PermissionDecisionService.java:71-95; menu/MenuApplicationService.java:323-354 | 只有组织页面派生可查询 ORG_NODE_READ；其他页面也能通过子按钮显示，但 API 读权限未必派生 | 给动作定义具体的最小只读依赖（不含管理权限），页面打开、选择器和数据请求依赖明确且统一 |
| student/application/ManagedStudentProfileService.java:56-82 | 创建学生要求 IAM_USER_CREATE AND STUDENT_CREATE，含班级时再 STUDENT_CLASS_ASSIGN | 执行标准 STUDENT_CREATE；内部创建学生账号不是额外授 IAM_USER_CREATE。班级分配若可选独立动作，应 UI 明确且后端独立校验 |
| growthpoint/application/GrowthPointQueryService.java:114-115、GrowthReviewQueryService.java:282-294、GrowthRewardService.java:186-189、GrowthRewardExchangeService.java:205-208 | Web 成长/奖励动作被硬编码 PARENT 身份限制；奖励还拒绝 SYS_AUDITOR。赋给自定义角色的 API 权限仍不能操作 | 产品若为家长专属，则授权树必须明确身份适用范围；若面向委派操作，则按权限+真实家长/学生关系范围或管理范围校验，不以固定 role.code 代替动作授权 |

## 3. 重点业务依赖映射与保留条件

| 业务 | 当前入口动作 | 服务附加条件必须保留 | 建议规范动作 |
|---|---|---|---|
| 组织成员查询/候选 | IAM_USER_LIST；列表 anyOf IAM_USER_LIST/organizations.members.query | 启用账号、memberScopes.requireTarget；候选用户可见范围 | IAM_USER_LIST（成员列表）；候选沿调用动作权限 |
| 成员关联/解绑 | IAM_USER_ORGANIZATION_ASSIGN | 目标组织、用户可见范围、组织状态；不能直接解绑仍为管理员的成员；批量事务原子 | IAM_USER_ORGANIZATION_ASSIGN / IAM_USER_ORGANIZATION_REVOKE |
| 设为/取消管理员 | 上述通用权限 + promote/add-admin/admin-picker.save/revoke-admin slug 或通用组合 | 管理员身份表与 ORG_ADMIN/PARENT_ADMIN 角色同步，家庭与机构类型正确；组织范围；取消不扩大成员权限 | ORG_ADMIN_ASSIGN / ORG_ADMIN_REVOKE |
| 角色用户列表/选择/关联/移除 | IAM_USER_ROLE_ASSIGN | UserAccessApplicationService.java:453-495：SELF 可见用户；全局 ALL 需全部组织范围；区域/学校/班级/家庭类型匹配；移除按现有授权范围而非新授予规则；管理员身份未撤前禁止直接删角色 | IAM_USER_ROLE_READ / IAM_USER_ROLE_ASSIGN / IAM_USER_ROLE_REVOKE；选择器沿写动作 |
| 家长关系列表 | PARENT_RELATIONSHIP_READ | 当前有效家长关系/学生对象范围，不可把读权限变成任意学生访问 | PARENT_RELATIONSHIP_READ |
| 次家长邀请/解绑 | SECONDARY_PARENT_INVITE_CREATE / SECONDARY_PARENT_UNBIND | PARENT_RELATIONSHIP_MANAGEMENT；有效主家长；关系上限、未处理邀请、身份状态 | 保留现有编码 |
| 主家长转移/自解绑 | PRIMARY_PARENT_TRANSFER_CREATE / PRIMARY_PARENT_SELF_UNBIND | 同功能开关；有效主家长；目标手机号验证、邀请状态/到期、关系事务一致性 | 保留现有编码 |
| 邀请接受/拒绝 | ParentRelationshipController.java:107-126 无 RequirePermission | 已是邀请手机号/SMS验证流程，可创建会话；功能开关、邀请有效性、手机号绑定和主家长关系状态 | 不作为登录后角色授权动作；仅公开验证流程能力 |
| 学生登录二维码签发 | STUDENT_LOGIN_QR_CREATE | StudentQrTicketApplicationService.java:63-145：STUDENT_QR_LOGIN；启用学生账号；Web 主家长或管理范围内有效入学组织；短时一次性票据 | 保留现有码，UI ticket/create/copy 不多次授予 |
| 学生验证码/登录码/扫码/微信登录 | 登录流程不授 RBAC 动作 | 验证码、启用账号、对应功能开关、票据一次性/到期、绑定真实性；管理凭据相关端点仍独立动作权限 | 登录功能能力与后台管理动作分开 |
| 成长点查看/修正 | GROWTH_POINT_READ_SELF/CHILD、GROWTH_POINT_CORRECT_CHILD | GROWTH_POINT_QUERY / GROWTH_POINT_CORRECTION、本人/有效子女关系；修正事务、余额/账本一致 | 保留并按 SELF/CHILD 区分，取消重复 UI slug |
| 成长报告查看/补齐 | GROWTH_REVIEW_READ_SELF/CHILD、GROWTH_REVIEW_SUPPLEMENT_SELF/CHILD | DAILY_GROWTH_REVIEW / PERIODIC_GROWTH_REPORT 按周期；父母关系与数据范围；补齐范围、并发生成条件 | 保留现有动作；报告导出另 EXPORT_JOB_CREATE |
| 奖励管理/兑换/审核 | REWARD_MANAGE_CHILD、REWARD_EXCHANGE_SELF、REWARD_EXCHANGE_REVIEW_CHILD | REWARD_EXCHANGE；管理/审核需有效主家长关系；兑换需本人学生；余额、库存、订单状态事务一致 | 保留现有动作；列表/选择奖励沿需要该列表的动作，不附加另一写动作 |
| 周报订阅 | 控制器声明见索引 | GROWTH_REVIEW_WEEKLY_SUBSCRIPTION AND PERIODIC_GROWTH_REPORT；有效关系、Web客户端 | 同一订阅动作，不额外把小程序码授给 Web |
| 匿名班级排行 | 控制器声明见索引 | ANONYMOUS_CLASS_RANK；有效家长学生关系、所属班级、排行数据匿名 | 按客户端现有码或统一 BOTH 码，但保持客户端语义 |

## 4. 重复/过时权限的清理范围

V112__align_business_permissions_with_menus.sql 把既有 API 权限追加为按钮，不会替换原 UI slug，故属于展现层累加而非统一。V103 的组织撤管理员按钮与 V97 的设管理员按钮，以及 V110 的名称前缀修改，均不能完成 API 对齐。

源码中 PRIMARY_PARENT_TRANSFER、PRIMARY_PARENT_UNBIND、PARENT_RELATIONSHIP_INVITE_RESPOND 未找到 RBAC 消费点；PRIMARY_PARENT_TRANSFER 的短信枚举是另一个语义，不是 RBAC 消费。V112 又把三个码作为可选按钮挂入家长关系页。应从角色授权树删去这些遗留授权项，使用真实的 TRANSFER_CREATE / SELF_UNBIND；邀请响应属于验证流程不授予登录角色。

MINIAPP_* 动作与 Web 动作不能仅因名称类似就直接合并。先确定客户端与对象语义；仅完全相同的动作可改 BOTH，独立小程序能力不进入 Web 授权树。父母/学生/教师身份角色属于业务关系入口，不能因“历史数据全清理”把家长绑定、入学、班级、管理员身份安全条件清掉。

## 5. 覆盖全 Web 的实施步骤与验收

1. 生成唯一动作目录：code、名称、客户端、功能条件、页面、数据范围策略、角色适用范围、是否纯 UI、读取依赖。将末尾全部控制器声明与 web-menu-actions.json 逐项对照；每个可授予动作必须有 API/服务消费，每个受保护 API 必须有可理解的授权项。
2. 修改菜单模型允许 permissionCode 与 node.code 分开；控制器和服务引用同一常量。为复合 members:batch/revoke 拆动作端点或按请求 administrator 分支执行同一动作（不能在控制器先统一阻断）。
3. 清理历史授权可全量重建：动作目录、菜单权限绑定、角色权限、用户权限、角色关联按规范生成。先定义管理员账号和必要数据范围初始化，再清理；数据库实际清理须由主任务另行执行，本报告没有执行。
4. 在授权树与保存接口应用客户端/状态/功能有效条件。关闭功能时不能新增授权且操作拒绝；既有授权可显示锁定原因以免误认已生效；再开启后恢复策略要明确。
5. 逐动作验收：只授该动作即可打开所需页面/选择器并执行范围内操作；未授按钮隐藏且直接 API 拒绝；DENY 不能通过通用权限或其他 UI 入口绕过；范围外对象拒绝；功能关闭树不可勾选且直接接口拒绝；撤管理员和撤角色次序一致；多角色、用户 ALLOW/DENY、状态停用、会话刷新都覆盖。
6. 对导出/敏感审核、缓存、接口服务、任务审核保留审批分离、任务状态机和功能开关；权限统一不能把提交与审核合成同一码。全 Web 控制器权限族索引如下，实施时必须逐项验证，不限于组织页。

## 6. 全部控制器权限声明索引（源码自动提取）

路径均相对 lingdong-bansui-server/src/main/java/com/lingdong/learning。该索引覆盖声明，不代表每个服务已完成逐行安全证明。

### attachment/web/AttachmentManagementController.java

- L51: `@RequirePermission("ATTACHMENT_RULE_READ")`
- L66: `@RequirePermission("ATTACHMENT_RULE_MANAGE")`
- L80: `@RequirePermission("ATTACHMENT_RULE_MANAGE")`
- L93: `@RequirePermission("ATTACHMENT_RULE_MANAGE")`
- L104: `@RequirePermission("ATTACHMENT_RULE_MANAGE")`
- L115: `@RequirePermission("ATTACHMENT_FILE_LEDGER_READ")`
- L136: `@RequirePermission("ATTACHMENT_FILE_LEDGER_READ")`

### attachment/web/TaskAttachmentController.java

- L35: `@RequirePermission("ATTACHMENT_UPLOAD")`
- L53: `@RequirePermission("ATTACHMENT_READ")`
- L61: `@RequirePermission("ATTACHMENT_READ")`
- L83: `@RequirePermission("ATTACHMENT_READ")`
- L105: `@RequirePermission("ATTACHMENT_UPLOAD")`

### attendance/web/AttendanceController.java

- L40: `@RequirePermission("ATTENDANCE_READ")`
- L52: `@RequirePermission(value = "ATTENDANCE_READ", anyOf = "ATTENDANCE_RECORD")`
- L60: `@RequirePermission("ATTENDANCE_RECORD")`
- L70: `@RequirePermission("ATTENDANCE_RECORD")`
- L77: `@RequirePermission("ATTENDANCE_READ")`

### audit/web/SystemTaskQueryController.java

- L22: `@GetMapping @RequirePermission("SYSTEM_TASK_READ")`
- L32: `@GetMapping("/{id}") @RequirePermission("SYSTEM_TASK_READ")`

### auth/web/ParentAuthenticationController.java

- L142: `@RequirePermission("PARENT_ACCOUNT_LIFECYCLE_MANAGE")`
- L151: `@RequirePermission("PARENT_ACCOUNT_LIFECYCLE_MANAGE")`
- L162: `@RequirePermission("PARENT_ACCOUNT_LIFECYCLE_MANAGE")`
- L173: `@RequirePermission("PARENT_ACCOUNT_LIFECYCLE_MANAGE")`
- L186: `@RequirePermission("PARENT_ACCOUNT_LIFECYCLE_MANAGE")`
- L197: `@RequirePermission("PARENT_ACCOUNT_LIFECYCLE_MANAGE")`
- L208: `@RequirePermission("PARENT_ACCOUNT_LIFECYCLE_MANAGE")`
- L220: `@RequirePermission("PARENT_ACCOUNT_LIFECYCLE_MANAGE")`

### auth/web/ParentMobileManualRecoveryController.java

- L39: `@RequirePermission(PERMISSION)`
- L49: `@RequirePermission(PERMISSION)`
- L64: `@RequirePermission(PERMISSION)`

### auth/web/StudentWechatBindingManagementController.java

- L29: `@RequirePermission("STUDENT_WECHAT_UNBIND")`
- L38: `@RequirePermission("STUDENT_WECHAT_UNBIND")`

### cache/web/CacheManagementController.java

- L46: `@RequirePermission("CACHE_READ")`
- L59: `@RequirePermission("CACHE_MANAGE")`
- L75: `@RequirePermission("CACHE_MANAGE")`
- L94: `@RequirePermission("CACHE_REVIEW")`
- L107: `@RequirePermission("CACHE_REVIEW")`
- L119: `@RequirePermission("CACHE_REVIEW")`

### dictionary/web/DictionaryManagementController.java

- L42: `@RequirePermission("DICTIONARY_READ")`
- L53: `@RequirePermission("DICTIONARY_MANAGE")`
- L66: `@RequirePermission("DICTIONARY_MANAGE")`
- L79: `@RequirePermission("DICTIONARY_READ")`
- L91: `@RequirePermission("DICTIONARY_MANAGE")`
- L106: `@RequirePermission("DICTIONARY_MANAGE")`

### exceptionreport/web/ExceptionReportController.java

- L29: `@RequirePermission("EXCEPTION_REPORT_CREATE")`
- L37: `@RequirePermission("EXCEPTION_REPORT_READ")`
- L50: `@RequirePermission("EXCEPTION_REPORT_READ")`
- L57: `@RequirePermission("EXCEPTION_REPORT_CREATE")`
- L66: `@RequirePermission("EXCEPTION_REPORT_READ")`
- L73: `@RequirePermission("EXCEPTION_REPORT_HANDLE")`

### exportjob/web/ExportJobController.java

- L59: `@RequirePermission("EXPORT_JOB_READ")`
- L72: `@RequirePermission("EXPORT_JOB_READ")`
- L82: `@RequirePermission("EXPORT_JOB_READ")`
- L91: `@RequirePermission("EXPORT_JOB_READ")`

### exportjob/web/ExportJobReviewController.java

- L32: `@RequirePermission("EXPORT_SENSITIVE_REVIEW")`
- L43: `@RequirePermission("EXPORT_SENSITIVE_REVIEW")`
- L54: `@RequirePermission("EXPORT_SENSITIVE_REVIEW")`

### exportjob/web/GrowthReviewExportController.java

- L32: `@RequirePermission("EXPORT_JOB_READ")`
- L40: `@RequirePermission("EXPORT_JOB_READ")`
- L46: `@RequirePermission("EXPORT_JOB_READ")`
- L61: `@RequirePermission("EXPORT_JOB_CREATE")`

### exportjob/web/GrowthReviewExportOptionController.java

- L25: `@RequirePermission("EXPORT_JOB_CREATE")`

### feature/web/FeatureManagementController.java

- L22: `@GetMapping("/toggles") @RequirePermission("FEATURE_TOGGLE_READ")`
- L24: `@GetMapping("/changes") @RequirePermission("FEATURE_TOGGLE_READ")`
- L27: `@GetMapping("/review-queue") @RequirePermission("FEATURE_TOGGLE_REVIEW")`
- L29: `@PostMapping("/review-submissions") @ResponseStatus(HttpStatus.CREATED) @RequirePermission("FEATURE_TOGGLE_MANAGE")`
- L31: `@PostMapping("/review-queue/{taskId}/approve") @RequirePermission("FEATURE_TOGGLE_REVIEW")`
- L33: `@PostMapping("/review-queue/{taskId}/reject") @RequirePermission("FEATURE_TOGGLE_REVIEW")`

### growthpoint/web/GrowthPointCorrectionController.java

- L26: `@RequirePermission("GROWTH_POINT_CORRECT_CHILD")`

### growthpoint/web/GrowthPointQueryController.java

- L25: `@RequirePermission("GROWTH_POINT_READ_SELF")`
- L33: `@RequirePermission("GROWTH_POINT_READ_SELF")`
- L44: `@RequirePermission("GROWTH_POINT_READ_CHILD")`
- L54: `@RequirePermission("GROWTH_POINT_READ_CHILD")`
- L64: `@RequirePermission("GROWTH_POINT_READ_CHILD")`

### growthpoint/web/GrowthReviewController.java

- L29: `@RequirePermission("MINIAPP_GROWTH_REVIEW_READ_CHILD")`
- L36: `@RequirePermission("MINIAPP_GROWTH_REVIEW_READ_CHILD")`
- L46: `@RequirePermission("MINIAPP_GROWTH_REVIEW_READ_CHILD")`
- L55: `@RequirePermission("GROWTH_REVIEW_READ_SELF")`
- L67: `@RequirePermission("GROWTH_REVIEW_READ_SELF")`
- L77: `@RequirePermission("GROWTH_REVIEW_SUPPLEMENT_SELF")`
- L89: `@RequirePermission("GROWTH_REVIEW_READ_CHILD")`
- L102: `@RequirePermission("GROWTH_REVIEW_READ_CHILD")`
- L113: `@RequirePermission("GROWTH_REVIEW_SUPPLEMENT_CHILD")`

### growthpoint/web/GrowthRewardController.java

- L32: `@RequirePermission("REWARD_MANAGE_CHILD")`
- L44: `@RequirePermission("REWARD_MANAGE_CHILD")`
- L56: `@RequirePermission("REWARD_MANAGE_CHILD")`
- L67: `@RequirePermission("REWARD_MANAGE_CHILD")`
- L77: `@RequirePermission("REWARD_EXCHANGE_SELF")`
- L87: `@RequirePermission("REWARD_EXCHANGE_SELF")`

### growthpoint/web/GrowthRewardExchangeController.java

- L30: `@RequirePermission("REWARD_EXCHANGE_SELF")`
- L41: `@RequirePermission("REWARD_EXCHANGE_SELF")`
- L52: `@RequirePermission("REWARD_EXCHANGE_REVIEW_CHILD")`
- L64: `@RequirePermission("REWARD_EXCHANGE_REVIEW_CHILD")`
- L74: `@RequirePermission("REWARD_EXCHANGE_REVIEW_CHILD")`
- L85: `@RequirePermission("REWARD_EXCHANGE_REVIEW_CHILD")`

### growthpoint/web/ParentRewardController.java

- L22: `@RequirePermission(anyOf = {"MINIAPP_REWARD_MANAGE_CHILD", "MINIAPP_REWARD_EXCHANGE_REVIEW_CHILD"})`
- L30: `@RequirePermission("MINIAPP_REWARD_MANAGE_CHILD")`
- L39: `@RequirePermission("MINIAPP_REWARD_MANAGE_CHILD")`
- L48: `@RequirePermission("MINIAPP_REWARD_MANAGE_CHILD")`
- L56: `@RequirePermission("MINIAPP_REWARD_MANAGE_CHILD")`

### growthpoint/web/ParentRewardExchangeController.java

- L17: `@RequirePermission("MINIAPP_REWARD_EXCHANGE_REVIEW_CHILD")`
- L26: `@RequirePermission("MINIAPP_REWARD_EXCHANGE_REVIEW_CHILD")`
- L34: `@RequirePermission("MINIAPP_REWARD_EXCHANGE_REVIEW_CHILD")`
- L42: `@RequirePermission("MINIAPP_REWARD_EXCHANGE_REVIEW_CHILD")`

### iam/web/DataScopeManagementController.java

- L26: `@RequirePermission("IAM_DATA_SCOPE_CONFIGURE")`

### iam/web/IamChangeAuditController.java

- L25: `@RequirePermission("IAM_AUDIT_READ")`

### iam/web/OrganizationMembershipController.java

- L23: `@GetMapping("/organizations/{organizationId}/member-picker") @RequirePermission("IAM_USER_LIST")`
- L30: `@GetMapping("/roles/{roleId}/user-picker") @RequirePermission("IAM_USER_ROLE_ASSIGN")`
- L42: `@GetMapping("/roles/{roleId}/users") @RequirePermission("IAM_USER_ROLE_ASSIGN")`
- L44: `@GetMapping("/organizations/{organizationId}/member-relations") @RequirePermission("IAM_USER_LIST")`
- L47: `@GetMapping("/organizations/{organizationId}/members") @RequirePermission(anyOf={"IAM_USER_LIST","organizations.members.query"})`
- L53: `@PostMapping("/organizations/{organizationId}/members:batch") @RequirePermission("IAM_USER_ORGANIZATION_ASSIGN") @ResponseStatus(HttpStatus.NO_CONTENT)`
- L56: `@PostMapping("/organizations/{organizationId}/members:revoke") @RequirePermission("IAM_USER_ORGANIZATION_ASSIGN") @ResponseStatus(HttpStatus.NO_CONTENT)`

### iam/web/PermissionManagementController.java

- L42: `@RequirePermission("IAM_PERMISSION_READ")`
- L48: `@RequirePermission("IAM_ROLE_PERMISSION_GRANT")`
- L61: `@RequirePermission("IAM_ROLE_PERMISSION_GRANT")`
- L74: `@RequirePermission("IAM_ROLE_PERMISSION_GRANT")`
- L84: `@RequirePermission("IAM_ROLE_PERMISSION_GRANT")`
- L98: `@RequirePermission("IAM_ROLE_PERMISSION_GRANT")`
- L111: `@RequirePermission("IAM_ROLE_PERMISSION_GRANT")`
- L123: `@RequirePermission("IAM_USER_PERMISSION_CONFIGURE")`
- L130: `@RequirePermission("IAM_USER_PERMISSION_CONFIGURE")`
- L139: `@RequirePermission("IAM_USER_PERMISSION_CONFIGURE")`
- L153: `@RequirePermission("IAM_USER_PERMISSION_CONFIGURE")`
- L163: `@RequirePermission("IAM_USER_PERMISSION_CONFIGURE")`

### iam/web/RoleManagementController.java

- L52: `@RequirePermission("IAM_ROLE_READ")`
- L58: `@RequirePermission("IAM_ROLE_CREATE")`
- L68: `@RequirePermission("IAM_ROLE_UPDATE")`
- L75: `@RequirePermission("IAM_ROLE_STATUS_CHANGE")`
- L82: `@RequirePermission("IAM_ROLE_DELETE")`
- L93: `@RequirePermission("IAM_DATA_SCOPE_CONFIGURE")`
- L105: `@RequirePermission("IAM_USER_ROLE_ASSIGN")`
- L120: `@RequirePermission("IAM_USER_ROLE_ASSIGN")`
- L132: `@RequirePermission("IAM_ROLE_PERMISSION_GRANT")`
- L142: `@RequirePermission("IAM_ROLE_PERMISSION_GRANT")`

### iam/web/UserManagementController.java

- L48: `@RequirePermission("IAM_USER_READ")`
- L54: `@RequirePermission("IAM_USER_LIST")`
- L68: `@RequirePermission("IAM_USER_CREATE")`
- L78: `@RequirePermission("IAM_USER_CREATE")`
- L86: `@RequirePermission("IAM_USER_UPDATE")`
- L92: `@RequirePermission("IAM_USER_ROLE_ASSIGN")`
- L96: `@RequirePermission("IAM_USER_ROLE_ASSIGN")`
- L100: `@RequirePermission("IAM_USER_ROLE_ASSIGN")`
- L106: `@RequirePermission("IAM_USER_UPDATE")`
- L112: `@RequirePermission("IAM_USER_STATUS_CHANGE")`
- L120: `@RequirePermission("IAM_USER_ORGANIZATION_ASSIGN")`
- L132: `@RequirePermission("IAM_USER_ROLE_ASSIGN")`
- L141: `@RequirePermission("IAM_USER_PASSWORD_SET")`

### importjob/web/ImportJobController.java

- L49: `@RequirePermission("IMPORT_JOB_CREATE")`
- L67: `@RequirePermission("IMPORT_JOB_READ")`
- L85: `@RequirePermission("IMPORT_JOB_READ")`
- L91: `@RequirePermission("IMPORT_JOB_READ")`
- L99: `@RequirePermission("IMPORT_JOB_READ")`
- L111: `@RequirePermission("IMPORT_JOB_READ")`
- L119: `@RequirePermission("IMPORT_JOB_READ")`

### interfaceconfig/web/InterfaceServiceManagementController.java

- L53: `@RequirePermission("INTERFACE_SERVICE_READ")`
- L73: `@RequirePermission("INTERFACE_SERVICE_READ")`
- L83: `@RequirePermission("INTERFACE_SERVICE_READ")`
- L96: `@RequirePermission("INTERFACE_SERVICE_REVIEW")`
- L106: `@RequirePermission("INTERFACE_SERVICE_MANAGE")`
- L122: `@RequirePermission("INTERFACE_SERVICE_MANAGE")`
- L137: `@RequirePermission("INTERFACE_SERVICE_MANAGE")`
- L152: `@RequirePermission("INTERFACE_SERVICE_MANAGE")`
- L168: `@RequirePermission("INTERFACE_SERVICE_REVIEW")`
- L180: `@RequirePermission("INTERFACE_SERVICE_REVIEW")`

### learningtask/web/LearningTaskController.java

- L52: `@RequirePermission("LEARNING_TASK_CREATE")`
- L62: `@RequirePermission("LEARNING_TASK_CREATE")`
- L73: `@RequirePermission("LEARNING_TASK_CREATE")`
- L83: `@RequirePermission("LEARNING_TASK_READ_MANAGED")`
- L91: `@RequirePermission("LEARNING_TASK_READ_MANAGED")`
- L106: `@RequirePermission("LEARNING_TASK_PUBLISH")`
- L116: `@RequirePermission("LEARNING_TASK_PUBLISH")`
- L124: `@RequirePermission("LEARNING_TASK_PUBLISH")`
- L132: `@RequirePermission("LEARNING_TASK_PROGRESS_READ")`

### learningtask/web/LearningTaskOptionController.java

- L25: `@RequirePermission("LEARNING_TASK_CREATE")`
- L37: `@RequirePermission("LEARNING_TASK_CREATE")`
- L50: `@RequirePermission("TEACHER_CLASS_ASSIGN")`

### learningtask/web/LearningTaskTemplateController.java

- L32: `@RequirePermission("LEARNING_TASK_TEMPLATE_READ")`
- L42: `@RequirePermission("LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL")`
- L53: `@RequirePermission("LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL")`
- L64: `@RequirePermission("LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL")`
- L75: `@RequirePermission("LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL")`

### learningtask/web/ManagedTaskAssignmentController.java

- L32: `@RequirePermission("TASK_ASSIGNMENT_EXEMPT")`
- L43: `@RequirePermission("TASK_ASSIGNMENT_DEFER")`
- L54: `@RequirePermission("TASK_ASSIGNMENT_DEFER")`

### learningtask/web/PreviousDayTaskCopyController.java

- L24: `@RequirePermission("LEARNING_TASK_COPY_PREVIOUS_DAY")`
- L34: `@RequirePermission("LEARNING_TASK_COPY_PREVIOUS_DAY")`
- L45: `@RequirePermission("LEARNING_TASK_COPY_PREVIOUS_DAY")`

### learningtask/web/StudentTaskAssignmentController.java

- L35: `@RequirePermission("TASK_ASSIGNMENT_READ_SELF")`
- L48: `@RequirePermission("TASK_ASSIGNMENT_READ_SELF")`
- L56: `@RequirePermission("TASK_ASSIGNMENT_EXECUTE_SELF")`
- L64: `@RequirePermission("TASK_ASSIGNMENT_EXECUTE_SELF")`
- L75: `@RequirePermission("TASK_ASSIGNMENT_EXECUTE_SELF")`
- L83: `@RequirePermission("TASK_ASSIGNMENT_EXECUTE_SELF")`
- L95: `@RequirePermission("TASK_ASSIGNMENT_EXECUTE_SELF")`

### learningtask/web/TaskReviewController.java

- L28: `@RequirePermission("TASK_ASSIGNMENT_REVIEW")`
- L38: `@RequirePermission("TASK_ASSIGNMENT_REVIEW")`
- L47: `@RequirePermission("TASK_ASSIGNMENT_REVIEW")`
- L58: `@RequirePermission("TASK_ASSIGNMENT_REVIEW")`
- L68: `@RequirePermission("TASK_ASSIGNMENT_REVIEW")`
- L79: `@RequirePermission("TASK_ASSIGNMENT_REVIEW")`

### learningtask/web/TeacherClassController.java

- L28: `@RequirePermission("TEACHER_CLASS_ASSIGN")`
- L39: `@RequirePermission("TEACHER_CLASS_ASSIGN")`
- L50: `@RequirePermission("LEARNING_TASK_CREATE")`

### menu/web/MenuController.java

- L15: `@GetMapping("/iam/menus") @RequirePermission("MENU_READ")`
- L19: `@PostMapping("/iam/menus") @RequirePermission("MENU_MANAGE") @ResponseStatus(HttpStatus.CREATED)`
- L21: `@PutMapping("/iam/menus/order") @RequirePermission("MENU_MANAGE")`
- L23: `@PutMapping("/iam/menus/{id}") @RequirePermission("MENU_MANAGE")`
- L25: `@PostMapping("/iam/menus/{id}/buttons:batch") @RequirePermission("MENU_MANAGE") @ResponseStatus(HttpStatus.CREATED)`
- L27: `@PutMapping("/iam/menus/buttons:batch") @RequirePermission("MENU_MANAGE")`
- L29: `@PutMapping("/iam/menus/{id}/position") @RequirePermission("MENU_MANAGE")`
- L31: `@DeleteMapping("/iam/menus/{id}") @RequirePermission("MENU_MANAGE") @ResponseStatus(HttpStatus.NO_CONTENT)`

### organization/web/ClassManagementController.java

- L36: `@RequirePermission("CLASS_READ")`
- L46: `@RequirePermission("CLASS_READ")`
- L59: `@RequirePermission("CLASS_CREATE")`
- L71: `@RequirePermission("CLASS_UPDATE")`
- L83: `@RequirePermission("CLASS_STATUS_CHANGE")`
- L94: `@RequirePermission("CLASS_STATUS_CHANGE")`
- L112: `@RequirePermission("CLASS_READ") @GetMapping("/grades")`
- L117: `@RequirePermission("CLASS_CREATE") @PostMapping("/grades") @ResponseStatus(HttpStatus.CREATED)`
- L121: `@RequirePermission("CLASS_UPDATE") @PutMapping("/grades/{gradeId}")`
- L125: `@RequirePermission("CLASS_STATUS_CHANGE") @PostMapping("/grades/{gradeId}/disable")`
- L129: `@RequirePermission("CLASS_STATUS_CHANGE") @PostMapping("/grades/{gradeId}/enable")`
- L133: `@RequirePermission("CLASS_UPDATE") @PostMapping("/{classId}/assign-grade")`
- L138: `@RequirePermission("CLASS_READ") @GetMapping("/campuses")`
- L140: `@RequirePermission("CLASS_CREATE") @PostMapping("/campuses") @ResponseStatus(HttpStatus.CREATED)`
- L142: `@RequirePermission("CLASS_UPDATE") @PutMapping("/campuses/{campusId}")`
- L144: `@RequirePermission("CLASS_STATUS_CHANGE") @PostMapping("/campuses/{campusId}/disable")`
- L146: `@RequirePermission("CLASS_STATUS_CHANGE") @PostMapping("/campuses/{campusId}/enable")`

### organization/web/OrganizationChangeController.java

- L39: `@RequirePermission("ORG_NODE_CHANGE_SUBMIT")`
- L52: `@RequirePermission(anyOf = {"ORG_NODE_CHANGE_SUBMIT", "ORG_NODE_CHANGE_REVIEW"})`
- L69: `@RequirePermission("ORG_NODE_CHANGE_REVIEW")`
- L81: `@RequirePermission("ORG_NODE_CHANGE_REVIEW")`

### organization/web/OrganizationManagementController.java

- L53: `@RequirePermission("ORG_TYPE_READ")`
- L63: `@RequirePermission("ORG_TYPE_CREATE")`
- L75: `@RequirePermission("ORG_TYPE_UPDATE")`
- L82: `@RequirePermission("ORG_TYPE_STATUS_CHANGE")`
- L89: `@RequirePermission("ORG_TYPE_DELETE")`
- L100: `@RequirePermission("ORG_NODE_READ")`
- L108: `@RequirePermission("ORG_NODE_CREATE")`
- L121: `@RequirePermission("ORG_NODE_UPDATE")`
- L136: `@RequirePermission("ORG_NODE_UPDATE")`
- L149: `@RequirePermission("ORG_NODE_UPDATE")`
- L166: `@RequirePermission("ORG_NODE_UPDATE")`

### student/web/ParentBindingInvitationController.java

- L29: `@RequirePermission("STUDENT_PARENT_INVITE_RESPOND")`
- L42: `@RequirePermission("STUDENT_PARENT_INVITE_RESPOND")`

### student/web/ParentRelationshipController.java

- L41: `@RequirePermission("PARENT_RELATIONSHIP_READ")`
- L51: `@RequirePermission("PARENT_RELATIONSHIP_READ")`
- L62: `@RequirePermission("SECONDARY_PARENT_INVITE_CREATE")`
- L75: `@RequirePermission("SECONDARY_PARENT_UNBIND")`
- L85: `@RequirePermission("PRIMARY_PARENT_SELF_UNBIND")`
- L96: `@RequirePermission("PRIMARY_PARENT_TRANSFER_CREATE")`

### student/web/StudentAccountCancellationController.java

- L34: `@RequirePermission(PERMISSION)`
- L44: `@RequirePermission(PERMISSION)`

### student/web/StudentClassQueryController.java

- L17: `@GetMapping("/class-assignment-options") @RequirePermission("STUDENT_CLASS_ASSIGN")`
- L21: `@GetMapping("/class-members") @RequirePermission("STUDENT_READ")`

### student/web/StudentManagementController.java

- L56: `@RequirePermission("STUDENT_READ")`
- L69: `@RequirePermission("STUDENT_READ")`
- L77: `@RequirePermission("STUDENT_CREATE")`
- L88: `@RequirePermission("STUDENT_CLASS_ASSIGN")`
- L103: `@RequirePermission("STUDENT_ORGANIZATION_MANAGE")`
- L113: `@RequirePermission("STUDENT_ORGANIZATION_MANAGE")`
- L123: `@RequirePermission("STUDENT_ORGANIZATION_MANAGE")`
- L133: `@RequirePermission("STUDENT_ORGANIZATION_MANAGE")`
- L146: `@RequirePermission("STUDENT_ORGANIZATION_MANAGE")`
- L160: `@RequirePermission("STUDENT_ORGANIZATION_MANAGE")`
- L167: `@RequirePermission("STUDENT_CREDENTIAL_INITIALIZE")`
- L177: `@RequirePermission("STUDENT_LOGIN_CODE_RESET")`
- L187: `@RequirePermission("STUDENT_LOGIN_QR_CREATE")`
- L197: `@RequirePermission("STUDENT_PARENT_INVITE_CREATE")`

### studentimport/web/StudentImportController.java

- L48: `@RequirePermission("STUDENT_IMPORT_EXECUTE")`
- L59: `@RequirePermission("STUDENT_IMPORT_RESULT_READ")`
- L71: `@RequirePermission("STUDENT_IMPORT_RESULT_READ")`
- L80: `@RequirePermission("STUDENT_IMPORT_RESULT_READ")`
- L93: `@RequirePermission("STUDENT_IMPORT_EXECUTE")`
- L103: `@RequirePermission("STUDENT_IMPORT_CREDENTIAL_DOWNLOAD")`

### teacher/web/TeacherManagementController.java

- L40: `@RequirePermission("TEACHER_READ")`
- L55: `@RequirePermission("TEACHER_READ")`
- L64: `@RequirePermission("TEACHER_CREATE")`
- L76: `@RequirePermission("TEACHER_UPDATE")`
- L89: `@RequirePermission("TEACHER_STATUS_CHANGE")`
- L100: `@RequirePermission("TEACHER_PASSWORD_RESET")`
- L111: `@RequirePermission("TEACHER_BATCH_MANAGE")`

### templateconfig/web/ImportExportTemplateManagementController.java

- L63: `@RequirePermission("IMPORT_EXPORT_TEMPLATE_READ")`
- L73: `@RequirePermission("IMPORT_EXPORT_TEMPLATE_READ")`
- L88: `@RequirePermission("IMPORT_EXPORT_TEMPLATE_MANAGE")`
- L125: `@RequirePermission("IMPORT_EXPORT_TEMPLATE_READ")`
- L137: `@RequirePermission("IMPORT_EXPORT_TEMPLATE_MANAGE")`
- L163: `@RequirePermission("IMPORT_EXPORT_TEMPLATE_MANAGE")`
- L175: `@RequirePermission("IMPORT_EXPORT_TEMPLATE_MANAGE")`
- L187: `@RequirePermission("IMPORT_EXPORT_TEMPLATE_MANAGE")`
- L199: `@RequirePermission("IMPORT_EXPORT_TEMPLATE_READ")`


## 7. 复核补充：关闭功能时详情直达仍有例外

student/application/ParentRelationshipApplicationService.java:115-140 的 getRelationships 只验证当前家长存在活动关系，没有 PARENT_RELATIONSHIP_MANAGEMENT 功能检查；:143-145 的学生选择列表有该检查。控制器详情仅检查 PARENT_RELATIONSHIP_READ，公共权限决策也不包含功能条件。因此关闭功能后列表被阻挡但直接请求已知 studentId 的关系详情仍可读取。若产品意图关闭整个家长关系功能，详情必须加同一开关；若是允许只读历史关系，则应明确作为例外并在有效能力契约中标明。
