# 六角色与 Web / uni-app 功能覆盖矩阵（R04 核对）

日期：2026-09-10（基线），2026-09-11 继续实施。状态：R04 尚未完成，下表保留初次核对缺口，实施变化见下节。

## 本次实施变化

- 2026-09-13 Web 新增 `/system-tasks` 系统任务工作台，管理员本人结果、审核员已提交任务与历史、分页和审计详情、按权限进入原领域处理页；V72 独立读取权限。四个已落地领域受各自开关与动态权限过滤，浏览器动态授权跳转和撤权清空验证通过。见 `2026-09-13-lingdong-system-task-workbench.md`；系统角色小程序及 R07 完整业务载荷仍缺。

- 2026-09-13 机构异常待处理摘要接入服务端 total，原异常页完成状态过滤、分页和处理后刷新，混合系统审核员显式拒绝；本地类型/构建/浏览器及后端全量通过，证据见 `2026-09-13-lingdong-exception-workbench.md`。

- 2026-09-13 家长小程序奖励配置、兑换审批/驳回/核销已通过本地闭环；V71 独立权限和接口，副家长只读，混合系统审核员拒绝。原“家长兑换待办仍缺”属于初始审计，现以 `2026-09-13-lingdong-parent-rewards-miniapp.md` 及实际验证为准，仍需真机验收。

- 2026-09-13 家长小程序独立家庭任务创建、编辑、发布闭环通过本地验证，保留 Web 草稿配置，后端明确排除混合系统审核员。证据见 `2026-09-12-lingdong-parent-family-tasks.md`；下方初始审计描述保留历史语境，家长奖励兑换等缺口仍待处理。

- 2026-09-12：学生首页复用本人日期查询显示今日任务总数、前三项来源和状态，跳入既有执行详情；家庭学生不要求机构。类型检查、H5/微信构建和 `verify-student-today.cjs` 通过，详情见 `2026-09-12-lingdong-student-workbench.md`。教师/机构首页真实待审入口及审核分页已实施，浏览器闭环正在验证。原矩阵保留作初始缺口对照。

- Web 工作台已为有任务审核权限的家长、教师、机构管理员复用实际队列与审核详情；窗口恢复时复核当前会话、权限与功能，审核员优先排除。2026-09-10 Web 全量 42 文件 176 项通过，日志 `.local-verification/remaining-r04-web-tests.log`；合成浏览器完成审核通过及权限撤回后清空验证。新增审核详情切换任务失败/旧响应覆盖回归正继续验证。
- 服务端 `TaskReviewService.requireReviewer` 已新增混合 `SYS_AUDITOR` 角色优先拒绝，专项从失败转为通过并完成代码复审；原表中的混合角色缺口因此已修复。
- 小程序家长待审核列表和操作正在实施。新增 H2 合成 MINIAPP 会话 HTTP 契约测试已提交验证，不代表实际短信或微信登录验收。其余六角色入口缺口仍保持未完成，不以待审核任务充当全部业务待办。

依据：根目录《灵动学习-业务需求说明书-V1.0.md》第 3、4、5 章；`docs/design/02-Web与小程序交互说明-V1.0.md` 第 3、4 节及后续版本补充；`docs/superpowers/plans/2026-09-09-lingdong-remaining-development.md` R04。本次只核对本地源码与已有测试源码，未运行 Maven、未连接远程、未作真实双端验收。以下“已有”不代表已重新验证通过；表中按既有操作族追溯，不按页面数量评分，也不扩充统计指标。

## 六角色操作与端覆盖

