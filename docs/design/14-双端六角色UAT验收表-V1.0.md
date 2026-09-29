# 灵动伴随 双端六角色 UAT 验收表（V1.0）

> 任务 7.3 交付物。逐条关联 BRD 功能拆解（§3.1.2-§3.6.2 共 72 条）与页面、接口、自动化测试及验收结果。
> 验收结果口径：**自动化通过** = 对应后端/Web 自动化测试全绿（见"测试"列与 §5 证据索引）；**真机待验** = 逻辑已由自动化覆盖、真实设备/通道验证待外部条件；**外部受阻** = 依赖外部环境或用户已确认暂缓；**范围外** = 用户确认保持关闭（任务 5.4）。
> 功能与入口矩阵口径详见《13-角色操作入口矩阵-V1.0.md》；本表不覆盖其结论，仅按 UAT 视角汇总。

## 1. 组织管理与权限管理（BRD §3.1.2，12 条）

| # | 功能 | 页面入口 | 接口 | 测试 | 验收结果 |
|---|---|---|---|---|---|
| 1 | 用户管理 | Web#15、机构工作台 | UserManagementController | 安全专项+全量 | 自动化通过 |
| 2 | 角色管理（内置六角色） | Web#16 | RoleManagementController | 全量 | 自动化通过 |
| 3 | 自定义角色管理 | Web#16 | RoleManagementController | 全量 | 自动化通过 |
| 4 | 角色权限管理 | Web#16 | PermissionManagementController | RoleMatrixAccessIntegrationTest 等 | 自动化通过 |
| 5 | 用户权限补充/限制 | Web#16 | PermissionManagementController 等 | 安全专项 | 自动化通过 |
| 6 | 数据权限管理 | Web#16 | DataScopeManagementController | 全量 | 自动化通过 |
| 7 | 区域/学校组织树（高风险审批） | Web#24 | OrganizationChangeController | 全量+审批专项 | 自动化通过 |
| 8 | 组织类型管理 | Web#24 | OrganizationManagementController | 全量 | 自动化通过 |
| 9 | 组织管理员配置 | Web#24/#15 | OrganizationManagementController 等 | 组织范围动态验证 | 自动化通过 |
| 10 | 班级管理 | Web#24、小程序班级管理 | ClassManagementController | 全量 | 自动化通过 |
| 11 | 权限范围约束（最小权限） | 服务端校验 | 各授权 Controller | 授权范围越界拒绝测试 | 自动化通过 |
| 12 | 权限变更日志 | Web#16 IamAuditPanel | IamChangeAuditController | IAM_CHANGE_AUDIT 导出专项 | 自动化通过 |

## 2. 系统配置、系统审核与功能启停（BRD §3.2.2，12 条）

| # | 功能 | 页面入口 | 接口 | 测试 | 验收结果 |
|---|---|---|---|---|---|
| 1 | 系统任务提交 | Web#2 | 各提交侧 Controller | 全量 | 自动化通过 |
| 2 | 系统任务审批 | Web#2 | SystemTaskQueryController 等 | 审核员专项+普通角色拒绝 | 自动化通过 |
| 3 | 审批意见记录 | Web#2 | 审批侧 Controller | 全量 | 自动化通过 |
| 4 | 附件管理（统一） | Web#20 | AttachmentManagementController 等 | 附件安全 8 项+生命周期 31 项 | 自动化通过 |
| 5 | 缓存管理（高风险审批） | Web#18 | CacheManagementController | 缓存边界测试+导出 | 自动化通过 |
| 6 | 数据字典管理 | Web#17 | DictionaryManagementController | 字典专项 | 自动化通过 |
| 7 | 导入导出模板配置 | Web#21 | ImportExportTemplateManagementController | 模板专项+下载权限 | 自动化通过 |
| 8 | 接口服务管理（变更审批） | Web#19 | InterfaceServiceManagementController | 接口服务专项+执行记录 | 自动化通过 |
| 9 | 功能开关配置 | Web#3 | FeatureManagementController | 功能管理专项 | 自动化通过 |
| 10 | 功能停用双拒绝（入口+API） | 两端能力位 | PublicCapabilityController 等 | featureDisabledHidesEntryAndRejectsApi | 自动化通过 |
| 11 | 停用后历史数据可查可导禁新增 | Web#23 导出中心 | ExportJobController | 导出边界测试 | 自动化通过 |
| 12 | 开关变更日志 | Web#3 | FeatureManagementController | 变更留痕测试 | 自动化通过 |

## 3. 账号登录与学生账号管理（BRD §3.3.2，10 条）

