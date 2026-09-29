# 灵动学习剩余开发执行计划

> 执行要求：使用 executing-plans 按任务实施；每项必须记录实现、验证证据和剩余限制。不扩大业务范围。Git 提交由用户自行处理，后续不检查提交状态、不提醒提交。

**目标：** 根据 PROJECT_STATUS.md 的实际缺口完成现有需求，不重做已有模块，不用测试通过率替代业务完成率。

**架构：** 保留 Spring Boot 分模块单体、MyBatis XML、MySQL/Redis/Flyway，以及独立 React Web、uni-app 小程序。复用现有权限、组织范围、功能开关、附件与导出基础设施。

**技术栈：** JDK17、Spring Boot3、MyBatis、Flyway、React、uni-app。Maven4 是原需求，当前本地 Maven3.9.14，只作为现有验证工具，不宣称版本要求已满足。

## 一、基线与执行约束

- 审计来源：根目录 PROJECT_STATUS.md，保留原样作为审计快照。
- 初始数据库源码迁移至 V68、94 张业务表；本次 R03 新增 V69 排行客户端权限，R04/R05 新增 V70 小程序家长周报读取权限，R04 新增 V71 小程序家长奖励权限和 V72 Web 系统任务读取权限，R07 新增 V73 全局开关版本快照及管理权限、V74 接口变更执行状态、V75 接口变更前快照；R06 新增 V76 字典台账及 V77 模板台账导出权限、类型约束和独立模板模块，均不新增业务表。后续新增迁移先核对编号，R06 新增 V78 接口服务台账导出权限、类型约束与模板模块；V79 增加缓存操作日志导出；V80 增加系统任务审批台账；V81 增加奖励兑换报表；V82 增加异常报备台账；V83 增加附件管理台账；当前下一编号 V84。远程已验证版本仍为 V77，V78 至 V83 未执行；不得修改已执行迁移。
- 历史 83.6% 仅是旧台账得分，后续按下表逐项验收，不据此承诺完工。
- 系统审核员只处理系统管理员提交的任务；新增角色和组织管理员不能越过授权范围。
- 功能停用既隐藏入口也拒绝直接 API 操作；撤销授权、退订等必要退出动作按既有设计保留。
- 定位能力保持关闭，不把个人主体上线解释为必须新增或启用定位。
- 新增接口沿用字符串雪花 ID；所有文档及必要业务注释使用中文，不记录凭据原文。
- 自动推进无外部阻塞任务；供应商资质、短信签名、微信模板等未给定时不虚构配置或发送成功。

## 二、任务与验收

| 编号 | 任务与交付边界 | 依赖 | 当前状态 |
|---|---|---|---|
| R01 | 恢复可重复的后端全量回归；固定学生登录测试随机碰撞 | 无 | 已完成，678 项全量通过 |
| R02 | 发布资源排除本地密钥、外置配置说明、工具链校验 | R01 | 配置隔离已验证；Maven 4 正式工具链受阻 |
| R03 | 匿名排行班级选项、适用端页面、授权开关与撤销完整闭环 | R01 | 本地双端闭环已验证；真实环境验收归 R10 |
| R04 | 六角色与两端功能矩阵、现有角色工作台待办接入 | R01 | 业务待审、学生今日任务、家长周报与家庭任务本地闭环已验证；其他矩阵缺口未完成 |
| R05 | 短信真实适配、微信订阅授权及周报发送/回执 | 供应商参数和模板 | 默认阿里云短信适配及离线专项通过；微信及真实送达未完成 |
| R06 | 剩余统计口径、数据集导出与业务导入适配 | R04 | 字典、模板、接口服务、缓存、系统任务、奖励兑换、异常报备及附件本地闭环通过；其余三类数据集与导入验收未完成 |
| R07 | 通用功能开关、系统任务完整载荷、缓存及接口服务执行边界 | R04 | 已落地五类任务本地闭环验证通过；真实环境验收归 R10 |
| R08 | 附件持久化、保留归档与销毁闭环 | R06 | 本地存储已有，交付未完成 |
| R09 | CI、部署、监控、备份恢复、安全及性能验证 | R02、R06至R08 | 未完成 |
| R10 | 真实环境联调、双端 UAT、需求追溯与交付验收 | 前述各项 | 未完成 |

### R01：回归基线

文件：server/src/test/java/com/lingdong/learning/auth/web/StudentAuthenticationControllerTest.java。

- [x] 固定发码为 9999，运行原有错误登录断言，确认可重复失败。
- [x] 选择与实际发码不同的四位测试输入，保持前四次 401、第五次验证码要求和停用拦截断言。
- [x] 执行后端全量测试，记录数量和结果；若出现其他失败先归因，不掩盖或跳过。
- [x] 记录指定 JDK 的可执行命令，明确没有进行远程迁移或生产验证。

### R02：发布配置与工具链

文件：server/pom.xml、server/src/main/resources/application.yml；新增中文部署说明与发布检查脚本，不将 application-local.yml 的值复制到文档。

- [x] 检查现有本地启动依赖，保留用户本地连接方式，设计发布资源排除和外部配置导入。
- [x] 对构建资源及最终 JAR 做检查，证明不包含本地配置；处理旧 target 资源残留，不能仅凭 Git 忽略宣称解决。
- [x] 验证缺少必要配置时明确报错，测试配置不访问远程数据库。
- [ ] 校验 Maven4/JDK17 兼容性；安装或环境切换不能偷偷降低原技术要求。

### R03：匿名排行闭环

现有文件：growthpoint/application/AnonymousRankQueryService.java、AnonymousRankAccess.java、growthpoint/web/AnonymousRankController.java、resources/mapper 下对应 Mapper XML（均位于 server/src/main）。前端在 web/src/features 与 miniapp/src 按既有路由模式扩展。

