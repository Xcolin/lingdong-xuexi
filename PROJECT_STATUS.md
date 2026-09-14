# 灵动学习项目现状核查报告

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
├─ 灵动学习-需求原始素材汇总V1.0.md
├─ 灵动学习-业务需求说明书-V1.0.md
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