| # | 功能 | 页面入口 | 接口 | 测试 | 验收结果 |
|---|---|---|---|---|---|
| 1 | 多角色登录 | Web#25、小程序登录页 | AuthenticationController 等 | 认证专项+真实环境 401 语义 | 自动化通过（逻辑）；真机待验（端上） |
| 2 | 家长注册登录（验证码/密码/微信） | 小程序 parent-login | ParentAuthenticationController | 家长认证专项 | 密码逻辑通过；短信/微信通道外部受阻 |
| 3 | 学生登录（登录码/扫码/微信） | 小程序 student-login、Web#12 | StudentAuthenticationController | 学生认证专项 | 登录码/扫码逻辑通过；微信通道外部受阻 |
| 4 | 微信绑定（一对一） | Web#12、小程序 | StudentWechatBindingManagementController | 绑定专项 | 逻辑通过；微信真实回调外部受阻 |
| 5 | 家长创建学生 | 小程序 parent-onboarding | StudentManagementController | 全量 | 自动化通过 |
| 6 | 机构创建学生（含批量导入） | Web#24/#22 | StudentImportController 等 | 2.4 导入验收（格式/逐行/越权/事务） | 自动化通过 |
| 7 | 副家长绑定 | Web#13、小程序 | ParentRelationshipController | 亲子关系安全专项 | 自动化通过 |
| 8 | 登录码重置 | Web#12、小程序 | StudentManagementController | 全量 | 自动化通过 |
| 9 | 账号注销 | 小程序/Web#24 | StudentAccountCancellationController | 生命周期专项 | 自动化通过 |
| 10 | 设备管理 | 小程序 account-security | AuthenticationController | 设备会话专项（V39 验收） | 自动化通过 |

## 4. 学习任务管理（BRD §3.4.2，14 条）

| # | 功能 | 页面入口 | 接口 | 测试 | 验收结果 |
|---|---|---|---|---|---|
| 1 | 家庭任务创建 | Web#5、family-tasks | ManagedTaskAssignmentController | 全量 | 自动化通过 |
| 2 | 机构任务创建下发 | Web#5、机构任务 | ManagedTaskAssignmentController | 全量+2.3 口径核对 | 自动化通过 |
| 3 | 教师任务创建下发 | 小程序/Web#5 | LearningTaskController、TeacherClassController | 全量 | 自动化通过 |
| 4 | 任务来源标识与筛选 | 小程序 task-list | LearningTaskController | 2.3 来源班级口径 | 自动化通过 |
| 5 | 任务认领 | 小程序 task-detail | StudentTaskAssignmentController | 全量 | 自动化通过 |
| 6 | 任务打卡（附件） | 小程序 task-detail | TaskReviewController、TaskAttachmentController | 附件安全+打卡专项 | 自动化通过 |
| 7 | 任务审核（三角色） | Web#5、parent-task-reviews | TaskReviewController | 混合审核员专项 | 自动化通过 |
| 8 | 情绪暂停 | 小程序 task-detail | StudentTaskAssignmentController | 全量 | 自动化通过 |
| 9 | 难题搁置 | 小程序 task-detail | StudentTaskAssignmentController | 全量 | 自动化通过 |
| 10 | 任务放弃 | 小程序 task-detail | StudentTaskAssignmentController | 全量 | 自动化通过 |
| 11 | 任务顺延 | Web#5 TaskDeferQueue | StudentTaskAssignmentController | 顺延专项 | 自动化通过 |
| 12 | 免执行 | Web#5 | StudentTaskAssignmentController | 免执行专项 | 自动化通过 |
| 13 | 批量复制（每日一次） | Web#5 PreviousDayTaskCopy | PreviousDayTaskCopyController | 幂等专项 | 自动化通过 |
| 14 | 任务模板 | Web#5 模板库 | LearningTaskTemplateController | 模板专项 | 自动化通过 |

## 5. 积分奖励与成长复盘（BRD §3.5.2，13 条）

