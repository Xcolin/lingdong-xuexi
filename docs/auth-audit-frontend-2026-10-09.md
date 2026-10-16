# Web 授权显示链路审计（2026-10-09）

本报告基于当前源码，只读审计业务代码，未修改数据库或重启服务。仅创建审计文档与清单。数据库63项额外接口/小程序权限由服务端审计另行确认，本报告不将其计数当作现场数据库事实。

## 结论与证据边界

当前存在三套交叉约束：菜单配置BUTTON可见性、业务API permissionCodes、角色/功能开关/对象状态。角色授权树只按菜单节点permissionCode收集单个权限；没有描述一个业务动作的API依赖和可用性。因此勾选一个数字序号UI动作，不等于授予该动作的真实API权限；仅授予API权限也不保证拥有同功能所有入口/确认按钮。

AST扫描得到25个页面目录、271个动作目录项、330处actionKey、12处actionPrefix、281个API对象方法、140处permissionCodes/roleCodes includes/some表达式。JSON包含所有非测试TS/TSX，API对象包括features目录内api.ts；281为客户端方法数量，不能作为服务端端点数量。104处动作在同文件事件处理器中能静态追踪API；其余跨组件/弹窗/动态动作必须人工归并。API候选并非已验证服务器鉴权结果。

机器清单：`docs/auth-audit-frontend-inventory-2026-10-09.json`。复现脚本：`docs/auth-audit-frontend-inventory.mjs` 与 `docs/auth-audit-frontend-trace.mjs`。清单保存源文件、行号、完整JSX、祖先条件、同文件处理器API追踪、提交候选、API方法端点参数；不包含配置、令牌或用户数据。

## 高优先级问题

1. **ConfiguredButton仅查菜单配置，不查permissionCodes。** `components/ConfiguredButton.tsx:27`读取菜单节点，缺失/停用隐藏；辅助动作不检查节点。业务权限由各页面手写条件负责，无法全局保证。`ConfiguredAction.tsx:6`才同时要求permissionCodes包含actionKey且BUTTON启用；两种组件语义不同。`MenuConfiguration.tsx`上下文也不包含当前权限集合。若/menu/current在后端已严格筛掉无权按钮，普通按钮仍依赖服务端的菜单筛选，但不能据此证明所有确认/动态动作有正确API映射。
2. **ConfiguredModal确认按钮再引入独立编码。** `components/ConfiguredModal.tsx:8` FooterAction只查菜单状态；入口`EXCEPTION_REPORT_CREATE`与`exception-reports...modal.1.ok`、入口`LEARNING_TASK_*`与`...confirm.*.ok`独立可配置。业务入口可见而提交按钮无权/缺失，或确认按钮仍在而相关API权限缺失。自定义footer绕过该逻辑，必须全量统一。
3. **授权树忽略菜单停用、停用祖先与功能开关。** `features/iam/rolePermissionTree.ts:6-17`只查grantable、permission.status、DENY；未检查menu.status或祖先状态，也无capabilities参数。停用页面/按钮仍可勾选有效permission；父节点收集全部子权限。角色与用户授权都复用此函数。保留已存在DENY和停用权限是合理历史显示行为，但不应使关闭功能重新可授予。
4. **菜单页面配置与直达路由不等价。** `app/menuNavigation.ts:7`递归检查节点与祖先启用；`App.tsx:271`起Routes只查canAccess/业务权限，不查configuredNavigation.pages。例如页面菜单停用后直接输入/users或/iam依然可能进入。应统一page eligibility，禁止仅通过菜单隐藏表达停用。
5. **组织页面保留角色名额外限制。** `OrganizationManagementPage.tsx:72`showAdminTabs硬编码SYS_ADMIN；`:96`仅PARENT即familyReadOnly；`:113`可读成员依赖这两者及IAM_USER_LIST或数字查询权限。自定义角色即使授予所需API权限也可能缺页签。组织成员设管理员、取消管理员、关联/取消关联等`:31-33`没有permissionCodes条件，只有ConfiguredButton配置及对象/忙碌状态。需要按真实API对应的权限分组，保留organization scope等服务端关系规则。
6. **奖励与积分页面授权检查不完整。** `App.tsx:477`奖励页面允许REWARD_MANAGE_CHILD或REWARD_EXCHANGE_REVIEW_CHILD任一进入，但RewardManagementPage没有接收permissionCodes，初始化同时读取奖励和兑换，按钮只按业务状态显示。仅授予管理或仅授予审核时可能读取不授权资源或显示不能执行的按钮。积分纠错只传correctionEnabled，GrowthPointPage纠错入口及确认不检查GROWTH_POINT_CORRECT_CHILD；已复核GrowthPointCorrectionController.java:26确实要求GROWTH_POINT_CORRECT_CHILD。
7. **授权/权限变化的刷新不统一。** App focus事件只刷新menus，currentUser.permissionCodes及capabilities不刷新。考勤/功能开关部分有专门refresh access；其余已打开页面可能继续使用旧props。撤权、关功能、撤关系后应统一刷新/关闭弹窗并处理API403，不能依赖再次登录。

## 功能入口额外条件全表

以下均来自App.tsx；按钮/页签的全部细节在JSON guards与原始JSX中。表中权限是当前源码条件，并非建议新的权限命名。

|页面|当前额外访问条件|统一时注意|
|---|---|---|
|工作台|无业务读取权限入口门槛|概览子功能各自读取和动作授权，账号自助与系统权限区分|
|系统任务|WEB client + SYSTEM_TASK_READ|可审核任务链接再按具体业务功能开关/权限/非自审与待审状态|
|功能开关|WEB client + FEATURE_TOGGLE_READ|客户端身份保留，管理/审核权限拆分|
|考勤|attendanceManagementEnabled + ATTENDANCE_READ|点名还含ORG_ADMIN/TEACHER与PARENT/STUDENT角色判断，改为身份能力+组织范围|
|学习任务|learningTaskManagementEnabled + LEARNING_TASK_READ_MANAGED|延期/复制/模板读/任务来源分别有权限与开关|
|异常报备|studentExceptionReportEnabled + EXCEPTION_REPORT_READ|新增/处理分别授权|
|积分|growthPointQueryEnabled + GROWTH_POINT_READ_CHILD|纠错仅开关；导出另查EXPORT_JOB_CREATE|
|奖励|rewardExchangeEnabled + manage或review|拆分读取/管理/审核/核销，按能读的页签加载|
|成长复盘|daily或periodic开关 + GROWTH_REVIEW_READ_CHILD|订阅额外PARENT，导出创建/历史额外EXPORT权限及开关|
|匿名排行|开关 + PARENT + ANONYMOUS_CLASS_RANK_READ|亲子关系与匿名可见范围保留；角色名不作为授权替代|
|排行查看授权|PARENT|无显式API权限条件；自助撤回属于身份/关系操作|
|学生登录|STUDENT_LOGIN_READ或STUDENT_READ + QR开关或微信开关且PARENT|微信解绑另需PARENT + STUDENT_WECHAT_UNBIND；明确查询与凭证读权限|
|家长关系|parentRelationshipManagementEnabled + PARENT|页面内PRIMARY_GUARDIAN/PARENT_SELF等真实关系规则保留|
|教师|teacherManagementEnabled + TEACHER_READ|编辑/状态/密码/班级关系各自权限|
|用户|IAM_USER_LIST|不能只授创建/更新却因缺LIST看不到入口；授权树需明确依赖|
|角色权限|IAM_ROLE_READ或IAM_PERMISSION_READ|关联角色用户/角色授权/用户授权额外真实API权限|
|字典|dictionaryManagementEnabled + DICTIONARY_READ|写入DICTIONARY_MANAGE，重复入口/保存编码合并|
|缓存|cacheManagementEnabled + CACHE_READ|manage/review/high-risk workflow分开|
|接口服务|interfaceServiceManagementEnabled + INTERFACE_SERVICE_READ|manage/review分开，导出额外多开关|
|附件|attachmentServiceEnabled + RULE_READ或FILE_LEDGER_READ|按读权限控制页签与加载；rule manage动作共享API权限|
|导入导出模板|开关 + IMPORT_EXPORT_TEMPLATE_READ|写入MANAGE，导出需EXPORT|
|导入作业|dataImportValidationEnabled + IMPORT_JOB_READ|学生导入三个独立权限+studentBatchImportEnabled|
|导出中心|dataExportEnabled + EXPORT_JOB_READ或EXPORT_SENSITIVE_REVIEW|创建并不能单独打开；每类导出还需其业务读/导出权限和附件/模板开关|
|组织|organizationManagementEnabled且NODE_READ/CHANGE_REVIEW，或CLASS_READ|CLASS_READ分支未要求classManagementEnabled；内部又关闭班级面板，入口/内容不一致|
|菜单管理|MENU_READ|管理/状态/排序/刷新各操作应来自实际API|

