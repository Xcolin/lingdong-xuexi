# 灵动学习 V30 每日固定任务实施计划

> **执行要求：** 按任务逐项实施，使用 TDD 先观察失败再写最小实现；每完成一项即更新复选框。当前工作区包含连续历史改动，不创建分支、不提交、不回滚无关文件。

**目标：** 为三类来源任务增加每日固定计划、跨日学生实例生成、主动停止和 Web 管理能力，并保持同一任务 ID 供 V29 连续衰减使用。

**架构：** `learn_task` 保存草稿周期配置，`learn_task_recurrence` 保存发布后的调度状态和游标；发布事务生成首日实例及计划，后台按计划逐日补生成学生实例，每个计划独立事务。学生小程序继续消费原任务实例，不增加管理入口。

**技术栈：** Spring Boot 3、JDK 17、MyBatis XML、Flyway V30、H2 MySQL 兼容测试、React 18、Ant Design Pro、uni-app。

---

### 任务一：V30 数据库与迁移基线

**文件：**
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`
- 新建：`server/src/main/resources/db/migration/V30__add_recurring_task.sql`

- [x] 在迁移测试中先断言 `learn_task_recurrence`、任务周期配置列、状态检查、任务唯一计划、游标索引和 55 张非自增雪花主键表。
- [x] 运行 `mvn -Dtest=FlywayMigrationTest test`，确认因 V30 尚不存在而失败。
- [x] 编写 V30：扩展 `learn_task`，创建周期计划表，所有主键显式 `BIGINT` 且不使用自增。
- [x] 再次运行迁移测试，确认 V1-V30 连续迁移通过，并执行 V30 自增与 19 位基础标识扫描。

### 任务二：草稿周期配置契约

**文件：**
- 修改：`server/src/test/java/com/lingdong/learning/learningtask/web/LearningTaskControllerTest.java`
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/domain/LearningTask.java`
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/application/LearningTaskDraftInput.java`
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/application/ValidatedLearningTaskDraft.java`
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/application/LearningTaskValidator.java`
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskRequest.java`
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/infrastructure/persistence/LearningTaskMapper.java`
- 修改：`server/src/main/resources/mapper/learningtask/LearningTaskMapper.xml`

- [x] 先增加 API 失败测试：固定任务字段可保存和查询；结束日早于开始日、非固定任务携带结束日被拒绝。
- [x] 运行目标 Controller 测试，确认响应缺字段或校验缺失导致预期失败。
- [x] 扩展输入、校验、领域记录、Mapper 和响应，默认 `recurrenceEnabled=false`，只允许有效结束日期。
- [x] 运行目标测试和已有任务创建/更新测试，确认通过。

### 任务三：发布时原子创建周期计划

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/domain/LearningTaskRecurrence.java`
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/domain/LearningTaskRecurrenceStatus.java`
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/infrastructure/persistence/LearningTaskRecurrenceMapper.java`
- 新建：`server/src/main/resources/mapper/learningtask/LearningTaskRecurrenceMapper.xml`
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/application/LearningTaskPublishService.java`
- 修改：`server/src/test/java/com/lingdong/learning/learningtask/web/LearningTaskControllerTest.java`

- [x] 先增加发布失败测试：固定任务首日实例和 `ACTIVE` 周期计划同时生成，游标为首日加一天；普通任务不创建计划。
- [x] 运行目标测试，确认周期计划查询为空而失败。
- [x] 实现周期领域对象与 MyBatis XML，并接入既有发布事务。
- [x] 增加重复发布与计划插入失败的原子性断言，运行测试确认无重复实例或半成品计划。

### 任务四：每日生成器与事务隔离

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/application/RecurringTaskGenerationResult.java`
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/application/RecurringTaskGenerationService.java`
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/application/RecurringTaskGenerationTransactionService.java`
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/application/RecurringTaskGenerationBatchService.java`
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/infrastructure/scheduling/RecurringTaskSchedulingConfiguration.java`
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/infrastructure/persistence/LearningTaskAssignmentMapper.java`
- 修改：`server/src/main/resources/mapper/learningtask/LearningTaskAssignmentMapper.xml`
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/infrastructure/persistence/LearningTaskRecurrenceMapper.java`
- 修改：`server/src/main/resources/mapper/learningtask/LearningTaskRecurrenceMapper.xml`
- 修改：`server/src/main/resources/application.yml`
- 修改：`server/src/test/resources/application-test.yml`
- 新建：`server/src/test/java/com/lingdong/learning/learningtask/application/RecurringTaskGenerationServiceTest.java`
- 新建：`server/src/test/java/com/lingdong/learning/learningtask/application/RecurringTaskGenerationBatchServiceTest.java`