- [x] 先核对现有学生/班级选项接口；只能返回当前监护关系下学生的有效班级，不向前端开放任意组织枚举。
- [x] 补授权后的班级选项及能力暴露测试；选班、读取偏好、启用、查看、停用采用既有接口版本控制。
- [x] Web 页面覆盖默认关闭、并列排名、空数据、关系撤销、版本冲突、功能关闭；只显示名次和积分，不泄露学生身份。
- [x] 明确小程序当前家长会话与 WEB 限制的匹配方式，完成适用页面及服务端权限测试，不直接放宽客户端校验。
- [x] 桌面/移动视口验证，后端契约和前端测试通过，文档更新后才标记本项完成。

### R04：角色与端覆盖

文件范围：web/src/App.tsx、现有 Dashboard/工作台；miniapp/src/pages/parent-home.vue、页面注册及角色会话模块；server/src/main/java 中现有 teacher、parent、learningtask 模块。

- [ ] 按六个内置角色逐条列出 BRD 操作入口、端适用性、数据范围、API 和测试，不以页面总数衡量覆盖。
- [x] 将家长首页固定“暂无待办事项”替换为真实待办查询及加载/失败/空态。
- [ ] 利用现有查询建设角色工作台，复杂统计仅 Web；不自行增加指标定义。
- [ ] 补齐矩阵中缺失的既定操作入口，验证角色动态变更、组织范围、无机构学生独立使用。

### R05：第三方消息

文件范围：server/src/main/java/com/lingdong/learning/auth/infrastructure/sms、成长报告订阅和 delivery 模块、miniapp 授权入口及接口服务配置。

- [ ] 用户提供短信供应商、签名、模板，以及微信订阅模板 ID、字段、授权类型后，依据真实契约实施适配。
- [ ] 发送成功、失败、超时、限流、重试及撤销授权分别验证；不得把排队记录当送达记录。
- [ ] 发送前重新校验亲子关系、功能与授权，幂等控制避免重复发信；日志脱敏。
- [ ] 使用可用个人主体真实设备验证，平台不支持的能力标注阻塞，不伪造长期授权。

### R06：报表与导入导出

文件范围：现有 exportjob、templateconfig、统计/导入模块及两端适用页面。

- [x] 对照 BRD 与统计口径文档逐项列出尚缺数据集；复用已完成积分、权限审计及 PDF 导出。见 `../specs/2026-09-20-lingdong-report-dataset-matrix.md`，此项仅为缺口核对，不代表新数据集已实现。
- [ ] 每个缺失数据集先固定公式、过滤范围、字段和脱敏规则，再接入执行适配器；不把模板 CRUD 当执行能力。
- [ ] 导入覆盖格式错误、逐行错误、重复数据、越权、部分失败与事务边界；导出覆盖异步任务、下载时权限复核。
- [ ] 用固定业务样例核对页面、导出与数据库结果一致。

当前增量：数据字典台账已接入原异步作业、独立默认模板、Web 创建/筛选/下载，创建、执行与下载复核字典动态权限和开关，混合审核员拒绝。专项覆盖固定样例 XLSX 内容、停用项及类型、时间闭区间、游标上界和撤权。2026-09-20 最终全量 `server/target/remaining-r06-dictionary-final.log` package 退出 0，188 类 749 项，失败/错误/跳过均 0；JAR 包含 V76 和新 Mapper、无本地配置，证据 `server/target/remaining-r06-dictionary-artifact-check.json`。Web 最终 6 项、类型检查及生产构建通过，日志 `.local-verification/r06-dictionary-web-final.log`、`.local-verification/r06-dictionary-web-build-final.log`。首轮全量发现两处旧模板选项精确断言，已分别限定 V55 原始选项及更新当前模块列表，并新增 V76 最小授权验证，未改历史迁移。

详见 `../specs/2026-09-20-lingdong-dictionary-export.md`；不据此勾选全部数据集验收。后续按矩阵推进模板台账等明确事实口径，既有学员导入仍需真实事务与业务样例验收，不能仅凭模拟调用测试认定完整闭环。真实环境需受控应用 V76 并配置字典台账默认模板；本次没有远程写入。Maven 4 正式工具链限制仍保留。

模板台账增量（2026-09-20）：六列白名单、类型/模块/状态/时间筛选、独立默认模板和 Web 入口本地闭环完成。真实工作簿验证、非法筛选拒绝、混合审核员边界、205 条分页和固定上界通过；独立复核发现的 XLSX 生成中撤权空档已先红后绿修复，每页及成功终态前重新校验完整权限，失败释放附件关系并清理内容。三类专项 20 项通过。

最终 `server/target/remaining-r06-template-verified.log` 全量 package 退出 0，188 类 755 项，失败/错误/跳过均 0；最终 JAR 含 V77 和新 Mapper、无本地配置，证据 `server/target/remaining-r06-template-artifact-check.json`。Web 最终契约测试 8 项、类型检查、生产构建通过，日志 `.local-verification/r06-template-web-contract-green.log`、`r06-template-web-typecheck.log`、`r06-template-web-build.log`。范围及默认模板配置见 `../specs/2026-09-20-lingdong-template-ledger-export.md`。远程迁移、默认模板配置和真实环境验收仍待发布，Maven 4 限制保持；未执行远程写入。R06 其余九类数据集和导入验收不据此标记完成，下一项按既定矩阵推进接口服务等事实台账。

### R07：配置平台

文件范围：现有 feature、systemtask、cache、interfaceservice 业务包及对应 Web 页面。