## UI到API明确映射与重复授权

URL为apiClient相对API_PREFIX的路径。一个功能可以有多个API或多个UI位置；应共享一个语义权限，而不是每个位置独立授权。全量目录及自动静态追踪见附录。

|UI动作/现有编码族|真实客户端API|合并/依赖建议|
|---|---|---|
|附件新增ATTACHMENT_RULE_MANAGE、编辑`.2`、停启`.3`、保存`.9`|attachmentManagementApi的create/update/status方法（端点见JSON）|共享rule manage，页面rules读取不另建查询权限|
|班级点名ATTENDANCE_RECORD、drawer`.2`提交|attendanceApi.batch POST `${base}/batch`；名单roster GET `${base}/roster`|共享ATTENDANCE_RECORD并明确名单读取依赖；重新加载名单不独立授权|
|缓存CACHE_MANAGE入口、`.7`提交保存|cacheManagementApi.executeOperation POST /cache-management/operations|执行功能单权限|
|缓存CACHE_REVIEW批准、`.2`驳回|POST /cache-management/review-queue/{id}/approve或reject|同审核权限；拒绝仍是业务写入|
|缓存`.4`高风险提交|POST /cache-management/review-submissions|明确属于manage或独立提交语义，勿按UI序号授权|
|字典DICTIONARY_MANAGE新增类型、`.2`新增项、`.4/.5`编辑、`.7`保存|dictionaryApi创建/更新类型和条目方法|共享manage，若确需细分按资源操作细分而非弹窗位置|
|功能开关MANAGE入口及`.7`确认提交|featureManagementApi.submit POST `${prefix}/review-submissions`|共享MANAGE；向导下一步非权限|
|功能开关REVIEW批准、`.6`驳回、`.8`确认|featureManagementApi.approve/reject POST `${prefix}/review-queue/{id}/...`|共享REVIEW；对象待审核状态、不可自审保留|
|积分纠错`.4`和`.6`确认|growthPointApi.correct POST /growth-points/students/{id}/corrections|同纠错权限；读孩子和台账为依赖|
|奖励新建/编辑/保存及状态操作|rewardApi.createReward POST /rewards/students/{id}；update PATCH /rewards/{id}；delete DELETE /rewards/{id}|同manage或明确create/update/delete语义权限；亲子范围保留|
|兑换批准/驳回/核销|POST /reward-exchanges/{id}/approve、reject、verify|审核/核销是否拆分以服务端语义为准，不能只靠状态|
|学习任务新建/编辑/保存|learningTaskApi.create POST /learning-tasks；update PATCH /learning-tasks/{id}|按create/update语义，入口与提交一致|
|任务发布/批量发布/停止循环及确认|POST /learning-tasks/{id}/publish、/learning-tasks/batch-publish、/{id}/recurrence/stop|同功能确认按钮共用API权限，批量结果关闭不授权|
|教师新增/修改/状态/密码/班级关系|POST /organization-teachers；PUT /{id}/profile或status；POST /{id}/password-resets；PUT或DELETE /teachers/{id}/classes/{classId}|每类真实权限共享入口/提交，密码重置是业务写操作|
|组织成员add/promote/add-admin与选择器submit|POST /organizations/{id}/members:batch，administrator决定关系类型|普通成员关联和管理员指派按业务语义拆分，不按按钮位置|
|组织成员revoke-admin/dissociate及选择器remove|POST /organizations/{id}/members:revoke|保留administrator差异，确认按钮共享权限|
|组织类型create/update/delete/status|POST /organization-types，PATCH /{id}或/{id}/status，DELETE /{id}|ORG_TYPE_*入口、单选和提交一致|
|组织节点排序/移动/创建/编辑/停用申请|PUT /organizations/order、/{id}/position，POST /organizations，PUT /{id}，POST /organization-changes|拖拽是写动作，必须纳入同元数据与API鉴权；scope/防循环/版本检查保留|
|角色IAM_ROLE_CREATE/UPDATE/DELETE/STATUS_CHANGE|POST /roles，PATCH /roles/{id}或/status，DELETE /roles/{id}|按钮与对应对话框提交共享语义权限；内置角色不可修改保留|
|角色授权入口/保存iam.role-permission-tree.save|GET /roles/{id}/permissions + PUT /roles/{id}/permissions:batch|编辑权限和读取依赖明确；retry为辅助|
|角色用户关联/撤销/查询|POST /roles/{id}/users:batch或users:revoke；GET /roles/{id}/users或user-picker|API语义授权，查询附属READ，不按picker UI路径分裂|
|用户授权users与iam两处入口/save|GET /users/{id}/permission-tree、/permissions；PUT /users/{id}/permissions:batch|同API不应产生users.permission-tree与iam.user-permission-tree两套权限|
|导出创建EXPORT_JOB_CREATE与CreateExportJobModal提交|POST /export-jobs|结合exportType的业务READ/EXPORT能力；同API不同exportType须保留实质授权|
|导出审核批准/驳回|POST /export-job-reviews/{id}/approve或reject|同sensitive review；页面/确认按钮一致|
|导出详情/下载|GET /export-jobs/{id}或/{id}/download|详情附属READ；下载可独立敏感权限及owner规则，不能直接归为纯辅助|
|家长关系邀请/主监护转移/解绑|POST /students/{id}/secondary-parent-invitations或primary-transfer-invitations；DELETE对应关系|PRIMARY_GUARDIAN、PARENT_SELF、目标关系类型是真正业务规则，必须保留|

## 只读/辅助动作未完全剔除

auxiliaryActions是显式白名单，未按语义统一。271目录仍包含查询规则/文件、考勤查询/详情、异常报备查询/详情/关闭、导出查询/详情、接口查询、模板查询、教师查询、用户查询、菜单查询、系统任务查看详情/关闭、学生登录关闭、复制昨日任务关闭、模板库关闭、批量发布结果关闭、角色/用户授权重新加载，以及多个用户选择器查询。附录列出全部实际编码。

不能把“只读API”都剔除：资源READ仍应存在并授权；查询按钮、分页、刷新、重试、详情打开继承对应READ，不应再产生UI位置权限。关闭、取消表单、本地重置、跳转属于辅助；撤回授权、停止循环、取消关联、重置密码、退出其他设备、下载敏感凭证均有实际业务效果，需根据API保留。退出当前会话应始终可用，不由管理员隐藏。自助短信验证码/账号注销流程需身份和风控元数据，不能与管理员角色权限混用。

