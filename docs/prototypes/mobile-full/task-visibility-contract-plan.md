# 02-04 任务可见范围契约实施方案

> 执行方式：使用 superpowers:executing-plans 逐项实施。此文为契约审查，未修改业务代码、数据库或共享 catalog。

**目标：** 区分任务下发对象与任务可见范围，以真实授权实现原图范围选择并保持 Web 兼容。

**架构：** 下发继续使用 targets；共享可见性独立存储、独立读取授权，不生成额外 assignment。首先补齐已有机构下发选择，再实现家庭只读共享。

**技术：** uni-app/Vue/TypeScript、Spring Boot、MyBatis、MySQL。

## 已核实证据（2026-10-14）

已读取总计划和 task-layout-gaps，直接查看 reference/02-04.png。原图上半部是孩子多选；下半部另有“仅家庭（仅自己可见）”“家庭 + 班级成长伙伴”“家庭 + 本校成长伙伴”“自定义范围”，并说明仅本校/本机构成员、不可跨校。原图本页没有“全班”下发控件；全班/指定孩子是机构任务的下发语义，不能用它替换家庭可见范围。学校名称、孩子班级名称应来自真实成员关系，不能沿用原图示例。

当前 LearningTaskRequest 没有 audience/visibility 字段。family-task.ts 返回 targets，family-tasks.vue 的范围步骤只保存 studentIds；服务端 validateFamily 仅允许当前主监护孩子的 STUDENT target。副监护不能通过增加共享字段取得创建权。

managed-learning-task.ts 已支持 ORGANIZATION/STUDENT targets；managed-tasks.vue 保存时若修改范围只产生一个 ORGANIZATION target，原草稿未修改时保留所有 targets。所以后端支持指定学生，客户端没有完整选择入口。

LearningTaskScopeService：机构来源允许 source 子树内组织或学生，教师来源必须是自己任教 CLASS，组织 target 必须等于来源班级，学生必须是该班活跃成员。管理员委派教师审核时组织 target 必须是教师任教班级。SelectedTaskInstitutionScope 已限制当前学校/培训根，不能放宽。

LearningTaskTargetExpansionService 在发布时按组织展开启用学生并去重；这种展开是下发，不是让伙伴看见任务。LearningTaskOptionController 已提供 organizations(sourceType, organizationType) 与 students(sourceType, organizationId, keyword)，无需先创造第二套机构目标接口。

## 最窄落地顺序

### 1. 机构全班/指定孩子（不迁移数据库）

- [ ] 修改 lingdong-bansui-miniapp/src/api/managed-learning-task.ts：接入现有 students 候选，复用其真实响应类型，禁止用组织成员列表自行推导发布权限。
- [ ] 修改 src/pages/managed-tasks/managed-tasks.vue：选授权学校/校区/年级/班级组织后，全体模式提交 ORGANIZATION target；指定孩子模式提交非空 STUDENT targets。教师只出现任教班级；机构委派审核教师时限制该教师班级。
- [ ] 保存前重新获取当前 token + selected organization 下候选，校验全部 ID；切换学校清空候选与选择、失效异步结果；原草稿目标未修改时继续完整保留。
- [ ] 校区/年级全体下发仅管理员自己审核时使用现有子树展开。界面名称应随组织类型显示“该范围全部孩子”，不能把整个学校称作全班。没有授权范围即显示空态，不退回全站候选。
- [ ] 后端复核 LearningTaskOptionService.students 对 selected root 的约束与 save/publish 一致；补负向测试后才启用入口。

### 2. 家庭可见性（独立新增契约）

推荐新增可选 visibility 字段：mode=PRIVATE/CLASS/INSTITUTION/CUSTOM，institutionId 与 organizationIds。PRIVATE 默认；CLASS 必须明确选一个孩子当前所属的授权班级，INSTITUTION 必须明确选一所关联学校或培训机构。家庭可能跨多个学校，不自动以第一个孩子或当前家庭组织推断学校。CUSTOM 最窄仅选择同一机构内班级组织，不先开放任意用户选择。

