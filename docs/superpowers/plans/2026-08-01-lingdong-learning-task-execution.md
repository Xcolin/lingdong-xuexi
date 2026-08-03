# 灵动学习 V23 任务执行与审核基础实施计划

> **执行要求：** 按测试驱动逐项实施；每个阶段先看到目标测试失败，再编写最小实现并回归。全程不得连接远程数据库、Redis 或微信服务。

**目标：** 在 V22 任务发布和学生只读查询基础上，实现学生执行、文字打卡、审核驳回、审核转交和免执行，并保持完整审计历史。

**架构：** 任务实例保存当前快照，暂停、打卡、状态事件和转交使用独立表。写操作通过任务实例行锁和期望状态更新控制并发，Web 与 uni-app 使用独立 Controller 和权限。

**技术栈：** Spring Boot 3、JDK 17、MyBatis XML、Flyway、MySQL 8/H2 MySQL 模式、React/Ant Design Pro、uni-app/Vue 3。

---

## 任务一：V23 迁移与迁移测试

**文件：**

- 新增：`server/src/main/resources/db/migration/V23__create_task_execution_foundation.sql`
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

- [ ] 先在迁移测试中断言 V23、4 张新表、完整状态约束、索引、权限和角色授权。
- [ ] 运行 `mvn -Dtest=FlywayMigrationTest test`，确认因 V23 不存在而失败。
- [ ] 新增任务事件、暂停、打卡、转交表，扩展任务实例状态并种入 3 项权限。
- [ ] 确认 4 张新表主键均为非自增 19 位雪花 `BIGINT`。
- [ ] 重新运行迁移测试并执行迁移文件 `git diff --check`。

## 任务二：任务执行领域模型和持久化

**文件：**

- 新增：`learningtask/domain/TaskAssignmentStatus.java`
- 新增：`learningtask/domain/TaskAssignmentEvent.java`
- 新增：`learningtask/domain/TaskPause.java`
- 新增：`learningtask/domain/TaskCheckIn.java`
- 新增：`learningtask/domain/ReviewerTransfer.java`
- 新增对应 Mapper 接口和 `mapper/learningtask/*.xml`
- 修改：`LearningTaskAssignmentMapper.java`、`LearningTaskAssignmentMapper.xml`

- [ ] 编写 Mapper 集成测试，覆盖行锁读取、状态条件更新、暂停关闭、打卡序号和转交记录。
- [ ] 运行测试并确认缺少 Mapper 或 SQL 时失败。
- [ ] 实现批量映射和条件更新，所有 SQL 使用参数绑定。
- [ ] 验证同一实例并发状态更新只有一个成功。

## 任务三：学生认领、暂停、恢复和放弃

**文件：**

- 新增：`StudentTaskExecutionService.java`
- 新增：`PauseTaskCommand.java`
- 新增：`AbandonTaskCommand.java`
- 修改：`StudentTaskAssignmentController.java`
- 新增请求 DTO 与测试 `StudentTaskExecutionControllerTest.java`

- [ ] 编写失败测试，覆盖本人认领、跨学生 404、重复认领 409、暂停类型、2 小时到期、恢复、放弃和功能关闭。
- [ ] 实现只从 `AuthenticatedUser` 解析学生档案的执行服务。
- [ ] 每次变化写入事件，暂停不修改基础状态，放弃关闭活动暂停并转待优化。
- [ ] 运行学生执行测试集并保持 V22 查询测试通过。

## 任务四：学生文字打卡

**文件：**

- 新增：`SubmitTaskCheckInCommand.java`
- 扩展：`StudentTaskExecutionService.java`
- 修改：学生任务响应，增加有效状态、活动暂停和最近打卡摘要
- 新增打卡请求 DTO 与测试

- [ ] 编写失败测试，覆盖内容必填、1000 字限制、暂停中禁止提交、无有效审核人拒绝、提交序号递增和并发重复提交。
- [ ] 实现打卡与任务转待审核的同一事务。
- [ ] 驳回后再次提交必须新增记录，不覆盖历史记录。
- [ ] 响应中的 19 位标识全部序列化为字符串。

## 任务五：审核待办、驳回和转交

**文件：**

- 新增：`TaskReviewService.java`
- 新增：`TaskReviewerOptionService.java`
- 新增：`TaskReviewController.java`
- 新增审核分页、详情、候选、驳回和转交 DTO
- 新增测试：`TaskReviewControllerTest.java`

- [ ] 编写失败测试，覆盖只读当前审核人待办、非审核人 404、驳回意见必填、驳回回到进行中和旧打卡标记为已驳回。
- [ ] 编写家庭、教师和机构来源候选人裁剪测试。
- [ ] 实现转交事务，同时更新当前审核人、写入转交记录和状态事件。
- [ ] 明确验证不存在审核通过端点。

## 任务六：免执行

**文件：**

- 新增：`ManagedTaskAssignmentService.java`
- 新增：`ManagedTaskAssignmentController.java`
- 新增请求 DTO 与测试

- [ ] 编写失败测试，覆盖家庭主家长、创建教师、机构管理员数据范围和越权 404。
- [ ] 只允许待认领、进行中或暂停中的任务设置免执行。
- [ ] 设置免执行时关闭活动暂停，写入状态事件，不生成打卡或积分记录。

## 任务七：uni-app 学生执行页面

**文件：**

- 修改：`miniapp/src/api/learning-task.ts`
- 修改：`miniapp/src/pages/task-list/task-list.vue`
- 修改：`miniapp/src/pages/task-detail/task-detail.vue`

- [ ] 扩展状态类型和学生操作 API。
- [ ] 列表显示待认领、进行中、暂停、待审核、待优化和免执行中性文案。
- [ ] 详情按状态显示认领、暂停、继续、放弃和文字打卡控件。
- [ ] 二次确认放弃，提交成功后清空打卡文本。
- [ ] 功能关闭或会话失效时不发送执行请求。

## 任务八：Web 审核待办

**文件：**

- 新增：`web/src/features/learning-tasks/reviewApi.ts`
- 新增：`TaskReviewQueue.tsx`
- 新增：`TaskReviewDrawer.tsx`
- 修改：`LearningTaskManagementPage.tsx`
- 修改：`types.ts`、页面测试和样式

- [ ] 在学习任务页增加“任务管理/审核待办”分段视图。
- [ ] 实现待办分页、详情、驳回确认和审核人转交。
- [ ] 页面不展示审核通过按钮，并为失败状态保留可重试反馈。
- [ ] 编写组件测试验证当前审核人操作和功能关闭边界。

## 任务九：中文设计文档同步

**文件：**

- 修改：FSD、HLD、数据库、Flyway、API、安全、测试和当前实现一致性核对文档。

- [ ] 记录 V23 已实现状态、表、接口、权限和双端行为。
- [ ] 明确审核通过、积分、附件上传、定时逾期和自动转交仍为后续范围。
- [ ] 扫描 `TODO|TBD|待补充|示例占位`，不得留下未说明占位符。

## 任务十：全量验证

- [ ] 后端运行 `mvn test` 并核对测试总数、失败、错误和跳过数。
- [ ] Web 运行 Vitest 和生产构建。
- [ ] uni-app 运行类型检查、H5 构建和微信小程序构建。
- [ ] 扫描真实数据库、Redis 和微信凭据，必须 0 命中。
- [ ] 扫描迁移中的自增、identity 和序列，必须 0 命中。
- [ ] 使用本地虚拟数据检查 Web 审核待办和 `390×844` 小程序执行页面。
- [ ] 停止临时服务并执行 `git diff --check`。