- [x] 补通用开关管理入口及 API，沿用管理员提交/审核员审批流程，不绕开审批直接写表。
- [x] 系统任务展示完整业务载荷、前后差异、申请人、审批记录；执行失败有明确状态与审计。以当前五类已落地领域的受控申请字段为边界，历史缺失原值明确标注，不回填猜测值。
- [x] 对缓存类型列清支持刷新/清除的真实操作；会话清除明确是强制退出，禁止伪装成无影响刷新。
- [x] 接口服务登记与实际调用适配分清，校验凭据脱敏、超时与异常处理，不实现任意 URL 调用器。

### R08：附件生命周期

文件范围：现有 attachment 存储接口、LocalAttachmentContentStorage、附件访问及业务绑定模块。

- [ ] 将临时目录默认行为与正式持久化部署区分，配置稳定存储目录并验证重启可读；云存储只有既定部署确需时才接入。
- [ ] 依据既有保留口径实现归档/销毁条件，处理业务引用、历史导出和过期附件，不自行设定新的保留期限。
- [ ] 验证越权预览/下载、类型和大小限制、路径穿越、销毁后访问拒绝及审计。

### R09：运行交付

- [ ] 基于实际构建命令新增 CI 与中文部署运行手册；三端检查、后端测试、迁移检查、密钥检查必须失败即阻断。
- [ ] 补健康监测、错误告警及日志脱敏；使用非真实敏感数据验证。
- [ ] 建立备份恢复脚本与恢复演练证据；Flyway 失败采用修复迁移，不盲目回滚历史脚本。
- [ ] 按现有测试方案执行安全/性能场景，记录环境和阈值来源，不编造吞吐承诺。

### R10：最终验收

2026-09-22 用户明确授权现有数据库结构变更。已完成加密备份、80 表 309 行隔离恢复一致性验证及 V56–V77 迁移演练；原库在迁移前核对与备份一致，现已升级至 V77，95 表（94 业务表），Flyway 校验通过且失败记录为 0。仅修正失败 V56、未执行 V58 和两个 Mapper 的保留字引用，成功历史 V1–V55 保持不变。升级中连接中断后先只读核对实际版本，再从 V66 续跑，没有重复清理。详细证据和恢复边界见 `../../deployment/MySQL迁移恢复与验证-2026-09-22.md`。Redis 仍仅只读核对，未写入测试数据；这不代表真实环境完整联调完成。


- [ ] 先确认远程连接指向可测试数据库，备份及测试数据隔离；连接授权不等于可删除数据。
- [ ] 完成 MySQL/Redis、短信/微信真机和双端角色主流程验证。
- [ ] 每条需求关联页面、接口、表、测试及验收结果；未完成或外部受阻项明列。
- [ ] 同步数据库/API/功能设计和操作交付文档，再生成新的状态报告，不覆盖旧审计证据。

## 三、推进与记录规则

执行顺序：R01 → R02 → R03 → R04；外部资料齐备后插入 R05，其余按依赖推进。每项以完整可使用闭环收口，不拆成对外宣称完成的零散测试。

估时和 token 不从历史分数倒推。先完成 R01 至 R04，依据实际吞吐、缺失功能矩阵和外部联调条件更新剩余工作量。

### 本次执行记录

- 2026-09-28 R06 异常报备台账本地闭环完成：V82、独立模板模块和最小教师/机构导出授权已接入。复用有效班级选项，冻结班级集合和教师身份，教师只导出本人报备，机构按组织范围，任何兼任审核员均拒绝；默认五列、双姓名脱敏，不含异常正文和处理意见。Web 班级/类型/状态/时间筛选及动态撤权、迟到文件失效完成。首轮整合发现机构测试先授角色后建关联，已仅修正测试准备顺序。最终 `server/target/remaining-r06-exception-verified.log` package 退出 0，189 类 788 项通过，全部报告本轮更新，失败/错误/跳过均 0；覆盖真实 XLSX、205 行分页上界、教师解绑、班级停用、角色变更、组织移出及执行中撤权附件回收。最终 JAR 含 V82 和新 Mapper、无本地配置，证据 `remaining-r06-exception-artifact-check.json`。Web 专项 49 项、全量 45 文件 226 项通过，类型检查和生产构建通过，日志 `.local-verification/r06-exception-web-{green,full,build}.log`。当前十类数据集本地闭环，余附件、学生任务、机构任务统计、考勤四类及业务导入验收；远程仍 V77，V78 至 V82 和默认模板尚未发布，Maven 4 限制不变。详见 `../specs/2026-09-27-lingdong-exception-report-export.md`。继续核对附件元数据导出授权边界。

- 2026-09-27 R06 异常报备台账开始实施：复用当前有效班级范围，教师仅本人报备，机构按授权，固化班级集合与身份后对旧文件复核停用/解绑/移出范围。只导出五列双姓名脱敏事实，不包含异常正文、处理意见；具体设计 `../specs/2026-09-27-lingdong-exception-report-export.md`。预期 V82，未完成验收前仍计九类已完成数据集。

- 2026-09-27 R06 奖励兑换报表本地闭环完成：新增 V81、独立模板及主家长导出权限，五列取申请快照和审批/核销事实，Web 接入学生/状态/申请时间筛选及撤权保护。实际 XLSX、六种状态、主副家长/他人学生/混合审核员、字符串 ID、205 行分页上界及撤权补偿通过。最终 `server/target/remaining-r06-reward-verified.log` package 退出 0，189 类 782 项全量通过，失败/错误/跳过均 0；本轮全部报告更新。最终 JAR 含 V81 和新 Mapper，无本地配置，证据 `remaining-r06-reward-artifact-check.json`。Web 46 项专项、类型检查和生产构建通过。详见 `../specs/2026-09-27-lingdong-reward-exchange-export.md`。当前九类数据集完成本地闭环，余五类和业务导入验收未完成；远程 V77，V78 至 V81 和独立默认模板尚未部署，Maven 4 限制保留。继续异常报备台账范围核对，保持教师本人报备与机构授权范围。

