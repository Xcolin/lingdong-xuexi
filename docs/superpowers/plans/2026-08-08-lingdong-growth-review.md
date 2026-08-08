# 灵动学习 V28 成长复盘实施计划

> **供代理执行：** 必须使用 `superpowers:subagent-driven-development` 或 `superpowers:executing-plans` 按任务逐项实施，所有步骤使用复选框跟踪。

**目标：** 完成日、周、月成长复盘不可变快照、自动生成、补算、安全查询和补录闭环。

**架构：** 在现有 `growthpoint` 模块内增加独立复盘边界。逻辑复盘与不可变快照分离，分类和日趋势归属快照；调度只编排，生成服务负责指标，查询和补录始终执行客户端、RBAC 和学生对象范围校验。

**技术栈：** Spring Boot 3、JDK 17、MyBatis XML、Flyway、MySQL 8/H2 测试、React/Ant Design Pro、uni-app。

---

### 任务一：V28 数据库、开关与最小权限

**文件：**
- 新增：`server/src/main/resources/db/migration/V28__add_growth_review.sql`
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityController.java`

- [x] 先在迁移测试断言 5 张复盘表、非自增 `BIGINT id`、周期唯一约束、快照版本唯一约束、外键和查询索引。
- [x] 断言 `DAILY_GROWTH_REVIEW`、`PERIODIC_GROWTH_REPORT` 两个功能开关和四项最小权限种子；学生只获得本人权限，家长只获得孩子权限。
- [x] 运行 `mvn -Dtest=FlywayMigrationTest,StudentAuthenticationControllerTest test`，观察 V28 与能力字段缺失导致失败。
- [x] 编写 V28 SQL，全部基础数据标识使用 19 位雪花常量，禁止自增、触发器和数据库序列。
- [x] 公共能力响应增加 `dailyGrowthReviewEnabled`、`periodicGrowthReportEnabled`，按客户端和开关返回。
- [x] 重新运行局部测试并确认通过。

### 任务二：复盘指标查询与不可变快照生成

**文件：**
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/domain/GrowthReview*.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/application/GrowthReviewGenerationService.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/infrastructure/persistence/GrowthReview*Mapper.java`
- 新增：`server/src/main/resources/mapper/growthpoint/GrowthReview*Mapper.xml`
- 新增测试：`server/src/test/java/com/lingdong/learning/growthpoint/application/GrowthReviewGenerationServiceTest.java`

- [x] 先写失败测试，建立同一学生当日完成、进行中、待优化和免执行任务，以及奖励、纠错、兑换台账和暂停记录。
- [x] 断言完成率只排除进行中；积分只合计任务奖励与纠错；兑换不进入累计获取；暂停和分类数量正确。
- [x] 断言首次生成版本 1，完全相同事实重复生成不新增版本，迟到事实补算生成版本 2 且版本 1 不变。
- [x] 实现周期边界值、事实查询、摘要比较、逻辑复盘、快照、分类和趋势的事务写入。
- [x] 运行 `mvn -Dtest=GrowthReviewGenerationServiceTest test` 并确认通过。

### 任务三：学生与主家长查询、补录

**文件：**
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/application/GrowthReviewQueryService.java`
- 补录职责并入：`server/src/main/java/com/lingdong/learning/growthpoint/application/GrowthReviewQueryService.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthReviewController.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthReview*Response.java`
- 新增测试：`server/src/test/java/com/lingdong/learning/growthpoint/application/GrowthReviewQueryServiceTest.java`

- [x] 先写失败测试覆盖学生本人列表/详情、主家长孩子列表/详情、分页和全部 19 位字符串标识。
- [x] 增加教师、机构管理员、无关家长、跨学生、小程序访问家长接口和 Web 访问本人接口的越权测试。
- [x] 增加学生与主家长三类补录、无限次追加、正文长度、当天/次日允许、超过次日拒绝和功能停用测试。
- [x] 实现 Controller、服务和 MyBatis 查询，历史查询在开关关闭后保持可用，生成与补录必须关闭。
- [x] 运行 `mvn -Dtest=GrowthReviewQueryServiceTest test` 并确认通过。

### 任务四：日、周、月调度和受控补算

**文件：**
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/application/GrowthReviewBatchService.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/infrastructure/scheduling/GrowthReviewSchedulingConfiguration.java`
- 修改：`server/src/main/resources/application.yml`
- 修改：`server/src/test/resources/application-test.yml`

- [x] 以失败测试覆盖上海时区日 21:00、周一 00:00、月初 00:00 的周期计算和开关停用跳过。
- [x] 实现配置化 cron、批量上限和逐学生独立事务；测试配置关闭真实调度。
- [x] 实现受控的前一自然日回补，不提供任意超大范围同步入口。
- [x] 重复事实由生成服务保证幂等，单个学生失败不影响其他学生。

### 任务五：Web 与 uni-app 复盘查询

**文件：**
- 新增：`web/src/features/growth-reviews/`
- 修改：`web/src/app/App.tsx`
- 修改：`web/src/api/capability.ts`
- 新增：`miniapp/src/api/growth-review.ts`
- 新增：`miniapp/src/pages/growth-reviews/growth-reviews.vue`
- 修改：`miniapp/src/pages.json`
- 修改：`miniapp/src/pages/student-home/student-home.vue`

- [x] Web 先写角色/开关路由、孩子切换、周期筛选、趋势、分类和补录测试。
- [x] 实现适合复杂统计的 Web 页面，宽表和趋势只在内容区响应式缩放或滚动。
- [x] 小程序按前端职责边界实现学生本人日复盘摘要、详情和三类补录，不承载复杂周/月统计或管理操作。
- [x] 运行 Web 全量测试与构建、小程序类型检查及 H5/微信双目标构建。

### 任务六：文档、回归和视觉验收

**文件：**
- 修改：`docs/design/00-07`、`09`、`10`、`12`
- 修改：`docs/superpowers/plans/2026-08-08-lingdong-learning-master-development.md`

- [x] 同步表结构、API、RBAC、指标、时区、补算、开关和未实现边界，后续迁移从 V29 开始。
- [x] 执行后端 `mvn test`、Web 全量测试/构建、小程序类型检查/H5/微信构建。
- [x] 使用本地虚拟接口核验 Web `1440×900`/`390×844` 与小程序 `390×844`，检查趋势、补录和无页面级横向溢出。
- [x] 执行 `git diff --check`、敏感信息扫描、V28 自增关键字和 19 位主键检查；不连接远程或共享环境。