| # | 功能 | 页面入口 | 接口 | 测试 | 验收结果 |
|---|---|---|---|---|---|
| 1 | 积分获取 | 服务端 | GrowthRewardController | 全量 | 自动化通过 |
| 2 | 积分审核 | Web#5、小程序待审核 | TaskReviewController | 净积分/最新审核状态专项（2.1） | 自动化通过 |
| 3 | 积分台账 | Web#7、growth-points | GrowthPointQueryController | 台账导出专项 | 自动化通过 |
| 4 | 积分衰减（≤40%） | 服务端 | GrowthPointCorrectionController | GrowthPointDecayIntegrationTest | 自动化通过 |
| 5 | 休眠清零（30 天+提醒） | 服务端 | GrowthPointCorrectionController | 休眠专项 | 自动化通过 |
| 6 | 奖励库 | Web#8、parent-rewards | ParentRewardController | 家长奖励专项（r04） | 自动化通过 |
| 7 | 兑换申请 | 小程序 rewards | GrowthRewardExchangeController | 兑换边界专项 | 自动化通过 |
| 8 | 兑换审批 | Web#8、parent-rewards | ParentRewardExchangeController | 兑换审批专项 | 自动化通过 |
| 9 | 奖励核销 | Web#8、parent-rewards | ParentRewardExchangeController | 核销专项 | 自动化通过 |
| 10 | 每日复盘 | Web#9、growth-reviews | GrowthReviewController | 复盘专项 | 自动化通过 |
| 11 | 周/月报表 | 小程序 parent-weekly、Web#9 | GrowthReviewController | 周报专项（r05） | 自动化通过；微信订阅提醒外部受阻 |
| 12 | PDF 导出（开关隐藏入口） | Web#9 | GrowthReviewExportController、ExportJobController | 真实 PDF 内容核对 | 自动化通过 |
| 13 | 匿名排行 | Web#10/#11、anonymous-ranks | AnonymousRankController | 匿名排行双端专项（V69-V72） | 自动化通过 |

## 6. 机构、教师、考勤与家校协同（BRD §3.6.2，11 条）

| # | 功能 | 页面入口 | 接口 | 测试 | 验收结果 |
|---|---|---|---|---|---|
| 1 | 学校/机构管理 | Web#24、机构工作台 | OrganizationWorkbenchController 等 | 全量 | 自动化通过 |
| 2 | 班级管理 | Web#24、小程序 | ClassManagementController | 全量 | 自动化通过 |
| 3 | 教师管理 | Web#14、小程序 | TeacherManagementController | 全量 | 自动化通过 |
| 4 | 学员管理（导入/邀请家长） | Web#22/#24 | StudentImportController、ParentBindingInvitationController | 2.4+邀请专项 | 自动化通过 |
| 5 | 机构任务 | Web#5、机构任务 | ManagedTaskAssignmentController | 2.1 机构统计专项 | 自动化通过 |
| 6 | 教师任务 | 小程序/Web#5 | LearningTaskController | 全量 | 自动化通过 |
| 7 | 地理考勤（默认停用，启用走审批） | Web#3/#2/#4 | AttendanceController | 围栏/时段校验测试 | 逻辑通过；真实启用+定位采集范围外（默认停用，开启需高风险审批链） |
| 8 | 轨迹记录 | — | — | — | **范围外**：2026-09-29 用户确认保持关闭，从剩余范围移除（任务 5.4），构建不包含该能力 |
| 9 | 异常报备 | Web#6、小程序 | ExceptionReportController | 异常报备专项（r04 系列）+台账导出 | 自动化通过 |
| 10 | 家校关联（邀请/确认） | Web#24、小程序 | ParentBindingInvitationController 等 | 邀请专项 | 自动化通过 |
| 11 | 人工考勤（登记+关系只读） | Web#4、attendance | AttendanceController | 2.2 考勤台账专项 | 自动化通过 |

## 7. 未完成/外部受阻项汇总（不虚构）

1. **短信验证码真实送达**：通道未配置（用户确认暂缓，任务 5.2）；验证码签发/校验/限流/哈希逻辑已由自动化覆盖。
2. **微信登录/绑定/周报订阅真机验证**：待真机环境与真实回调（用户确认暂缓，任务 5.3 归 7.2）；逻辑与幂等已由自动化覆盖，端上真机 UAT 待执行。
3. **双端六角色人工 UAT 签字**：自动化已覆盖全部 72 条的功能逻辑、权限边界与数据范围；真实用户按角色走查并签署结果需业务方组织（外部流程）。
4. **地理考勤真实启用**：默认停用；围栏/时段校验逻辑已测试，真实启用属运营决策+高风险审批链。
5. **轨迹记录**：用户确认保持关闭，构建不包含，不在 UAT 范围。

## 8. 证据索引

- 后端全量 839 项 0 失败/错误/跳过 + JAR：`.local-verification/ci-backend-package.log`（2026-09-29 CI）
- Web 全量 247 项 + 类型检查 + 构建：`ci-web-tests.log`、`ci-web-typecheck.log`、`ci-web-build.log`
- 小程序类型检查 + H5/微信双构建：`ci-miniapp-*.log`
- 角色矩阵动态验证 5 用例：`r12-role-matrix-green.log`
- 附件安全 8 项：`r15-attachment-security-tests.log`；生命周期回归 31 项：`r15-attachment-regression.log`
- 安全专项 138 项：`r64-security-suite.log`
- 真实环境健康探测与认证语义：`r72-real-env-app.log`
- 学员批量导入验收：任务 2.4 专项日志；考勤台账：任务 2.2；机构统计：任务 2.1（见 docs/superpowers/specs/2026-09-20-lingdong-report-dataset-matrix.md）
