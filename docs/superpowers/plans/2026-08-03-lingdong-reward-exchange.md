# 灵动学习 V27 家庭奖励与积分兑换实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现学生独立家庭奖励库、兑换申请、主家长审批扣分、驳回、核销和超时/到期处理闭环。

**Architecture:** Flyway V27 新增奖励与兑换事实表，并以 `source_exchange_id` 将兑换扣分接入既有不可变积分台账。后端在 `growthpoint` 模块内新增奖励和兑换边界，Web 只承担主家长管理操作，uni-app 只承担当前学生浏览、申请和查询。

**Tech Stack:** Spring Boot 3、JDK 17、MyBatis XML、Flyway、MySQL 8/H2 测试模式、React/Ant Design Pro、uni-app/Vue 3。

---

### 任务一：V27 数据库基线与访问控制

**文件：**
- 新增：`server/src/main/resources/db/migration/V27__add_reward_exchange.sql`
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityController.java`
- 修改：`server/src/test/java/com/lingdong/learning/auth/web/StudentAuthenticationControllerTest.java`

- [x] 先在迁移测试中断言 V27、`growth_reward`、`growth_reward_exchange`、台账 `source_exchange_id`、约束、`REWARD_EXCHANGE` 开关、三项权限和角色授权。
- [x] 运行 `mvn -Dtest=FlywayMigrationTest,StudentAuthenticationControllerTest test`，确认因 V27 和新能力字段不存在而失败。
- [x] 编写 V27：所有主键使用显式 19 位雪花 `BIGINT`；奖励和兑换使用检查、外键、查询索引；兑换快照不可依赖奖励后续值；兑换台账允许 `amount=0`、`available_delta<0` 并以兑换标识唯一。
- [x] 公共能力响应增加 `rewardExchangeEnabled`，Web 与小程序均按开关返回；未启用时为否。
- [x] 重新运行局部测试，确认迁移与能力摘要通过。

### 任务二：奖励库后端

**文件：**
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/domain/GrowthReward.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/domain/GrowthRewardStatus.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/application/GrowthRewardService.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/infrastructure/persistence/GrowthRewardMapper.java`
- 新增：`server/src/main/resources/mapper/growthpoint/GrowthRewardMapper.xml`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthRewardController.java` 及请求/响应模型
- 新增：`server/src/test/java/com/lingdong/learning/growthpoint/web/RewardExchangeControllerTest.java`

- [x] 先写集成测试：活动主家长新增、编辑、上下架和逻辑删除学生奖励；名称 1-30 字、描述最多 200 字、正积分和未来有效期；跨关系越权；学生仅见本人在线未到期奖励。
- [x] 运行 `mvn -Dtest=RewardExchangeControllerTest test`，确认因接口不存在返回失败。
- [x] 实现奖励领域模型、MyBatis XML、对象范围校验、版本条件更新、控制器和字符串标识响应。
- [x] 保证当前活动主家长可接管既有奖励，创建人只保留审计意义；删除不物理移除历史。
- [x] 重新运行测试，确认奖励库主流程用例通过。
- [x] 补充教师越权、功能停用和直达接口拦截测试。

### 任务三：兑换申请、审批扣分和核销

**文件：**
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/domain/GrowthRewardExchange.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/domain/GrowthRewardExchangeStatus.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/application/GrowthRewardExchangeService.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/infrastructure/persistence/GrowthRewardExchangeMapper.java`
- 新增：`server/src/main/resources/mapper/growthpoint/GrowthRewardExchangeMapper.xml`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthRewardExchangeController.java` 及请求/响应模型
- 修改：`GrowthPointAccountMapper.java/xml`、`GrowthPointLedger.java`、`GrowthPointLedgerMapper.java/xml`、积分查询行/响应模型
- 修改测试：`RewardExchangeControllerTest.java`

- [x] 先写失败测试：学生按会话申请、积分不足和重复活动申请失败；申请不扣积分。
- [x] 再写失败测试：当前主家长同意后只扣可用积分、累计积分不变，生成唯一 `REDEMPTION` 台账并进入待核销；余额在审批时不足、重复审批失败且无部分数据。
- [x] 再写失败测试：驳回原因必填、驳回不扣积分、待核销可由当前主家长核销、重复核销失败；学生和家长分别只能查询本人/孩子记录。
- [x] 实现兑换模型、快照写入、行锁与版本更新、可用积分扣减、不可变兑换台账及审批/驳回/核销控制器。
- [x] 每轮测试先观察预期失败，再实现最小代码并重新运行 `mvn -Dtest=RewardExchangeControllerTest test`。
- [x] 补充快照不可变、下架/过期、跨学生、功能停用和真实并发竞争测试。
- [x] 将学生和家长兑换记录查询改为受控分页，限制单次返回数量。

### 任务四：72 小时与奖励到期自动处理

**文件：**
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/application/GrowthRewardExchangeCleanupService.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/infrastructure/scheduling/GrowthRewardExchangeSchedulingConfiguration.java`
- 修改：`server/src/main/resources/application.yml`
- 修改：`GrowthRewardMapper.java/xml`、`GrowthRewardExchangeMapper.java/xml`
- 修改测试：`RewardExchangeControllerTest.java`