## 统一metadata建议

建立服务端权威OperationDefinition，页面和按钮仅引用稳定语义operationCode：

- id/code、name、resource、mutation/read/selfService类别；同功能所有入口/确认/modal/拖拽共享operationCode。
- apiBindings包含HTTP method、path template、必要请求判别字段（如exportType、administrator、sourceType）；确保每个可授予operation至少有真实受保护API，无API辅助动作grantable=false。
- requiredPermissions的allOf/anyOf与requiredReadDependencies；一个菜单节点不能只能表达任意一个permissionCode。
- availability包含client、featureKeys allOf/anyOf、页面有效状态/祖先状态；按环境和功能停用统一计算，不为每个组件复制表达式。
- contextualRules如亲子关系、主监护、组织范围、任务来源身份、内置资源、对象状态、版本、自审限制；由后端返回可执行能力/disabledReason，前端不根据SYS_ADMIN/PARENT/ORG_ADMIN角色字符串猜测授权。
- grantable、disabledReason、historicalGrant、effectiveStatus、visibility策略；关闭功能显示不可勾选并说明原因，历史授权保留展示但不执行。
- UI placement仅记录label、page、order、icon等显示配置，和授权operation解耦。菜单PAGE入口依赖业务READ及availability；ConfiguredButton、ConfiguredAction、ConfiguredModal与路由使用同evaluateAccess。

真正身份关系不删除：家长账号自助、主/次监护、亲子读取、组织授权范围、任务FAMILY/TEACHER来源身份、考勤记录组织身份、内置角色保护。应从角色名升级为后端明确identity/capability/relationship条件，并与API同源。组织管理页签、导出入口、普通CRUD不应额外硬编码角色名。

## 实施范围与验证清单

1. 先与服务端API鉴权映射合并JSON，逐个完成271项归并决策：语义操作/继承READ辅助/本地辅助/身份自助；自动阻止没有真实API绑定的grantable项进入种子数据。
2. 重建permission/operation/menu种子和角色/用户授权数据（用户允许历史删除重建，但本报告未执行），删除数字UI位置权限、同API重复入口权限；保留真正API差异、deny及scope模型。
3. 修改/menu/current、授权树接口、currentUser/capabilities统一返回effective eligibility与原因；关闭功能或停用祖先不可勾选，历史项可读但不可误编辑。
4. 统一Web组件、modal footer、路由、非按钮写控件与拖拽；按获授权页签加载奖励/附件/组织等资源；权限刷新统一。
5. 用自定义无内置角色名账号逐个单授operation，检查登录菜单、直达路由、入口按钮、确认按钮、实际HTTP成功；撤权后四者同步消失/拒绝。不同用户对象scope外必须403。
6. 为allOf依赖、anyOf读取、关闭feature、PAGE/BUTTON停用、祖先停用、仅写无读、仅审核无管理、deny优先级、用户显式授权、角色多重授权、身份与关系撤销分别验证。
7. 验证查询/详情/重试/取消/关闭/分页仍可用且不在授权树；业务取消/密码重置/敏感下载仍授权。
8. 验证同一功能跨users/iam/organization/editor/modal授权一次即可完整执行；不同导出类型与普通成员/管理员关系仍各自鉴权。
9. API反向检查：每个grantable UI operation有真实受保护API；每个Web受保护API有操作或READ归属；小程序专属接口不误混入Web功能授权树。
10. 增加metadata契约测试、单权限端到端矩阵、直达路径测试和403/feature变更后的弹窗状态测试。完成源码变更后执行Web typecheck/test/build及后端授权测试；本次仅审计，不宣称这些实施验证已经通过。

## 全量动作静态追踪附录

下表覆盖271个目录项。直接追踪为同文件事件处理器/引用函数静态闭包；读取刷新也可能包含在内，不能把列表中全部API误认为单一动作的授权要求。无直接追踪项多为弹窗入口、跨组件提交、动态actionKey或辅助动作，必须按主报告的功能权限分组人工归并；不得为其创建无API的权限。完整端点、原始JSX和条件在JSON。