| 内置角色、BRD 操作 | Web 实际入口 | uni-app 实际入口 | API / 数据范围及可复用查询 | 测试证据与缺口 |
|---|---|---|---|---|
| 系统管理员：组织、用户、角色权限、通用配置、提交系统任务、审计 | `web/src/app/App.tsx` 注册 `/organizations`、`/users`、`/iam`、字典、缓存、接口服务、附件、模板、导入和导出等；工作台为公共 Dashboard | `pages.json` 未注册系统管理员独立登录/工作台；设计 4.1 规定的本人系统任务结果、公告摘要尚无入口 | 现有 `web/src/api/organization.ts`、`iam.ts`、`cache-management.ts`、`interface-services.ts` 等分领域 API；范围不能因系统管理身份绕开既有授权/审计 | `SystemTaskApplicationServiceTest`、组织/IAM 与配置模块专项测试可复用；未见通用系统任务完整 Web 页面与统一待办查询接入，通用开关等仍属 R07，不能由配置页面数量认定完成 |
| 系统审核员：仅审核系统管理员高风险系统任务、审批历史 | 组织变更审核、缓存审核、接口变更审核、敏感导出审核分散在各模块；公共 Dashboard 无待审聚合 | 设计 4.1 的待审、单笔通过/驳回及历史摘要无专属页面/会话 | 复用各模块已有待审核查询，严格沿用申请人和审核人边界；不得调用学习任务/积分/奖励业务审核充当系统待办 | 既有 `SystemTaskApplicationServiceTest`、`ExportJobReviewServiceTest` 等；缺六角色组合、工作台入口与小程序系统审核闭环验证 |
| 机构管理员：班级、教师、学员关系、机构任务、进度、考勤、异常处理、报表 | `/organizations`、`/teachers`、`/learning-tasks`、`/attendance-records`、`/exception-reports`、导入导出相关入口 | `organization-home` → 班级、教师、学员关系、机构任务、考勤、异常报备、家长换号核验、学生注销；单笔为主 | `/organization-workbench/context` 返回直接管理组织与实时权限；`/learning-tasks`、`/task-reviews`、异常/考勤 API；业务查询按授权组织裁剪，不能把首页直接管理组织列表当全部业务范围 | `OrganizationWorkbenchControllerTest`、`LearningTaskControllerTest`、组织数据范围和各业务测试；首页只有组织与按钮，缺真实待审核/异常待处理摘要；复杂报表按 R06 核对 |
| 教师：本班任务创建/下发、学生进度、审核、异常报备、考勤、班级复盘 | `/learning-tasks`、`/attendance-records`、`/exception-reports` 等按能力和权限进入 | `teacher-home` → `managed-tasks?identity=teacher`、异常报备、考勤；首页显示有效班级 | `/teacher-workbench/context`；`/learning-tasks/{id}/progress`；`/task-reviews`；班级来自服务端有效绑定，家庭私有明细不可扩入 | `TeacherWorkbenchQueryServiceTest`、`LearningTaskControllerTest`、`TeacherReviewAutoTransferServiceTest`；首页缺待审核摘要；班级复盘/复杂统计是否满足全部 BRD 仍需 R06 逐口径核对 |
| 家长：学生切换/创建绑定、家庭任务、打卡审核、积分、奖励配置/兑换审批、复盘、账号 | `/parent-relationships`、`/student-login`、`/learning-tasks`、`/growth-points`、`/rewards`、`/growth-reviews`，工作台有账号生命周期 | `parent-home` 目前只有家长关系、账号、学生微信、考勤及排行相关入口，固定“暂无待办事项”；无家庭任务/奖励审批/复盘主入口 | 任务审核 `/task-reviews` 可直接复用；亲子关系而非组织树决定家庭范围，主/副家长边界沿用现有服务；奖励家长查询当前存在 WEB 限制，不能直接给小程序接入 | `LearningTaskControllerTest` 有家长待审与他人 404；`LearningTaskScopeServiceSecondaryParentTest`、`GrowthRewardExchangeServiceSecondaryParentTest`；缺小程序家长真实待办状态、操作入口和专门 MINIAPP 契约测试 |
| 学生：认领/执行/暂停/搁置/打卡、积分、兑换申请、个人复盘/考勤 | 设计明确不提供学生独立 Web 管理入口；不应作为缺页补建 | `student-home` → `task-list`/`task-detail`、`growth-points`、`rewards`、`growth-reviews`、`attendance` | `miniapp/src/api/learning-task.ts`、`growth-point.ts`、`reward.ts`、`growth-review.ts` 使用本人学生会话；任务来源合并但维持来源标记；无机构家庭学生应继续可用 | 学生认证、`LearningTaskControllerTest`、`RewardExchangeControllerTest`、成长复盘测试可复用；首页为功能按钮，未直接呈现今日待执行摘要；独立家庭跨端主流程仍需验收 |