- 2026-09-27 R06 奖励兑换报表开始实施：沿用兑换申请快照、活动主家长范围和原异步作业，独立五列、学生/状态/申请时间筛选，禁止副家长及混合审核员导出。Web 缺入口红灯已复现后接入，后端缺类型/模板红灯已保存；V81 尚待实施和整合验证，当前仍按八类已完成数据集计。范围见 `../specs/2026-09-27-lingdong-reward-exchange-export.md`。

- 2026-09-27 R06 系统任务审批台账本地闭环完成：V80 接入六列事实、类型/状态/创建时间筛选、独立模板和 Web 创建/详情/下载；复用原五领域权限决策及 SQL 范围，管理员本人、审核员已提交非草稿、混合身份审核员优先。创建固化角色与领域，执行每页、终态和旧文件访问复核；撤权后停止生成或拒绝下载。全量 `server/target/remaining-r06-system-task-verified.log` package 退出 0，189 类 776 项通过，失败/错误/跳过均 0；JAR 含 V80/新 Mapper 且无本地配置，证据 `remaining-r06-system-task-artifact-check.json`。Web 组件 22 项及 App 权限 19 项通过，生产构建通过；详见 `../specs/2026-09-26-lingdong-system-task-export.md`。当前共八类数据集本地可导出，其余六类及业务导入验收未完成；远程仍为 V77，V78 至 V80 和默认模板待部署，Maven 4 要求保持。下一项沿既定矩阵推进主家长奖励兑换事实报表。

- 2026-09-27 R06 系统任务审批台账开始实施：Web 已接入服务端可见类型、状态/创建时间筛选及角色/领域权限变更后详情清除和迟到响应失效，40 项专项及生产构建通过。后端先红确认新类型选项尚不存在，日志 `server/target/remaining-r06-system-task-red.log`；正在复用 SystemTaskQueryService 的唯一可见范围决策，并固化范围以拒绝撤权后的旧文件下载。当前未完成整合，不计为已实现数据集；范围见 `../specs/2026-09-26-lingdong-system-task-export.md`。

- 2026-09-27 核对R06缓存日志最终证据：后端全量189类768项通过且package退出0，日志 `server/target/remaining-r06-cache-verified.log`；Web专项37项和类型检查/生产构建通过，最终JAR本地配置排除通过。该数据集本地闭环完成，V79及默认模板未远程部署；详见 `../specs/2026-09-26-lingdong-cache-log-export.md`。继续系统任务审批台账，须复用当前五领域可见范围并对旧文件下载复核角色/领域权限，不新增通用审批能力。

- 2026-09-26 持续推进 R06 缓存操作日志：后端和 Web 按固定六列、缓存域/状态/申请时间筛选实施，复用原异步作业，不执行任何缓存操作。定义未执行记录不填写执行人、会话清除明确强制退出；范围见 `../specs/2026-09-26-lingdong-cache-log-export.md`。当前实施中，待红绿测试及整合验证，不计为完成数据集。

- 2026-09-26 R06 接口服务台账本地闭环验收：六列白名单、调用方/状态/责任人/时间筛选、独立模板、动态鉴权与 Web 创建/详情/下载已完成。新增 V78，未改已执行迁移、未写远程库。后端全量 package 退出 0，189 类 763 项通过，失败/错误/跳过均 0，日志 `server/target/remaining-r06-interface-final.log`；最终 JAR 包含 V78/新 Mapper且无本地配置。Web 最终专项 31 项及类型检查/生产构建通过，日志 `.local-verification/r06-interface-web-verified.log`、`r06-interface-web-build-verified.log`；此前全量 45 文件 205 项通过。独立审查发现的附件开关和下载途中撤权保护已修复；仅撤回作业读取权限也清除详情。详见 `../specs/2026-09-22-lingdong-interface-ledger-export.md`。部署仍需 V78 与独立默认模板，R06 剩八类数据集和导入验收，不据此宣称整项完成。下一项按矩阵推进缓存操作日志等明确事实台账。

- 2026-09-22 R06 接口服务台账实施中：按既定统计设计固定六列，调用方/状态/责任人及更新时间筛选，复用异步作业与独立模板模块，权限撤回及停用双重拦截。范围见 `../specs/2026-09-22-lingdong-interface-ledger-export.md`。Web 新增两项先红后绿，类型检查通过；后端红灯确认缺少 INTERFACE_REPORT 模块，日志 `server/target/remaining-r06-interface-red.log`。最终整合验证未完成前不计为已完成数据集；V78 未执行远程迁移。

- 2026-09-22 R09/R10 MySQL 恢复收口：有效备份 `.local-verification/mysql-recovery-f8ce559f/` 已隔离恢复验证，原库升级至 V77。隔离库先复现 V56 语法错误，再验证 22 项迁移；原库连接中断后按实际 V65 续跑剩余 12 项成功。源码范围仅 V56/V58 的 SQL 保留字、两份导入 Mapper 和兼容测试；后端 package 退出 0，189 类 757 项通过，失败/错误/跳过均 0，日志 `server/target/remaining-mysql-compat-verified.log`，最终 JAR 配置排除通过。升级后原数据最终核对结果见部署记录；R09 通用备份运维、R10 真实双端角色及消息验收仍未完成，不据此提高业务完成率。

- 2026-09-20 R07 最后受控载荷收口：敏感导出从既有快照按白名单展示开始/结束时间、事件类型、导出列及姓名脱敏策略版本，不透传未知字段、内部范围快照、文件引用或数据内容；快照缺失/异常明确提示核查。红灯 `server/target/remaining-r07-export-payload-red.log`；最新四类后端专项共 33 项通过，日志 `server/target/remaining-r07-export-payload-green.log`，重新 package 退出 0，日志 `server/target/remaining-r07-export-payload-package.log`。本轮较早全量为 188 类 744 项通过，末次载荷投影增量由上述专项验证，不将累计报告数冒充又一次全量。Web 详情 8 项、类型检查和生产构建通过；R07 当前已落地领域本地闭环完成，真实环境仍归 R10。下一项按 R06 数据集矩阵推进事实台账导出。

