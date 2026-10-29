# 灵动伴随项目现状核查报告

> 核查日期：2026-09-09。对象：当前工作目录 `E:\apps\lingdong-xuexi-code`，包含未提交和未跟踪文件，不只检查 Git HEAD。
> 本轮为只读审计，仅新增本文件；不修改业务代码、配置、SQL、测试或既有文档，不连接远程数据库、Redis、短信或微信，不执行迁移或启动业务服务。
> “已完成”在本报告中最多表示明确功能子项存在本地实现及验证证据，不表示生产验收通过。历史报告、设计稿、测试替身和合成浏览器检查分别标注。

## 核查结论摘要

- 系统不是只有页面的原型：认证、组织、任务、积分、奖励、人工考勤等已存在真实服务、MyBatis 持久化和测试。
- 系统也不是已可完整上线的产品：真实短信、周报消息投递、完整角色看板、通用适配器及发布运维等仍有明显缺口。
- 目前是三个独立工程：单体模块化 Java 后端、React Web、Vue 3/uni-app 小程序；不是微服务。
- 源码盘点：1137 个主代码 Java 文件、93 个 Mapper XML、52 个业务控制器、263 个 HTTP 方法映射、27 个小程序页面注册项。
- 迁移文件 V1–V68 定义 94 张业务表；另有 Flyway 自有历史表。不代表远程 MySQL 已执行 V68。
- 最近一次后端全量记录：172 套件、678 项、1 项失败、0 错误、0 跳过。**当前不能称后端全量通过。**
- 本轮 Web `tsc --noEmit`、uni-app `vue-tsc --noEmit` 均通过。未重新打包或执行全部浏览器/UAT。
- 原“83.6%”是历史加权台账，不是本次核验出的真实验收比例；不可据此宣称仅剩 16.4% 的开发量或 token 消耗。

### 证据等级

| 等级 | 含义 |
|---|---|
| A | 本轮直接读取源码/路由/配置/迁移，或执行无输出类型检查获得 |
| B | 当前工作目录已有测试 XML、构建日志、合成浏览器脚本/截图证明；本轮未重复执行 |
| C | 需求/计划中存在，但源码中未找到完整实现；作为未完成或待核实，不作为已交付 |
| 注意 | 静态检查不能证明所有运行时组合无缺陷；没有远程环境审计、压力测试、渗透测试及真实用户验收 |

## 1. 当前系统架构

```text
React 18 + Ant Design/ProComponents + React Router + Vite
                    │ /api/v1
                    ▼
Spring Boot 3.4.5 / Spring MVC / Spring Security
  Web 控制器 → Application 用例 → Domain / MyBatis Mapper XML
  认证会话 + 动态权限 + 数据范围 + 功能开关
  任务调度、导入/导出队列、内部消息及周报待发队列
                    │
            MySQL + Redis + 本地文件
                    ▲
                    │ /api/v1
Vue 3 + uni-app：H5 与 mp-weixin 独立构建
```

| 项目 | 当前事实 | 限制 |
|---|---|---|
| 后端 | 单 Maven 模块，按 auth/iam/organization/learningtask/growthpoint 等业务包划分；JDK 17、MyBatis 3.0.4、XML SQL | 不是 Maven 多模块或微服务；部分业务包较大 |
| Web | React 18.3、Ant Design 5、ProComponents、React Router 7、Vite | 不是完整 Ant Design Pro/Umi 脚手架；属于使用 ProComponents 的自建应用 |
| 小程序 | uni-app + Vue 3；支持 H5/mp-weixin 构建脚本 | TypeScript/Vite 版本独立于 Web；两端功能覆盖不完全对称 |
| 数据 | MySQL 驱动 + Flyway；自动化测试 H2 | 没有其他生产数据库适配及验证矩阵，不能把 H2 测试等同“任意数据库可切换” |
| 缓存/票据 | Redis 实现 + test profile 内存替身；Spring Cache 字典缓存 | Redis 实际可用性未在本轮验证 |
| 安全 | 无状态令牌/设备会话；Spring Security 对非公开 /api/v1 请求要求认证；拦截器与用例层权限/对象检查 | 不能仅凭 controller 上无注解判断无授权，也不能仅凭存在鉴权组件判断所有接口无漏洞 |
| 文件 | 统一附件规则、文件记录与业务关系，本地文件存储实现 | 无云对象存储实现；默认附件目录在临时目录，有持久化运维风险 |
| 报表 | Apache POI 5.5.1、PDFBox 3.0.8、嵌入中文字体；复用导出作业 | 当前数据集范围有限，非完整全业务报表平台 |
| API 文档 | springdoc 2.8.17，配置路径 /api/v1/openapi，Swagger UI 关闭 | 本报告清单来自源码，不是在线 OpenAPI 实测；OpenAPI 路径也受 API 认证规则约束 |
| 主键 | 19 位雪花 BIGINT；JSON 业务标识按现有 DTO 多采用字符串 | 新增表符合约束；本轮未逐字段证明所有 JSON Long 均按字符串输出 |

证据：[server/pom.xml](E:/apps/lingdong-xuexi-code/server/pom.xml)、[web/package.json](E:/apps/lingdong-xuexi-code/web/package.json)、[miniapp/package.json](E:/apps/lingdong-xuexi-code/miniapp/package.json)、[server/src/main/java/com/lingdong/learning/common/security/SecurityConfiguration.java](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/common/security/SecurityConfiguration.java)。

## 2. 当前项目目录

```text
lingdong-xuexi-code/
├─ server/
│  ├─ pom.xml
│  ├─ src/main/java/com/lingdong/learning/
│  │  ├─ auth、user、iam、permission、datascope、audit、common
│  │  ├─ organization、student、teacher、studentimport
│  │  ├─ learningtask、growthpoint、attendance、exceptionreport
│  │  └─ feature、dictionary、cache、interfaceconfig、attachment、
│  │     templateconfig、importjob、exportjob
│  ├─ src/main/resources/
│  │  ├─ db/migration/       V1–V68
│  │  ├─ mapper/             93 个 XML
│  │  ├─ fonts/              PDF 中文字体
│  │  └─ application*.yml
│  ├─ src/test/              Java 测试、H2/test 配置
│  └─ target/                编译产物、测试报告、验证日志
├─ web/
│  ├─ src/app/、api/、features/、styles/、test/
│  ├─ scripts/               合成接口 Playwright 检查
│  └─ package.json、vite.config.ts、node_modules/、dist/
├─ miniapp/
│  ├─ src/api/、pages/、session/、composables/、static/
│  ├─ src/pages.json、manifest.json
│  ├─ scripts/、package.json、vite.config.ts
│  └─ node_modules/、dist/
├─ docs/design/              需求追溯、FSD/HLD、数据库/API、安全、测试、部署
├─ docs/superpowers/         方案与增量实施记录
├─ docs/templates/
├─ tools/                    当前未发现实质工具文件
├─ .local-verification/      本地日志及截图，不能当生产运行数据
├─ .git/、.idea/、.corepack/、.npm-cache/
├─ README.md
├─ 灵动伴随-需求原始素材汇总V1.0.md
├─ 灵动伴随-业务需求说明书-V1.0.md
├─ 【项目名称】业务需求文档_模版.md
└─ PROJECT_STATUS.md         本次报告
```

Web 路由（包含跳转/兜底，不能按路由数量计算功能完成率）：
- `*`
- `/`
- `/*`
- `/attachment-management`
- `/attendance-records`
- `/cache-management`
- `/dashboard`
- `/dictionaries`
- `/exception-reports`
- `/export-jobs`
- `/growth-points`
- `/growth-reviews`
- `/iam`
- `/import-export-templates`
- `/import-jobs`
- `/interface-services`
- `/learning-tasks`
- `/login`
- `/organizations`
- `/parent-onboarding`
- `/parent-relationship-invitations/:invitationId`
- `/parent-relationships`
- `/rewards`
- `/student-login`
- `/teachers`
- `/users`

uni-app 已注册页面：
- `pages/index/index`
- `pages/student-login/student-login`
- `pages/parent-login/parent-login`
- `pages/parent-onboarding/parent-onboarding`
- `pages/parent-home/parent-home`
- `pages/parent-student-wechat/parent-student-wechat`
- `pages/organization-login/organization-login`
- `pages/organization-home/organization-home`
- `pages/teacher-home/teacher-home`
- `pages/managed-tasks/managed-tasks`
- `pages/exception-reports/exception-reports`
- `pages/organization-students/organization-students`
- `pages/attendance/attendance`
- `pages/organization-classes/organization-classes`
- `pages/organization-teachers/organization-teachers`
- `pages/organization-parent-mobile-recovery/organization-parent-mobile-recovery`
- `pages/organization-student-cancellation/organization-student-cancellation`
- `pages/parent-relationships/parent-relationships`
- `pages/parent-relationship-invitation/parent-relationship-invitation`
- `pages/student-home/student-home`
- `pages/account-security/account-security`
- `pages/parent-account-lifecycle/parent-account-lifecycle`
- `pages/task-list/task-list`
- `pages/task-detail/task-detail`
- `pages/growth-points/growth-points`
- `pages/rewards/rewards`
- `pages/growth-reviews/growth-reviews`

## 3. 已经真正完成的功能

以下是**本地实现较完整的子功能**，不是对整个模块或双端全部需求的全量验收声明。依据为对应主代码、接口、Mapper/迁移及当前测试/前端调用，整体仍受第 12 节回归失败影响。

| 子功能 | 已存在的实现 | 主要证据位置 |
|---|---|---|
| 平台/机构密码认证及会话 | 登录、刷新、退出、设备查询/撤销、全设备下线 | server/.../auth；web/src/api/auth.ts |
| 学生账号与登录基础 | 8 位账号、4 位登录码、验证码/限流、登录码重置、短时二维码 | auth、student；miniapp 学生登录与任务入口；注意失败测试 |
| 账号安全 | 设备会话、安全事件读取/已读、风险提示 | DashboardPage、account-security 页面、auth_security_event |
| 用户/角色/权限基础 | 用户查询/创建/状态、角色创建、角色及用户授权、权限变更审计 | iam、permission、user；Web users/iam |
| 组织与班级基础 | 组织类型、组织树、节点变更审批、班级新增/修改/启停、关联保护 | organization；Web organizations；小程序机构班级页 |
| 教师管理 | 账号维护、状态、密码重置、班级关联及批量处理 | teacher；Web teachers；小程序 organization-teachers |
| 学生家校关系 | 学生档案、主副家长关系、邀请/转交/解除、班级转移与机构关系变更 | student；Web parent-relationships/organizations；对应小程序页面 |
| 学习任务核心 | 家庭/机构/教师来源、创建/发布、认领、暂停/恢复/放弃、打卡、审核/驳回/转交 | learningtask；Web 管理/审核；小程序任务执行/管理 |
| 任务扩展已实现部分 | 每日固定实例、过期/待优化/顺延、免执行、复制昨日、系统/个人模板 | learningtask 相关服务和 V30/V32/V33/V34 |
| 积分账户与台账 | 审核入账、累计/可用积分、台账查询、家庭误审纠错、衰减/沉睡状态 | growthpoint；V24/V26/V29；Web/小程序积分页 |
| 奖励兑换 | 家庭奖励维护、学生申请、家长审批/驳回、核销、过期处理 | growth_reward/exchange；Web rewards；小程序 rewards |
| 成长复盘已有部分 | 日/周/月快照、分类和趋势、追加补录；Web 简洁/详细模板 | growthpoint；Web growth-reviews；小程序以学生复盘为主 |
| 复盘 PDF Web 操作 | 单份/区间、模板选取、内容固化、作业执行、PDF/ZIP、历史及附件下载 | exportjob；Web CreateGrowthReviewExport/History；有历史专项及合成浏览器证据 |
| 人工考勤 | 点名、请假结果、更正、历史、名单和范围过滤 | attendance；双端页面；非定位考勤/请假审批 |
| 异常报备 | 提交、查询、处理、不可变动作与本地事件 | exceptionreport；双端页面；不含真正送达通知 |
| 字典管理 | 类型/条目管理、启停/默认、字典缓存 | dictionary；Web dictionaries |
| 统一附件基础 | 上传、查看/下载、规则、文件关系、删除/释放检查 | attachment；Web 附件管理；任务图片使用统一通道 |
| 模板与导入已有闭环 | 模板/字段配置、XLSX 校验、错误文件、学员批量开户/班级关系、一次性凭证 | templateconfig/importjob/studentimport；Web 页面 |
| 导出已有闭环 | 异步队列、审批入口、任务状态、受控下载；已配置的积分与 IAM 审计数据集及复盘 PDF | exportjob；Web export-jobs/growth-reviews |

“家长手机号登录”“微信登录”“账号换号”等虽有服务和页面，真实通道限制较大，统一列入下一节，不能与本地纯业务闭环混为一谈。

## 4. 半成品功能

| 功能 | 已完成部分 | 缺口/限制 |
|---|---|---|
| 周报订阅 | Web 偏好开关、版本控制、内部周一排程、取消/去重/过期处理 | 无真实微信消息授权与发送；无消息送达结果；相关小程序交互未接；调度默认关闭 |
| 匿名排行 | 班级净积分汇总、1/1/3、关系/权限校验、V68 偏好、GET/PUT 接口，专项报告通过 | Web/小程序均无引用，无班级选项/排行页面和公开能力入口；默认关闭；整项不可验收 |
| 微信身份接入 | code2Session 真实 HTTP 适配器、票据/绑定、登录服务 | 未见本轮真实微信成功证据；绑定不是消息订阅授权 |
| 家长短信与账号生命周期 | 短信挑战、校验、换号/注销/恢复状态机、页面 | 非 local/test 实现直接发送失败；local/test 不发短信；真实手机号验证未闭环 |
| 角色看板 | 身份、会话、安全事件、班级及业务入口 | 不等于六角色任务完成率、活跃度、机构趋势等完整看板；家长首页固定“暂无待办事项” |
| 权限/组织管理全覆盖 | 动态授权和多数对象边界已实现 | 全量组织树管理仍仅系统管理员；数据范围配置 UI 不完整，不能称所有操作双端可配 |
| 功能开关与系统任务 | 表、访问拦截、部分业务审批执行 | 没有完整通用开关管理 controller/page 或统一系统任务全载荷执行控制台；主要是领域专用审批 |
| 接口服务管理 | 注册/启停/授权变更审批、调用日志元数据，微信接入登记检查 | 不等于通用 API 网关；全渠道鉴签、防重放、限流、重试/回执执行未形成完整平台 |
| 缓存管理 | 字典清除/预热、会话清除和审核流程 | 当前处理器主要 DICTIONARY、USER_SESSION；后者“清除”实际撤销会话，不支持 refresh |
| 附件体系 | 本地统一附件、规则、关联及管理页 | 无对象存储、跨实例共享/备份、完整格式预览和内容安全生产链路 |
| 导入导出平台 | 模板字段、校验、学员导入、有限导出数据集 | “任意业务模板可配置”不等于有对应业务适配器；其他数据集、完整脱敏/保留销毁策略待交付 |
| 定时/重复任务 | 每日固定计划与调度 | 未见周规则/Cron 用户配置/节假日跳过等完整能力；不得以定时执行器存在宣称全部周期规则完成 |
| 运维与上线 | 设计文档、配置和本地测试资产 | 无完整 CI/CD、监控告警、备份恢复演练、压测/安全测试/UAT/正式发布证据 |

## 5. 完全未开发功能

这里的“未开发”指**可执行交付物未发现**，可能已经有需求、设计、接口端口或表设计；不把“存在文档”计作实现。

1. 真实短信供应商发送适配器：现有生产路径是 UnconfiguredParentSmsSender。
2. 微信周报订阅消息的授权采集、实际发送、回执/不确定结果处理完整流程；扫描未发现 requestSubscribeMessage/订阅消息发送实现。
3. 云对象存储适配器（当前只有 LocalAttachmentContentStorage）。
4. 完整六角色统计看板与后续复杂报表数据集。不是删除已存在的小范围统计，而是目标成品未实现。
5. 定位/围栏签到完整业务链路：主代码未见 uni.getLocation/chooseLocation 或地图签到实现。个人主体暂不启用属于既定限制，不是已完成该原始功能。
6. 可执行 CI 工作流、容器/部署流水线、备份恢复自动化与监控告警部署文件，本次文件盘点未找到 .github/workflows、Docker/Compose/Jenkins 等交付物。
7. 多生产数据库切换实现与测试矩阵：当前仅 MySQL 运行驱动、H2 测试。
8. 匿名排行的两端展示组件、班级选择和入口能力配置（后端已有，不把整个功能列为零开发）。

## 6. 有页面但没有后端的功能

未发现可确认的“整套 CRUD 页面全部靠硬编码数组”的普遍现象；已抽查核心页面采用 apiClient/uni.request 调服务。**下面是页面中的缺失流程，不是说其所属页面所有后端都不存在。**

| 页面/能力 | 当前情况 | 判定 |
|---|---|---|
| miniapp/src/pages/parent-home/parent-home.vue:13 | 直接写“暂无待办事项”，未按待办查询结果渲染 | 待办展示占位，不能作为真实待办后端已交付 |
| Web /dashboard | 身份、安全事件/设备接口真实存在 | 业务经营/学习统计看板后端未完整实现；不是“整个 Dashboard 没后端” |
| 家长短信登录、换号、注销等页面 | 内部 API 存在，真实短信发送器缺失 | 有页面、有业务后端，但缺外部通道，不能归为纯页面假功能 |
| 接口服务、模板管理页面 | 配置元数据 API 存在 | 配置后是否可执行取决于具体适配器，不能把配置页面当完整执行引擎 |
| 其他已注册页面 | 本轮按调用封装、controller 清单与主要流程抽查 | 未逐一以真实账号执行 263 个端点；不宣称“全部页面都有可用后端” |

## 7. 有后端但没有前端的功能

| 后端 | 缺失端/说明 |
|---|---|
| AnonymousRankController 的 3 个端点 | web/src、miniapp/src 未找到 anonymous-ranks/ANONYMOUS_CLASS_RANK 引用，排行 UI 未接 |
| RoleManagementController 的 POST /roles/{roleId}/data-scopes | Web IAM 当前以角色/操作权限为主，未找到同等完整数据范围编辑界面 |
| 通用功能开关、系统任务内部服务 | 存在领域服务/表和专用审核页，但缺通用配置/统一系统任务操作台 |
| 周报内部排程/待发队列 | 无实际消息授权/投递页面；Web 偏好页面不能替代这些功能 |
| 复盘 PDF/周报偏好 | Web 已接；小程序仍未提供同等家长操作闭环，现有小程序复盘使用学生会话 |
| Web 平台管理操作 | 小程序无用户、角色权限、字典、缓存、接口服务、附件/模板平台管理同等界面 |