## 家长首页可实施的最小闭环

1. 直接复用 `GET /api/v1/task-reviews?page=1&pageSize=20`，返回 `items/page/pageSize/total`。调用要求 `TASK_ASSIGNMENT_REVIEW`、`LEARNING_TASK_MANAGEMENT` 功能开启及 PARENT/TEACHER/ORG_ADMIN 业务角色。`TaskReviewService.requireReviewer` 本身没有 WEB 客户端限制；仍须补 MINIAPP 家长契约测试，不能以这一静态事实代替测试。
2. 已有 `miniapp/src/api/managed-learning-task.ts:listManagedTaskReviews(accessToken)` 使用显式令牌请求同接口，但固定取前 100 条。首页只需展示服务端 `total` 与首屏待审核，不能用 `items.length` 冒充总数。建议独立家长 API 包装或提取中性审核查询，显式传入家长令牌，避免误读组织/学生会话。
3. `server/src/main/resources/mapper/learningtask/TaskReviewMapper.xml` 的 `findPage/count` 按 `assignment.current_reviewer_id = 当前用户`、`PENDING_REVIEW`、最新打卡 `SUBMITTED` 过滤，按提交时间升序；无需前端枚举全部孩子或传任意组织 ID。无学校的家庭任务同样可列出。
4. 待办准确命名为“待审核任务”，不要把它称作覆盖兑换、亲子关系等全部业务的总待办。只有成功返回零条才显示空态；分别支持加载、失败重试、分页、刷新后旧响应丢弃、会话变化清空。能力关闭/权限撤回隐藏入口并阻断直达。
5. `managed-tasks` 是教师/机构任务管理页，依赖其角色上下文，不能把家长直接跳入此页。家长需要独立待审核页或首页单笔详情/通过/驳回闭环，复用 `/task-reviews/{assignmentId}`、`approve`、`reject`，保留失败输入、提交中与审核后刷新。
6. `GrowthRewardExchangeService` 的家长访问明确要求 WEB；miniapp `reward.ts` 当前为学生本人接口。小程序家长兑换待办属于后续适配，不能绕过这一限制或将学生接口换令牌当作家长接口。

## 工作台接入顺序与剩余验收

- Web `DashboardPage.tsx` 当前六角色共用身份、安全事件、设备会话及可选考勤按钮；家长额外账号生命周期。可为有业务审核权限的家长/教师/机构管理员接入同一任务审核查询，并跳至已有学习任务审核区域。系统审核员必须单独走系统审核领域。
- 教师与机构首页可复用 `listManagedTaskReviews` 展示本人待审核；机构异常待处理应沿用现有异常查询的状态和范围，不自行定义“风险率”等指标。学生首页复用本人任务查询即可，不引入新统计公式。
- Web `App.tsx` 首次载入身份和能力；仅靠首次菜单判断不足以证明角色动态变更后旧数据消失。新增工作台应在进入/恢复与操作前重新确认权限，测试返回乱序、角色撤回、能力关闭与组织停用。
- `TaskReviewMapper` 当前直接按指定审核人过滤，没有在此 SQL 再联表检查活动亲子/班级关系；关系转移依赖生命周期转交/失效处理。需要联合现有主家长变更、教师失效转交与组织停用测试验证，不应在文档中宣称所有范围撤回已覆盖。
- `TaskReviewService` 角色判断为角色集合交集，没有显式 SYS_AUDITOR 混合角色排除。依据 BRD 审核员不得参与业务审核，需要核对实时权限解析的显式拒绝与混合角色测试，不能只验证纯审核员缺权限。
- 已有 `web/src/features/dashboard/DashboardPage.test.tsx` 主要覆盖当前账号安全面板；miniapp 有考勤/排行验证脚本，但本次检索未见家长工作台自动化测试。新增待办应覆盖加载、失败重试、真空态、非零数据、首屏与 total 差异、切身份清空、主/副家长、无机构学生及越权拒绝。

本矩阵只创建 R04 核对文档；未实现上述待办和缺失入口，未修改 PROJECT_STATUS，未提交 Git。所有其他历史工作区改动保留。