- 2026-09-20 R07 系统任务详情及持久化差异：沿用原任务可见范围、领域权限和开关，详情增加五类已落地领域的显式公开字段、原值/目标差异、执行结果与历史缺失提示；列表不加载业务载荷。组织状态复用 REQUEST 审计；V75 为接口服务新申请固化原状态、原授权范围和值，历史申请不伪造回填。红灯 `server/target/remaining-r07-payload-red.log`、`server/target/remaining-r07-interface-snapshot-red.log`、`.local-verification/r07-payload-web-red.log`；专项绿灯 `server/target/remaining-r07-snapshot-green.log`、`.local-verification/r07-payload-web-green.log`。最终 `server/target/remaining-r07-payload-final.log` package 退出 0，188 类 744 项测试，失败/错误/跳过均 0；JAR 包含 V75 且无本地配置。Web 详情专项 8 项、类型检查及生产构建通过，构建日志 `.local-verification/r07-payload-web-build.log`。剩余限制：敏感导出沿用公开摘要，原始权限范围、过滤快照和文件内容不开放；完整受控申请载荷契约仍需与导出领域收口，R07 对应验收项保持未勾选。未修改审计快照、未连接远程执行迁移、未提交 Git。

- 2026-09-20 R06 核对统计设计十四类数据集与当前三个导出类型，形成缺口矩阵；另十一类适配器尚缺，未将模板维护或查询页算作导出完成。R-001 至 R-004 未确认公式保留待定，优先推进已有明确口径的事实台账。

- 2026-09-19 R07 接口服务执行失败状态与审计闭环：新增 V74，仅为既有变更快照补 `PENDING/APPLIED/FAILED` 执行状态和脱敏失败原因。审批成功后，业务应用、执行状态和任务生效在同一事务完成；应用失败则业务事务回滚、任务保留 `APPROVED`、变更记录独立持久化为 `FAILED`，Web 变更记录展示执行状态与失败原因。红灯 `server/target/remaining-r07-interface-execution-red.log`、`.local-verification/r07-interface-execution-web-red.log`，专项绿灯 `server/target/remaining-r07-interface-execution-green.log`、`.local-verification/r07-interface-execution-web-green.log`。全量后端 `server/target/remaining-r07-interface-execution-full.log` 为 187 类 737 项，失败/错误/跳过均 0；package 退出 0，最终 JAR 无 `application-local.yml`。Web `.local-verification/r07-interface-execution-web-full.log` 为 45 文件 196 项全部通过，生产构建通过。R07 尚余通用系统任务完整业务载荷与前后差异展示，未据此宣称整项完成。

- 2026-09-14 R07 全局功能开关管理闭环实施中：复用现有领域审批服务，新增动态权限、持久化前后状态与版本校验、Web 管理入口及系统任务跳转。定位新申请与历史审批禁启边界已完成四项红绿验证，连同原审批/会话撤销共六项专项通过；管理接口及页面尚待验收，不计为 R07 完成。范围与证据见 `../specs/2026-09-14-lingdong-feature-management.md`。

- 2026-09-15 R07 缓存真实操作边界收口：页面明确仅数据字典支持直接刷新/清除，全部已注册缓存和用户会话仅支持审批后清除；会话清除明确为强制退出所有活动设备会话，包括审批人的当前会话。后端新增审核员优先边界，混合系统管理员/审核员不得执行或提交缓存变更。红灯证据 `server/target/remaining-r07-cache-role-red.log` 与 `.local-verification/r07-web-full-final2.log`，专项绿灯 `server/target/remaining-r07-cache-boundary-green.log` 和 `.local-verification/r07-cache-boundary-green.log`；全量后端 187 类 736 项通过且 JAR 排除本地配置，Web 45 文件 195 项及生产构建通过。

- 2026-09-15 R07 接口服务登记/调用边界收口：后端阻止混合系统管理员/审核员发起登记、启停及授权变更；Web 明确登记台账不保存接口地址或凭据，也不提供任意 URL 调用，真实调用仍由已有业务适配器通过外置配置控制超时与异常。红灯 `server/target/remaining-r07-interface-role-red.log`、`.local-verification/r07-interface-boundary-red.log`，专项绿灯 `server/target/remaining-r07-interface-boundary-green.log`、`.local-verification/r07-interface-boundary-green.log`；全量后端 187 类 737 项及 Web 45 文件 195 项通过，生产构建及 JAR 排除检查通过。尚未把登记台账解释为真实通道可用或送达成功。

- 2026-09-13 R04 系统角色 Web 工作台本地验收完成：后端 185 类 725 项全量及产物配置排除通过；Web 改为单 worker 后 44 文件 185 项全量通过、生产构建通过，浏览器分页/详情/领域跳转/动态授权与撤权通过。独立审查问题已修复复核，证据见 `../specs/2026-09-13-lingdong-system-task-workbench.md`。当前仅新增读取闭环，不提供通用审批入口、不宣称 R07 完整业务载荷或系统角色小程序完成；下一步仍按角色矩阵和配置平台依赖推进。

- 2026-09-13 R04 系统角色 Web 任务查询实施中：V72 独立只读权限，管理员本人/审核员已提交范围，按组织、缓存、接口服务、敏感导出四领域开关和权限统一过滤列表、total 与详情；不暴露通用批准接口。新路由红灯 404 后专项通过；修复前端撤权弹窗保留内容和动态授权未同步 App 路由的问题，浏览器先复现跳转失败再通过。全量 `server/target/remaining-r04-system-task-full.log` package 退出 0，185 类 725 项，失败/错误/跳过均 0，最终 JAR 配置排除通过。Web 首次高并发与 Maven 全量资源竞争发生原有测试超时，已停止该轮，改为单 worker 全量验证，最终结果待记录。不将后端通过视为本阶段全部验收。

