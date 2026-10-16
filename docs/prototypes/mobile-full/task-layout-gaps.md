# 任务布局数据与验证缺口（2026-10-13）

范围：task-list、task-detail、family-tasks。仅记录已读客户端契约，不断言服务端不存在其他接口。

| 原图需求 | 当前真实契约 | 需要补充或确认的契约 |
|---|---|---|
| 02-02 列表“进行中 / 待审核 / 已完成”筛选 | FamilyTask.status 仅 DRAFT/PUBLISHED，list 仅 sourceType/page/pageSize | 每个任务的 assignment 状态汇总以及服务端状态过滤、对应总数；发布状态不能代替孩子执行状态 |
| 02-02 完成进度 3/7 次、每日、孩子姓名 | targets 只有 targetType/targetId；recurrenceEnabled/recurrenceEndDate 可保留 | target 显示姓名、recurrence 规则展示、已完成次数/计划次数；不可从已加载分页推算 |
| LAYOUT-02 家庭任务详情/进度 | familyTaskApi.detail 返回任务定义，没有 assignments | 任务下发对象的 assignment id、学生姓名、effectiveStatus、完成/审核时间和进度摘要；需要权限保护的家庭详情读取 |
| LAYOUT-06 目标要求勾选列表 | StudentTaskAssignment.remark 为自由文本 | 结构化 requirements 数组与是否为仅展示/可交互的定义；不能把自由文本伪装为已完成目标 |
| 05 任务列表状态过滤及统计 | StudentTaskAssignmentFilters 仅 sourceType/scheduledDate/page/pageSize | effectiveStatus 服务端过滤以及每类总数；本页 source filter 仍保留真实功能 |
| 05 已完成记录与积分结果 | basePoints、latestCheckIn；latestCheckIn.status 仅 SUBMITTED/REJECTED | 实际 awardedPoints、reviewedAt、approved 状态/审核结果；basePoints 不等于实际奖励 |
| 05 成长连续次数与历史打卡 | 仅 latestCheckIn，无历史列表 | 分页 check-in history、连续完成次数的后端口径；submissionNo 是提交轮次，不是重复任务完成次数 |

现有行为保持：family-tasks 的 revision/token 校验、身份及 capability/permission 校验、主监护限制、草稿分类/标签/重复配置/全部目标保留、保存失败保持输入；task-detail 的 claim/pause/resume/abandon/check-in、附件上传/重试/删除/预览均未改动脚本。

验证：vue-tsc --noEmit 通过。原 scripts/test-family-task-draft.cjs 直接运行失败：动态 Function 没有 require，当前模型新增 parent-identity 导入触发 ReferenceError。使用临时内存 CommonJS loader 补充该导入后，原有全部断言通过；未修改共享测试文件。scripts/verify-family-tasks.cjs 已读但未运行，旧定位文字“暂无家庭任务”“新建家庭任务”与当前页面“还没有成长任务”“发布任务”不一致，需要根代理维护测试定位。

视觉状态：每页合并为一个 scoped SCSS，清理同选择器后置重复属性（保留最后级联值）；任务图标、卡片、筛选、编辑操作栏和上传输入做原图方向调整。尚无微信原生截图或全图差异证明；不标记 1:1 验收。真实账号、权限撤回、跨账号切换和原生附件交互仍需集成验证。task-list/task-detail 原有异步操作缺少跨账号 epoch 检查，此轮只动模板样式，不能声明该检查通过。

## 第二轮：账号竞态修复及 02 分步路由建议

已在 task-list/task-detail 增加 token + epoch 校验、onHide/onUnload 清空及失效；能力查询、列表/详情读取、任务 mutation、附件上传进度与结果、下载预览、删除结果、相册回调、放弃确认回调都必须仍属于当前页面与账号。新增 scripts/test-task-account-races.cjs，用真实页面脚本的延迟 Promise 验证旧账号加载结果及旧账号 mutation 结果不会回写新页面；通过。原生相册可能触发生命周期失效，需要微信真机确认体验。

02 分步建议（当前仍为已有单页 editor，以下路由尚未注册）：
- 02-02 `/pages/family-tasks/family-tasks`：GET /learning-tasks?sourceType=FAMILY；现有列表入口。
- 02-03 `/pages/family-task-edit/family-task-edit?id=...`：GET /learning-tasks/:id、GET /learning-task-options/students；POST/PUT /learning-tasks。草稿需要保持原分类/标签/重复配置。
- 02-04 `/pages/family-task-scope/family-task-scope`：复用 students 返回的主监护关系选择；跨页编辑缓存必须 token scoped，并在账号切换销毁，不能使用全局无账号草稿。
- 02-05 `/pages/family-task-confirm/family-task-confirm`：确认后先保存真实草稿，POST /learning-tasks/:id/publish；不凭前端显示声明发布成功。
- 02-06 `/pages/family-task-detail/family-task-detail?id=...`：GET /learning-tasks/:id 只能显示定义；执行进度还缺家庭授权的 assignment 列表/汇总。
- 02-07～10：需确认并复用现有审核模块的 reviewer 授权列表、check-in 详情与通过/驳回接口；本三页已读 API 不提供这些操作，不能在 family-tasks 临时伪造审核状态或结果。