上述端差异需要与最终角色/端适用矩阵验收。依据最初“操作性功能两端都有”的要求，不能笼统称双端全部完成；学生执行主要在小程序、家长任务管理主要在 Web，同样需单列端覆盖缺口。

## 8. Mock 数据尚未替换的位置

### 8.1 影响真实业务运行的替代实现

| 位置 | 条件 | 行为 | 性质 |
|---|---|---|---|
| auth/infrastructure/memory/LocalParentSmsSender.java | local、test | send 空实现，不调用供应商 | 本地替代实现，不是短信发送成功 |
| auth/infrastructure/sms/UnconfiguredParentSmsSender.java | !local & !test | 抛 SmsDeliveryUnavailableException | 明确未配置、失败关闭，不是 Mock 成功 |
| miniapp/src/pages/parent-home/parent-home.vue:13 | 页面直接渲染 | 固定“暂无待办事项” | 业务占位文案 |
| growth_review_delivery / msg_local_event | 内部流程 | PENDING/CANCELLED 或本地消费状态 | 不是外部消息已送达的证明，不能当真正推送 |

### 8.2 应保留、但不能拿来证明生产能力的测试替身

- server/src/main/java/.../auth/infrastructure/memory/FixedTestParentWechatIdentityGateway.java：test profile 固定微信身份。
- 同目录 FixedTestParentSmsCodeGenerator、TestCaptchaImageGenerator、TestParentWechatIntegrationAccess：测试验证码/图片/接入许可。
- 同目录 InMemory*Store：用于测试的票据、短信校验、保护状态替身；相应 Redis 实现独立存在。
- server/src/test：MockMvc 参数解析替身、Mockito、H2 合成账号与关系。
- web/src/test/setup.ts：ResizeObserver 等 jsdom 替身。
- web/src/**/*.test.ts(x)：vi.mock 接口响应。
- web/scripts/verify-review-*.cjs 及其他验证脚本：Playwright route.fulfill 和 synthetic-token；不是真实登录或远程 API 联调。
- .local-verification 截图/日志、server/target/pdf-verification：合成验证资产，不是生产数据。

未发现 Web/miniapp 主业务 API 普遍由 mock 数组替代的证据。LocalAttachmentContentStorage 是真实本地 I/O，不应错误标成 Mock；WechatCode2SessionGateway 是真实适配器，不应因测试使用固定网关而标成完全未开发。

## 9. TODO / FIXME

- 本轮对 server/src、web/src、miniapp/src 的 TODO/FIXME 文字扫描：**未找到匹配项**。
- docs/superpowers/plans 中匹配到的 TODO/TBD 主要是历史“扫描这些关键词”的命令，不是可执行代码中的待办。
- 没有 TODO 不等于没有缺口。实质待办分布在失败关闭类、默认关闭功能、未接 UI 的接口及实施计划未勾选项中。

应作为后续问题单但本轮不修改：
1. 学生登录失败测试的固定错误码与随机有效码可能碰撞。
2. 匿名排行页面与班级选项未接。
3. 真实短信、真实周报消息尚未接通。
4. 功能开关通用管理/平台任务通用载荷尚未闭环。
5. 双端角色操作覆盖和统计看板不完整。
6. 本地配置凭据进入构建输出，需建立发布排除/密钥注入边界。
7. README/设计/总计划中的阶段数据滞后，V63 与迁移 V68 混读风险较高。

## 10. 无用代码

本轮没有足够证据认定可直接删除的业务类；Spring 注入、MyBatis XML 和计划中待接的组件不能仅按文本引用数判废。

| 候选 | 核查结果 | 建议性质 |
|---|---|---|
| web/src/features/growth-reviews/exportApi.ts 的 download() | 主代码搜索未找到 growthReviewExportApi.download(...) 调用；历史页改用 downloadFile()；测试仍覆盖旧方法 | 兼容包装候选，待调用图确认后清理，非本轮删除 |
| web/vite-history.log、vite-history-error.log | 工作区有未跟踪日志 | 运行资产，不应作为业务源码交付 |
| target、dist、.local-verification、.npm-cache、node_modules | 构建/缓存/验证产物 | 不计功能，不建议盲删，可能包含现场证据 |
| docs 中过时进度段落 | 仍写旧迁移号、PDF 未完成或订阅页面未完成 | 信息债务，应整合最新状态；不是直接删除原始需求 |
| 固定测试网关与 InMemory 实现 | 受 profile 隔离，并被测试依赖 | 不是无用代码，应保留或合理归档 |

## 11. 重复实现

- 对主代码 Java/TS/TSX/Vue/CSS 做内容哈希分组，未发现完全相同文件重复；不代表没有语义重复。
- GrowthReviewSubscriptionService 与 AnonymousRankAccess/AnonymousRankQueryService 重复了“账号、家长且非审核员、对象关系、版本偏好”等控制逻辑。可作为后续安全规则一致性检查对象，不能不分析差异就抽成统一万能服务。
- Web 订阅面板、导出历史、复盘主页面分别实现请求序号/卸载失效保护，存在可复用模式，但各自读取、写入和取消语义不同。
- Web 与 miniapp 的 HTTP、会话和权限映射是独立前端要求的一部分，不应为了消除重复而合并应用。
- LearningTaskController 同时提供 PATCH 与 PUT 更新，代码注释明确 PUT 为 uni-app 请求层兼容，两者调用同一 service；这是协议别名，不是两套业务算法。
- ParentBindingInvitation 与 ParentRelationshipInvitation 对应机构绑定邀请及家长关系生命周期邀请，不能仅按名称相似认定重复。
- 代码量和测试数量不能作为完成率依据，尤其是多个 DTO、Mapper、控制器封装会增加文件数但不增加业务范围。

## 12. 当前无法编译/运行的问题

### 12.1 实际验证结果

| 项目 | 证据 | 结果 |
|---|---|---|
| Web 类型检查 | 本轮 npm run typecheck，tsc --noEmit | 退出 0；没有修改源文件 |
| miniapp 类型检查 | 本轮 npm run type-check，vue-tsc --noEmit | 退出 0；没有修改源文件 |
| Web 最近构建 | .local-verification/subscription-panel-build.log，2026-09-09 | 历史构建通过；本轮未重新打包 |
| Web 最近复盘专项 | .local-verification/subscription-panel-final-tests.log | 6 文件 28 项通过，**不是全部 Web 测试** |
| 后端最近全量 | server/target/rank-backend-full.log 与 surefire-reports | 172 套件 678 项，1 失败，0 错误，0 跳过；编译阶段已通过但测试未全绿 |
| 默认 Maven 启动 | 本轮直接 mvn -v | JVM 报 Failed setting boot class path，当前默认 Java 环境不正确 |
| 指定 JDK17 后 Maven | 设置 JAVA_HOME 指向本机 Temurin 17.0.20 后 mvn -v | 可启动；Maven 3.9.14，不满足原要求 Maven4+ |
| 本地端口 | 本轮监听查询未确认 5173/8080 服务 | 不把页面曾经打开当作服务当前仍可用；本轮未启动/恢复服务 |
| 远程 MySQL/Redis/微信/SMS | 本轮未连接 | 不得给出“已连通”结论 |

### 12.2 需要记录的实际失败

- 文件：[StudentAuthenticationControllerTest.java:114](E:/apps/lingdong-xuexi-code/server/src/test/java/com/lingdong/learning/auth/web/StudentAuthenticationControllerTest.java:114)。
- 用例：returnsStudentSpecificErrorsAndRejectsLoginWhenFeatureIsDisabled。
- 最近全量结果：期望 401，实际 200。
- 测试用固定字符串 `9999` 作为错误登录码；StudentLoginCodeGenerator 使用 `secureRandom.nextInt(10_000)` 生成四位码，可能生成相同值。
- **高可信的测试不稳定风险，但尚不能仅凭此认定这一次失败必然是随机碰撞，更不能直接宣称生产登录绕过。** 本轮未更改测试、未重跑来掩盖失败，需后续单独复现并确定失败当次签发码。
- 匿名排行新增专项与组织引用保护在最新报告中没有列为失败；不能因为一个其他用例失败就否认它们已有实现，也不能因此忽略整体回归门槛。

### 12.3 配置及真实运行阻碍

1. **本地明文配置构建泄露风险。** application-local.yml 有直填数据库/Redis/微信凭据，虽被 Git 忽略，但文件位于 src/main/resources，且本轮确认 target/classes/application-local.yml 已存在。application.yml 还显式导入该资源；当前 pom 未配置该文件资源排除。Git ignore 不等于打包排除。本报告不复制任何凭据，也不声称已找到对外发布的泄露 JAR。
2. **短信生产发送器不可用。** 非 local/test 会直接抛异常；local/test 空发送。需要手机号验证码的真实链路不能完整运行。
3. **小程序真机 API 地址。** VITE_API_BASE_URL 默认空字符串；H5 通过 Vite /api 代理可工作，微信小程序正式构建需要有效 HTTPS 后端及相应配置，不能把 H5 代理等同真机网络配置。
4. **本地附件临时目录。** 默认 java.io.tmpdir 下存储，不具备跨节点共享、永久保留和备份保障。
5. **Flyway 启动执行风险。** spring.flyway.enabled=true；默认应用启动会对所配置数据库检查/执行迁移。本轮不启动生产 profile，不以审计名义自动连接用户远程库。
6. **Maven4+ 未达标。** 工程不是“已验证 Maven4+”；实际测试工具是 Maven3.9.14 + JDK17。
7. **发布基础未就绪。** 没有完整 CI/CD、部署清单、真实消息通道和 UAT 证据。
8. **脏工作区。** 存在大量未提交修改和未跟踪关键实现，包括 V63–V68；仅打包 Git 已提交版本会遗漏当前成果。不能随意 reset 或只按 HEAD 评估。

## 13. 当前数据库表

清单来自实际 V1–V68 CREATE TABLE 语句；扫描未发现 DROP TABLE/RENAME TABLE 将下列集合改变。最近 H2/Flyway 记录成功执行到 V68，Flyway 主键表断言为 94。**远程 MySQL 实际库未查询，以下不是远程库在线盘点。**

- 94 张业务表，全部列于下表。
- Flyway 另维护 flyway_schema_history，属于工具表，不计 94。
- 表的迁移列是“首次建表版本”，后续字段/约束调整还需合并其后 ALTER，不可仅看首次建表脚本。
- 设计稿中的 growth_rank_snapshot 不在当前建表集合；当前匿名排行是实时 SQL 汇总 + growth_rank_preference，不应把设计表当成已落库。

| 序号 | 表名 | 首次建表迁移 |
|---:|---|---|
| 1 | `sys_config` | [V1__create_system_config.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V1__create_system_config.sql) |
| 2 | `sys_organization` | [V2__create_iam_rbac_tables.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V2__create_iam_rbac_tables.sql) |
| 3 | `sys_user` | [V2__create_iam_rbac_tables.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V2__create_iam_rbac_tables.sql) |
| 4 | `sys_role` | [V2__create_iam_rbac_tables.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V2__create_iam_rbac_tables.sql) |
| 5 | `sys_permission` | [V2__create_iam_rbac_tables.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V2__create_iam_rbac_tables.sql) |
| 6 | `sys_user_role` | [V2__create_iam_rbac_tables.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V2__create_iam_rbac_tables.sql) |
| 7 | `sys_role_permission` | [V2__create_iam_rbac_tables.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V2__create_iam_rbac_tables.sql) |
| 8 | `sys_organization_admin` | [V2__create_iam_rbac_tables.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V2__create_iam_rbac_tables.sql) |
| 9 | `sys_organization_type` | [V3__create_organization_types.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V3__create_organization_types.sql) |
| 10 | `sys_user_organization` | [V4__create_user_organization_relations.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V4__create_user_organization_relations.sql) |
| 11 | `sys_system_task` | [V5__create_system_task_audit.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V5__create_system_task_audit.sql) |
| 12 | `sys_feature_toggle` | [V6__create_feature_toggles.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V6__create_feature_toggles.sql) |
| 13 | `sys_feature_toggle_change` | [V7__create_feature_toggle_changes.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V7__create_feature_toggle_changes.sql) |
| 14 | `sys_user_permission` | [V8__create_user_permissions.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V8__create_user_permissions.sql) |
| 15 | `sys_role_data_scope` | [V9__create_role_data_scopes.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V9__create_role_data_scopes.sql) |
| 16 | `sys_dictionary_type` | [V10__create_dictionary_tables.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V10__create_dictionary_tables.sql) |
| 17 | `sys_dictionary_item` | [V10__create_dictionary_tables.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V10__create_dictionary_tables.sql) |
| 18 | `sys_cache_operation_log` | [V11__create_cache_operation_log.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V11__create_cache_operation_log.sql) |
| 19 | `sys_interface_service` | [V12__create_interface_service_tables.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V12__create_interface_service_tables.sql) |
| 20 | `sys_interface_service_change` | [V12__create_interface_service_tables.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V12__create_interface_service_tables.sql) |
| 21 | `sys_interface_call_log` | [V12__create_interface_service_tables.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V12__create_interface_service_tables.sql) |
| 22 | `sys_attachment_rule` | [V13__create_attachment_core_tables.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V13__create_attachment_core_tables.sql) |
| 23 | `sys_attachment_rule_extension` | [V13__create_attachment_core_tables.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V13__create_attachment_core_tables.sql) |
| 24 | `sys_file` | [V13__create_attachment_core_tables.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V13__create_attachment_core_tables.sql) |
| 25 | `sys_file_relation` | [V13__create_attachment_core_tables.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V13__create_attachment_core_tables.sql) |
| 26 | `sys_import_export_template` | [V14__create_import_export_template_table.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V14__create_import_export_template_table.sql) |
| 27 | `auth_device_session` | [V15__create_auth_device_session.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V15__create_auth_device_session.sql) |
| 28 | `edu_student` | [V19__create_student_relationship_foundation.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V19__create_student_relationship_foundation.sql) |
| 29 | `edu_parent_student` | [V19__create_student_relationship_foundation.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V19__create_student_relationship_foundation.sql) |
| 30 | `edu_student_organization` | [V19__create_student_relationship_foundation.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V19__create_student_relationship_foundation.sql) |
| 31 | `edu_parent_binding_invitation` | [V20__create_parent_binding_invitation.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V20__create_parent_binding_invitation.sql) |
| 32 | `auth_student_account_sequence` | [V21__create_student_code_login.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V21__create_student_code_login.sql) |
| 33 | `auth_student_credential` | [V21__create_student_code_login.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V21__create_student_code_login.sql) |
| 34 | `edu_teacher_class` | [V22__create_learning_task_foundation.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V22__create_learning_task_foundation.sql) |
| 35 | `learn_task` | [V22__create_learning_task_foundation.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V22__create_learning_task_foundation.sql) |
| 36 | `learn_task_target` | [V22__create_learning_task_foundation.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V22__create_learning_task_foundation.sql) |
| 37 | `learn_task_tag` | [V22__create_learning_task_foundation.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V22__create_learning_task_foundation.sql) |
| 38 | `learn_task_assignment` | [V22__create_learning_task_foundation.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V22__create_learning_task_foundation.sql) |
| 39 | `learn_task_assignment_event` | [V23__create_task_execution_foundation.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V23__create_task_execution_foundation.sql) |
| 40 | `learn_task_pause` | [V23__create_task_execution_foundation.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V23__create_task_execution_foundation.sql) |
| 41 | `learn_task_checkin` | [V23__create_task_execution_foundation.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V23__create_task_execution_foundation.sql) |
| 42 | `learn_task_reviewer_transfer` | [V23__create_task_execution_foundation.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V23__create_task_execution_foundation.sql) |
| 43 | `growth_point_account` | [V24__create_growth_point_account_and_ledger.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V24__create_growth_point_account_and_ledger.sql) |
| 44 | `growth_point_ledger` | [V24__create_growth_point_account_and_ledger.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V24__create_growth_point_account_and_ledger.sql) |
| 45 | `growth_reward` | [V27__add_reward_exchange.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V27__add_reward_exchange.sql) |
| 46 | `growth_reward_exchange` | [V27__add_reward_exchange.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V27__add_reward_exchange.sql) |
| 47 | `growth_review` | [V28__add_growth_review.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V28__add_growth_review.sql) |
| 48 | `growth_review_snapshot` | [V28__add_growth_review.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V28__add_growth_review.sql) |
| 49 | `growth_review_category_stat` | [V28__add_growth_review.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V28__add_growth_review.sql) |
| 50 | `growth_review_daily_trend` | [V28__add_growth_review.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V28__add_growth_review.sql) |
| 51 | `growth_review_supplement` | [V28__add_growth_review.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V28__add_growth_review.sql) |
| 52 | `growth_point_decay_rule` | [V29__add_point_lifecycle.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V29__add_point_lifecycle.sql) |
| 53 | `growth_point_dormancy_state` | [V29__add_point_lifecycle.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V29__add_point_lifecycle.sql) |
| 54 | `growth_point_dormancy_notice` | [V29__add_point_lifecycle.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V29__add_point_lifecycle.sql) |
| 55 | `learn_task_recurrence` | [V30__add_recurring_task.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V30__add_recurring_task.sql) |
| 56 | `learn_task_defer_history` | [V32__add_task_overdue_defer.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V32__add_task_overdue_defer.sql) |
| 57 | `learn_task_copy_batch` | [V33__add_previous_day_task_copy.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V33__add_previous_day_task_copy.sql) |
| 58 | `learn_task_copy_item` | [V33__add_previous_day_task_copy.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V33__add_previous_day_task_copy.sql) |
| 59 | `learn_task_template` | [V34__add_learning_task_template.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V34__add_learning_task_template.sql) |
| 60 | `learn_task_template_tag` | [V34__add_learning_task_template.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V34__add_learning_task_template.sql) |
| 61 | `auth_student_qr_ticket` | [V35__add_student_qr_login.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V35__add_student_qr_login.sql) |
| 62 | `auth_user_agreement_acceptance` | [V36__add_parent_phone_auth.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V36__add_parent_phone_auth.sql) |
| 63 | `auth_parent_profile` | [V36__add_parent_phone_auth.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V36__add_parent_phone_auth.sql) |
| 64 | `auth_parent_wechat_binding` | [V37__add_parent_wechat_auth.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V37__add_parent_wechat_auth.sql) |
| 65 | `edu_parent_relationship_invitation` | [V38__add_parent_relationship_lifecycle.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V38__add_parent_relationship_lifecycle.sql) |
| 66 | `edu_parent_relationship_change_log` | [V38__add_parent_relationship_lifecycle.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V38__add_parent_relationship_lifecycle.sql) |
| 67 | `auth_security_event` | [V39__add_account_security_event.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V39__add_account_security_event.sql) |
| 68 | `edu_student_organization_change` | [V41__add_student_organization_lifecycle.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V41__add_student_organization_lifecycle.sql) |
| 69 | `auth_parent_mobile_change` | [V42__add_parent_account_lifecycle.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V42__add_parent_account_lifecycle.sql) |
| 70 | `auth_parent_account_cancellation` | [V42__add_parent_account_lifecycle.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V42__add_parent_account_lifecycle.sql) |
| 71 | `auth_parent_mobile_manual_recovery` | [V44__add_parent_mobile_manual_recovery.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V44__add_parent_mobile_manual_recovery.sql) |
| 72 | `auth_student_account_cancellation` | [V45__add_student_account_cancellation.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V45__add_student_account_cancellation.sql) |
| 73 | `auth_student_wechat_binding` | [V46__add_student_wechat_auth.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V46__add_student_wechat_auth.sql) |
| 74 | `auth_student_wechat_binding_audit` | [V46__add_student_wechat_auth.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V46__add_student_wechat_auth.sql) |
| 75 | `sys_organization_change` | [V47__add_organization_node_lifecycle.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V47__add_organization_node_lifecycle.sql) |
| 76 | `sys_organization_change_audit` | [V47__add_organization_node_lifecycle.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V47__add_organization_node_lifecycle.sql) |
| 77 | `sys_iam_change_audit` | [V50__add_iam_change_audit.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V50__add_iam_change_audit.sql) |
| 78 | `sys_import_export_template_field` | [V56__add_import_validation_job.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V56__add_import_validation_job.sql) |
| 79 | `sys_import_job` | [V56__add_import_validation_job.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V56__add_import_validation_job.sql) |
| 80 | `sys_import_job_row_result` | [V56__add_import_validation_job.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V56__add_import_validation_job.sql) |
| 81 | `sys_export_job` | [V57__add_async_export_job.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V57__add_async_export_job.sql) |
| 82 | `sys_export_job_event` | [V57__add_async_export_job.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V57__add_async_export_job.sql) |
| 83 | `sys_student_import_execution` | [V58__add_student_batch_import.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V58__add_student_batch_import.sql) |
| 84 | `sys_student_import_row` | [V58__add_student_batch_import.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V58__add_student_batch_import.sql) |
| 85 | `edu_teacher_class_change_log` | [V59__add_teacher_management.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V59__add_teacher_management.sql) |
| 86 | `edu_exception_report` | [V61__add_student_exception_report.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V61__add_student_exception_report.sql) |
| 87 | `edu_exception_report_action` | [V61__add_student_exception_report.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V61__add_student_exception_report.sql) |
| 88 | `msg_local_event` | [V61__add_student_exception_report.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V61__add_student_exception_report.sql) |
| 89 | `attendance_record` | [V62__add_manual_attendance.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V62__add_manual_attendance.sql) |
| 90 | `attendance_record_action` | [V62__add_manual_attendance.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V62__add_manual_attendance.sql) |
| 91 | `sys_export_job_payload` | [V63__add_export_job_payload.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V63__add_export_job_payload.sql) |
| 92 | `growth_review_subscription` | [V66__add_growth_review_subscription.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V66__add_growth_review_subscription.sql) |
| 93 | `growth_review_delivery` | [V67__add_growth_review_delivery_queue.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V67__add_growth_review_delivery_queue.sql) |
| 94 | `growth_rank_preference` | [V68__add_anonymous_rank_preference.sql](E:/apps/lingdong-xuexi-code/server/src/main/resources/db/migration/V68__add_anonymous_rank_preference.sql) |