- 2026-09-13 R04 机构异常待处理闭环完成本地验证：首页按 SUBMITTED 显示服务端总数，原异常页新增状态筛选及分页、失败状态、操作前权限复核和旧响应失效；保留原处理版本与组织范围。修复混合审核员读取异常/候选及业务操作守卫。全量 `server/target/remaining-r04-exception-full.log` package 退出 0，184 类 721 项，失败/错误/跳过均 0；最终 JAR 配置排除通过。小程序类型检查、H5/微信构建、异常闭环及教师/机构待审回归通过，见 `../specs/2026-09-13-lingdong-exception-workbench.md`。未新增迁移、未写远程数据；R04 尚有系统角色等入口缺口。

- 2026-09-13 R04 家长小程序奖励与兑换本地闭环完成：独立孩子选项、奖励分页/新增/编辑/删除及兑换审批/驳回/核销；V71 独立 MINIAPP 权限，保留主家长写、副家长只读，拒绝混合审核员。新接口与混合审核员先红后绿，独立审查后完善上下架影响确认。全量 `server/target/remaining-r04-parent-rewards-full.log` package 退出 0，183 类 720 项，失败/错误/跳过均 0；两类本地配置产物排除检查通过。小程序最终类型检查、H5/微信构建、模型及合成浏览器通过，详见 `../specs/2026-09-13-lingdong-parent-rewards-miniapp.md`。未执行远程迁移或真实消息发送，真实微信与其他矩阵项仍未验收。

- 2026-09-13 R04 家长家庭任务本地闭环完成：独立小程序列表、草稿创建/编辑及发布，复用既有接口和权限，编辑保留全部目标及分类、标签、重复配置；服务端修复混合 SYS_AUDITOR 对家庭任务管理、选项与范围的访问。专项先红后绿，全量 `server/target/remaining-r04-family-full.log` package 退出 0，183 类 716 项，失败/错误/跳过均 0。小程序类型检查、H5/微信构建、家庭任务模型及合成浏览器、家长待审/周报回归通过；最终 JAR 与 target/classes 排除本地配置核验通过。详情及日志见 `../specs/2026-09-12-lingdong-parent-family-tasks.md`。无新增迁移、远程写入或真实消息发送；真实微信验收及 R04 其余矩阵缺口仍未完成。

- V70 整合全量已通过：`server/target/remaining-r05-weekly-full.log` package 退出 0，182 类 711 项测试，失败/错误/跳过均 0；全部报告本轮更新。最终 JAR 与 target/classes 再次通过本地配置排除检查。家长小程序周报只读及消息落地前置闭环完成本地验收，微信授权与真实发送/送达仍未完成；前端类型检查、H5/微信构建和家长待审回归均通过。下一迁移编号 V71，未执行远程迁移。

- 家长周报阶段专项通过：`server/target/remaining-r05-weekly-green.log` 覆盖新 HTTP 绑定契约、H2 权限与对象范围、V70 授权及原复盘回归。小程序类型检查、H5/微信构建通过；`verify-parent-weekly.cjs` 验证列表总数、详情、冷启动消息链接、权限撤回、无权报告与非法链接，截图 `.local-verification/parent-weekly-mini.png` 已目检。HTTP 测试为 standalone 绑定契约，真实对象授权由 H2 服务测试覆盖，不把它记为实际微信登录验收。当前全量日志 `server/target/remaining-r05-weekly-full.log` 仍在运行，待结果后再记录最终状态。

- 2026-09-12 R04 工作台最终复核：教师/机构仅审核权限入口隐藏修订后，`r04-workbenches-final-types.log`、`r04-workbenches-final-h5.log`、`r04-workbenches-final-weixin.log` 均通过；教师/机构和学生浏览器脚本再次通过，覆盖真实总数、失败重试、审核分页、当前打卡 ID、提交前撤权、混合审核员、功能关闭与今日任务失败清空。后端未继续修改，仍以上述 701 项全量为已验收基线。
- R04/R05 家长小程序周报前置闭环开始实施，设计 `../specs/2026-09-12-lingdong-parent-weekly-miniapp.md`：复用历史周报查询，独立 MINIAPP 读取权限与页面，不借用 Web 权限，不触发消息发送。新增接口红灯 `server/target/remaining-r05-weekly-red.log` 为期望 200 实际 404；前端缺页红灯 `.local-verification/r05-weekly-mini-red.log`。当前实现及验证进行中，下一迁移预留 V70，待落盘后核对更新。

- 2026-09-12 R04 后续：学生首页今日任务复用本人日期查询、服务端总数与原任务详情，未新增统计指标；类型检查及 H5/微信构建通过（`r04-workbenches-h5.log`、`r04-workbenches-weixin.log` 位于 `.local-verification`），浏览器从缺功能红灯到通过，日志 `remaining-r04-student-red.log`、`remaining-r04-student-green.log` 同目录。教师/机构首页待审、审核分页及权限恢复核验已实施，浏览器验证仍在进行；此轮只改前端，沿用已通过的后端全量证据。

- 2026-09-12 整合全量完成：`server/target/remaining-r04-r05-full.log` 的 package 退出 0；本次全部更新的 179 份 Surefire XML 汇总 701 项，失败/错误/跳过均 0。`tools/check-release.ps1 -ArtifactOnly` 再次检查最终 JAR 和 target/classes 均无 application-local 配置，源本地文件仍保留。本轮没有远程迁移、真实短信或微信发送。R04 继续教师/机构首页真实待审入口，R05 微信及真实送达尚未完成；不将本次测试数当业务完成率。