- [x] 先写失败测试：72 小时待审批进入 `AUTO_REJECTED`；奖励到期自动下架并使待审批进入 `EXPIRED`；两类处理均不扣积分；待核销不受影响。
- [x] 显式启用 Spring 调度，使用配置化执行间隔和批量上限；实现批量处理服务和定时入口，使用状态条件更新支持多实例竞争。
- [x] 审批接口同步检查截止时间与奖励有效期，定时任务延迟时仍拒绝过期审批。
- [x] 运行局部测试并确认重复执行批处理保持幂等。

### 任务五：Web 主家长奖励管理

**文件：**
- 新增：`web/src/features/rewards/types.ts`
- 新增：`web/src/features/rewards/api.ts`
- 新增：`web/src/features/rewards/RewardManagementPage.tsx`
- 新增：`web/src/features/rewards/RewardManagementPage.test.tsx`
- 修改：`web/src/api/capability.ts`、`web/src/app/App.tsx`、`web/src/app/App.test.tsx`、`web/src/styles/index.css`

- [x] 先写失败测试：家长且开关启用才显示路由；孩子切换；奖励新增/编辑/上下架/删除；待审批同意/驳回；待核销核销；成功后刷新。
- [x] 运行 `npx vitest run src/features/rewards/RewardManagementPage.test.tsx src/app/App.test.tsx`，确认组件/能力缺失导致失败。
- [x] 实现“奖励库/兑换处理”页签、表单校验、状态筛选和确认反馈，使用 Ant Design 上下文消息 API 与 Lucide 图标。
- [x] 窄屏表格只在内容区滚动，按钮和最长文案不得溢出。
- [x] 运行 Web 局部测试和 `npm run build`。

### 任务六：uni-app 学生奖励兑换

**文件：**
- 新增：`miniapp/src/api/reward.ts`
- 新增：`miniapp/src/pages/rewards/rewards.vue`
- 修改：`miniapp/src/api/capability.ts`、`miniapp/src/pages.json`、`miniapp/src/pages/student-home/student-home.vue`

- [x] 先以类型契约声明奖励、账户摘要、申请和兑换历史响应，确保全部标识为字符串。
- [x] 实现首页入口和直达页开关/会话复核；“可兑换奖励/我的兑换”页签；积分不足禁用；提交二次确认；成功后刷新。
- [x] 学生页面不提供奖励配置、审批、驳回或核销入口。
- [x] 运行 `npm run type-check`、`npm run build:h5`、`npm run build:mp-weixin`。

### 任务七：中文文档与最终验证

**文件：**
- 修改：`docs/design/00`、`01`、`02`、`03`、`04`、`05`、`06`、`07`、`09`、`10`、`12` 对应中文设计文档

- [x] 将 V27 实现事实、状态机、表结构、接口、RBAC、积分口径、测试证据和未实现边界同步到中文文档；后续迁移从 V28 开始。
- [x] 执行后端 `mvn test`、Web 全量测试和生产构建、小程序类型检查及双目标构建。
- [x] 使用本地虚拟接口核验 Web `1440×900`/`390×844` 与小程序 `390×844` 的主流程、空态、确认弹窗、状态反馈和内部滚动。
- [x] 执行 `git diff --check`、敏感信息扫描、迁移自增关键字扫描和 V27 19 位主键检查；不连接远程 MySQL、Redis、微信服务或任何共享/预生产/生产数据库。