## 14. API 清单

以下为主代码 52 个 controller 的 **263 个 HTTP 方法映射**，从 Spring 映射注解读取并合并类级前缀。GET/POST 同路径分别计数；包含有意兼容的 PATCH/PUT。它们是“存在实现入口”，不等于每个入口都通过真实登录、授权、参数边界和外部依赖验收。

- 查询参数、请求体字段及动态权限条件请跟随源码链接查看；本表不是完整 OpenAPI schema。
- 第三方 URI、测试路由、前端路由不计为业务 API。
- springdoc 配置额外生成 `/api/v1/openapi`（及其框架子资源），不计入业务 controller 的 263 项。
- 除 SecurityConfiguration 明列公开的健康、登录、验证码、刷新及部分邀请/公开能力端点外，/api/v1/** 需要认证；具体业务权限还在拦截器/service 层校验。
- 匿名排行虽有 API，功能默认关闭且无 UI；短信 API 虽有实现，真实发送通道未配置。

| 方法 | 路径 | 控制器源码 |
|---|---|---|
| GET | `/api/v1/attachment-management/rules` | [AttachmentManagementController.java:52](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/attachment/web/AttachmentManagementController.java:52) |
| POST | `/api/v1/attachment-management/rules` | [AttachmentManagementController.java:67](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/attachment/web/AttachmentManagementController.java:67) |
| PUT | `/api/v1/attachment-management/rules/{id}` | [AttachmentManagementController.java:81](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/attachment/web/AttachmentManagementController.java:81) |
| POST | `/api/v1/attachment-management/rules/{id}/enable` | [AttachmentManagementController.java:94](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/attachment/web/AttachmentManagementController.java:94) |
| POST | `/api/v1/attachment-management/rules/{id}/disable` | [AttachmentManagementController.java:105](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/attachment/web/AttachmentManagementController.java:105) |
| GET | `/api/v1/attachment-management/files` | [AttachmentManagementController.java:116](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/attachment/web/AttachmentManagementController.java:116) |
| GET | `/api/v1/attachment-management/files/{id}/relations` | [AttachmentManagementController.java:137](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/attachment/web/AttachmentManagementController.java:137) |
| POST | `/api/v1/attachments/uploads` | [TaskAttachmentController.java:36](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/attachment/web/TaskAttachmentController.java:36) |
| GET | `/api/v1/attachments/{id}` | [TaskAttachmentController.java:54](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/attachment/web/TaskAttachmentController.java:54) |
| GET | `/api/v1/attachments/{id}/content` | [TaskAttachmentController.java:62](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/attachment/web/TaskAttachmentController.java:62) |
| GET | `/api/v1/attachments/{id}/download` | [TaskAttachmentController.java:84](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/attachment/web/TaskAttachmentController.java:84) |
| DELETE | `/api/v1/attachments/{id}` | [TaskAttachmentController.java:106](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/attachment/web/TaskAttachmentController.java:106) |
| GET | `/api/v1/attendance-records` | [AttendanceController.java:39](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/attendance/web/AttendanceController.java:39) |
| GET | `/api/v1/attendance-records/class-options` | [AttendanceController.java:51](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/attendance/web/AttendanceController.java:51) |
| GET | `/api/v1/attendance-records/roster` | [AttendanceController.java:59](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/attendance/web/AttendanceController.java:59) |
| POST | `/api/v1/attendance-records/batch` | [AttendanceController.java:69](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/attendance/web/AttendanceController.java:69) |
| GET | `/api/v1/attendance-records/{id}` | [AttendanceController.java:76](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/attendance/web/AttendanceController.java:76) |
| POST | `/api/v1/auth/sessions/password` | [AuthenticationController.java:47](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/AuthenticationController.java:47) |
| POST | `/api/v1/auth/organization-sessions/password` | [AuthenticationController.java:54](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/AuthenticationController.java:54) |
| POST | `/api/v1/auth/sessions/refresh` | [AuthenticationController.java:63](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/AuthenticationController.java:63) |
| DELETE | `/api/v1/auth/sessions/current` | [AuthenticationController.java:68](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/AuthenticationController.java:68) |
| GET | `/api/v1/auth/me` | [AuthenticationController.java:74](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/AuthenticationController.java:74) |
| GET | `/api/v1/auth/devices` | [AuthenticationController.java:82](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/AuthenticationController.java:82) |
| DELETE | `/api/v1/auth/devices/{sessionId}` | [AuthenticationController.java:89](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/AuthenticationController.java:89) |
| POST | `/api/v1/auth/devices/sign-out-all` | [AuthenticationController.java:95](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/AuthenticationController.java:95) |
| GET | `/api/v1/auth/security-events` | [AuthenticationController.java:101](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/AuthenticationController.java:101) |
| POST | `/api/v1/auth/security-events/{eventId}/read` | [AuthenticationController.java:111](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/AuthenticationController.java:111) |
| POST | `/api/v1/auth/security-events/read-all` | [AuthenticationController.java:120](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/AuthenticationController.java:120) |
| GET | `/api/v1/public/parent-auth-context` | [ParentAuthenticationController.java:49](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentAuthenticationController.java:49) |
| GET | `/api/v1/auth/parent-state` | [ParentAuthenticationController.java:54](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentAuthenticationController.java:54) |
| POST | `/api/v1/auth/parent-sms-codes` | [ParentAuthenticationController.java:59](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentAuthenticationController.java:59) |
| POST | `/api/v1/auth/parent-sessions/sms` | [ParentAuthenticationController.java:70](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentAuthenticationController.java:70) |
| POST | `/api/v1/auth/parent-sessions/password` | [ParentAuthenticationController.java:81](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentAuthenticationController.java:81) |
| POST | `/api/v1/auth/parent-wechat-sessions` | [ParentAuthenticationController.java:87](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentAuthenticationController.java:87) |
| POST | `/api/v1/auth/parent-wechat-bindings` | [ParentAuthenticationController.java:96](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentAuthenticationController.java:96) |
| POST | `/api/v1/auth/parent-passwords` | [ParentAuthenticationController.java:107](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentAuthenticationController.java:107) |
| POST | `/api/v1/auth/parent-password-resets` | [ParentAuthenticationController.java:116](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentAuthenticationController.java:116) |
| POST | `/api/v1/auth/parent-agreement-acceptances` | [ParentAuthenticationController.java:123](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentAuthenticationController.java:123) |
| POST | `/api/v1/parent-onboarding/completion` | [ParentAuthenticationController.java:135](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentAuthenticationController.java:135) |
| GET | `/api/v1/auth/parent-account-lifecycle` | [ParentAuthenticationController.java:141](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentAuthenticationController.java:141) |
| POST | `/api/v1/auth/parent-mobile-change/current-codes` | [ParentAuthenticationController.java:149](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentAuthenticationController.java:149) |
| POST | `/api/v1/auth/parent-mobile-change-tickets` | [ParentAuthenticationController.java:160](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentAuthenticationController.java:160) |
| POST | `/api/v1/auth/parent-mobile-change/new-codes` | [ParentAuthenticationController.java:171](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentAuthenticationController.java:171) |
| POST | `/api/v1/auth/parent-mobile-changes` | [ParentAuthenticationController.java:184](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentAuthenticationController.java:184) |
| POST | `/api/v1/auth/parent-account-cancellation-codes` | [ParentAuthenticationController.java:195](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentAuthenticationController.java:195) |
| POST | `/api/v1/auth/parent-account-cancellations` | [ParentAuthenticationController.java:206](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentAuthenticationController.java:206) |
| DELETE | `/api/v1/auth/parent-account-cancellations/current` | [ParentAuthenticationController.java:218](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentAuthenticationController.java:218) |
| GET | `/api/v1/organization-parent-mobile-recoveries/candidates` | [ParentMobileManualRecoveryController.java:40](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentMobileManualRecoveryController.java:40) |
| POST | `/api/v1/organization-parent-mobile-recoveries/codes` | [ParentMobileManualRecoveryController.java:50](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentMobileManualRecoveryController.java:50) |
| POST | `/api/v1/organization-parent-mobile-recoveries` | [ParentMobileManualRecoveryController.java:65](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/ParentMobileManualRecoveryController.java:65) |
| POST | `/api/v1/auth/student-captchas` | [StudentAuthenticationController.java:42](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/StudentAuthenticationController.java:42) |
| POST | `/api/v1/auth/student-sessions/code` | [StudentAuthenticationController.java:50](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/StudentAuthenticationController.java:50) |
| POST | `/api/v1/auth/student-sessions/qr` | [StudentAuthenticationController.java:63](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/StudentAuthenticationController.java:63) |
| POST | `/api/v1/auth/student-qr-captchas` | [StudentAuthenticationController.java:73](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/StudentAuthenticationController.java:73) |
| POST | `/api/v1/auth/student-wechat-sessions` | [StudentWechatAuthenticationController.java:25](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/StudentWechatAuthenticationController.java:25) |
| POST | `/api/v1/auth/student-wechat-binding-codes` | [StudentWechatAuthenticationController.java:31](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/StudentWechatAuthenticationController.java:31) |
| POST | `/api/v1/auth/student-wechat-bindings` | [StudentWechatAuthenticationController.java:44](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/StudentWechatAuthenticationController.java:44) |
| GET | `/api/v1/student-wechat-bindings` | [StudentWechatBindingManagementController.java:28](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/StudentWechatBindingManagementController.java:28) |
| POST | `/api/v1/students/{studentId}/wechat-unbindings` | [StudentWechatBindingManagementController.java:36](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/auth/web/StudentWechatBindingManagementController.java:36) |
| GET | `/api/v1/cache-management/operations` | [CacheManagementController.java:40](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/cache/web/CacheManagementController.java:40) |
| POST | `/api/v1/cache-management/operations` | [CacheManagementController.java:51](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/cache/web/CacheManagementController.java:51) |
| POST | `/api/v1/cache-management/review-submissions` | [CacheManagementController.java:67](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/cache/web/CacheManagementController.java:67) |
| GET | `/api/v1/cache-management/review-queue` | [CacheManagementController.java:86](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/cache/web/CacheManagementController.java:86) |
| POST | `/api/v1/cache-management/review-queue/{taskId}/approve` | [CacheManagementController.java:97](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/cache/web/CacheManagementController.java:97) |
| POST | `/api/v1/cache-management/review-queue/{taskId}/reject` | [CacheManagementController.java:109](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/cache/web/CacheManagementController.java:109) |
| GET | `/api/v1/health` | [HealthController.java:12](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/common/web/HealthController.java:12) |
| GET | `/api/v1/dictionaries/types` | [DictionaryManagementController.java:43](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/dictionary/web/DictionaryManagementController.java:43) |
| POST | `/api/v1/dictionaries/types` | [DictionaryManagementController.java:54](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/dictionary/web/DictionaryManagementController.java:54) |
| PUT | `/api/v1/dictionaries/types/{typeId}` | [DictionaryManagementController.java:67](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/dictionary/web/DictionaryManagementController.java:67) |
| GET | `/api/v1/dictionaries/types/{typeId}/items` | [DictionaryManagementController.java:80](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/dictionary/web/DictionaryManagementController.java:80) |
| POST | `/api/v1/dictionaries/types/{typeId}/items` | [DictionaryManagementController.java:92](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/dictionary/web/DictionaryManagementController.java:92) |
| PUT | `/api/v1/dictionaries/items/{itemId}` | [DictionaryManagementController.java:107](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/dictionary/web/DictionaryManagementController.java:107) |
| POST | `/api/v1/exception-reports` | [ExceptionReportController.java:30](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/exceptionreport/web/ExceptionReportController.java:30) |
| GET | `/api/v1/exception-reports` | [ExceptionReportController.java:38](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/exceptionreport/web/ExceptionReportController.java:38) |
| GET | `/api/v1/exception-reports/class-options` | [ExceptionReportController.java:51](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/exceptionreport/web/ExceptionReportController.java:51) |
| GET | `/api/v1/exception-reports/student-options` | [ExceptionReportController.java:58](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/exceptionreport/web/ExceptionReportController.java:58) |
| GET | `/api/v1/exception-reports/{id}` | [ExceptionReportController.java:67](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/exceptionreport/web/ExceptionReportController.java:67) |
| POST | `/api/v1/exception-reports/{id}/handle` | [ExceptionReportController.java:74](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/exceptionreport/web/ExceptionReportController.java:74) |
| POST | `/api/v1/export-jobs` | [ExportJobController.java:48](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/exportjob/web/ExportJobController.java:48) |
| GET | `/api/v1/export-jobs` | [ExportJobController.java:60](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/exportjob/web/ExportJobController.java:60) |
| GET | `/api/v1/export-jobs/options` | [ExportJobController.java:73](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/exportjob/web/ExportJobController.java:73) |
| GET | `/api/v1/export-jobs/{id}` | [ExportJobController.java:83](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/exportjob/web/ExportJobController.java:83) |
| GET | `/api/v1/export-jobs/{id}/download` | [ExportJobController.java:92](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/exportjob/web/ExportJobController.java:92) |
| GET | `/api/v1/export-job-reviews` | [ExportJobReviewController.java:33](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/exportjob/web/ExportJobReviewController.java:33) |
| POST | `/api/v1/export-job-reviews/{taskId}/approve` | [ExportJobReviewController.java:44](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/exportjob/web/ExportJobReviewController.java:44) |
| POST | `/api/v1/export-job-reviews/{taskId}/reject` | [ExportJobReviewController.java:55](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/exportjob/web/ExportJobReviewController.java:55) |
| GET | `/api/v1/growth-review-export-jobs` | [GrowthReviewExportController.java:31](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/exportjob/web/GrowthReviewExportController.java:31) |
| GET | `/api/v1/growth-review-export-jobs/{id}` | [GrowthReviewExportController.java:39](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/exportjob/web/GrowthReviewExportController.java:39) |
| GET | `/api/v1/growth-review-export-jobs/{id}/download` | [GrowthReviewExportController.java:45](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/exportjob/web/GrowthReviewExportController.java:45) |
| POST | `/api/v1/growth-review-export-jobs` | [GrowthReviewExportController.java:60](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/exportjob/web/GrowthReviewExportController.java:60) |
| GET | `/api/v1/growth-review-export-jobs/options` | [GrowthReviewExportOptionController.java:24](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/exportjob/web/GrowthReviewExportOptionController.java:24) |
| GET | `/api/v1/public/capabilities` | [PublicCapabilityController.java:52](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityController.java:52) |
| GET | `/api/v1/anonymous-ranks/students/{studentId}/classes/{classId}` | [AnonymousRankController.java:19](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/AnonymousRankController.java:19) |
| GET | `/api/v1/anonymous-ranks/students/{studentId}/classes/{classId}/preference` | [AnonymousRankController.java:24](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/AnonymousRankController.java:24) |
| PUT | `/api/v1/anonymous-ranks/students/{studentId}/classes/{classId}/preference` | [AnonymousRankController.java:29](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/AnonymousRankController.java:29) |
| POST | `/api/v1/growth-points/students/{studentId}/corrections` | [GrowthPointCorrectionController.java:25](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthPointCorrectionController.java:25) |
| GET | `/api/v1/growth-points/me/account` | [GrowthPointQueryController.java:26](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthPointQueryController.java:26) |
| GET | `/api/v1/growth-points/me/ledgers` | [GrowthPointQueryController.java:34](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthPointQueryController.java:34) |
| GET | `/api/v1/growth-points/students` | [GrowthPointQueryController.java:45](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthPointQueryController.java:45) |
| GET | `/api/v1/growth-points/students/{studentId}/account` | [GrowthPointQueryController.java:55](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthPointQueryController.java:55) |
| GET | `/api/v1/growth-points/students/{studentId}/ledgers` | [GrowthPointQueryController.java:65](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthPointQueryController.java:65) |
| GET | `/api/v1/growth-reviews/me` | [GrowthReviewController.java:30](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthReviewController.java:30) |
| GET | `/api/v1/growth-reviews/me/{reviewId}` | [GrowthReviewController.java:42](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthReviewController.java:42) |
| POST | `/api/v1/growth-reviews/me/{reviewId}/supplements` | [GrowthReviewController.java:52](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthReviewController.java:52) |
| GET | `/api/v1/growth-reviews/students/{studentId}` | [GrowthReviewController.java:64](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthReviewController.java:64) |
| GET | `/api/v1/growth-reviews/students/{studentId}/{reviewId}` | [GrowthReviewController.java:77](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthReviewController.java:77) |
| POST | `/api/v1/growth-reviews/students/{studentId}/{reviewId}/supplements` | [GrowthReviewController.java:88](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthReviewController.java:88) |
| GET | `/api/v1/growth-review-subscriptions/students/{studentId}` | [GrowthReviewSubscriptionController.java:16](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthReviewSubscriptionController.java:16) |
| PUT | `/api/v1/growth-review-subscriptions/students/{studentId}` | [GrowthReviewSubscriptionController.java:20](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthReviewSubscriptionController.java:20) |
| GET | `/api/v1/rewards/students/{studentId}` | [GrowthRewardController.java:33](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthRewardController.java:33) |
| POST | `/api/v1/rewards/students/{studentId}` | [GrowthRewardController.java:45](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthRewardController.java:45) |
| PATCH | `/api/v1/rewards/{rewardId}` | [GrowthRewardController.java:57](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthRewardController.java:57) |
| DELETE | `/api/v1/rewards/{rewardId}` | [GrowthRewardController.java:68](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthRewardController.java:68) |
| GET | `/api/v1/rewards/me` | [GrowthRewardController.java:78](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthRewardController.java:78) |
| GET | `/api/v1/rewards/me/summary` | [GrowthRewardController.java:88](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthRewardController.java:88) |
| POST | `/api/v1/reward-exchanges` | [GrowthRewardExchangeController.java:31](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthRewardExchangeController.java:31) |
| GET | `/api/v1/reward-exchanges/me` | [GrowthRewardExchangeController.java:42](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthRewardExchangeController.java:42) |
| GET | `/api/v1/reward-exchanges/students/{studentId}` | [GrowthRewardExchangeController.java:53](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthRewardExchangeController.java:53) |
| POST | `/api/v1/reward-exchanges/{exchangeId}/approve` | [GrowthRewardExchangeController.java:65](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthRewardExchangeController.java:65) |
| POST | `/api/v1/reward-exchanges/{exchangeId}/reject` | [GrowthRewardExchangeController.java:75](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthRewardExchangeController.java:75) |
| POST | `/api/v1/reward-exchanges/{exchangeId}/verify` | [GrowthRewardExchangeController.java:86](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthRewardExchangeController.java:86) |
| POST | `/api/v1/organization-admins` | [DataScopeManagementController.java:26](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/DataScopeManagementController.java:26) |
| GET | `/api/v1/iam/audits` | [IamChangeAuditController.java:26](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/IamChangeAuditController.java:26) |
| GET | `/api/v1/permissions` | [PermissionManagementController.java:42](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/PermissionManagementController.java:42) |
| POST | `/api/v1/permissions` | [PermissionManagementController.java:48](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/PermissionManagementController.java:48) |
| POST | `/api/v1/roles/{roleId}/permissions` | [PermissionManagementController.java:60](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/PermissionManagementController.java:60) |
| GET | `/api/v1/roles/{roleId}/permissions` | [PermissionManagementController.java:73](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/PermissionManagementController.java:73) |
| PUT | `/api/v1/roles/{roleId}/permissions/{permissionId}` | [PermissionManagementController.java:83](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/PermissionManagementController.java:83) |
| DELETE | `/api/v1/roles/{roleId}/permissions/{permissionId}` | [PermissionManagementController.java:96](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/PermissionManagementController.java:96) |
| PUT | `/api/v1/users/{userId}/permissions/{permissionId}` | [PermissionManagementController.java:108](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/PermissionManagementController.java:108) |
| GET | `/api/v1/users/{userId}/permissions` | [PermissionManagementController.java:122](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/PermissionManagementController.java:122) |
| DELETE | `/api/v1/users/{userId}/permissions/{permissionId}` | [PermissionManagementController.java:132](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/PermissionManagementController.java:132) |
| GET | `/api/v1/roles` | [RoleManagementController.java:41](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/RoleManagementController.java:41) |
| POST | `/api/v1/roles` | [RoleManagementController.java:47](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/RoleManagementController.java:47) |
| POST | `/api/v1/roles/{roleId}/data-scopes` | [RoleManagementController.java:57](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/RoleManagementController.java:57) |
| GET | `/api/v1/users/{id}` | [UserManagementController.java:47](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/UserManagementController.java:47) |
| GET | `/api/v1/users` | [UserManagementController.java:53](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/UserManagementController.java:53) |
| POST | `/api/v1/users` | [UserManagementController.java:65](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/UserManagementController.java:65) |
| PATCH | `/api/v1/users/{id}/status` | [UserManagementController.java:75](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/UserManagementController.java:75) |
| POST | `/api/v1/users/{id}/organizations` | [UserManagementController.java:83](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/UserManagementController.java:83) |
| POST | `/api/v1/users/{id}/roles` | [UserManagementController.java:95](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/UserManagementController.java:95) |
| POST | `/api/v1/users/{id}/password` | [UserManagementController.java:104](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/iam/web/UserManagementController.java:104) |
| POST | `/api/v1/import-jobs` | [ImportJobController.java:50](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/importjob/web/ImportJobController.java:50) |
| GET | `/api/v1/import-jobs` | [ImportJobController.java:68](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/importjob/web/ImportJobController.java:68) |
| GET | `/api/v1/import-jobs/options` | [ImportJobController.java:86](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/importjob/web/ImportJobController.java:86) |
| GET | `/api/v1/import-jobs/{id}` | [ImportJobController.java:92](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/importjob/web/ImportJobController.java:92) |
| GET | `/api/v1/import-jobs/{id}/errors` | [ImportJobController.java:100](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/importjob/web/ImportJobController.java:100) |
| GET | `/api/v1/import-jobs/{id}/source-file` | [ImportJobController.java:112](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/importjob/web/ImportJobController.java:112) |
| GET | `/api/v1/import-jobs/{id}/error-file` | [ImportJobController.java:120](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/importjob/web/ImportJobController.java:120) |
| GET | `/api/v1/interface-services` | [InterfaceServiceManagementController.java:46](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/interfaceconfig/web/InterfaceServiceManagementController.java:46) |
| GET | `/api/v1/interface-services/changes` | [InterfaceServiceManagementController.java:63](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/interfaceconfig/web/InterfaceServiceManagementController.java:63) |
| GET | `/api/v1/interface-services/call-logs` | [InterfaceServiceManagementController.java:74](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/interfaceconfig/web/InterfaceServiceManagementController.java:74) |
| GET | `/api/v1/interface-services/review-queue` | [InterfaceServiceManagementController.java:87](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/interfaceconfig/web/InterfaceServiceManagementController.java:87) |
| POST | `/api/v1/interface-services/registration-submissions` | [InterfaceServiceManagementController.java:98](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/interfaceconfig/web/InterfaceServiceManagementController.java:98) |
| POST | `/api/v1/interface-services/{serviceId}/enable-submissions` | [InterfaceServiceManagementController.java:114](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/interfaceconfig/web/InterfaceServiceManagementController.java:114) |
| POST | `/api/v1/interface-services/{serviceId}/disable-submissions` | [InterfaceServiceManagementController.java:129](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/interfaceconfig/web/InterfaceServiceManagementController.java:129) |
| POST | `/api/v1/interface-services/{serviceId}/authorization-submissions` | [InterfaceServiceManagementController.java:144](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/interfaceconfig/web/InterfaceServiceManagementController.java:144) |
| POST | `/api/v1/interface-services/review-tasks/{taskId}/approve` | [InterfaceServiceManagementController.java:160](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/interfaceconfig/web/InterfaceServiceManagementController.java:160) |
| POST | `/api/v1/interface-services/review-tasks/{taskId}/reject` | [InterfaceServiceManagementController.java:172](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/interfaceconfig/web/InterfaceServiceManagementController.java:172) |
| POST | `/api/v1/learning-tasks` | [LearningTaskController.java:53](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskController.java:53) |
| PATCH | `/api/v1/learning-tasks/{id}` | [LearningTaskController.java:63](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskController.java:63) |
| PUT | `/api/v1/learning-tasks/{id}` | [LearningTaskController.java:74](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskController.java:74) |
| GET | `/api/v1/learning-tasks/{id}` | [LearningTaskController.java:84](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskController.java:84) |
| GET | `/api/v1/learning-tasks` | [LearningTaskController.java:92](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskController.java:92) |
| POST | `/api/v1/learning-tasks/batch-publish` | [LearningTaskController.java:107](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskController.java:107) |
| POST | `/api/v1/learning-tasks/{id}/publish` | [LearningTaskController.java:117](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskController.java:117) |
| POST | `/api/v1/learning-tasks/{id}/recurrence/stop` | [LearningTaskController.java:125](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskController.java:125) |
| GET | `/api/v1/learning-tasks/{id}/progress` | [LearningTaskController.java:133](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskController.java:133) |
| GET | `/api/v1/learning-task-options/organizations` | [LearningTaskOptionController.java:26](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskOptionController.java:26) |
| GET | `/api/v1/learning-task-options/students` | [LearningTaskOptionController.java:38](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskOptionController.java:38) |
| GET | `/api/v1/learning-task-options/teachers` | [LearningTaskOptionController.java:51](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskOptionController.java:51) |
| GET | `/api/v1/task-templates` | [LearningTaskTemplateController.java:33](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskTemplateController.java:33) |
| POST | `/api/v1/task-templates` | [LearningTaskTemplateController.java:43](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskTemplateController.java:43) |
| PATCH | `/api/v1/task-templates/{templateId}` | [LearningTaskTemplateController.java:54](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskTemplateController.java:54) |
| DELETE | `/api/v1/task-templates/{templateId}` | [LearningTaskTemplateController.java:65](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskTemplateController.java:65) |
| PUT | `/api/v1/task-templates/personal-order` | [LearningTaskTemplateController.java:76](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskTemplateController.java:76) |
| POST | `/api/v1/managed-task-assignments/{id}/exempt` | [ManagedTaskAssignmentController.java:33](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/ManagedTaskAssignmentController.java:33) |
| GET | `/api/v1/managed-task-assignments` | [ManagedTaskAssignmentController.java:44](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/ManagedTaskAssignmentController.java:44) |
| POST | `/api/v1/managed-task-assignments/{id}/defer` | [ManagedTaskAssignmentController.java:55](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/ManagedTaskAssignmentController.java:55) |
| GET | `/api/v1/students/{studentId}/previous-day-task-copy/preview` | [PreviousDayTaskCopyController.java:25](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/PreviousDayTaskCopyController.java:25) |
| POST | `/api/v1/students/{studentId}/previous-day-task-copy` | [PreviousDayTaskCopyController.java:35](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/PreviousDayTaskCopyController.java:35) |
| POST | `/api/v1/task-copy-batches/{batchId}/items/{itemId}/retry` | [PreviousDayTaskCopyController.java:46](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/PreviousDayTaskCopyController.java:46) |
| GET | `/api/v1/task-assignments` | [StudentTaskAssignmentController.java:36](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/StudentTaskAssignmentController.java:36) |
| GET | `/api/v1/task-assignments/{id}` | [StudentTaskAssignmentController.java:49](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/StudentTaskAssignmentController.java:49) |
| POST | `/api/v1/task-assignments/{id}/claim` | [StudentTaskAssignmentController.java:57](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/StudentTaskAssignmentController.java:57) |
| POST | `/api/v1/task-assignments/{id}/pause` | [StudentTaskAssignmentController.java:65](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/StudentTaskAssignmentController.java:65) |
| POST | `/api/v1/task-assignments/{id}/resume` | [StudentTaskAssignmentController.java:76](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/StudentTaskAssignmentController.java:76) |
| POST | `/api/v1/task-assignments/{id}/abandon` | [StudentTaskAssignmentController.java:84](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/StudentTaskAssignmentController.java:84) |
| POST | `/api/v1/task-assignments/{id}/check-ins` | [StudentTaskAssignmentController.java:96](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/StudentTaskAssignmentController.java:96) |
| GET | `/api/v1/task-reviews` | [TaskReviewController.java:29](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/TaskReviewController.java:29) |
| GET | `/api/v1/task-reviews/{assignmentId}` | [TaskReviewController.java:39](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/TaskReviewController.java:39) |
| POST | `/api/v1/task-reviews/{assignmentId}/reject` | [TaskReviewController.java:48](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/TaskReviewController.java:48) |
| POST | `/api/v1/task-reviews/{assignmentId}/approve` | [TaskReviewController.java:59](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/TaskReviewController.java:59) |
| GET | `/api/v1/task-reviews/{assignmentId}/reviewer-options` | [TaskReviewController.java:68](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/TaskReviewController.java:68) |
| POST | `/api/v1/task-reviews/{assignmentId}/transfer` | [TaskReviewController.java:79](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/TaskReviewController.java:79) |
| PUT | `/api/v1/teachers/{teacherUserId}/classes/{classId}` | [TeacherClassController.java:29](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/TeacherClassController.java:29) |
| DELETE | `/api/v1/teachers/{teacherUserId}/classes/{classId}` | [TeacherClassController.java:40](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/TeacherClassController.java:40) |
| GET | `/api/v1/teachers/{teacherUserId}/classes` | [TeacherClassController.java:51](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/learningtask/web/TeacherClassController.java:51) |
| GET | `/api/v1/classes/schools` | [ClassManagementController.java:35](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/organization/web/ClassManagementController.java:35) |
| GET | `/api/v1/classes` | [ClassManagementController.java:45](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/organization/web/ClassManagementController.java:45) |
| POST | `/api/v1/classes` | [ClassManagementController.java:55](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/organization/web/ClassManagementController.java:55) |
| PUT | `/api/v1/classes/{classId}` | [ClassManagementController.java:67](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/organization/web/ClassManagementController.java:67) |
| POST | `/api/v1/classes/{classId}/disable` | [ClassManagementController.java:79](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/organization/web/ClassManagementController.java:79) |
| POST | `/api/v1/classes/{classId}/enable` | [ClassManagementController.java:90](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/organization/web/ClassManagementController.java:90) |
| POST | `/api/v1/organization-changes` | [OrganizationChangeController.java:33](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/organization/web/OrganizationChangeController.java:33) |
| GET | `/api/v1/organization-changes` | [OrganizationChangeController.java:46](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/organization/web/OrganizationChangeController.java:46) |
| POST | `/api/v1/organization-changes/{taskId}/approve` | [OrganizationChangeController.java:56](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/organization/web/OrganizationChangeController.java:56) |
| POST | `/api/v1/organization-changes/{taskId}/reject` | [OrganizationChangeController.java:68](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/organization/web/OrganizationChangeController.java:68) |
| GET | `/api/v1/organization-types` | [OrganizationManagementController.java:40](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/organization/web/OrganizationManagementController.java:40) |
| POST | `/api/v1/organization-types` | [OrganizationManagementController.java:50](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/organization/web/OrganizationManagementController.java:50) |
| GET | `/api/v1/organizations` | [OrganizationManagementController.java:62](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/organization/web/OrganizationManagementController.java:62) |
| POST | `/api/v1/organizations` | [OrganizationManagementController.java:70](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/organization/web/OrganizationManagementController.java:70) |
| PUT | `/api/v1/organizations/{organizationId}` | [OrganizationManagementController.java:82](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/organization/web/OrganizationManagementController.java:82) |
| POST | `/api/v1/organizations/{organizationId}/enable` | [OrganizationManagementController.java:96](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/organization/web/OrganizationManagementController.java:96) |
| GET | `/api/v1/organization-workbench/context` | [OrganizationWorkbenchController.java:20](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/organization/web/OrganizationWorkbenchController.java:20) |
| POST | `/api/v1/parent-invitations/{id}/accept` | [ParentBindingInvitationController.java:30](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/ParentBindingInvitationController.java:30) |
| POST | `/api/v1/parent-invitations/{id}/reject` | [ParentBindingInvitationController.java:43](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/ParentBindingInvitationController.java:43) |
| GET | `/api/v1/students/{studentId}/parent-relationships` | [ParentRelationshipController.java:40](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/ParentRelationshipController.java:40) |
| GET | `/api/v1/parent-relationships/students` | [ParentRelationshipController.java:50](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/ParentRelationshipController.java:50) |
| POST | `/api/v1/students/{studentId}/secondary-parent-invitations` | [ParentRelationshipController.java:60](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/ParentRelationshipController.java:60) |
| DELETE | `/api/v1/students/{studentId}/secondary-parent-relationships/{parentUserId}` | [ParentRelationshipController.java:73](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/ParentRelationshipController.java:73) |
| DELETE | `/api/v1/students/{studentId}/primary-parent-relationship` | [ParentRelationshipController.java:84](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/ParentRelationshipController.java:84) |
| POST | `/api/v1/students/{studentId}/primary-transfer-invitations` | [ParentRelationshipController.java:94](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/ParentRelationshipController.java:94) |
| POST | `/api/v1/parent-relationship-invitations/{invitationId}/acceptance` | [ParentRelationshipController.java:107](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/ParentRelationshipController.java:107) |
| POST | `/api/v1/parent-relationship-invitations/{invitationId}/rejection` | [ParentRelationshipController.java:118](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/ParentRelationshipController.java:118) |
| GET | `/api/v1/student-account-cancellations/candidates` | [StudentAccountCancellationController.java:35](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/StudentAccountCancellationController.java:35) |
| POST | `/api/v1/students/{studentId}/cancellations` | [StudentAccountCancellationController.java:45](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/StudentAccountCancellationController.java:45) |
| GET | `/api/v1/students` | [StudentManagementController.java:57](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/StudentManagementController.java:57) |
| GET | `/api/v1/students/{id}` | [StudentManagementController.java:70](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/StudentManagementController.java:70) |
| POST | `/api/v1/students` | [StudentManagementController.java:78](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/StudentManagementController.java:78) |
| PUT | `/api/v1/students/{studentId}/class` | [StudentManagementController.java:89](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/StudentManagementController.java:89) |
| GET | `/api/v1/students/organization-relationships` | [StudentManagementController.java:104](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/StudentManagementController.java:104) |
| GET | `/api/v1/students/organization-relationship-classes` | [StudentManagementController.java:114](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/StudentManagementController.java:114) |
| GET | `/api/v1/students/{studentId}/organization-relationships` | [StudentManagementController.java:124](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/StudentManagementController.java:124) |
| POST | `/api/v1/students/{studentId}/class-transfers` | [StudentManagementController.java:134](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/StudentManagementController.java:134) |
| POST | `/api/v1/students/{studentId}/organization-deactivations` | [StudentManagementController.java:147](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/StudentManagementController.java:147) |
| POST | `/api/v1/students/{studentId}/credentials/initialize` | [StudentManagementController.java:160](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/StudentManagementController.java:160) |
| POST | `/api/v1/students/{studentId}/login-code-resets` | [StudentManagementController.java:170](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/StudentManagementController.java:170) |
| POST | `/api/v1/students/{studentId}/login-qr-tickets` | [StudentManagementController.java:180](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/StudentManagementController.java:180) |
| POST | `/api/v1/students/{studentId}/parent-invitations` | [StudentManagementController.java:190](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/student/web/StudentManagementController.java:190) |
| POST | `/api/v1/student-import-executions` | [StudentImportController.java:49](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/studentimport/web/StudentImportController.java:49) |
| GET | `/api/v1/student-import-executions` | [StudentImportController.java:60](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/studentimport/web/StudentImportController.java:60) |
| GET | `/api/v1/student-import-executions/{id}` | [StudentImportController.java:72](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/studentimport/web/StudentImportController.java:72) |
| GET | `/api/v1/student-import-executions/{id}/rows` | [StudentImportController.java:81](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/studentimport/web/StudentImportController.java:81) |
| POST | `/api/v1/student-import-executions/{id}/retry-failures` | [StudentImportController.java:94](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/studentimport/web/StudentImportController.java:94) |
| GET | `/api/v1/student-import-executions/{id}/credentials` | [StudentImportController.java:104](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/studentimport/web/StudentImportController.java:104) |
| GET | `/api/v1/organization-teachers` | [TeacherManagementController.java:41](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/teacher/web/TeacherManagementController.java:41) |
| GET | `/api/v1/organization-teachers/{teacherUserId}` | [TeacherManagementController.java:56](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/teacher/web/TeacherManagementController.java:56) |
| POST | `/api/v1/organization-teachers` | [TeacherManagementController.java:65](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/teacher/web/TeacherManagementController.java:65) |
| PUT | `/api/v1/organization-teachers/{teacherUserId}/profile` | [TeacherManagementController.java:77](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/teacher/web/TeacherManagementController.java:77) |
| PUT | `/api/v1/organization-teachers/{teacherUserId}/status` | [TeacherManagementController.java:90](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/teacher/web/TeacherManagementController.java:90) |
| POST | `/api/v1/organization-teachers/{teacherUserId}/password-resets` | [TeacherManagementController.java:101](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/teacher/web/TeacherManagementController.java:101) |
| POST | `/api/v1/organization-teachers/batch` | [TeacherManagementController.java:112](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/teacher/web/TeacherManagementController.java:112) |
| GET | `/api/v1/teacher-workbench/context` | [TeacherWorkbenchController.java:20](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/teacher/web/TeacherWorkbenchController.java:20) |
| GET | `/api/v1/import-export-templates/options` | [ImportExportTemplateManagementController.java:64](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateManagementController.java:64) |
| GET | `/api/v1/import-export-templates` | [ImportExportTemplateManagementController.java:74](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateManagementController.java:74) |
| POST | `/api/v1/import-export-templates` | [ImportExportTemplateManagementController.java:89](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateManagementController.java:89) |
| GET | `/api/v1/import-export-templates/{id}/fields` | [ImportExportTemplateManagementController.java:126](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateManagementController.java:126) |
| PUT | `/api/v1/import-export-templates/{id}/fields` | [ImportExportTemplateManagementController.java:138](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateManagementController.java:138) |
| POST | `/api/v1/import-export-templates/{id}/enable` | [ImportExportTemplateManagementController.java:164](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateManagementController.java:164) |
| POST | `/api/v1/import-export-templates/{id}/disable` | [ImportExportTemplateManagementController.java:176](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateManagementController.java:176) |
| POST | `/api/v1/import-export-templates/{id}/default` | [ImportExportTemplateManagementController.java:188](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateManagementController.java:188) |
| GET | `/api/v1/import-export-templates/{id}/download` | [ImportExportTemplateManagementController.java:200](E:/apps/lingdong-xuexi-code/server/src/main/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateManagementController.java:200) |

## 15. 项目目前真实完成度

### 15.1 能确认的状态

**结论：核心业务已有实质性的本地实现，项目处于集成收口前的开发阶段；完整交付和生产上线均未完成。不能把当前状态称为“全部可运行”或“只剩验收”。**

| 维度 | 本次结论 |
|---|---|
| 历史计划进度 | 836/1000 = 83.6%，来源为旧工作包台账，尚未计入完整 V63；保留为历史记录，不在本轮篡改 |
| 源码实现范围 | 核心账号、组织、任务、积分、人工考勤等已有实现；订阅/排行/配置平台和报表仍存在半成品 |
| 跨端业务闭环 | 未全面满足；Web/小程序职责覆盖存在缺口，不能从两个工程都能类型检查推导业务双端完成 |
| 后端验证 | 最近全量 677/678 项通过，1 项失败；**99.85% 测试通过率不是项目完成率** |
| 第三方就绪 | 微信身份有真实适配器但未在本轮验真；短信发送缺失，周报发送缺失 |
| 发布就绪 | 不具备可证明的上线条件：凭据打包风险、默认 Java 环境、CI/运维/UAT/外部通道未收口 |

### 15.2 为什么不再给一个看似精确的新百分比

1. 旧台账存在“得分封顶但仍开发中”的项：例如认证、机构协同得满分，说明文字却保留真实联调、地理能力等缺口；得分不等于完整需求验收。
2. 当前总计划仍有较旧描述（部分段落仍写 PDF 未完成/旧迁移号），而代码已有 PDF、订阅 Web 和 V68，文档与源码不同步。
3. 本轮没有逐条完成 BRD → 两端页面 → API → 表 → 验收用例的全量签字追溯，也没有真实环境 UAT，不能凭源码数量重算百分比。
4. 尚有外部依赖和范围待明确项，剩余 16.4% 分值不等于剩余 16.4% 工时、代码量或 token。

因此，本报告给出**可核验的分项状态与缺口，而不编造“真实完成度 85%/90%”**。若必须沿用单一数字，应明确写为“历史计划 83.6%，未经本次完整需求验收确认”，不能用作上线承诺。

### 15.3 后续优先级建议（仅建议，本轮不执行）

| 优先级 | 事项 | 原因 |
|---|---|---|
| P0 发布阻断 | 排查本地配置进入构建资源的凭据风险；建立运行时密钥注入和发布排除 | Git 忽略不能防止打包 |
| P1 | 固化 JDK/Maven 工具链；调查学生登录失败测试并恢复可靠全量回归 | 当前全量不是绿灯，默认 Maven 启动异常 |
| P1 | 匿名排行班级选项/两端适用页面与真实权限回归 | 后端已具备基础但用户尚不能完整使用 |
| P1 | 真实短信与微信订阅授权/发送 | 直接阻断账号验证码及周报送达 |
| P1 | 角色/端适用矩阵和角色看板 | 防止继续以局部页面完成代表全项目完成 |
| P2 | 其余报表/导入适配器、附件持久化/保留销毁、缓存/系统任务通用能力 | 明确真实可执行范围，不只做元数据配置 |
| P2 | CI/CD、监控、备份恢复、安全/性能、UAT 和发布演练 | 软件交付必须具备运行与恢复证据 |

## 审计记录及限制

- 本轮只新增 PROJECT_STATUS.md。既有未提交/未跟踪代码保留原状；没有替用户提交、回滚或修复。
- 源码读取范围包括三个 src 目录、构建配置、路由、主要模块/接口/Mapper、68 份迁移及测试/验证结果。
- 只执行 Web/miniapp 无输出类型检查、工具版本检查及文件/本地监听读取；未触发后端数据库迁移、真实短信/微信或远程测试。
- 本报告不含密码、AppSecret、验证码或令牌原值。
- “未发现”均限定当前静态扫描及列明证据，不声称进行了所有可达性/竞态/漏洞/生产数据验证。
- 业务源码检查使用 SHA-256 汇总比对，核查根为 server/src、web/src、miniapp/src（1668 个文件），报告写入前后比对一致。汇总值：A6BC8E5DECA98A43DC319C8EB140762233503B325B7B99EC597C0E014C9CDB91。
## 2026-10-01 菜单与按钮配置

管理端新增 `/menu-management`，采用独立菜单配置复用现有权限。支持目录、页面与按钮的树形管理，新增/编辑、启停、同级上移下移及排序号；保存后刷新导航、页签名称和现有按钮显示。已初始化25个页面、330个动作，包括默认弹窗页脚；业务路由、业务权限和能力开关仍执行现有校验。

真实 MySQL 已应用 V87—V89；运行后端健康检查 `status=UP, db=true, redis=true`。Web 5173、H5 5174 均返回200。浏览器验证了菜单名称更新、菜单上下移、按钮停用隐藏及启用恢复，验证配置已恢复。菜单管理在1280×720下外层高度与内容高度均617，无整页长滚动；查询输入同宽223.25px，查询/重置保持同排。

菜单绑定目录维护位置：Web `src/app/menuCatalog.ts`、Server `src/main/resources/web-menu-actions.json`。添加新业务动作须同步目录及新增Flyway迁移，已应用迁移不得重写。静态动作绑定用于阻止未知按钮编码及错误父页面。系统管理员维护；审核员只读；工作台和菜单管理入口保留。

工作区仍包含此前重命名、基础数据和布局改动，未提交、未重置其他文件。

## 2026-10-01 账号修改密码

管理端右上角账号菜单新增“修改密码”，填写旧密码、新密码与确认新密码；新密码沿用8—20位字母和数字组合规则。错误保留输入，成功清理本地会话并回登录，销毁页面缓存。接口POST /api/v1/auth/password仅修改当前principal账号，事务保存加密哈希并撤销全部设备凭证。密码登录和修改密码共用账号行锁，防止旧密码并发登录在改密后留下有效会话。

无需数据库迁移，不依赖业务菜单、角色权限或账号安全开关。开发验收不修改真实admin密码。

修改密码验收：后端46项、前端相关40项测试通过，生产构建通过；最终服务已启动，数据库/Redis健康，真实页面旧密码错误提示与会话保留已验证。

## 2026-10-03 菜单拖动与角色树授权

菜单管理使用树形拖动：同级调整顺序、跨目录调整层级，按钮只能放页面下，禁止循环和核心入口跨层级移动，取消上移下移按钮。批量新增按钮使用多行抽屉，名称/编码/排序号一次提交；新页面及新权限按钮编码直接作为权限编码。保存后刷新当前菜单与左侧导航，版本冲突会刷新树并提示重新编辑。

角色授权使用级联勾选树，包含页面自身权限、按钮权限和其他权限分组；支持半选、一次保存，保留已有DENY、停用权限及用户独立授权。批量接口使用角色行锁、最新锁定读和完整授权快照防并发覆盖，旧菜单授权写入也共用角色锁。窗口默认折叠并内部滚动，取消额外静态说明。

真实MySQL已同步V96，初始化新批量按钮及角色树保存/重试/取消配置。8080健康检查UP且db/redis均true，Web5173与H5 5174返回200。真实浏览器登录成功，验证多行新增和页面勾选级联；未提交测试权限变更。截图位于.local-verification/menu-tree-final.png及role-permission-tree-final.png。后端菜单17、IAM13、权限决策5、菜单授权4相关测试通过；前端相关回归和构建结果记录于.local-verification/menu-iam-final-web.log及menu-final-build.log。

历史工作区改动均保留，未提交。

本轮最终验收：前端10文件46测试全部通过，typecheck/生产构建通过。后端上述39相关测试通过；运行服务健康复查正常。

## 2026-10-04 用户角色与组织成员管理

角色列表新增行内授权和关联用户，授权使用菜单/按钮级联树，用户批量关联并继承角色权限。用户列表使用同样的树授权，角色继承权限勾选只读，独立授权批量保存；已有显式拒绝保留，完整快照及用户行锁防止并发覆盖。

组织管理选择节点后进入“组织用户”，支持分页查询、关联已有用户、添加组织管理员、将成员设为管理员及当前组织新增用户。管理员设置事务内完成组织关联、ORG_ADMIN角色和组织数据范围；批量操作全量验证，失败不部分写入。已有成员/管理员在跨页候选中只读勾选。新增用户必须选择有效组织，组织入口自动固定当前组织，账号和归属原子保存。

前端18文件42测试、后端6类40测试通过；Web类型检查/生产构建、后端打包通过。独立审查发现的继承勾选显示、跨页已有成员及加载失败旧候选问题均已修复并复审。真实MySQL同步V97，重启后8080健康UP且db/redis=true；Web5173和H5 5174均返回200。

真实浏览器验证角色行内授权、角色批量用户选择及已有用户只读勾选、用户角色继承权限只读回显、用户新增组织必填、组织管理员身份及组织内新增默认归属；未实际提交真实授权变更。截图：.local-verification/user-permission-inheritance-final.png、organization-members-final.png。保留历史工作区改动，未提交。

## 2026-10-04 全部菜单按钮统一权限

所有配置BUTTON强制grantable=true且permissionCode=code，保留现有动作编码绑定。新增、编辑、批量新增不再生成仅展示按钮。V98补齐全部按钮权限，SYS_ADMIN补齐缺失授权，其他角色自行勾选；保留已有拒绝和业务接口校验。current菜单逐按钮检查权限，缺失权限按钮隐藏。

前端12文件52测试、后端菜单23+IAM29共52测试通过，类型检查/构建/打包通过。独立规格及质量审查无阻塞。测试清理改为await act，解决AntD静态实例在环境销毁后回调错误。真实MySQL8成功同步V98，后端8080 UP/db=true/redis=true，Web5173/H55174返回200。真实页面验证SYS_AUDITOR授权树下系统任务的查看详情、批准、驳回均可独立勾选；未提交测试角色授权。截图.local-verification/all-buttons-authorization-final.png。

未删除历史数据，未提交或重置工作区。

## 2026-10-04 四个管理页面操作优化

组织管理采用组织树与选中组织详情分区，默认直接展示组织用户，新增下级预填上级组织，成员/管理员操作复用原有逻辑。菜单管理树行只显示对象，选中后集中展示编辑、下级新增、批量按钮及启停；保留拖动排序与层级调整。用户管理和角色与权限采用独立查询区、功能按钮区、列表区，查询重置同行，行内文字操作清晰，保留授权、关联用户、状态修改、审计及全部原业务能力。限定四页样式，统一查询框、内部表格滚动和中文字段显示。

新增5个界面动作同步菜单目录与V99权限初始化。真实MySQL成功同步V99，后端重启并验证UP/db=true/redis=true，Web5173和H55174返回200。前端23文件81项回归测试、后端菜单迁移14项测试通过，生产构建和后端打包通过；独立规格/质量复审无P1/P2。

浏览器1280x720验证四页操作区与查询区布局，组织、菜单及用户外层高度617/滚动高度617；1440x900组织及角色外层高度797/滚动高度797。700x900组织上下分区、按钮文字完整，无横向页面溢出，窄屏有适当纵向滚动。已恢复浏览器原尺寸。截图.local-verification/management-organizations-final.png、management-menus-final.png、management-users-final.png、management-roles-final.png、management-narrow-final.png。

未删除历史数据，未提交或重置工作区。

## 2026-10-04 菜单权限统一及清空重建

按用户明确授权清空lingdong_learning并全量初始化，复用本地账号密码摘要与运行密钥。真实MySQL成功执行101个应用迁移至V100及1个联调种子：7用户、43组织（包含原始行政/学校种子）、6角色、471权限、412菜单节点、2示例学生，旧会话和业务数据清空。新增显式reset工具，校验配置URL与实际catalog均为该schema，不自动执行。

移除权限目录和新增权限表单，独立POST /permissions返回403。菜单/按钮编码创建后不可变，页面URL从已注册25页面选择；菜单保存同步权限名称/类型/状态/父级，保护核心入口所有祖先，拒绝占用MINIAPP权限编码。既有业务接口权限和数据范围/审核规则保留，纯UI按钮编码仍承担显示权限。菜单分组：权限管理、系统设置管理、导入导出管理、系统运维、教育管理、成长管理，工作台独立；授权树与导航共用结构。

V100清理废弃入口和权限、初始化分组。Fresh MySQL暴露旧V29外键依赖索引错误，通过新增V28.1先补task_id索引解决（历史迁移文件不改）；本次全量初始化兼容。已有其他数据库升级需独立安排28.1 out-of-order处理，不作为常规升级承诺。

验证：前端22文件83测试+最后核心祖先保护16菜单测试、后端48菜单/IAM/权限测试及最后2个初始化测试通过，生产构建/后端打包通过；独立复审无P1/P2。真实API管理员登录、3个模块归属、412节点权限元数据、废弃新增权限403、8080 UP/db=true/redis=true验证通过。未提交或重置历史工作区修改。

真实浏览器发现V91已将45个存量按钮改为业务权限编码，而组件仍引用旧编码，造成角色工具条等隐藏。本次全量同步组件、测试、前端动作目录和服务端JSON到规范编码，移除别名；新增目录映射/唯一性/前后端一致性及实际配置上下文回归。23项相关测试及4项目录/组件测试、构建通过。真实API确认352个前端动作均在数据库按钮中，全部菜单权限元数据一致；浏览器确认新增角色/配置角色权限/配置用户权限恢复、权限目录已移除，按模块授权树正常。

最后部署已重新打包同步后的动作JSON并重启，管理员正常登录，接口352动作目录一致性/菜单权限/独立新增403再次通过，后端UP/db=true/redis=true。浏览器截图：.local-verification/unified-iam-canonical-final.png（角色工具条及模块导航）、unified-role-modules-final.png（按模块授权树）。菜单管理和授权弹窗未提交真实授权变更。

## 2026-10-04 用户密码与所属组织查询
新增用户（非学生）填写初始密码及确认密码，沿用8–20位字母数字校验，BCrypt摘要与用户/组织同事务保存；学生保留专门凭据流程，家长保留既有角色及资料资格校验。系统管理员通过IAM_USER_PASSWORD_SET重置非学生、非注销账号密码，撤销全部客户端旧会话并审计，密码不回显且日志字符串脱敏。
用户管理新增所属组织机构树查询，按直接成员关联精确过滤，分页与总数一致；查询/重置回第一页，翻页保留条件。组织加载失败不阻断用户目录。
真实MySQL启动迁移V101成功，保留现有业务数据；38项后端、12项前端测试、类型检查、前端构建及后端打包通过，独立复核无P1/P2。真实API验证创建密码登录、组织正/反过滤及总数、重置旧会话与旧密码失效、新密码登录，健康UP/db=true/redis=true。测试创建一条独立联调用户，未改管理员密码。后端PID170492。

## 2026-10-04 用户列表机构名称
用户管理列表在名称后增加机构名称列；接口按当前页用户批量查询直属机构名称，多机构并列显示，未关联显示—，长文本省略且可悬停查看。沿用现有成员关联和组织筛选，未调整数据及权限。
验证前端7测试、后端24回归测试、前端构建/后端打包通过，真实API确认teacher01机构名称，浏览器列显示正常，服务重启UP/db=true/redis=true。截图.local-verification/user-organization-column-final.png。

## 2026-10-05 组织管理并入机构业务页签

背景：组织管理页两种形态互斥（ORG_ADMIN补授ORG_NODE_READ/ORG_NODE_CHANGE_REVIEW后切入组织树管理形态），新增班级、学员关系、家长换号核验、学生账号注销、配置教师班级只存在于纯机构管理员（仅CLASS_READ）的机构业务独立页，补授后全系统无账号可见。按用户选择将机构业务并入平台组织视图。

改动：OrganizationManagementPage抽出OrganizationOperationBody（showHeading区分独立页与页签嵌入，按钮、抽屉、班级面板逻辑单源），PlatformOrganizationManagementView顶部新增「机构业务」页签承接原机构业务全部内容；能力开关门控保留（学员关系/家长换号核验/学生账号注销分别受开关控制，配置教师班级恒显）；纯机构管理员独立页行为不变。纯前端改动，无后端与迁移变更。

验证：organizations目录8个测试文件20项通过（新增2项回归：平台视图机构业务页签包含班级与业务入口、学员关系按开关显隐且配置教师班级恒在）；Web全量68文件360项通过、typecheck、生产构建通过。真实浏览器orgadmin实测：机构业务页签显示学员关系、配置教师班级、班级管理（新增/编辑/停用），配置教师班级抽屉正常打开且含取消/解除绑定/确认绑定；能力开关parentMobileManualRecoveryEnabled与studentAccountCancellationEnabled当前为关闭，对应按钮按设计隐藏。截图.local-verification/organization-operations-tab-final.png。未提交工作区改动。

## 2026-10-05 V102清理29个残留UI按钮

背景：组件与菜单配置比对发现29个按钮编码在前端动作目录（355个动作）中已无任何组件绑定，对任何角色均不可见：各页「刷新/重试」类14个（多轮改版去除刷新按钮）、菜单管理旧交互5个（改拖拽排序与集中操作区）、角色与权限旧授权5个（改级联勾选树与行内授权）、系统任务旧按钮3个（改跳转业务页审核）、匿名排行/排行授权2个。按受控数据修正规范以V102迁移清理（禁止手工改库）。

改动：V102__remove_stale_ui_buttons.sql按V100同序清理——先删sys_role_permission与sys_user_permission授权，再删sys_menu按钮节点（限定type='BUTTON'），最后删sys_permission权限行，审计历史保留；历史迁移文件不改。同步修正MenuCatalogUpgradeTest对menu-management-page.10的断言（原断言权限必须存在，改为断言菜单行/授权/权限均已移除）。

验证过程发现两处既有问题并一并处理：1) MenuPersistenceTest混合角色断言仍要求SYS_ADMIN+SYS_AUDITOR身份拒绝菜单写入，与已提交的权限编码判定设计（67a34cd移除固定身份门槛）冲突，属V102之前既有失败，按权限化语义修正为混合角色可管理菜单、纯审核员只读断言保留；2) 后端全量套件919项中另有21失败+1错误，经移除V102对照复跑确认失败集完全一致，全部为权限化等前几轮的既有问题（多为403期望未随身份门槛移除更新，及Flyway计数、导出边界等），与V102无关，留待专项对账轮处理，未在本轮掩盖。

验证：MenuCatalogUpgradeTest 8/8、menu包6类30/30通过；后端全量套件919项中V102相关零新增失败（有/无V102对照均为21失败+1错误，既有问题见上）。重新打包后端并经pwsh start-real-env.ps1重启，Flyway对真实MySQL应用V102成功（lingdong_learning → v102），health UP且db=true、redis=true。真实API复核：菜单按钮384→355与前端动作目录精确一致（双向差异均为0）、菜单节点415→386、ORG_ADMIN授权118→116（移除其挂接的2个组织管理刷新按钮授权）、admin权限码462→433；菜单管理页渲染正常且残留按钮不再出现。截图.local-verification/v102-menu-management-after-cleanup.png。未提交工作区改动。

## 2026-10-05 四项管理端增强（V103）

需求与实现：1) 组织用户支持撤销——成员行新增「取消关联」、管理员行新增「取消管理员」（danger+二次确认），后端新增 POST /organizations/{id}/members:revoke（镜像 addMembers 锁序与权限：IAM_USER_ORGANIZATION_ASSIGN，管理员撤销另需 IAM_USER_ROLE_ASSIGN+IAM_DATA_SCOPE_CONFIGURE），三个 Mapper 补删除方法，新增审计事件 USER_ORGANIZATION_DISSOCIATE/ORGANIZATION_ADMIN_REVOKE/USER_ROLE_REMOVE；取消关联对管理员身份先拒后撤。2) 组织删除报错治理——根因：带业务引用的组织删除在审核通过执行时失败，但 safeFailureReason 兜底吞掉真实原因且 OrganizationChangeExecutionException 落入 IllegalStateException 通用409。修复：safeFailureReason 保留业务异常真实消息、ApiExceptionHandler 新增 OrganizationChangeExecutionException→409 透出原因、createAndSubmit 对 DELETE 增加引用计数预检（申请时即拒绝并告知真实原因）。3) 菜单管理增强——树节点类型 Tag 着色（目录 blue/页面 green/按钮 gold），新增节点删除（DELETE /iam/menus/{id}，@RequirePermission("MENU_MANAGE")）：核心入口（/dashboard、/menu-management）及其祖先与菜单管理页按钮保护不可删、有下级不可删，删除按 V102 同序级联清理角色授权/用户授权/权限行，审计 MENU_DELETE；新按钮 menu-management.node.delete。4) 机构名称全路径——新增 OrganizationNamePathService（·分隔），组织树响应增加 namePath（详情标题/名称行、父级选择下拉显示全路径层级）、用户列表机构名称列与任务组织选项/教师班级选项/考勤班级选项/异常报备班级选项/导出中心班级选项经 SQL 子查询（祖先路径 GROUP_CONCAT）统一升级为「学校·年级·班级」全路径，响应结构不变。

V103 迁移：3个新按钮码（organizations.members.dissociate/revoke-admin 授 SYS_ADMIN+ORG_ADMIN，menu-management.node.delete 授 SYS_ADMIN）+权限行+按钮权限父级回填（沿用 V100 派生表写法规避 MySQL 1093）+审计事件 CHECK 扩展。过程中修复两处真实环境问题：UPDATE JOIN 写法 MySQL 不兼容改 V100 派生表式；首次失败残留的 flyway_schema_history 记录经一次性工具（凭据运行时读取不回显）清理。

验证：受影响包测试（menu/organization/iam/exceptionreport/learningtask）新增失败全部修复后通过，后端全量919项失败集与 V102 轮基线逐字一致（21失败+1错误均为既有，零新增）；前端 organizations/menus 60项、typecheck、全量构建通过；LearningTaskOptionControllerTest 断言同步升级为全路径。真实浏览器逐项实测：①orgadmin 取消关联 teacher3（确认框→API→列表刷新）后经关联已有用户恢复原状，管理员行/成员行按钮齐备；②admin 对四年级三班提交删除申请当场弹出「组织变更执行失败：组织存在下级节点或业务引用（成员、管理员、师生关系、任务等），不能删除」；③创建 DICTIONARY_TEST_CLEANUP 测试按钮（gold 标记）→ 删除成功且菜单行/权限行级联清除（390→389），核心入口删除按钮正确禁用；④选中四年级三班详情标题显示「重庆两江新区新牌坊小学·四年级·四年级三班」。截图.local-verification/menu-delete-verified-final.png。未提交工作区改动。

## 2026-10-05 修复组织删除执行在 MySQL 上必然失败（ERROR 1093）

用户审核组织删除（四年级六班，两次）均提示兜底文案「组织变更执行失败：组织变更条件已变化，请核对后重新申请」。排查：预检 SQL 覆盖全部 23 张外键表且该组织引用计数为 0；按 applyApprovedChange 顺序在真实 MySQL 上逐步模拟（SELECT FOR UPDATE/DELETE/markApplied/audit INSERT/markEffective）全部成功，排除数据问题；给 catch 分支加 ERROR 日志（taskId/changeId/reason/handler+堆栈）后复现，定位 handler=UncategorizedSQLException——deleteLeaf 的 DELETE 语句含 NOT EXISTS(SELECT...FROM sys_organization) 自身子查询，触发 MySQL ERROR 1093（目标表不得出现在 UPDATE/DELETE 子查询中）；H2 允许该写法故后端测试全绿未暴露，属删除审核执行链路（V100 后新增的直通执行）在真实 MySQL 上的存量缺陷，此前从未成功执行过。修复：deleteLeaf 子查询改派生表物化写法（与 V100 权限回填同款规避方式），H2/MySQL 双兼容。

修复同时保留新增的执行失败 ERROR 日志（含任务/变更标识、归因原因与异常类型），后续执行失败可第一时间定位。

验证：OrganizationChangeApplicationServiceTest 14/14 通过；重新打包重启（health UP、db/redis=true）后真实链路复验——admin 对「验证删除组织乙」提交删除、auditor 批准，返回 executionStatus=APPLIED、taskStatus=EFFECTIVE，组织物理删除成功（测试组织甲同样清理成功，两次独立验证）。用户此前失败的「四年级六班」现可重新提交删除申请，提交预检通过后审核执行将正常生效；其两条 FAILED 历史变更记录保留作为审计痕迹。未提交工作区改动。

## 2026-10-05 修复组织树拖拽换父误报

用户把四年级六班（挂在三班下）拖到与五班平级时被误报「不能移动到自身或其下级组织」，被迫改走申请移动。根因：前端 handleTreeDrop 的换父空隙分支防环判定方向写反——检查的是「新父的子树包含被拖节点」（换父时几乎恒真，因为节点本来就在新父子树里），而正确的环判定是「新父位于被拖节点自己的子树内」（挂到自己后代下）。拖入内部分支同时补上反向判定（把祖先拖进后代内部成环，原判定漏检该方向，仅由后端兜底）。

实现：防环判定抽为导出纯函数 dragMoveViolation（含注释说明两个方向的语义），handleTreeDrop 拖入内部与换父空隙分支改用该函数；纯前端改动，后端 validateMoveTarget 的路径前缀防环本就正确未动。

验证：新增 7 项判定矩阵单测（空隙换父合法/换到后代违规/同父重排合法/拖入后代违规/祖先拖入后代违规/拖入无关节点合法/拖到自身违规），organizations 目录 8 文件 27 项、typecheck、生产构建通过。真实浏览器鼠标拖拽复验：临时创建「拖拽验证班」挂三班下（复现原场景），真实鼠标拖到五班空隙——出现「组织节点已移动」且无误报，树刷新后验证班位于四年级下与五班平级；随后经删除流程清理（APPLIED/EFFECTIVE）。截图.local-verification/drag-before.png、drag-after-fix.png。未提交工作区改动。

## 2026-10-05 修复新增下级组织「请求参数不合法」吞掉真实原因

用户新增下级组织保存时仅提示通用文案「请求参数不合法」，无法得知具体错项。复现：组织编码输入中文或小写均返回 400 VALIDATION_ERROR 通用文案。根因两层：1) 后端 ApiExceptionHandler 将 IllegalArgumentException 与 Bean Validation 异常合并映射为固定文案，而组织编码格式（3至64位大写字母、数字、下划线且字母开头）、名称长度、排序值等校验失败都以 IllegalArgumentException 抛出真实中文原因；2) 前端新增组织节点/新增组织类型弹窗的组织编码、类型编码字段只有必填和长度校验，缺少与后端一致的格式预校验，用户输入不规范编码直到提交才失败且看不到原因。

修复：后端把 IllegalArgumentException 拆分为独立 handler 透出真实消息（全部为面向用户的中文业务文案；MethodArgumentNotValid 与 HttpMessageNotReadable 保持通用文案），消息为空时回退通用文案；前端组织编码、类型编码字段增加与后端一致的正则预校验并显示相同提示文案。

验证：前端 organizations 8 文件 27 项、typecheck、生产构建通过；后端全量 919 项失败集与基线完全一致（21失败+1错误均为既有，零新增）。真实浏览器复验：API 层中文编码请求返回「组织编码仅允许3至64位大写字母、数字和下划线，且必须以字母开头」；表单输入中文编码即时红框提示相同文案且无法提交；改为合法编码 SIBAN_QI_TEST 创建成功且详情 namePath 正确显示全路径「重庆两江新区新牌坊小学·四年级·四年级七班」（顺带验证上级组织下拉的全路径层级显示）；测试节点经删除流程清理（APPLIED/EFFECTIVE）。截图.local-verification/org-code-validation.png。未提交工作区改动。

## 2026-10-05 按权限编码访问管理端

移除Web管理访问中SYS_ADMIN/SYS_AUDITOR/ORG_ADMIN/TEACHER固定身份门槛，管理端页面与接口一律按权限编码判定；保留有效账号、WEB客户端、功能开关、显式拒绝、组织数据范围及业务关系和审核不能自审。系统/IAM后端（菜单、组织、用户、角色、功能开关、系统任务、字典、缓存、接口服务、附件）与教育/导入导出后端（教师、班级、学生、考勤、异常报备、导入、导出）均改为权限加真实组织范围授权，修正导入作业列表、教师班级元数据、混合审核加只读权限导出三处范围不一致；二维码Web签发改为精确权限、活跃ENROLLMENT、组织数据范围及组织有效启用检查，保留主家长关系与MINIAPP原规则；导出冻结范围新增systemTaskReviewableTypes，旧快照不随新增权限扩大。前端菜单、页面入口与按钮按权限编码显示，自定义角色授权后即可访问对应管理页面。

验证：系统组后端78项、业务组修复夹具后复测4/4通过（.local-verification/business-fixture-retest.log），SystemPermissionDrivenAccessTest覆盖混合权限导出、二维码权限和范围；前端相关回归（含PermissionRoutes、学生登录管理页46项等）与生产构建通过（permission-final-web-tests.log、permission-final-web-build.log），后端打包通过。verify-permission-runtime.ps1确认：health UP且db=true、redis=true，admin访问菜单/IAM/用户/组织/开关/系统任务接口全200，无TEACHER_READ与LEARNING_TASK_READ_MANAGED的教师目录、学习任务接口按权限返回403，Web 5173与H5 5174均200（permission-runtime-verification.log）。

Codex因额度中断后由ZCode接手收尾浏览器复核：重启后端（pwsh运行start-real-env.ps1，密钥持续有效）与双前端dev服务后，admin登录5173，工作台、用户管理、角色与权限、菜单管理、组织管理、功能开关、系统任务、学生登录页均正常加载并返回真实数据；左侧导航教育管理组仅显示学生登录，教师管理、学习任务等未授权菜单隐藏，无成长管理组，与后端403行为一致。截图.local-verification/permission-driven-web-final.png。未提交工作区改动，未提交真实角色授权变更。

## 2026-10-06 学校、年级、班级统一管理

用户确认学校→年级→班级并要求简单清晰。组织架构与机构业务共用左侧选中节点：学校默认年级列表，年级默认班级列表，班级默认学生/教师；新增自动固定上级，仅填名称、排序，保存刷新树和列表并保留位置。组织用户、信息为次级页签，原类型管理、变更审核及其他业务能力保留。局部年级/班级范围以不可操作的学校/年级祖先显示上下文，防止局部权限账号出现空树；成员查询按独立权限裁剪，异步切换不会显示旧班级人员。

新增年级直接归属学校、班级直接归属年级，班级为叶节点，禁止学校直接或间接嵌套。创建与直接/审核移动共享校验；直接跨校移动拒绝，审核跨校检查子树业务引用。年级CRUD复用CLASS_*权限，停用年级及审批停用祖先都会使子班未完成任务失效。存量直属学校班级保留，可通过CLASS_UPDATE归并同校年级，保持ID、关系及审计。班级响应返回学校/年级ID名称，教师摘要按学校祖先解析；教师候选允许未绑定的同校有效教师，保留实际范围和祖先关系要求。新增班级学生目录/候选接口分别使用STUDENT_READ/STUDENT_CLASS_ASSIGN，避免依赖任务创建权限。

小程序同步年级选择、创建gradeOrganizationId和学校/年级名称显示；构建时修复teacher-home原有多余view闭合标签。浏览器复验发现教师响应classOrganizationIds与前端classIds不一致，已在API边界映射并补真实响应契约回归。

验证：针对性后端99项（核心57项与额外兼容42项，重叠用例只计一次；旧两层测试夹具同步年级、失效状态及正确范围后复测通过），前端30项、Web构建、小程序type-check及mp-weixin构建、后端打包均通过。独立复核停用任务失效及跨校引用计数问题已修复，无剩余P1/P2。真实库schema103保持不变，不删数据、不改密码或角色授权。8080新后端PID404720健康UP/db=true/redis=true；管理员组织树、学校/年级/班级、教师候选、班级学生及入班候选接口200；现有2学校、1年级、4班级归属正确，Web5173/H5 5174均200。orgadmin当前未获CLASS_READ，验证保留拒绝权限，不修改授权。

证据：.local-verification/school-hierarchy-final-backend-tests.log、school-hierarchy-compatibility-retest.log、school-hierarchy-final-web-tests.log（29项；新增教师wire契约后最终30项由前端复测；root再次运行API2项通过，school-hierarchy-teacher-contract-tests.log）、school-hierarchy-runtime-verification.log、school-hierarchy-miniapp-build.log。浏览器确认学校年级列表、年级4个班级、新增班级固定四年级、学生名单、教师候选及名单页面无报错；未提交新增/绑定数据。页面截图school-grade-class-final.png。

## 2026-10-06 组织操作按钮直接展示

按用户要求移除更多/更多业务Popover。新增下级、编辑、申请停用/移动/删除直接显示于组织操作行；学员关系、家长换号核验、学生账号注销、配置教师班级直接显示于独立业务操作行，继续保留权限及功能开关。按钮自动换行且文字不竖排，不挤压新增年级/班级入口。25项相关页面回归、类型检查及Web构建通过；浏览器确认年级页组织操作与业务操作均直接可见。无需重启后端。证据.local-verification/organization-visible-actions-tests.log、organization-visible-actions-build.log、organization-visible-actions-final.png。

## 2026-10-08 班级学生列表重复刷新

定位SchoolHierarchyPanel成员effect同时依赖班级ID与classes数组；StrictMode重放加组织列表异步返回导致同班学生请求3次。新增回归先确认3次失败，再移除组织列表依赖，以班级ID、读取权限和显式刷新版本控制成员请求；当前加载Promise在effect重放中复用，保留取消旧结果，离开班级重新进入仍重新查询。保存学生/教师关系和手动重试显式刷新，避免缓存旧列表。27项页面回归（含严格模式只查询一次、保存后一次刷新及切换班级防串数据）、类型检查和构建通过，Web5173 HTTP200。证据.local-verification/student-list-refresh-red.log、student-list-refresh-tests.log、student-list-refresh-build.log。仅前端变更，无需重启后端。

## 2026-10-08 用户管理与组织用户统一学生创建

STUDENT类型经两个共用入口创建时保留输入用户名/账号ID，在同事务建立edu_student、积分/lifecycle、内置学生角色、专属凭据和学校入学关系。学校/年级暂未分班，班级自动入班；非教育组织必须选择有效学校和年级信息，班级年级祖先默认推导，旧式直属学校班级允许手填年级。权限IAM_USER_CREATE、STUDENT_CREATE及分班权限、真实组织范围和相关开关校验保留；不因任务管理关闭阻止学生入班。

补齐POST要求目标用户已关联所选组织，拒绝已停用账号、冲突有效入学/班级关系及历史停用入学关系。用户名、既有角色/关系/凭据保留。受保护创建/补齐POST仅在新签发凭据时返回initialLoginCode，GET/列表不返回；创建成功弹窗展示一次性账号和登录码，结果对象toString脱敏、studentId字符串序列化。学生账号认证与小程序输入兼容xiangsihan等合法64字符自定义账号，原数字账号保留。

统一学生组织范围谓词供目录、候选、关系生命周期及studentAllowed使用：学校范围或当前活动班级范围；未分班时仅认可同校范围内GRADE账号会员。旧CLASS会员不保留转班后的读权限，同校其他学生不会进入窄范围。新SQL字面前缀LEFT比较阻止编码下划线通配越范围。成功创建学生发送无数据内部通知，仅当前CLASS且有STUDENT_READ面板刷新一次，保留StrictMode去重与卸载清理。

验证：最终核心后端25项通过，加新HTTP事务隔离与StudentManagementController既有3项回归合计4项通过（重叠HTTP1项，唯一相关后端28项），最终前端创建/成员/班级联动22项通过；Web构建、小程序type-check/mp-weixin构建及后端打包通过。独立复核所发现跨目标用户范围、窄范围可见性、历史班级年级字段已修复，无剩余确认P1/P2。新HTTP测试加事务隔离，避免持久化测试学生污染其他ALL范围计数；旧扩展组仍有已有授权角色硬编码的历史权限断言，不作为全量测试通过声明。

真实MySQL仅修复xiangsihan（原userId2107115161507594240）：档案0→1、凭据0→1，原CLASS2190000000000020003与住宅FAMILY2107115465045180416会员均保持。修复接口再调用不重复档案、不生成新码；四年级三班成员与候选都包含向思翰。首次码已保存忽略的私有文件.local-verification/xiangsihan-student-initial-credentials.json（不写日志），用该码完成真实学生QR登录并撤销验证会话，用户名和身份一致。未改变其他账号、密码或管理员授权，未清空数据。后端PID63276 health UP/db=true/redis=true，schema103不变。

证据.local-verification/unified-student-final-tests.log、unified-student-http-isolation-tests.log、unified-literal-scope-green.log、unified-student-package.log、xiangsihan-before-repair.log、xiangsihan-after-repair.log、xiangsihan-repair-verification.log；浏览器新增独立验证页，三班学生名单和添加学生候选均显示向思翰，未提交额外绑定。截图unified-student-class-final.png、unified-student-candidate-final.png。未提交或回退工作区历史变更。

## 2026-10-06 家长账号 xiangkanglin 无法登录：恢复服务并补齐账号数据

用户报告管理端新增的家长账号 xiangkanglin 无法登录（提示请求失败）。排查发现两层原因：1) 直接原因——后端服务未运行（8080 无监听，早间实例已退出且无关闭日志），前端一切请求失败，任何账号均无法登录；2) 账号数据缺口——管理端「新增用户（家长类型）」按现有设计仅落库 sys_user、sys_user_organization 与 password_hash 三项，而家长登录资格判定（AuthenticationApplicationService.isEnabledWebUserWithMatchingPassword 与小程序家长端点同款）要求 type=FAMILY + ENABLED + PARENT 角色绑定 + auth_parent_profile 记录四项全满足；新增流程不自动建后两项，故凡管理端新增的家长账号均无法通过资格判定（种子家长 parent01/02 因初始化数据带全两项而可登录）。

处置（按用户约束不动任何功能代码）：pwsh 重启后端（health UP、db/redis=true）；经现有角色分配接口为 xiangkanglin 绑定 PARENT 角色（GLOBAL 域，204 成功）；以一次性 SQL 工具按 parent01 行结构补建 auth_parent_profile（onboarding_status='COMPLETED'，幂等防重，凭据运行时读取不回显）。终态核对：type=FAMILY、status=ENABLED、password_hash 非空、PARENT 角色已绑（启用）、profile COMPLETED——四项资格条件全部就绪，登录验证由用户以自设密码完成。

现有设计已知约束（记录备查，未经批准不改动）：管理端新增家长账号后需手工绑定 PARENT 角色并补 auth_parent_profile 方可登录；小程序家长登录要求账号名即 11 位手机号（按手机号定位账号），字母账号名仅适用管理端 Web 密码登录。另：权限判定接口 GET /users/{id}/roles 路径猜测错误时返回 500 未预期异常（噪声记录，未影响本次处置）。未提交工作区改动。

## 2026-10-06 xiangkanglin 组织管理空树：数据范围角色语义错位（用现有自定义角色能力解决）

xiangkanglin（家长+组织管理员双身份，管理「中渝香奈公馆4栋」）登录后组织管理页空树。排查：账号数据完整（ORG_ADMIN 绑定 scopeKey=ORG:4栋、sys_organization_admin 记录在、组织 ENABLED、144 个有效权限码含 ORG_NODE_READ/CLASS_READ，前端走平台组织树管理形态）；树结构正常（4栋 直接挂两江新区下，path 无异常）。根因：数据范围解析（OrganizationDataScopeService.resolveRoleRootPaths）要求 ORG_ADMIN（dataScope=SCHOOL）授权行指向组织的 type_code 严格等于 SCHOOL，而 4栋 为楼栋类组织 → ORG_ADMIN 范围路径为空；PARENT（dataScope=SELF）对组织树零贡献；三层交集（角色路径 ∩ 用户组织关联 ∩ 管理员记录）为空 → GET /organizations 返回空列表。该角色的 SCHOOL 语义面向"学校管理员"（orgadmin 管学校时正常），用于非学校类组织即出现空树。

处置（不动功能代码，全部使用现有自定义数据范围角色能力）：新建角色「楼栋管理员」LOUDONG_ADMIN（dataScope=CUSTOM）；经现有数据范围配置接口指定 4栋；权限批量复制 ORG_ADMIN 全部 117 条 ALLOW；绑定 xiangkanglin（GLOBAL）。数据范围推演：CUSTOM→4栋.path 与成员关系、管理员记录三层交集=4栋 子树，组织树接口预期返回「中渝香奈公馆4栋」。保留原 ORG_ADMIN 绑定（SCHOOL 语义对其无贡献亦无害）。登录实测由用户完成。

现有设计已知约束（记录备查）：ORG_ADMIN 内置角色的数据范围语义限定 SCHOOL 类型组织；对学校以外类型（楼栋/小区等）的组织管理员，应使用自定义数据范围角色（现有角色管理能力支持）。未提交工作区改动。

## 2026-10-06 修复数据范围裁剪子树的组织树 409（xiangkanglin 组织管理报「当前状态不允许执行此操作」）

配置楼栋管理员角色（CUSTOM 范围指向 4栋）后，xiangkanglin 打开组织管理报 409 STATE_CONFLICT。定位：数据范围解析已正确返回 4栋 子树（rootPaths=[4栋.path]，findByRootPaths 返回 4栋 单节点），但 toTree 组装时该节点 parentId 指向两江新区（不在可见集合内），命中「组织树数据不完整」IllegalStateException → 409。这是数据范围裁剪的固有语义缺陷：**任何非 ALL 范围的可见子树，其顶层节点的父必然不在可见集合内**（orgadmin 学校范围同构），全量管理员不受影响。

修复：toTree 组装改为「父不在可见集合内的节点（含真根）一律作为可见根渲染」——数据库外键保证父子引用有效，裁剪集合中父不可见即父在范围外，作为根是正确语义；注释同步说明。后端 organization 包 57 项通过；全量 932 项失败 19+1（较既有基线 21+1 减少，系今晨会话修复旧夹具所致，本轮零新增）。打包重启 health UP。实测由用户以 xiangkanglin 登录完成（预期组织树显示「中渝香奈公馆4栋」为根）。未提交工作区改动。

## 2026-10-06 xiangkanglin 菜单收敛（楼栋管理员角色权限收紧）

用户反馈家长+楼栋管理员账号不应看到教师管理菜单。成因：LOUDONG_ADMIN 角色建立时批量复制了 ORG_ADMIN 全部 117 项授权，带入教师/学习任务/考勤/异常报备/导入导出等学校语义权限码，菜单随权限码显示。处置（差量权限收紧，不动代码）：经现有批量授权接口撤除 TEACHER_READ、TEACHER_CREATE、LEARNING_TASK_READ_MANAGED、ATTENDANCE_READ、ATTENDANCE_RECORD、EXCEPTION_REPORT_READ、IMPORT_JOB_READ、EXPORT_JOB_READ 八码（117→109），保留组织树管理、学生登录（STUDENT_READ/STUDENT_LOGIN_QR_CREATE）、用户管理（IAM_*）、机构业务（CLASS_READ 等）与家长成长类（GROWTH_*_CHILD 等）权限。刷新后教师管理、学习任务、考勤台账、异常报备、导入校验作业、数据导出中心六个菜单消失。

学生登录「看孩子」确认：现有功能已支持——学生目录接口（StudentMapper.directoryFilters）内置家长亲子分支（PARENT 角色 → EXISTS edu_parent_student ACTIVE），与组织范围分支为 OR 并集；xiangkanglin 当前无亲子关系数据故列表为空。孩子绑定待用户提供学生姓名后以一次性数据操作建立（现有管理端无绑定入口）。未提交工作区改动。

## 2026-10-08 校区层级、双栏关联与默认角色收尾

支持学校直接年级或学校→校区→年级→班级，CAMPUS只能属于学校。学生关系及教师分班以学校隔离且继续校验实际授权范围；选项以组织DFS顺序、完整路径显示。组织成员/管理员和角色用户均使用待加入/已加入双栏，子树筛选、独立分页、批量增减，角色按用户+授权组织精确撤销。自动ORG_ADMIN按同校管理员关系保留/回收，既有手工角色绑定保留。默认STUDENT/TEACHER/PARENT/ORG_MEMBER/PLATFORM_USER，不自动SYS_ADMIN；普通角色SELF无管理业务权限。授权学校角色允许既有下级会员，不补学校会员，不扩大原实际范围。

复核教师源范围写入绕过已修复：绑定及解除要求源教师在操作者实际组织范围内；校内其他校区教师只能由有对应范围的账号操作。教师DTO新增organizationNames，旧classOrganizationIds不变；菜单动作前后端358项内容及顺序一致。

最终相关后端46项通过，前端专项及tsc+vite build通过，后端package成功。8080/5173/5174已运行，真实MySQL结构V104、Redis正常、admin登录通过、双栏真实API通过，四年级三班向思翰保留。浏览器工具初始化失败，实际点击/视觉验收尚未完成；没有清空数据或改变密码。日志与待验收细节见docs/superpowers/plans/2026-10-08-campus-school-members.md。

## 2026-10-08 关联用户弹窗布局简化

组织成员、组织管理员、角色关联用户共用弹窗加宽至min(1480px,96vw)，列表高度随视口增加；加入/移除移到两栏中间，保留双箭头、文字与原权限actionKey。组织筛选与授权组织框340px，树节点及列表只显示当前组织名称，树缩进保留层级；后端原组织路径和授权范围不变。小屏改为上下两栏，中间按钮横排。既有双栏/角色回归10项通过，tsc+vite build通过。证据.local-verification/user-picker-layout-tests.log、user-picker-layout-build.log。

## 2026-10-08 用户资料编辑与随机重置密码

用户列表新增独立IAM_USER_UPDATE编辑按钮和抽屉，可修改名称/手机号；账号、用户类型只读，机构关系沿用原关联流程。后端PATCH /users/{id}校验编辑权限与目标实际组织范围、注销状态及重复手机号，事务锁用户行；学生姓名同步档案，手机号变更撤销该用户现有会话，记录USER_PROFILE_CHANGE。V105初始化可授权编辑/保存/取消菜单按钮，并继承现有用户创建允许/拒绝授予；前后端动作目录一致。

重置密码弹窗用Web Crypto生成每次新8位字母数字组合，至少一字母一数字，同时填入确认值，仍可手改；密码只在原确认操作后提交，不存日志或本地持久化。学生既有登录码流程不变。

后端用户编辑及角色回归16项、前端编辑/随机密码/目录/菜单13项通过，前端类型检查构建成功，后端打包成功。运行JAR锁定导致首次打包失败，核实项目8080进程后停服重打包并恢复；真实MySQL已成功V105，健康db/redis正常，admin获新编辑权限，以原资料无变化PATCH验证新接口成功，未改任何真实密码。证据.local-verification/user-edit-backend-tests.log、user-edit-web-tests.log、user-edit-web-build.log、user-edit-package.log、user-edit-server.log、user-edit-runtime-verification.log。

## 2026-10-08 组织业务同校选择与创建目录联动

组织用户选项卡前置并默认打开。学校业务动作接收SchoolHierarchyPanel推导的真实学校ID，未取得上下文时禁用；教师班级通过/classes?schoolOrganizationId查询、前端再次按学校及有效状态过滤，无固定学校的入口先选择学校。学员关系目标班级DTO新增schoolOrganizationId，按所选学生入学学校过滤；上下文或学生改变清空目标选择，提交再次核对本校。后端保留原校内关系写入校验及实际数据范围。

关联已有用户/管理员弹窗删除同校与未归属分组下拉，固定本校候选；组织筛选树只呈现本校节点，裁剪树仍保留同校已授权兄弟校区。后端组织候选拒绝UNBOUND分组（403）、allowedUnbound=false；非学校目标候选收窄自身子树。角色用户关联仍按其原实际授权范围。没有清空数据或改变既有密码。

普通/教师/家长新账号使用同一Web Crypto生成器自动填8位字母数字初始密码与确认值，可修改；学生保持专属学生登录码。按学生类型自动建立学生档案、凭据及入学关系，按教师类型自动角色与学校会员，已用真实教师目录查询和学生创建集成测试确认自动挂接。创建成功通知已打开的学生/教师目录刷新一次，无凭据进入事件。

验证：后端4组17项通过；前端9组75项通过，追加裁剪校区回归后选择器9项通过（与前次重叠8项，唯一相关前端76项）。类型检查/前端构建与后端打包成功；后端已恢复8080，管理端5173 HTTP200，MySQL V105、Redis正常。真实3所学校查询各自班级及学员目标班级隔离，未归属查询均403，原向思翰班级关系保留。证据.local-verification/school-only-backend-tests.log、school-only-web-final-tests.log、school-only-picker-final.log、school-only-web-build.log、school-only-package.log、school-only-server.log、school-only-runtime-verification.log。

## 2026-10-08 班级组织用户创建后自动挂接班级师生列表

定位：STUDENT在CLASS下创建已有同事务学生档案/入学/班级关系；TEACHER此前仅建立身份和学校会员，缺edu_teacher_class授课关系，故班级学生与教师选项卡不显示新教师。UserAccess创建教师且目标CLASS时，现在预检TEACHER_CLASS_ASSIGN权限，并在同一事务复用TeacherClassAssignmentService.assign，保留真实范围、同校、有效状态、功能开关与BIND审计；学校/校区/年级账号不猜测具体班级。缺分班权限不会写入账号。班级面板订阅教师创建通知，StrictMode下更新一次并在卸载后清理；学生原有通知与自动入班保持。

真实只读查询仅找启用ORGANIZATION教师、已有直接CLASS会员但不存在任何授课历史的组合，发现2条。通过原有受保护绑定API补齐并确认classOrganizationIds包含对应班级，排除主动解除/停用的关系，无密码变更。重启后2条关系仍可见，向思翰原班级关系保持。

验证：后端CampusMembership10、学生创建4、教师班级2项（16项）通过；相关前端创建14项已通过，班级面板新增刷新夹具修正后14项通过（唯一28项）；前后端构建/打包通过，8080/5173正常，真实DB/Redis UP，结构仍V105。证据.local-verification/class-user-autobind-backend.log、class-user-autobind-web.log、class-user-autobind-web-final.log、class-user-autobind-repair.log、class-user-autobind-build.log、class-user-autobind-package.log、class-user-autobind-runtime.log。只读检测脚本MissingClassTeachers.java及结果在忽略的.local-verification目录。

## 2026-10-08 组织类型/角色生命周期与组织用户类型限制

组织类型、自定义角色新增编辑、删除与启停单选，内置类型及内置角色前后端禁止这些操作；角色编码/数据范围保持只读，名称说明可编辑；组织类型编码只读、名称排序可编辑。有组织引用的类型、有用户关联的角色拒绝删除，未关联角色清理权限/自定义范围后删除。角色状态改变撤销关联用户现有会话。组织类型停用阻止新建该类型组织，不级联停用现有组织。Role列表授权/关联用户保留，去掉两项toolbar配置角色权限/配置用户权限入口。

用户目录启停用、锁定解锁改两组Radio单选：LOCKED属于启用且锁定，DISABLED时锁定组不可操作；注销无操作。Radio动作同时校验运行权限和菜单配置，IAM_USER_STATUS_CHANGE补为菜单主键；旧三个独立状态按钮停用。HTTP用户状态改为显式授权包装，检查真实用户可见范围，内部可信状态入口保留。

新增用户依据选中组织动态限制：FAMILY只FAMILY/STUDENT，SCHOOL/CAMPUS/GRADE/CLASS只ORGANIZATION/TEACHER/STUDENT，COUNTRY/CITY/REGION只PLATFORM；未知自定义组织保持原选项。后端同规则拒绝伪造请求，默认类型合理修正，随机密码、学生专属登录码、班级师生自动挂接及缓存刷新保留。

V106增加六项生命周期权限及新状态配置入口、两项取消动作，沿已有创建授予保留ALLOW/DENY；审计增六事件和ORGANIZATION_TYPE，前端/导出中文标签同步。后端相关6组40项全部最终通过（首次39pass/1fail为区域创建测试被误改机构类型，按真实REGION夹具纠正后相关16项重测绿色）；前端6组67项通过，类型检查/构建/后端打包成功。

真实MySQL增量迁移V106成功；临时专用role/type完整create/edit/disable/enable/delete烟测成功并清理，内置无变化修改请求被400拒绝、用户status无变化请求保持updatedAt，无现有用户密码或资料更改，健康DB/Redis UP、5173 HTTP200。证据.local-verification/management-lifecycle-backend.log、management-lifecycle-backend-final.log、management-lifecycle-web-final.log、management-lifecycle-web-build.log、management-lifecycle-package.log、management-lifecycle-server.log、management-lifecycle-runtime.log。

## 2026-10-09 培训机构、多机构学生关系与统一分页

新增内置TRAINING_INSTITUTION，教育机构根统一覆盖学校/培训，校区与GRADE（培训作为课程分类）及CLASS层级保留，机构范围SQL、教师、角色SCHOOL范围、成员、任务与导入等兼容；不自动转换已有SCHOOL记录。普通学校每机构一个行政班，培训可同时多课程班，转班/单课退出/机构退出只处理该机构，其他学校/培训/家庭关联及同一账号凭据保持。多机构摘要增加机构类型、班级ID和名称数组，不能用任意单值表示多课。Family学生创建学校/年级不再必填，单独建档和一次凭据创建。

新增班级邀请码（256bit随机码，SHA256存储，有效期默认7天，最多100次申请）、有限信息解析、家庭申请及机构确认。邀请码不提供全校搜索；申请前核验真实亲子关系、家庭管理员或管理权限+实际家庭范围，共居不等于亲子。审批复用已有学生身份，保留家庭会员并建立实际机构和班级关系。学校认证暂不做。家庭组织用户新增编辑入口（复用IAM_USER_UPDATE）和关联机构申请；班级业务新增邀请码/入学审核，家庭可查看本学生申请记录。

公共分页参照授权审计：默认10，10/20/50，右下角共X条与跳页，容量变化回第一页；服务端请求实际传容量，筛选/刷新保留容量，双栏名单独立容量，静态表公共wrapper控制分页。明确pagination:false的树/小配置/报表不强制分页，内部完整加载100批次保持。所有相关Web页容量已检查，包括用户/教师/学生/任务/考勤/异常/功能开关/导入导出/复盘等。

验证：机构负责人19项、学生31项及完整多机构E2E通过，root整合59项首次58通过/1错误为不同测试同名家庭约束，唯一家庭名称修正后相关学生及入学12项通过，最终59核心均有成功证据。Web全量76文件443项初次439pass/4fail，旧默认20断言及mock reset修正，导入详情专项通过、2邀请测试与14班级面板最终通过，四项均已专项绿色，未重复声明全量443单次绿。公共分页负责人另13文件119独立相关用例通过。前端typecheck/build、后端package通过。旧FlywayMigrationTest仍有6个历史固定数量/旧权限断言未更新，不作为全量后端成功声明。

真实MySQL从V106增量到V108（109迁移校验），没有删除/重建业务数据，不重置学生码或用户密码；健康DB/Redis UP，admin登录、新类型及三个入学权限、实际pageSize10/50接口均验证，向思翰原班级保留。8080/5173/5174持续会话恢复，原密钥复用，JDK17本轮采用已验证Microsoft17路径。证据.local-verification/training-pagination-backend-final.log、training-backend-failures-final.log、student-multi-e2e-final.log、training-pagination-web-full.log、training-web-full-failures-final.log、training-web-final-green.log、training-pagination-web-build.log、training-pagination-package.log、training-pagination-server.log、training-pagination-runtime.log。


### 2026-10-09 辅助按钮无需授权

- 精确移除取消弹窗、重试加载、重置查询共100个辅助动作目录；前后端目录同步。
- ConfiguredButton使用已核对的编码清单，辅助操作不依赖菜单权限；ConfiguredModal取消按钮始终可用。
- 取消关联、取消管理员、重置密码等业务操作保留权限控制。
- V109已在真实数据库迁移成功；后端health为UP、数据库与Redis正常。相关8项组件/目录测试和前端构建通过。


### 2026-10-09 入学申请状态与按钮文案修复

- 已核对向思翰在新牌坊小学三班的ACTIVE关系；申请同校四班应走校内转班，不修改其真实关系。
- 入学已入班、待审重复申请、同校已有行政班等已知冲突返回具体说明，普通内部状态异常仍保留泛化提示。
- 关联弹窗分步按钮保留“确认班级”和“提交入学申请”，展示权限仍由同一申请动作控制。
- 8项后端及7项前端相关回归通过，前端类型检查通过；后端重新打包部署。


### 2026-10-09 组织管理身份展示与满屏布局

- 组织类型、变更记录页签仅SYS_ADMIN角色显示；有组织读取权限的其他角色直接显示组织架构且不渲染外层页签栏。
- 保留原接口权限与组织数据范围；仅有审核权限但无组织读取权限的账号不加载变更记录，也不扩大组织读取权限。
- 组织架构、菜单管理、数据字典占满工作区可用高度，左右白色面板等高；长内容在面板内部滚动，外层页面不滚动。
- ViewportTable同时参考所属面板底部计算表格高度，保留分页空间。
- 前端49项相关回归、类型检查和生产构建通过。


### 2026-10-09 角色关联撤销校验修复

- removeRole与batchRemoveRole使用独立撤销范围校验，不复用新增授权的组织类型/角色范围匹配条件。
- 仍验证操作者实际组织范围、具体用户角色组织授权记录、全局范围和组织管理员身份保护；不直接修改真实用户授权。
- RoleBatchAssignmentTest共4项通过，包含旧类型不匹配授权可撤销、新授权仍拒绝、越权撤销不改变数据。验证使用spring.flyway.target=108隔离V109 MySQL删除语法的H2兼容问题；真实数据库仍V109，不修改已执行迁移。
- 后端重新打包并部署。


### 2026-10-09 组织管理员角色撤销明确提示

- 只读确认xiangkanglin在中渝香奈公馆4栋有实际组织管理员身份与ORG_ADMIN组织范围授权。直接撤销被管理员身份保护阻止。
- 已知撤销冲突使用RoleRemovalStateException返回明确操作说明，未知状态异常保持泛化。
- 正确流程为组织用户取消管理员，同步移除对应ORG_ADMIN并保留成员身份；未自动修改该真实用户身份。
- 角色撤销响应测试通过，后端重新打包部署。


### 2026-10-09 菜单按钮授权名称功能前缀

- 270项前后端内置动作目录按所属子功能使用“功能 · 操作”，label字段保留实际页面短文案。
- V110同步真实库sys_menu按钮名和sys_permission权限名；手工按钮以所属页面为前缀，编码与授权关联不变。
- ConfiguredButton/ConfiguredModal不使用带功能前缀的配置名称覆盖实际操作按钮；既有普通自定义短名称仍支持。
- 批量新增按钮名称提供功能前缀示例。
- 10项组件/目录回归及前端构建通过；真实库无未加前缀按钮、无菜单权限名称不一致，V110迁移成功。


### 2026-10-09 按钮功能分组排序

- V111按所属页面将同一功能前缀按钮连续分组；主功能组在前，组内查询查看、新增编辑、关联授权、审核、执行和保存确认、停用删除排序。
- 同步sys_menu及sys_permission.sort_order；页面目录顺序、编码与授予关系不变。
- 修正MySQL临时表重复引用限制，仅清理V111失败迁移记录后重试成功。真实库V111已应用、后端恢复。


### 2026-10-09 权限与菜单完全对齐及用户组织编辑

- V112逐项将63个接口/小程序权限挂到现有业务页面，保留客户端、原代码及角色用户ALLOW/DENY，角色与用户授权树不再产生其他权限。兼容权限明确标注并保留，避免破坏已有授权。
- 真实接口核对未映射权限为0；权限客户端、角色授权及用户授权迁移前后摘要相同。
- 编辑用户所属组织支持多选且至少一个；GET organization-edit回显当前、锁定及可访问选项，PATCH资料与组织差量在同一事务更新。
- 编辑依赖IAM_USER_UPDATE，变更关联还需IAM_USER_ORGANIZATION_ASSIGN；目标类型、组织范围校验保留，管理员/角色/学生教师业务依赖锁定，组织列表并发变化拒绝覆盖。学生学校班级仍走入学申请或学员关系。
- 用户编辑前端15项及后端11项相关回归、最终构建通过；真实接口学生组织回显成功、空组织保存400拒绝、4条业务依赖锁定；后端健康UP、数据库Redis正常。
- 权限映射前端19项授权树测试及后端6项菜单客户端测试通过；修正未成功V112同表更新子查询后成功部署。


### 2026-10-09 用户列表关联角色

- 用户管理新增“关联角色”，左右显示未关联/已关联角色，中间加入所选、移除所选，按角色名称编码筛选。
- 同一角色的组织范围授权独立回显；全局角色直接关联，学校机构/区域/班级等角色先选授权组织；移除使用原授权组织。
- UserRoleAssociationService复用既有批量授予及撤销逻辑，在外层事务完成多角色操作，保留操作者权限、用户和组织范围以及管理员身份保护。
- V113将既有IAM_USER_ROLE_ASSIGN规范节点置于用户管理，编码与授权不变；角色侧关联用户继续复用同一权限。
- 15项前端相关回归、4项后端测试及构建通过。真实接口角色回显、空批次400拒绝及菜单归属验证通过；未改变真实用户角色，后端健康UP。


### 2026-10-09 家长账号菜单差异与学生查询编码统一

- 只读核对xiangkanglin仅PARENT角色，无用户独立权限；角色已授予学生登录页面和旧查询按钮，缺少实际STUDENT_READ接口码。
- 家长关系、班级匿名排行全局开关关闭，仍按既有功能限制隐藏，未启用开关。
- V114把student-login.student-login-management-page.2与STUDENT_READ同功能合并，角色用户授权迁移，DENY优先；清理旧查询节点/权限，把规范查询按钮归学生登录页面。
- 学生登录前端入口识别页面权限STUDENT_LOGIN_READ及STUDENT_READ，功能开关依然有效；按钮使用规范接口权限码。
- 49项菜单路由学生登录回归及前端构建通过；真实库V114成功，迁移前后查询合并效果摘要校验一致，家长STUDENT_READ授权已补齐。


### 2026-10-09 组织管理子按钮授权入口一致性

- 核对PARENT已有组织管理多个按钮ALLOW但无ORG_NODE_READ，前端入口与组织树接口只看页面读取码造成菜单缺失。
- PermissionDecisionService针对WEB组织管理只读入口，按同一菜单可见性规则从实际已允许子按钮派生ORG_NODE_READ；不派生写权限，显式用户/角色DENY及菜单停用优先。
- PARENT数据范围为SELF，本人不等于家庭组织范围；组织数据范围解析不变，组织树无可访问数据时明确提示检查角色范围与组织授权。未给真实账号加组织范围或管理员身份。
- 5项拒绝/停用/无关按钮决策测试及1项真实范围集成测试通过；集成使用兼容V108测试结构，真实数据库仍V114。前端构建通过，后端重新部署。


### 2026-10-09 家长家庭组织可见与默认PARENT_ADMIN

- 普通PARENT可通过组织管理只读查看本人直接关联且启用的FAMILY节点；不修改SELF写入范围，不展示未关联家庭、学校或组织成员管理操作。
- FAMILY组织设置管理员时挂PARENT_ADMIN及当前家庭组织范围，学校/培训机构仍ORG_ADMIN；取消家庭管理员移除对应自动角色，保留PARENT与成员身份，角色停用也可撤销管理员。
- 增加FAMILY角色数据范围，按sys_user_role.organization_id与用户组织关联、管理员边界交集计算，不给全部家庭范围。
- V115查到已有PARENT_ADMIN为用户自建CUSTOM/SELF，保留名称与全部权限，仅改为FAMILY；不自动变更现有用户角色，不移除旧ORG_ADMIN。初始化默认权限只对尚不存在的新角色生效。
- 最初大量管理授权/旧ORG_ADMIN清理迁移被自动审批拒绝未执行，改为最小方案后完成。真实授权、用户权限与权限客户端摘要前后相同。
- 前端26项相关回归及最终23项组织页面回归、构建通过；后端7项家庭隔离及管理员生命周期回归、最后2项角色停用撤销回归通过。集成验证使用兼容V108结构，真实库V115已应用。
- 三服务运行，后端数据库Redis正常；现有PARENT_ADMIN权限按用户配置控制，不默认追加高权限管理授权。


### 2026-10-09 家庭管理员组织用户页签修复

- PARENT_ADMIN已授予organizations.members.query但未授予IAM_USER_LIST，旧页面和成员接口额外要求后者导致页签隐藏。
- 页签及组织成员查询接口接受专属组织用户查询权限，保留旧IAM_USER_LIST兼容；仍按当前家庭范围校验，不新增用户目录或其他权限。
- 全局组织类型403不再阻断当前家庭组织加载；补齐ApiRequestError导入。
- 25项前端页面回归、2项家庭范围后端集成回归及构建通过。核对xiangkanglin实际PARENT_ADMIN绑定家庭中渝香奈公馆4栋28-1，存在对应管理员身份。
- 后端、Web5173、H55174恢复运行，健康UP，数据库Redis正常，两个前端HTTP200。     

### 2026-10-23 小程序 78 视图四门开发自测完成（待Codex验收，非签收）

- 按 quantified-plan-2026-10-22.md 六板块顺序完成小程序 78 视图×4 门（F 真实功能闭环/P 权限及组织范围/V 原型与原生视觉/C 兼容与回归）原生验收，同候选 mp-acceptance-20261021-next / SDK 9439 / 8081 隔离库。全部为开发自测，统一标“待验收”；勾选数不作为实际完成率。
- 状态：340 已勾选（开发自测）/ 10 未勾选（07-05 四门三端无实现缺口 + 2026-10-23 初审撤回 6 项：04-01-V、04-04-V、04-06 四门）。板块6 最终全量回归+真实权限巡检 47 断言全绿：越权矩阵 7 项全拦截（跨身份 403/无 token 401）、SQL 角色边界白名单核对（STUDENT 无管理/机构/IAM 码、PARENT 的 ORG_MEMBER_* 恰为家庭三码）、数据范围隔离实证（parent 持 ORG_MEMBER_READ 访问学校管理员列表仍 403）。
- 证据：.local-verification/ 下 20+ 验收脚本（g03xx/g04xx/g0101-06/g0103/g0107-10/g0705-10/g0201-06/g0207-10/g0501-10/g0601-10/g-layout/g-final-regression 等）、239 张截图（native-20261022/23-*.png）、结果 JSON/日志多份；handoff-2026-10-21.md 末尾六板块段落。
- 未提交git；真实8080未动、权限未放宽、真实库未清。结论待Codex复验。
