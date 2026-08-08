# 灵动学习 V24 任务审核通过与积分原子入账实施计划

> **执行要求：** 按任务逐项实施并使用测试先行；每个生产行为必须先由失败测试证明缺口。

**目标：** 在单一事务中完成任务审核通过、积分账户更新、奖励台账写入和任务完成，并提供 Web 审核入口。

**架构：** 沿用 `learningtask` 审核事务边界，通过独立 `growthpoint` 持久化模块维护账户和不可变台账。Web 仅提交任务实例标识，奖励值和对象范围全部由后端锁定数据计算。

**技术栈：** Spring Boot 3、JDK 17、MyBatis XML、Flyway、MySQL 8/H2 兼容测试、React、Ant Design Pro、Vitest。

---

### 任务 1：迁移红灯与 V24 数据结构

**文件：**
- 修改：`server/src/test/java/com/lingdong/learning/database/FlywayMigrationTest.java`
- 新增：`server/src/main/resources/db/migration/V24__create_growth_point_account_and_ledger.sql`

- [ ] 在迁移测试中断言 V24、44 张表、账户与台账字段、雪花主键、账户回填、任务奖励唯一约束、`APPROVED` 打卡约束和 `REVIEW_APPROVED` 事件约束。
- [ ] 运行 `mvn -Dtest=FlywayMigrationTest test`，确认因 V24 缺失而失败。
- [ ] 新增 V24 迁移并再次运行，确认迁移测试通过。

### 任务 2：审核通过后端红绿循环

**文件：**
- 修改：`server/src/test/java/com/lingdong/learning/learningtask/web/LearningTaskControllerTest.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/domain/GrowthPointAccount.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/domain/GrowthPointLedger.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/domain/GrowthPointChangeType.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/infrastructure/persistence/GrowthPointAccountMapper.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/infrastructure/persistence/GrowthPointLedgerMapper.java`
- 新增：`server/src/main/resources/mapper/growthpoint/GrowthPointAccountMapper.xml`
- 新增：`server/src/main/resources/mapper/growthpoint/GrowthPointLedgerMapper.xml`
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/application/TaskReviewService.java`
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/infrastructure/persistence/TaskReviewStateRow.java`
- 修改：`server/src/main/resources/mapper/learningtask/TaskReviewMapper.xml`
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/infrastructure/persistence/TaskCheckInMapper.java`
- 修改：`server/src/main/resources/mapper/learningtask/TaskCheckInMapper.xml`
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/domain/TaskAssignmentEventType.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/application/ApproveTaskReviewResult.java`

- [ ] 增加端到端测试：当前审核人通过后任务为 `COMPLETED`、打卡为 `APPROVED`、账户累计与可用积分增加基础积分、唯一台账和审核事件存在。
- [ ] 增加重复通过与非审核人用例，断言不产生第二次积分。
- [ ] 运行 `mvn -Dtest=LearningTaskControllerTest test`，确认审核通过端点缺失导致失败。
- [ ] 实现最小原子事务和持久化代码，运行局部测试直到通过。

### 任务 3：控制器与响应契约

**文件：**
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/web/TaskReviewController.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/web/ApproveTaskReviewResponse.java`

- [ ] 在现有失败端到端测试约束下新增 `POST /api/v1/task-reviews/{assignmentId}/approve`。
- [ ] 将所有长整型标识序列化为字符串，返回奖励和账户新余额。
- [ ] 运行学习任务控制器测试并确认通过。

### 任务 4：新建学生账户一致性

**文件：**
- 修改：`server/src/main/java/com/lingdong/learning/student/application/StudentApplicationService.java`
- 修改：`server/src/test/java/com/lingdong/learning/student/web/StudentManagementControllerTest.java`

- [ ] 先增加失败断言：新建学生事务完成后存在同标识零余额积分账户。
- [ ] 在学生档案写入后建立一对一账户，检查单行写入结果。
- [ ] 运行学生管理控制器测试并确认通过。

### 任务 5：Web 审核通过红绿循环

**文件：**
- 修改：`web/src/features/learning-tasks/LearningTaskManagementPage.test.tsx`
- 修改：`web/src/features/learning-tasks/reviewApi.ts`
- 修改：`web/src/features/learning-tasks/types.ts`
- 修改：`web/src/features/learning-tasks/TaskReviewDrawer.tsx`

- [ ] 将既有“只提供驳回和转交”测试改为审核通过闭环，先确认 `taskReviewApi.approve` 缺失导致失败。
- [ ] 增加基础积分展示、通过按钮和二次确认，成功后刷新待办。
- [ ] 运行 Web 测试和生产构建。

### 任务 6：中文文档与全量准出

**文件：**
- 修改：`docs/design/01-功能详细设计-FSD-V1.0.md`
- 修改：`docs/design/03-系统架构设计-HLD-V1.0.md`
- 修改：`docs/design/04-数据库设计-V1.0.md`
- 修改：`docs/design/05-Flyway迁移规范-V1.0.md`
- 修改：`docs/design/06-API接口设计-V1.0.md`
- 修改：`docs/design/07-权限与安全设计-V1.0.md`
- 修改：`docs/design/10-测试方案与验收用例-V1.0.md`
- 修改：`docs/design/12-当前实现一致性核对-V1.0.md`

- [ ] 将当前实现边界更新到 V24，删除“审核通过未实现”的过时表述。
- [ ] 执行后端全量测试、Web 全量测试与构建、uni-app 类型检查和双构建。
- [ ] 扫描真实密钥、非雪花主键、文档占位符并执行 `git diff --check`。
- [ ] 使用本地虚拟数据检查 Web 审核通过抽屉的桌面和窄屏布局。