- 2026-09-12：R04 家长小程序待审分页、详情、通过/退回、鉴权附件及首页真实总数已实施，保留独立家长会话。模型测试、类型检查、H5/微信构建及合成 H5 验证通过；设计及验证脚本见 `../specs/2026-09-11-lingdong-parent-task-reviews.md`。Web 待审相关 18 项专项、生产构建和工作台浏览器验证通过；修复详情旧响应覆盖、查询失败误报空态。
- R04 并发安全补充：红灯 `server/target/remaining-r04-review-version-red.log` 复现旧窗口请求错误通过新打卡；现在通过/退回都必须携带当前显示的字符串 `expectedCheckInId`，服务端锁后比较，不一致 409，缺失或 JSON 数字 400。Web、家长与教师/机构小程序同步适配；旧客户端须升级，不能静默审核最新打卡。整合专项日志 `server/target/remaining-r04-r05-integrated.log` 已通过，当前全量 package 验证另记结果，不用专项代替全量。
- R05 默认阿里云 ACS3 适配已实现，外置配置说明 `docs/aliyun-parent-sms-configuration.md`。9 项离线测试覆盖签名向量、配置缺失、默认/非支持供应商和 local/test 选择、响应拒绝及大小限制、重定向/认证不重放。真实签名模板、发送与送达、微信授权/周报发送仍未验收；用户授权选默认供应商不等于真实参数已齐。

- 2026-09-10 后续核验：R03 双端及必要撤回入口已完成本地闭环，证据详见 `../specs/2026-09-10-lingdong-rank-clients-design.md`。V69 后全量 package 为 174 套件 689 项通过，日志 `server/target/remaining-r03-final-full.log`；两端类型检查、构建及合成浏览器通过。后续复审发现并修复偏好撤回读取旧快照问题：用户锁前不作数据库查询，偏好使用禁用缓存的 FOR UPDATE 当前读；模拟回归先失败后通过，复审通过。这不替代真实 MySQL 并发验证。专项日志 `server/target/remaining-r03-r04-boundary-red.log`、`server/target/remaining-r03-r05-special.log`；后者同时覆盖审核员混合角色业务审核拒绝和短信适配基础测试。最终整合全量仍待 R04/R05 当前代码稳定后执行。
- R04 已建立六角色端矩阵，Web 工作台复用实际待审核列表和详情操作，恢复窗口时重查当前权限与功能。组件专项、类型检查及合成浏览器审核通过/权限撤销验证通过；小程序家长待审闭环仍在实施，六角色其他缺口不因这一入口而标记全部完成。

- 2026-09-10：R03 新增当前家长学生及有效班级选项、WEB 能力摘要、独立排行页面和路由拦截；复用原偏好与汇总。Web 3 项组件测试通过（含 StrictMode）、typecheck/build 通过，合成浏览器 1280/390px 通过默认关闭、开启、1/1/3、撤回清空、功能关闭直达拒绝与无溢出，脚本 web/scripts/verify-anonymous-ranks.cjs，截图 .local-verification/anonymous-rank-*.png。
- 最新 R02/R03 全量 package：174 套件 687 项通过，失败/错误/跳过均 0，日志 server/target/remaining-r02-r03-full.log；最终 JAR 再次通过 tools/check-release.ps1 -ArtifactOnly。R03 尚未完成：小程序独立权限适配、关系撤销后重新进入的撤回管理仍在实施；设计见 2026-09-10-lingdong-rank-clients-design.md，不以 Web 单端测试宣称闭环。
- R05 用户已授权先选默认供应商，之后外置配置密钥与接口。暂定阿里云短信适配；无参数时明确通道未配置，真实发送和微信模板/授权及送达验收仍待有效参数，不将内部排队当发送成功。

- 2026-09-10：R02 配置隔离实施。POM 增加资源及 JAR 排除，并在 initialize 阶段仅移除 outputDirectory 下历史 application-local.*；移除 application.yml 的 classpath 自动导入。原本地文件仍存在，未读取或复制凭据。README 改为显式外置文件加载并恢复 Maven 4 原要求；新增 docs/deployment/外置配置与发布检查.md、tools/check-release.ps1、ReleaseConfigurationTest。
- 本轮 Maven 3.9.14/JDK17 执行 package 退出 0，172 套件 678 项、失败/错误/跳过均 0；日志 server/target/remaining-r02-package.log。实际残留在构建前存在、构建后消失，最终 Spring Boot JAR 目录项检查通过。4 项配置专项通过，日志 server/target/remaining-r02-config.log，覆盖缺少密钥、缺少外置文件、显式加载和 H2 test 配置；不创建远程基础设施。
- 发布准入脚本在 Maven 3 下按预期失败。Apache 官方下载页当前 Maven 4.0.0-rc-6 为预览版且不适用生产，Maven 4 正式工具链要求保留为阻塞，不将 R02 整项标记完成。继续独立的 R03；已核对现有 WEB 限制及排行口径，尚未修改排行业务。未修改 PROJECT_STATUS.md、未提交 Git、未连接远程数据库或执行远程迁移。

- 2026-09-09：建立计划；开始 R01，固定 9999 发码复现历史测试数据碰撞。
- 2026-09-09：R01 完成。复现日志 server/target/auth-collision-red.log：2 项中 1 项失败，预期 401 实际 200。修复后全量日志 server/target/remaining-r01-full.log，Maven 退出码 0；本轮更新的 172 份 Surefire XML 汇总为 678 项、失败 0、错误 0、跳过 0。这证明碰撞机制可复现，不追溯断言历史那次发码一定是 9999。
- 仅修改登录测试夹具和本计划，保留生产鉴权逻辑及 PROJECT_STATUS.md；未执行远程迁移。git diff --check 通过。
- 下一任务 R02：已确认 classpath 无条件导入本地配置及资源打包风险，尚未完成修复或发布验证，不标记完成。