- [x] 先写失败测试覆盖单日生成、跨日补生成、重复执行、结束日完成、目标为空、开关停用和实例已存在。
- [x] 运行两个目标测试，确认生成服务或持久化方法缺失导致失败。
- [x] 实现计划行锁、日期循环、目标动态展开、缺失实例插入、游标推进和结束状态；截止时间固定为计划日 23:59:59。
- [x] 先写批处理失败测试，覆盖按计划游标分页和单计划异常不阻断其余计划。
- [x] 实现逐计划 `REQUIRES_NEW` 事务和每日 00:05 上海时区调度，测试配置关闭自动调度。
- [x] 运行生成器、批处理、学生任务查询和 V29 衰减集成测试，确认同任务跨日实例可形成连续链。

### 任务五：停止周期计划 API

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/application/StopRecurringTaskResult.java`
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/application/RecurringTaskManagementService.java`
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/web/StopRecurringTaskResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskController.java`
- 修改：`server/src/test/java/com/lingdong/learning/learningtask/web/LearningTaskControllerTest.java`

- [x] 先写失败测试覆盖合法停止、越权不可见、普通任务、重复停止和功能开关停用。
- [x] 运行目标测试，确认停止端点不存在或状态未变化。
- [x] 实现任务范围复核、计划行锁、`ACTIVE -> STOPPED` 条件更新和停止审计；响应中所有 19 位标识按字符串输出。
- [x] 运行 Controller、任务发布、执行、审核和积分回归测试。

### 任务六：Web 固定任务管理

**文件：**
- 修改：`web/src/features/learning-tasks/types.ts`
- 修改：`web/src/features/learning-tasks/api.ts`
- 修改：`web/src/features/learning-tasks/LearningTaskEditorDrawer.tsx`
- 修改：`web/src/features/learning-tasks/LearningTaskManagementPage.tsx`
- 修改：`web/src/features/learning-tasks/LearningTaskManagementPage.test.tsx`

- [x] 先写失败组件测试：启用每日固定任务后提交周期字段，关闭时清空结束日；活动计划显示停止操作并二次确认。
- [x] 运行目标 Vitest，确认字段或停止操作不存在而失败。
- [x] 使用 Ant Design `Switch` 和日期输入实现草稿配置，使用现有操作菜单和确认弹窗实现停止，不新增嵌套卡片。
- [x] 运行目标测试，并复核既有窄屏单列网格规则可容纳新增中文字段且不遮挡。

### 任务七：回归、文档与进度同步

**文件：**
- 修改：`docs/design/00-设计文档体系与需求追溯-V1.0.md`
- 修改：`docs/design/01-功能详细设计-FSD-V1.0.md`
- 修改：`docs/design/02-Web与小程序交互说明-V1.0.md`
- 修改：`docs/design/03-系统架构设计-HLD-V1.0.md`
- 修改：`docs/design/04-数据库设计-V1.0.md`
- 修改：`docs/design/05-Flyway迁移规范-V1.0.md`
- 修改：`docs/design/06-API接口设计-V1.0.md`
- 修改：`docs/design/07-权限与安全设计-V1.0.md`
- 修改：`docs/design/10-测试方案与验收用例-V1.0.md`
- 修改：`docs/design/12-当前实现一致性核对-V1.0.md`
- 修改：`docs/superpowers/plans/2026-08-08-lingdong-learning-master-development.md`
- 修改：`README.md`

- [x] 执行后端 `mvn test`，记录测试总数、失败数和 V1-V30 迁移结果。
- [x] 执行 Web 全量 Vitest 与生产构建，小程序类型检查、H5 和微信小程序构建。
- [x] 执行 `git diff --check`、V30 自增扫描、敏感信息扫描和中文文档版本一致性扫描。
- [x] 同步 V30 已实现范围、未实现的复制/顺延/移动端管理边界和按 1000 分权重计算的项目进度。