新增 learning_task_visibility 与 learning_task_visibility_organization 表，旧任务没有配置等价 PRIVATE。保留 task.targets 不变。visibility 只允许伙伴读取任务定义摘要，不含孩子姓名、assignment、打卡、附件或审核结果。原图“仅自己可见”采用创建者可见口径；孩子通过自己 assignment 执行的原有权限另行保留。

- [ ] 新增 LearningTaskVisibilityInput/Response/Service 与 mapper，LearningTaskRequest/Response 增加可选字段；ManagementService 在创建/更新同一事务写配置，PublishService 重验成员和机构状态。拒绝跨机构、自定义空集合、非 CLASS 节点以及失效成员。
- [ ] 新增 GET /api/v1/learning-task-options/visibility-organizations?studentId=...&institutionId=...：只返回当前主监护孩子关联的机构内合法班级，返回机构根与完整组织名称；该候选不授予发布任务给这些班级的权利。
- [ ] 新增 GET /api/v1/shared-learning-tasks 和 /shared-learning-tasks/:id：只读已发布任务摘要，根据访问者实时有效成员身份判断共享范围，不复用 creator 管理接口、assignment/review 接口。禁止仅凭知道任务 ID 读取。
- [ ] 修改 family-task.ts、family-task-draft 模型和 family-tasks.vue：独立可见性选择、真实候选、确认摘要、旧草稿回填、保存失败保留、账号/学校变更失效。能力未开放时展示私人范围并说明服务尚不支持其他选项，不显示可成功提交的假共享。
- [ ] Web 请求不带新字段：新建默认 PRIVATE，更新已有共享配置保持原值；显式 visibility 才替换配置。需要测试省略与 PRIVATE 的差别，不能每次旧 Web 保存把共享清空。客户端 UNKNOWN mode 必须阻断修改而非默默降级。

## 验证清单

- [ ] 服务端 isolated fixtures：主监护可创建、副监护拒绝；同校兄弟班级允许、其他学校拒绝；教师非任教班级拒绝；学生跨班拒绝；禁用节点拒绝；重叠组织展开去重。
- [ ] 发布前关系撤回/转校：候选曾合法也必须拒绝；已发布共享读取实时撤回授权，不返回旧校孩子信息。共享不增加 assignment/积分/审核人。
- [ ] 私人任务不可由同班伙伴 ID 猜测读取；CLASS 只同班、INSTITUTION 只同机构、CUSTOM 仅选定班级伙伴；未发布草稿不共享。分页 total 仅统计授权结果。
- [ ] Web JSON 兼容与编辑回归：旧请求反序列化、旧任务默认私人、已有共享字段省略保留、显式私人清除共享、targets/分类/标签/重复配置不丢。
- [ ] miniapp 延迟 Promise 测试：切账号/机构后候选与 save 结果不回写；双击保存一次写入；失败保留输入；未授权选择不能提交。
- [ ] 使用 server 当前可用 JDK 运行 LearningTaskScopeServiceTest/OptionControllerTest/ControllerTest 及新增 visibility 测试；miniapp 执行 npx vue-tsc --noEmit、现有草稿和竞态测试、微信生产构建；Web 运行任务 editor/management 测试与类型构建。
- [ ] 微信原生 02-04 截图核对孩子多选、可见范围单选、组织摘要、安全区与返回确认；数据差异记录，不伪造原图姓名。上述验证尚未执行，不据本方案标记验收。

## 实施边界

第一阶段可直接利用真实接口补齐机构下发；第二阶段必须同时实现存储、读取授权和前端，不能只存一个无效单选值。共享范围不开放给家庭下发班级任务，不展示其他孩子执行记录。原型没有规定伙伴互动或共享打卡，最小方案不引入评论、社交、额外审核及真实数据迁移写入。