R01 验证命令（在 server 目录运行，测试使用 test 配置）：

```powershell
$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-17.0.20'
$env:MAVEN_OPTS='-Xms128m -Xmx640m -XX:MaxMetaspaceSize=384m -XX:ReservedCodeCacheSize=96m -XX:CICompilerCount=2'
mvn -q '-DforkCount=0' test
```

- 2026-09-28 用户确认 R-001 至 R-004：免执行不计完成率分母；有效主动行为至少一次计当日活跃，登录和自动积分不计；趋势展示四项原始指标不合成得分；机构统计仅已生成公开实例，按计划日和来源班级，转班不改历史。统计设计第 2.4 节已固化，当前先实施 R-001，其他相关功能尚不计完成。

- 2026-09-28 R-001/R-003 本地闭环完成：完成率分母排除进行中及免执行；复盘 SQL 零任务日期聚合归零，修复周/月生成空值异常。新版本快照与每日趋势同口径，历史快照不覆盖。Web 趋势呈现服务端完成率、积分、待优化数和暂停次数，小程序周报补齐每日待优化。后端专项 36 项、最终 `remaining-r06-metrics-verified.log` package 退出 0，189 类 791 项全量通过且报告全部更新；Web 10 项专项与类型/生产构建、小程序类型与微信构建通过，产物配置排除通过。详见 `../specs/2026-09-28-lingdong-report-metric-rules.md`。R-002 活跃度与 R-004 机构统计仍待实施，不将统计确认视为整个 R06 完成。
- 2026-09-28 继续 R06 附件管理台账：已核对 V54 动态安全元数据授权，不将台账权限变为源附件内容权限；确定七列、创建时间、模块/上传人/分类筛选及自定义运维权限闭环。预期 V83，详见 `../specs/2026-09-28-lingdong-attachment-ledger-export.md`；正在实施，仍按十类已完成数据集计。

- 2026-09-28 R06 附件管理台账本地闭环完成：V83、新七列适配器、创建时间及模块/上传人/分类筛选、Web 创建与下载接入。沿 V54 动态元数据授权且需独立导出权，允许显式授权的自定义运维角色；本人作业读取仍需 EXPORT_JOB_READ，不授源附件内容访问权、不放宽其他数据集。实际 XLSX、205 行上界、姓名脱敏/公式转义、三种文件状态、非法及跨数据集筛选、DENY/撤权/非本人/源内容拒绝与执行补偿验证通过。最终 `server/target/remaining-r06-attachment-verified.log` package 退出 0，189 类 797 项，失败/错误/跳过均 0，189 份报告全部本轮更新；最终 JAR 含 V83、新 Mapper 且无本地配置，证据 `remaining-r06-attachment-artifact-check.json`。Web 专项 54 项、最终全量 45 文件 234 项通过，类型和生产构建通过（`.local-verification/r06-attachment-web-full.log`、`r06-attachment-web-build-final.log`）。当前十一类数据集本地闭环，剩学生任务、机构任务统计、考勤三类及业务导入验收。远程仍 V77，V78 至 V83 和独立默认模板未发布，Maven 4 限制保留。下一项核对学生任务报表多角色范围与 Excel/PDF，禁止用仅 Excel 代替完整要求。

- 2026-09-28 再核对 R02 工具链：Apache 官方发布历史仍标 Maven 4 为 not yet GA，2026-09-24 为 4.0.0-rc-7，当前正式版 3.9.16（https://maven.apache.org/docs/history.html）。已向用户明确询问是否将发布要求改为 3.9.16 正式版并重验，答复前保持原 Maven 4 正式版门槛；不使用预览版假称发布准入。

- 2026-09-29 继续 R06 学生任务报表：已核对主副家长有效绑定、教师逐实例班级关系、机构任务组织范围，沿用现有业务读取权限并新增独立导出权。按六列事实、计划日期和 Excel/PDF 实施，冻结实例集合并复核撤权；设计 `../specs/2026-09-29-lingdong-student-task-report.md`。红灯 `server/target/remaining-r06-student-pdf-red.log` 与 `remaining-r06-student-report-red.log` 确认新写入器和范围服务尚未实现。当前仍为十一类已验收数据集，学生任务报表须待实际文件和整合验证通过再记完成；未变更远程环境及工具链要求。

- 2026-09-29 R06 学生任务报表本地闭环完成：新增 H2/MyBatis 专项 `StudentTaskExportApiIntegrationTest` 5 项，覆盖主副家长有效绑定、教师行级班级限制、机构组织范围与家庭排除、混合审核员拒绝、冻结集合撤权复核、净积分（TASK_REWARD+CORRECTION）与最新打卡审核状态、真实 XLSX 与 PDF 文件内容、205 行冻结上界及 V84 三角色授权。修正 `ImportExportTemplateApplicationServiceTest` 模块清单补入 V84 字典项。最终全量 package 退出 0，192 套件 806 项，失败/错误/跳过均为 0；JAR 含 V84、`StudentTaskExportMapper` 且无本地配置（`server/target/r06-student-report-package-final.log`）。Web 侧既有创建/筛选/PDF 下载/迟到文件专项通过，修复测试类型错误后类型检查与生产构建、全量 45 文件 241 项通过（`.local-verification/r06-student-report-web-build.log`、`r06-student-report-web-tests.log`）。README 与总计划基线已同步 V84。当前十二类数据集本地闭环，剩机构任务统计、考勤台账两类及业务导入验收；远程仍 V77，V78-V84 未发布，Maven 4 限制保留。R06 至此收口，后续按 OpenSpec 变更 finish-remaining-delivery 推进。