## 自定义导航协调

新增独立组件 src/components/pixel/PixelPageHeader.vue，使用 WeChat statusBarHeight 和 getMenuButtonBoundingClientRect 留出安全区域；task-list/family-tasks 列表无伪造搜索聊天按钮，task-detail 有返回并在无历史时回真实 task-list。编辑三步使用 header back 退回当前流程；退出编辑确认且回调绑定 revision/token。

请根代理在 pages.json 将以下现存路由 navigationStyle 设置 custom：pages/task-list/task-list、pages/task-detail/task-detail、pages/family-tasks/family-tasks。组件已接入，未改 shared pages.json；在完成该设置前原生栏与新栏会重复，不能以当前双栏截图验收。

## 审核与周报页

parent-task-reviews 现有页新增 detail/reject/approve/success 四状态：独立退回意见、通过确认、真实接口完成后的成功状态；仍先读取 latestCheckIn.id 并发送 expectedCheckInId，版本变化不审批，失败保留意见。空意见在函数内也阻断。header back 从确认/退回回详情，再返回列表。

parent-weekly-reviews 使用 PixelPageHeader；保持仅真实 DTO 的任务数/积分/完成率、分类、日趋势和 supplements，详情返回列表，不提供不存在的审批动作。

根代理新增自定义导航名单：pages/parent-task-reviews/parent-task-reviews、pages/parent-weekly-reviews/parent-weekly-reviews。新增 scripts/test-parent-review-stages.cjs 为内存 API 测试，无生产写入；覆盖权限撤回、旧打卡、空意见、双击与换账号的零审批写入。

## 机构发布范围核查（2026-10-14）

LearningTaskOptionService.organizations 的 MINIAPP ORGANIZATION 分支调用 findOrganizationOptionsForAdministrator；当前该 SQL 只限定管理员所有有效根及后代，没有 SelectedOrganizationContext 限定。TEACHER 候选同样是教师任教范围；当前 managed-tasks.initialize 将 options 直接赋 targets。若一个用户管理多个学校，同账号的合法候选可能跨当前选中学校，不能据此宣称 picker 已限制当前学校。

最小兼容方案：扩展 ManagedOrganizationOption 与真实后端 OrganizationOption 一致的 parentId/path，再以选中机构组织树/路径筛选候选；提交前从当前令牌+selected organization 重新读取授权候选并校验所有 target。前端筛选只改善 UI，严格隔离仍需后端 options 查询与 save/publish 范围同时验证 SelectedOrganizationContext，含跨学校 SQL/服务负向测试。根代理正在部署，本轮未改后端，也未修改其他 owner 的 managed-tasks。

catalog 02-01..02-10 已登记实际 route/viewState/API 与不足，02-06 仍 missing，全部保持未验收，没有伪造截图/差异证据。已有 reference 路径与裁切坐标保持原值。

任务模板：catalog 原图索引未列模板屏，不能仅凭“新建任务”推断原型有模板功能。当前 family form/managed form 为自由填写，已读 learning-task API 无模板列表或模板应用接口；在逐张原图确认模板选择项之前，不添加假模板。若确有要求，可先复用真实已有草稿作为编辑来源，但不能冒称可共享任务模板。

逐图新增证据：已查看 reference/02-03.png 与 02-04.png。02-03 明确六种任务类型、开始/结束日期、每天/每周/自定义频率；现 familyForm 只有 difficulty/duration/scheduledDate/remark，重复字段仅保留旧草稿不能新配置，必须登记功能差异。02-04 明确“仅家庭 / 家庭+班级成长伙伴 / 家庭+本校成长伙伴 / 自定义范围”以及“仅限本校/本机构成员，不可跨校选择”；family-task 的 targets 仅主监护 studentIds，没有 audience/visibility 数据字段，不能将选择孩子替代可见范围。两张原图均未见模板入口，暂没有模板缺失的原图证据。

## 机构当前学校范围修复

新增 SelectedTaskInstitutionScope：从 interceptor 已验证 SelectedOrganizationContext 向父链解析 SCHOOL/TRAINING_INSTITUTION 根，候选必须位于根 path 下；无 selection 保留旧行为，未知/家庭根不向机构授权。LearningTaskOptionService 对现有管理员/教师授权候选再过滤；LearningTaskScopeService 在 source 校验和 requireManageable 上增加相同约束，发布仍执行现有 source/target 校验。未增加角色授权或修改数据库。

managed-tasks 加 token+selected scope 指纹，教师 context classes 与 TEACHER options 取交集；防止教师工作台跨学校班级直接作为发布候选。现有目标字段及草稿保留逻辑不变。

先新增 SelectedTaskInstitutionScopeTest，覆盖当前校园下同学校兄弟班级允许、其他学校拒绝、无选择旧行为；运行默认 Maven 时 JVM 初始化仍 Failed setting boot class path，未报告测试通过，需根代理稳定 Java 环境验证。