|动作编码|名称|源码|直接API追踪/提交候选|
|---|---|---|---|
|rank-preferences.anonymous-rank-withdrawal-page.2|撤回授权|anonymous-ranks/AnonymousRankWithdrawalPage.tsx:38|待跨组件/弹窗映射；详见JSON原始事件|
|attachment-management.attachment-management-page.1|查询规则|attachments/AttachmentManagementPage.tsx:198|attachmentManagementApi.listRules|
|attachment-management.attachment-management-page.2|编辑|attachments/AttachmentManagementPage.tsx:215|待跨组件/弹窗映射；详见JSON原始事件|
|attachment-management.attachment-management-page.3|停用 / 启用|attachments/AttachmentManagementPage.tsx:217|attachmentManagementApi.disableRule, attachmentManagementApi.enableRule, attachmentManagementApi.listRules|
|attachment-management.attachment-management-page.4|查询文件|attachments/AttachmentManagementPage.tsx:238|attachmentManagementApi.listFiles|
|attachment-management.attachment-management-page.5|查看关系|attachments/AttachmentManagementPage.tsx:254|attachmentManagementApi.listRelations|
|ATTACHMENT_RULE_MANAGE|新增附件规则|attachments/AttachmentManagementPage.tsx:263|待跨组件/弹窗映射；详见JSON原始事件|
|attachment-management.attachment-management-page.9|保存规则|attachments/AttachmentManagementPage.tsx:285|待跨组件/弹窗映射；详见JSON原始事件|
|ATTENDANCE_RECORD|班级点名|attendance/AttendancePage.tsx:96|待跨组件/弹窗映射；详见JSON原始事件|
|attendance-records.attendance-page.5|查询|attendance/AttendancePage.tsx:105|待跨组件/弹窗映射；详见JSON原始事件|
|attendance-records.attendance-page.7|详情|attendance/AttendancePage.tsx:115|待跨组件/弹窗映射；详见JSON原始事件|
|attendance-records.attendance-record-drawer.2|提交考勤|attendance/AttendanceRecordDrawer.tsx:69|attendanceApi.batch|
|attendance-records.attendance-record-drawer.3|重新加载名单|attendance/AttendanceRecordDrawer.tsx:76|待跨组件/弹窗映射；详见JSON原始事件|
|CACHE_REVIEW|批准|cache/CacheManagementPage.tsx:214|待跨组件/弹窗映射；详见JSON原始事件|
|cache-management.cache-management-page.2|驳回|cache/CacheManagementPage.tsx:217|待跨组件/弹窗映射；详见JSON原始事件|
|CACHE_MANAGE|执行缓存操作|cache/CacheManagementPage.tsx:233|待跨组件/弹窗映射；详见JSON原始事件|
|cache-management.cache-management-page.4|提交高风险操作|cache/CacheManagementPage.tsx:234|待跨组件/弹窗映射；详见JSON原始事件|
|cache-management.cache-management-page.7|提交保存|cache/CacheManagementPage.tsx:320|待跨组件/弹窗映射；详见JSON原始事件|
|dashboard.dashboard-page.1|考勤台账|dashboard/DashboardPage.tsx:95|待跨组件/弹窗映射；详见JSON原始事件|
|dashboard.dashboard-page.3|退出当前会话|dashboard/DashboardPage.tsx:107|authApi.signOutCurrent|
|dashboard.dashboard-page.4|标记已读|dashboard/DashboardPage.tsx:133|authApi.markSecurityEventRead|
|dashboard.dashboard-page.5|全部标记已读|dashboard/DashboardPage.tsx:137|authApi.markAllSecurityEventsRead|
|dashboard.dashboard-page.6|下线|dashboard/DashboardPage.tsx:154|authApi.signOutDevice, authApi.listDevices, authApi.listSecurityEvents|
|dashboard.dashboard-page.7|下线全部设备|dashboard/DashboardPage.tsx:158|authApi.signOutAllDevices, authApi.clearLocalSession|
|dashboard.parent-account-lifecycle-panel.1|更换手机号|dashboard/ParentAccountLifecyclePanel.tsx:112|待跨组件/弹窗映射；详见JSON原始事件|
|dashboard.parent-account-lifecycle-panel.2|申请注销|dashboard/ParentAccountLifecyclePanel.tsx:114|待跨组件/弹窗映射；详见JSON原始事件|
|dashboard.parent-account-lifecycle-panel.3|撤销注销申请|dashboard/ParentAccountLifecyclePanel.tsx:120|authApi.revokeParentCancellation, authApi.getParentAccountLifecycle|
|dashboard.parent-account-lifecycle-panel.4|发送当前手机号验证码|dashboard/ParentAccountLifecyclePanel.tsx:133|authApi.issueCurrentMobileCode|
|dashboard.parent-account-lifecycle-panel.5|验证当前手机号|dashboard/ParentAccountLifecyclePanel.tsx:139|authApi.verifyCurrentMobile|
|dashboard.parent-account-lifecycle-panel.6|发送新手机号验证码|dashboard/ParentAccountLifecyclePanel.tsx:144|authApi.issueNewMobileCode|
|dashboard.parent-account-lifecycle-panel.7|确认更换手机号|dashboard/ParentAccountLifecyclePanel.tsx:151|authApi.changeParentMobile, authApi.clearLocalSession|
|dashboard.parent-account-lifecycle-panel.8|发送注销验证码|dashboard/ParentAccountLifecyclePanel.tsx:160|authApi.issueParentCancellationCode|
|dashboard.parent-account-lifecycle-panel.9|提交注销申请|dashboard/ParentAccountLifecyclePanel.tsx:168|authApi.requestParentCancellation, authApi.getParentAccountLifecycle|
|DICTIONARY_MANAGE|新增类型|dictionaries/DictionaryManagementPage.tsx:152|待跨组件/弹窗映射；详见JSON原始事件|
|dictionaries.dictionary-management-page.2|新增字典项|dictionaries/DictionaryManagementPage.tsx:153|待跨组件/弹窗映射；详见JSON原始事件|
|dictionaries.dictionary-management-page.4|编辑字典类型|dictionaries/DictionaryManagementPage.tsx:173|待跨组件/弹窗映射；详见JSON原始事件|
|dictionaries.dictionary-management-page.5|编辑字典项|dictionaries/DictionaryManagementPage.tsx:199|待跨组件/弹窗映射；详见JSON原始事件|
|dictionaries.dictionary-management-page.7|保存|dictionaries/DictionaryManagementPage.tsx:248|待跨组件/弹窗映射；详见JSON原始事件|
|EXCEPTION_REPORT_CREATE|新增报备|exception-reports/ExceptionReportPage.tsx:74|exceptionReportApi.classes|
|exception-reports.exception-report-page.3|查询|exception-reports/ExceptionReportPage.tsx:85|exceptionReportApi.list|
|exception-reports.exception-report-page.5|详情|exception-reports/ExceptionReportPage.tsx:97|exceptionReportApi.details|
|EXCEPTION_REPORT_HANDLE|确认处理|exception-reports/ExceptionReportPage.tsx:116|待跨组件/弹窗映射；详见JSON原始事件|
|exception-reports.exception-report-page.8|关闭|exception-reports/ExceptionReportPage.tsx:117|待跨组件/弹窗映射；详见JSON原始事件|
|EXPORT_JOB_CREATE|新建导出|export-jobs/ExportJobManagementPage.tsx:149|待跨组件/弹窗映射；详见JSON原始事件|
|export-jobs.export-job-management-page.2|详情|export-jobs/ExportJobManagementPage.tsx:208|待跨组件/弹窗映射；详见JSON原始事件|
|export-jobs.export-job-management-page.3|下载|export-jobs/ExportJobManagementPage.tsx:210|待跨组件/弹窗映射；详见JSON原始事件|
|export-jobs.export-job-management-page.4|查询|export-jobs/ExportJobManagementPage.tsx:221|待跨组件/弹窗映射；详见JSON原始事件|
|export-jobs.export-job-review-panel.2|批准|export-jobs/ExportJobReviewPanel.tsx:64|待跨组件/弹窗映射；详见JSON原始事件|
|export-jobs.export-job-review-panel.3|驳回|export-jobs/ExportJobReviewPanel.tsx:65|待跨组件/弹窗映射；详见JSON原始事件|
|feature-management.feature-management-page.1|查看记录|feature-management/FeatureManagementPage.tsx:87|待跨组件/弹窗映射；详见JSON原始事件|
|FEATURE_TOGGLE_MANAGE|申请停用 / 申请启用|feature-management/FeatureManagementPage.tsx:95|待跨组件/弹窗映射；详见JSON原始事件|
|FEATURE_TOGGLE_REVIEW|批准|feature-management/FeatureManagementPage.tsx:98|待跨组件/弹窗映射；详见JSON原始事件|
|feature-management.feature-management-page.6|驳回|feature-management/FeatureManagementPage.tsx:98|待跨组件/弹窗映射；详见JSON原始事件|
|feature-management.feature-management-page.7|确认提交 / 下一步|feature-management/FeatureManagementPage.tsx:100|authApi.currentUser, capabilityApi.web|
|feature-management.feature-management-page.8|确认批准 / 确认驳回|feature-management/FeatureManagementPage.tsx:105|authApi.currentUser, capabilityApi.web|
|feature-management.feature-management-page.9|关闭|feature-management/FeatureManagementPage.tsx:106|待跨组件/弹窗映射；详见JSON原始事件|
|growth-points.growth-point-page.1|导出|growth-points/GrowthPointPage.tsx:163|待跨组件/弹窗映射；详见JSON原始事件|
|growth-points.growth-point-page.4|纠错|growth-points/GrowthPointPage.tsx:251|待跨组件/弹窗映射；详见JSON原始事件|
|growth-points.growth-point-page.6|确认纠错|growth-points/GrowthPointPage.tsx:299|growthPointApi.correct, growthPointApi.account, growthPointApi.ledgers|
|growth-reviews.create-growth-review-export.1|导出 PDF|growth-reviews/CreateGrowthReviewExport.tsx:59|待跨组件/弹窗映射；详见JSON原始事件|
|growth-reviews.create-growth-review-export.2|重试模板|growth-reviews/CreateGrowthReviewExport.tsx:88|待跨组件/弹窗映射；详见JSON原始事件|
|GROWTH_REVIEW_PDF_EXPORT|提交导出|growth-reviews/CreateGrowthReviewExport.tsx:101|待跨组件/弹窗映射；详见JSON原始事件|
|growth-reviews.growth-review-export-history.1|导出历史|growth-reviews/GrowthReviewExportHistory.tsx:71|待跨组件/弹窗映射；详见JSON原始事件|
|growth-reviews.growth-review-export-history.3|详情|growth-reviews/GrowthReviewExportHistory.tsx:87|待跨组件/弹窗映射；详见JSON原始事件|
|growth-reviews.growth-review-export-history.4|下载|growth-reviews/GrowthReviewExportHistory.tsx:88|待跨组件/弹窗映射；详见JSON原始事件|
|growth-reviews.growth-review-page.2|补录复盘|growth-reviews/GrowthReviewPage.tsx:229|待跨组件/弹窗映射；详见JSON原始事件|
|growth-reviews.growth-review-page.4|确认追加|growth-reviews/GrowthReviewPage.tsx:350|growthReviewApi.supplement, growthReviewApi.detail|
|IAM_AUDIT_READ|查询审计日志|iam/IamAuditPanel.tsx:84|iamApi.listAudits|
|IAM_ROLE_CREATE|新增角色|iam/IamManagementPage.tsx:108|待跨组件/弹窗映射；详见JSON原始事件|
|IAM_ROLE_PERMISSION_GRANT|配置角色权限|iam/IamManagementPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|IAM_USER_PERMISSION_CONFIGURE|配置用户权限|iam/IamManagementPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|iam.iam-management-page.7|创建角色|iam/IamManagementPage.tsx:137|待跨组件/弹窗映射；详见JSON原始事件|
|iam.iam-management-page.10|提交保存|iam/IamManagementPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|iam.iam-management-page.11|撤销|iam/IamManagementPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|import-export-templates.import-export-template-management-page.1|下载|import-export-templates/ImportExportTemplateManagementPage.tsx:268|importExportTemplateApi.download|
|import-export-templates.import-export-template-management-page.2|字段映射|import-export-templates/ImportExportTemplateManagementPage.tsx:275|importExportTemplateApi.fields|
|import-export-templates.import-export-template-management-page.3|设为默认|import-export-templates/ImportExportTemplateManagementPage.tsx:283|importExportTemplateApi.list, importExportTemplateApi.setDefault|
|import-export-templates.import-export-template-management-page.4|停用 / 启用|import-export-templates/ImportExportTemplateManagementPage.tsx:293|importExportTemplateApi.list, importExportTemplateApi.disable, importExportTemplateApi.enable|
|IMPORT_EXPORT_TEMPLATE_MANAGE|新增模板版本|import-export-templates/ImportExportTemplateManagementPage.tsx:318|待跨组件/弹窗映射；详见JSON原始事件|
|import-export-templates.import-export-template-management-page.7|查询|import-export-templates/ImportExportTemplateManagementPage.tsx:345|importExportTemplateApi.list|
|import-export-templates.import-export-template-management-page.9|选择模板文件|import-export-templates/ImportExportTemplateManagementPage.tsx:395|待跨组件/弹窗映射；详见JSON原始事件|
|import-export-templates.import-export-template-management-page.10|保存字段映射|import-export-templates/ImportExportTemplateManagementPage.tsx:412|待跨组件/弹窗映射；详见JSON原始事件|
|import-export-templates.import-export-template-management-page.11|上移字段|import-export-templates/ImportExportTemplateManagementPage.tsx:482|待跨组件/弹窗映射；详见JSON原始事件|
|import-export-templates.import-export-template-management-page.12|下移字段|import-export-templates/ImportExportTemplateManagementPage.tsx:483|待跨组件/弹窗映射；详见JSON原始事件|
|import-export-templates.import-export-template-management-page.13|删除字段|import-export-templates/ImportExportTemplateManagementPage.tsx:484|待跨组件/弹窗映射；详见JSON原始事件|
|import-export-templates.import-export-template-management-page.14|新增字段|import-export-templates/ImportExportTemplateManagementPage.tsx:490|待跨组件/弹窗映射；详见JSON原始事件|
|import-jobs.import-job-management-page.1|详情|import-jobs/ImportJobManagementPage.tsx:161|importJobApi.detail, importJobApi.errors, studentImportApi.list, studentImportApi.create, importJobApi.create, importJobApi.list, studentImportApi.rows|
|import-jobs.import-job-management-page.2|下载错误|import-jobs/ImportJobManagementPage.tsx:162|importJobApi.downloadError|
|IMPORT_JOB_CREATE|新建校验作业|import-jobs/ImportJobManagementPage.tsx:167|待跨组件/弹窗映射；详见JSON原始事件|
|import-jobs.import-job-management-page.5|查询|import-jobs/ImportJobManagementPage.tsx:173|importJobApi.list|
|import-jobs.import-job-management-page.7|选择文件|import-jobs/ImportJobManagementPage.tsx:180|待跨组件/弹窗映射；详见JSON原始事件|
|STUDENT_IMPORT_EXECUTE|执行学员导入|import-jobs/ImportJobManagementPage.tsx:196|classApi.listClasses|
|import-jobs.import-job-management-page.9|重试失败行|import-jobs/ImportJobManagementPage.tsx:203|studentImportApi.retryFailures|
|import-jobs.import-job-management-page.10|下载初始凭证|import-jobs/ImportJobManagementPage.tsx:204|importJobApi.downloadError, studentImportApi.downloadCredentials|
|interface-services.interface-service-management-page.1|查询|interface-services/InterfaceServiceManagementPage.tsx:228|待跨组件/弹窗映射；详见JSON原始事件|
|interface-services.interface-service-management-page.2|调整授权|interface-services/InterfaceServiceManagementPage.tsx:250|待跨组件/弹窗映射；详见JSON原始事件|
|interface-services.interface-service-management-page.3|停用 / 启用|interface-services/InterfaceServiceManagementPage.tsx:253|待跨组件/弹窗映射；详见JSON原始事件|
|INTERFACE_SERVICE_REVIEW|批准|interface-services/InterfaceServiceManagementPage.tsx:287|待跨组件/弹窗映射；详见JSON原始事件|
|interface-services.interface-service-management-page.5|驳回|interface-services/InterfaceServiceManagementPage.tsx:290|待跨组件/弹窗映射；详见JSON原始事件|
|INTERFACE_SERVICE_MANAGE|登记接口服务|interface-services/InterfaceServiceManagementPage.tsx:321|待跨组件/弹窗映射；详见JSON原始事件|
|interface-services.interface-service-management-page.9|提交保存|interface-services/InterfaceServiceManagementPage.tsx:412|待跨组件/弹窗映射；详见JSON原始事件|
|learning-tasks.learning-task-editor-drawer.1|保存为个人模板|learning-tasks/LearningTaskEditorDrawer.tsx:204|待跨组件/弹窗映射；详见JSON原始事件|
|learning-tasks.learning-task-editor-drawer.2|保存草稿|learning-tasks/LearningTaskEditorDrawer.tsx:206|learningTaskApi.update, learningTaskApi.create|
|learning-tasks.learning-task-editor-drawer.3|添加目标|learning-tasks/LearningTaskEditorDrawer.tsx:314|learningTaskApi.update, learningTaskApi.create|
|learning-tasks.learning-task-editor-drawer.4|删除目标|learning-tasks/LearningTaskEditorDrawer.tsx:346|learningTaskApi.update, learningTaskApi.create|
|learning-tasks.learning-task-management-page.1|复制昨日任务|learning-tasks/LearningTaskManagementPage.tsx:198|待跨组件/弹窗映射；详见JSON原始事件|
|learning-tasks.learning-task-management-page.2|任务模板|learning-tasks/LearningTaskManagementPage.tsx:204|待跨组件/弹窗映射；详见JSON原始事件|
|learning-tasks.learning-task-management-page.3|批量发布|learning-tasks/LearningTaskManagementPage.tsx:209|learningTaskApi.batchPublish, learningTaskApi.list|
|LEARNING_TASK_CREATE|新建任务|learning-tasks/LearningTaskManagementPage.tsx:215|待跨组件/弹窗映射；详见JSON原始事件|
|learning-tasks.learning-task-management-page.6|查询|learning-tasks/LearningTaskManagementPage.tsx:253|learningTaskApi.list|
|learning-tasks.draft.edit|编辑草稿|learning-tasks/LearningTaskManagementPage.tsx:300|learningTaskApi.findById|
|LEARNING_TASK_PUBLISH|发布任务|learning-tasks/LearningTaskManagementPage.tsx:306|learningTaskApi.batchPublish, learningTaskApi.publish, learningTaskApi.list|
|learning-tasks.progress|学生进度|learning-tasks/LearningTaskManagementPage.tsx:316|待跨组件/弹窗映射；详见JSON原始事件|
|learning-tasks.recurrence.stop|停止每日固定任务|learning-tasks/LearningTaskManagementPage.tsx:324|learningTaskApi.stopRecurrence, learningTaskApi.list|
|learning-tasks.previous-day-task-copy-modal.1|关闭|learning-tasks/PreviousDayTaskCopyModal.tsx:109|待跨组件/弹窗映射；详见JSON原始事件|
|LEARNING_TASK_COPY_PREVIOUS_DAY|确认复制|learning-tasks/PreviousDayTaskCopyModal.tsx:110|previousDayTaskCopyApi.retry, previousDayTaskCopyApi.copy|
|TASK_ASSIGNMENT_DEFER|顺延|learning-tasks/TaskDeferQueue.tsx:112|待跨组件/弹窗映射；详见JSON原始事件|
|learning-tasks.task-review-drawer.1|刷新详情|learning-tasks/TaskReviewDrawer.tsx:133|待跨组件/弹窗映射；详见JSON原始事件|
|TASK_ASSIGNMENT_REVIEW|审核通过并发放积分|learning-tasks/TaskReviewDrawer.tsx:155|taskReviewApi.approve|
|learning-tasks.task-review-drawer.3|驳回打卡|learning-tasks/TaskReviewDrawer.tsx:173|taskReviewApi.reject|
|learning-tasks.task-review-drawer.4|确认转交|learning-tasks/TaskReviewDrawer.tsx:200|taskReviewApi.transfer|
|learning-tasks.task-review-queue.2|查看审核|learning-tasks/TaskReviewQueue.tsx:87|待跨组件/弹窗映射；详见JSON原始事件|
|LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL|保存|learning-tasks/TaskTemplateEditorModal.tsx:75|taskTemplateApi.update, taskTemplateApi.create|
|learning-tasks.template.select|选用模板|learning-tasks/TaskTemplateLibraryModal.tsx:93|待跨组件/弹窗映射；详见JSON原始事件|
|learning-tasks.template.edit|编辑个人模板|learning-tasks/TaskTemplateLibraryModal.tsx:102|待跨组件/弹窗映射；详见JSON原始事件|
|learning-tasks.template.up|上移|learning-tasks/TaskTemplateLibraryModal.tsx:113|taskTemplateApi.reorder|
|learning-tasks.template.down|下移|learning-tasks/TaskTemplateLibraryModal.tsx:122|taskTemplateApi.reorder|
|learning-tasks.template.delete|删除个人模板|learning-tasks/TaskTemplateLibraryModal.tsx:131|taskTemplateApi.remove, taskTemplateApi.list|
|learning-tasks.task-template-library-modal.1|关闭|learning-tasks/TaskTemplateLibraryModal.tsx:162|待跨组件/弹窗映射；详见JSON原始事件|
|learning-tasks.task-template-library-modal.2|新建个人模板|learning-tasks/TaskTemplateLibraryModal.tsx:175|待跨组件/弹窗映射；详见JSON原始事件|
|MENU_MANAGE|新增菜单|menus/MenuManagementPage.tsx:112|待跨组件/弹窗映射；详见JSON原始事件|
|menu-management.menu-management-page.3|查询|menus/MenuManagementPage.tsx:118|待跨组件/弹窗映射；详见JSON原始事件|
|menu-management.menu-management-page.5|编辑|menus/MenuManagementPage.tsx:133|待跨组件/弹窗映射；详见JSON原始事件|
|menu-management.menu-management-page.6|上移|menus/MenuManagementPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|menu-management.menu-management-page.7|下移|menus/MenuManagementPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|menu-management.menu-management-page.8|停用 / 启用|menus/MenuManagementPage.tsx:136|menuApi.list, menuApi.update|
|menu-management.menu-management-page.9|保存|menus/MenuManagementPage.tsx:152|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.class-management-panel.1|编辑|organizations/ClassManagementPanel.tsx:138|classApi.setCampusStatus, classApi.setGradeStatus, classApi.disableClass, classApi.enableClass, classApi.listSchools, classApi.listCampuses, classApi.listGrades, classApi.listClasses, classApi.assignGrade|
|organizations.class-management-panel.2|停用 / 启用|organizations/ClassManagementPanel.tsx:139|classApi.disableClass, classApi.enableClass, classApi.listSchools, classApi.listClasses, classApi.listGrades|
|CLASS_CREATE|新增班级|organizations/ClassManagementPanel.tsx:153|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.class-management-panel.7|保存修改 / 创建班级|organizations/ClassManagementPanel.tsx:191|classApi.updateClass, classApi.createClass, classApi.listSchools, classApi.listClasses, classApi.listGrades, classApi.updateCampus, classApi.updateGrade, classApi.createCampus, classApi.createGrade, classApi.listCampuses|
|ORG_NODE_CHANGE_REVIEW|批准|organizations/OrganizationChangeReviewPanel.tsx:96|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.organization-change-review-panel.2|驳回|organizations/OrganizationChangeReviewPanel.tsx:97|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.organization-change-review-panel.5|确认批准 / 确认驳回|organizations/OrganizationChangeReviewPanel.tsx:130|organizationApi.approveChange, organizationApi.rejectChange, organizationApi.listChanges|
|organizations.organization-management-page.1|新增组织类型|organizations/OrganizationManagementPage.tsx:341|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.organization-management-page.2|新增组织节点|organizations/OrganizationManagementPage.tsx:340|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.organization-management-page.4|编辑|organizations/OrganizationManagementPage.tsx:353|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.organization-management-page.5|重新启用|organizations/OrganizationManagementPage.tsx:354|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.organization-management-page.6|申请停用|organizations/OrganizationManagementPage.tsx:354|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.organization-management-page.7|申请移动|organizations/OrganizationManagementPage.tsx:355|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.organization-management-page.8|申请删除|organizations/OrganizationManagementPage.tsx:356|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.organization-management-page.10|提交审核|organizations/OrganizationManagementPage.tsx:394|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.organization-management-page.11|学员关系|organizations/OrganizationManagementPage.tsx:412|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.organization-management-page.12|家长换号核验|organizations/OrganizationManagementPage.tsx:413|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.organization-management-page.13|学生账号注销|organizations/OrganizationManagementPage.tsx:414|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.organization-management-page.14|配置教师班级|organizations/OrganizationManagementPage.tsx:415|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.organization-management-page.16|创建类型|organizations/OrganizationManagementPage.tsx:433|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.organization-management-page.18|创建节点|organizations/OrganizationManagementPage.tsx:445|待跨组件/弹窗映射；详见JSON原始事件|
|ORG_NODE_UPDATE|保存|organizations/OrganizationNodeEditorDrawer.tsx:86|organizationApi.updateOrganization|
|organizations.parent-mobile-manual-recovery-drawer.1|发送验证码|organizations/ParentMobileManualRecoveryDrawer.tsx:118|parentMobileManualRecoveryApi.issueCode, parentMobileManualRecoveryApi.recover|
|PARENT_ACCOUNT_LIFECYCLE_MANAGE|确认换绑|organizations/ParentMobileManualRecoveryDrawer.tsx:143|parentMobileManualRecoveryApi.recover|
|organizations.student-account-cancellation-drawer.2|注销学生账号|organizations/StudentAccountCancellationDrawer.tsx:124|studentAccountCancellationApi.cancel|
|STUDENT_CLASS_ASSIGN|确认配置|organizations/StudentClassAssignmentDrawer.tsx:122|classAssignmentApi.assignStudent|
|organizations.student-organization-lifecycle-drawer.2|确认转班 / 确认转出|organizations/StudentOrganizationLifecycleDrawer.tsx:169|classAssignmentApi.deactivateStudent, classAssignmentApi.listRelationships, classAssignmentApi.deactivateCourse, classAssignmentApi.transferStudent|
|organizations.teacher-class-assignment-drawer.2|解除绑定|organizations/TeacherClassAssignmentDrawer.tsx:100|classAssignmentApi.assignTeacher, classAssignmentApi.removeTeacher|
|organizations.teacher-class-assignment-drawer.3|确认绑定|organizations/TeacherClassAssignmentDrawer.tsx:101|classAssignmentApi.assignTeacher, classAssignmentApi.removeTeacher|
|SECONDARY_PARENT_INVITE_CREATE|邀请副家长|parent-relationships/ParentRelationshipPage.tsx:150|待跨组件/弹窗映射；详见JSON原始事件|
|PRIMARY_PARENT_TRANSFER_CREATE|转移监护权|parent-relationships/ParentRelationshipPage.tsx:156|待跨组件/弹窗映射；详见JSON原始事件|
|PRIMARY_PARENT_SELF_UNBIND|解除我的主家长关系|parent-relationships/ParentRelationshipPage.tsx:159|parentRelationshipApi.unbindPrimary, parentRelationshipApi.get|
|SECONDARY_PARENT_UNBIND|解除副家长|parent-relationships/ParentRelationshipPage.tsx:180|parentRelationshipApi.unbindSecondary, parentRelationshipApi.get|
|rewards.reward-management-page.2|新建奖励|rewards/RewardManagementPage.tsx:256|待跨组件/弹窗映射；详见JSON原始事件|
|rewards.reward-management-page.4|编辑奖励|rewards/RewardManagementPage.tsx:320|待跨组件/弹窗映射；详见JSON原始事件|
|rewards.reward-management-page.5|下架 / 上架|rewards/RewardManagementPage.tsx:328|rewardApi.updateReward, rewardApi.listRewards|
|rewards.reward-management-page.6|删除奖励|rewards/RewardManagementPage.tsx:336|rewardApi.deleteReward, rewardApi.listRewards|
|REWARD_EXCHANGE_REVIEW|同意|rewards/RewardManagementPage.tsx:402|rewardApi.approveExchange, rewardApi.listExchanges|
|rewards.reward-management-page.8|驳回|rewards/RewardManagementPage.tsx:409|待跨组件/弹窗映射；详见JSON原始事件|
|rewards.reward-management-page.9|确认核销|rewards/RewardManagementPage.tsx:419|rewardApi.verifyExchange, rewardApi.listExchanges|
|rewards.reward-management-page.11|保存奖励|rewards/RewardManagementPage.tsx:495|rewardApi.createReward, rewardApi.updateReward, rewardApi.listRewards|
|rewards.reward-management-page.13|确认驳回|rewards/RewardManagementPage.tsx:523|rewardApi.rejectExchange, rewardApi.listExchanges|
|STUDENT_READ|查询|student-login/StudentLoginManagementPage.tsx:128|studentLoginApi.list, studentLoginApi.listWechatBindings|
|STUDENT_LOGIN_QR_CREATE|生成|student-login/StudentLoginManagementPage.tsx:158|studentLoginApi.issueQrTicket|
|STUDENT_WECHAT_UNBIND|解绑|student-login/StudentLoginManagementPage.tsx:168|studentLoginApi.unbindWechat, studentLoginApi.list, studentLoginApi.listWechatBindings|
|student-login.student-login-management-page.5|关闭|student-login/StudentLoginManagementPage.tsx:188|待跨组件/弹窗映射；详见JSON原始事件|
|student-login.student-login-management-page.6|刷新二维码|student-login/StudentLoginManagementPage.tsx:197|studentLoginApi.issueQrTicket|
|system-tasks.system-task-workbench.3|查看详情|system-tasks/SystemTaskWorkbench.tsx:84|authApi.currentUser, capabilityApi.web, systemTasksApi.detail|
|system-tasks.system-task-workbench.4|关闭|system-tasks/SystemTaskWorkbench.tsx:86|待跨组件/弹窗映射；详见JSON原始事件|
|system-tasks.system-task-workbench.5|前往领域处理页|system-tasks/SystemTaskWorkbench.tsx:103|待跨组件/弹窗映射；详见JSON原始事件|
|TEACHER_BATCH_MANAGE|批量操作|teachers/TeacherManagementPage.tsx:245|待跨组件/弹窗映射；详见JSON原始事件|
|TEACHER_CREATE|新增教师|teachers/TeacherManagementPage.tsx:246|待跨组件/弹窗映射；详见JSON原始事件|
|teachers.teacher-management-page.4|查询|teachers/TeacherManagementPage.tsx:261|teachersApi.list|
|TEACHER_UPDATE|编辑资料|teachers/TeacherManagementPage.tsx:279|待跨组件/弹窗映射；详见JSON原始事件|
|TEACHER_PASSWORD_RESET|重置密码|teachers/TeacherManagementPage.tsx:280|待跨组件/弹窗映射；详见JSON原始事件|
|TEACHER_CLASS_ASSIGN|班级范围|teachers/TeacherManagementPage.tsx:281|待跨组件/弹窗映射；详见JSON原始事件|
|teachers.teacher-management-page.5|保存|teachers/TeacherManagementPage.tsx:317|teachersApi.bindClass, teachersApi.unbindClass, teachersApi.list|
|teachers.teacher-management-page.9|提交保存|teachers/TeacherManagementPage.tsx:352|待跨组件/弹窗映射；详见JSON原始事件|
|IAM_USER_UPDATE|编辑|users/UserManagementPage.tsx:32|organizationApi.members, organizationApi.associateMembers, organizationApi.revokeMembers|
|users.edit.save|保存|users/EditUserDrawer.tsx:15|待跨组件/弹窗映射；详见JSON原始事件|
|IAM_USER_CREATE|新增用户|users/UserManagementPage.tsx:124|待跨组件/弹窗映射；详见JSON原始事件|
|users.user-management-page.3|查询|users/UserManagementPage.tsx:120|usersApi.list|
|users.user-management-page.5|创建用户|users/UserManagementPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|teachers.status.enabled|启用账号|teachers/*|待跨组件/弹窗映射；详见JSON原始事件|
|teachers.status.disabled|停用账号|teachers/*|待跨组件/弹窗映射；详见JSON原始事件|
|teachers.status.locked|锁定账号|teachers/*|待跨组件/弹窗映射；详见JSON原始事件|
|exception-reports.exception-report-page.modal.1.ok|确定|exception-reports/ExceptionReportPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|export-jobs.create-export-job-modal.modal.1.ok|提交导出|export-jobs/CreateExportJobModal.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|export-jobs.export-job-review-panel.modal.1.ok|确定|export-jobs/ExportJobReviewPanel.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|learning-tasks.batch-publish-result-modal.modal.1.ok|关闭|learning-tasks/BatchPublishResultModal.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|learning-tasks.learning-task-management-page.confirm.1.ok|确认发布|learning-tasks/LearningTaskManagementPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|learning-tasks.learning-task-management-page.confirm.2.ok|确认发布|learning-tasks/LearningTaskManagementPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|learning-tasks.learning-task-management-page.confirm.3.ok|确认停止|learning-tasks/LearningTaskManagementPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|learning-tasks.task-defer-queue.modal.1.ok|确认顺延|learning-tasks/TaskDeferQueue.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|learning-tasks.task-review-drawer.confirm.1.ok|确认驳回|learning-tasks/TaskReviewDrawer.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|learning-tasks.task-review-drawer.confirm.2.ok|确认通过|learning-tasks/TaskReviewDrawer.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|learning-tasks.task-template-library-modal.confirm.1.ok|确认删除|learning-tasks/TaskTemplateLibraryModal.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.class-management-panel.confirm.1.ok|确定|organizations/ClassManagementPanel.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.organization-management-page.confirm.1.ok|确认启用|organizations/OrganizationManagementPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.student-account-cancellation-drawer.confirm.1.ok|确认注销|organizations/StudentAccountCancellationDrawer.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.student-organization-lifecycle-drawer.confirm.1.ok|确认转出|organizations/StudentOrganizationLifecycleDrawer.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|parent-relationships.parent-relationship-page.confirm.1.ok|确认解除|parent-relationships/ParentRelationshipPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|parent-relationships.parent-relationship-page.confirm.2.ok|确认解除|parent-relationships/ParentRelationshipPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|parent-relationships.parent-relationship-page.modal.1.ok|发送邀请|parent-relationships/ParentRelationshipPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|student-login.student-login-management-page.confirm.1.ok|确认解绑|student-login/StudentLoginManagementPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|import-export-templates.import-export-template-management-page.modal.1.ok|保存模板|import-export-templates/ImportExportTemplateManagementPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|import-jobs.import-job-management-page.confirm.1.ok|确认下载|import-jobs/ImportJobManagementPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|import-jobs.import-job-management-page.modal.1.ok|提交校验|import-jobs/ImportJobManagementPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|import-jobs.import-job-management-page.modal.2.ok|确认执行|import-jobs/ImportJobManagementPage.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|menu-management.batch-buttons.open|批量新增按钮|menus/BatchMenuButtons.tsx:112|待跨组件/弹窗映射；详见JSON原始事件|
|menu-management.batch-buttons.save|批量保存|menus/BatchMenuButtons.tsx:9|待跨组件/弹窗映射；详见JSON原始事件|
|menu-management.batch-buttons.add|添加一行|menus/BatchMenuButtons.tsx:22|待跨组件/弹窗映射；详见JSON原始事件|
|menu-management.batch-buttons.remove|删除|menus/BatchMenuButtons.tsx:20|待跨组件/弹窗映射；详见JSON原始事件|
|iam.role-permission-tree.save|保存角色权限|iam/RolePermissionTreeModal.tsx:66|iamApi.batchRolePermissions|
|iam.role-permission-tree.retry|重新加载|iam/RolePermissionTreeModal.tsx:70|menuApi.list, iamApi.listRolePermissionMenus, iamApi.listRolePermissions|
|iam.role-row.authorize|授权|iam/IamManagementPage.tsx:122|待跨组件/弹窗映射；详见JSON原始事件|
|iam.role-row.users|关联用户|iam/IamManagementPage.tsx:123|待跨组件/弹窗映射；详见JSON原始事件|
|iam.role-users.query|查询|iam/RoleUsersModal.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|iam.role-users.save|保存关联|iam/RoleUsersModal.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|users.row.authorize|授权|users/UserManagementPage.tsx:153|待跨组件/弹窗映射；详见JSON原始事件|
|users.permission-tree.save|保存用户权限|iam/UserPermissionTreeModal.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|users.permission-tree.retry|重新加载|iam/UserPermissionTreeModal.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|iam.user-permission-tree.save|保存用户权限|iam/UserPermissionTreeModal.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|iam.user-permission-tree.retry|重新加载|iam/UserPermissionTreeModal.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|users.create-user.save|创建用户|users/CreateUserDrawer.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.create-user.save|创建用户|users/CreateUserDrawer.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.members.query|查询|organizations/OrganizationMembersDrawer.tsx:30|organizationApi.members|
|organizations.members.add|关联已有用户|organizations/OrganizationMembersDrawer.tsx:31|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.members.add-admin|添加组织管理员|organizations/OrganizationMembersDrawer.tsx:31|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.members.create|新增用户|organizations/OrganizationMembersDrawer.tsx:31|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.members.promote|设为管理员|organizations/OrganizationMembersDrawer.tsx:32|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.member-picker.save|保存关联|users/UserPickerModal.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.member-picker.query|查询|users/UserPickerModal.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.admin-picker.save|保存关联|users/UserPickerModal.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.admin-picker.query|查询|users/UserPickerModal.tsx|待跨组件/弹窗映射；详见JSON原始事件|
|iam.directory.query|查询|iam/IamManagementPage.tsx:102|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.node.add-child|新增下级|organizations/OrganizationManagementPage.tsx:351|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.members.dissociate|取消关联|organizations/OrganizationMembersDrawer.tsx:32|待跨组件/弹窗映射；详见JSON原始事件|
|organizations.members.revoke-admin|取消管理员|organizations/OrganizationMembersDrawer.tsx:32|待跨组件/弹窗映射；详见JSON原始事件|
|menu-management.node.add-child|新增下级|menus/MenuManagementPage.tsx:134|待跨组件/弹窗映射；详见JSON原始事件|
|menu-management.node.delete|删除|menus/MenuManagementPage.tsx:137|menuApi.list, menuApi.remove|
|IAM_USER_PASSWORD_SET|重置密码|users/UserManagementPage.tsx:154|待跨组件/弹窗映射；详见JSON原始事件|
|users.reset-password.save|保存密码|users/ResetUserPasswordModal.tsx:14|待跨组件/弹窗映射；详见JSON原始事件|
|IAM_ROLE_UPDATE|编辑角色|iam/IamManagementPage.tsx:121|待跨组件/弹窗映射；详见JSON原始事件|
|IAM_ROLE_DELETE|删除角色|iam/IamManagementPage.tsx:124|iamApi.listRoles, iamApi.listPermissions, iamApi.deleteRole|
|IAM_ROLE_STATUS_CHANGE|角色启停|iam/IamManagementPage.tsx:119|待跨组件/弹窗映射；详见JSON原始事件|
|ORG_TYPE_UPDATE|编辑组织类型|organizations/OrganizationManagementPage.tsx:213|待跨组件/弹窗映射；详见JSON原始事件|
|ORG_TYPE_DELETE|删除组织类型|organizations/OrganizationManagementPage.tsx:214|organizationApi.listTypes, organizationApi.listTree, dictionaryApi.listTypes, dictionaryApi.listItems, organizationApi.deleteType|
|ORG_TYPE_STATUS_CHANGE|组织类型启停|organizations/OrganizationManagementPage.tsx:205|待跨组件/弹窗映射；详见JSON原始事件|
|IAM_USER_STATUS_CHANGE|用户启停与锁定|users/UserManagementPage.tsx:145|待跨组件/弹窗映射；详见JSON原始事件|
|STUDENT_ENROLLMENT_INVITE_CREATE|班级邀请码|organizations/InstitutionEnrollmentReviewDrawer.tsx:25|institutionEnrollmentApi.issue|
|STUDENT_ENROLLMENT_REQUEST_CREATE|关联机构申请|organizations/InstitutionEnrollmentModal.tsx:13|institutionEnrollmentApi.apply, institutionEnrollmentApi.resolve|
|STUDENT_ENROLLMENT_REVIEW|入学申请审核|organizations/InstitutionEnrollmentReviewDrawer.tsx:17|institutionEnrollmentApi.reject, institutionEnrollmentApi.list|
|IAM_USER_ROLE_ASSIGN|关联角色|users/UserRoleAssociationModal.tsx:152|usersApi.removeRoles, usersApi.addRoles, usersApi.roleAssociations|

