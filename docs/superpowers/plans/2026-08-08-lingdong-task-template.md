# 灵动学习 V34 任务模板实施计划

> **执行要求：** 使用 `superpowers:executing-plans` 按任务逐项执行；坚持测试驱动，所有文档、注释、测试名称和界面文案使用中文。当前工作区包含连续开发改动，不执行提交、暂存或工作树切换。

**目标：** 实现系统预置常用任务模板和 Web 主家长个人模板的选用、保存、编辑、逻辑删除及排序闭环。

**架构：** V34 新增模板主表与标签表，系统模板由 Flyway 只读预置，个人模板由应用服务按当前家长隔离。模板 API 只返回可复用配置，Web 选用后回填现有任务编辑器，最终任务仍走 V22 创建与发布校验。

**技术栈：** Spring Boot 3、JDK 17、MyBatis XML、Flyway、H2/MySQL 兼容 SQL、React 18、Ant Design Pro、Vitest、uni-app。

---

## 任务一：V34 迁移与失败测试

**文件：**
- 新建：`server/src/main/resources/db/migration/V34__add_learning_task_template.sql`
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`
- 新建：`server/src/test/java/com/lingdong/learning/learningtask/application/LearningTaskTemplateServiceTest.java`

- [x] 先扩展 Flyway 测试，断言 V1-V34、60 张显式非自增 `BIGINT id` 主键、模板表约束、两条系统模板、双权限、家长授权和 9 个新增 19 位基础数据标识。
- [x] 运行 `mvn -Dtest=FlywayMigrationTest test`，确认因 V34 缺失而失败。
- [x] 新增 V34：`learn_task_template` 保存范围、拥有者、唯一作用域键、活动名称键、可复用字段、排序、状态和版本；`learn_task_template_tag` 保存标签并限制模板内唯一。
- [x] 初始化 `LEARNING_TASK_TEMPLATE`、`LEARNING_TASK_TEMPLATE_READ`、`LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL`、家长授权，以及“每日阅读30分钟”“口算练习”和 `DAILY` 标签。
- [x] 运行 Flyway 测试，迁移 34 个版本、60 张表且断言通过。
- [x] 写应用服务测试，覆盖系统模板只读、非 Web 家长拒绝、字段归一化、并发重名、乐观版本和排序集合不完整；上限、字典失效及跨家长隔离由应用校验和真实持久化约束共同覆盖。

## 任务二：后端模板闭环

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/domain/LearningTaskTemplate.java`
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/infrastructure/persistence/LearningTaskTemplateMapper.java`
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/infrastructure/persistence/LearningTaskTemplateRow.java`
- 新建：`server/src/main/resources/mapper/learningtask/LearningTaskTemplateMapper.xml`
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/application/LearningTaskTemplateInput.java`
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/application/LearningTaskTemplateView.java`
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/application/LearningTaskTemplateService.java`
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskTemplateController.java`
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskTemplateRequest.java`
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskTemplateResponse.java`
- 新建：`server/src/main/java/com/lingdong/learning/learningtask/web/PersonalTemplateOrderRequest.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityController.java`
- 修改：`server/src/test/java/com/lingdong/learning/auth/web/StudentAuthenticationControllerTest.java`

- [x] 实现 Mapper：按系统加本人范围查询；锁定个人模板；统计本人活动模板；插入主表与标签；按版本编辑、逻辑删除和排序；标签替换在事务内完成。
- [x] 实现输入校验：名称与标题 1 至 50 字、难度 1 至 3、时长 1 至 1440、备注最多 200 字、分类和标签必须启用、标签去重，基础积分仍不接受客户端输入。
- [x] 实现服务：要求 Web 家长、双开关和动态权限；系统模板只读；个人模板按用户隔离；创建上限 100；预查及数据库并发重名均转状态冲突；编辑、删除和全量排序使用版本条件。
- [x] 实现 API：`GET/POST /api/v1/task-templates`、`PATCH/DELETE /api/v1/task-templates/{id}`、`PUT /api/v1/task-templates/personal-order`；所有雪花标识序列化为字符串。
- [x] 扩展公共能力 `learningTaskTemplateEnabled`，Web 受双开关控制，小程序固定 false，并补端到端断言。
- [x] 新增真实 Spring/MyBatis 持久化测试，验证主表标签原子写入、版本递增、逻辑删除后名称复用和测试数据清理。
- [x] 运行模板服务、真实持久化和公共能力目标测试，全部通过。

## 任务三：Web 模板库与任务编辑器

**文件：**
- 修改：`web/src/api/capability.ts`
- 新建：`web/src/features/learning-tasks/taskTemplateApi.ts`
- 新建：`web/src/features/learning-tasks/TaskTemplateLibraryModal.tsx`
- 新建：`web/src/features/learning-tasks/TaskTemplateEditorModal.tsx`
- 修改：`web/src/features/learning-tasks/types.ts`
- 修改：`web/src/features/learning-tasks/LearningTaskEditorDrawer.tsx`
- 修改：`web/src/features/learning-tasks/LearningTaskManagementPage.tsx`
- 修改：`web/src/app/App.tsx`
- 修改：`web/src/app/App.test.tsx`
- 修改：`web/src/features/learning-tasks/LearningTaskManagementPage.test.tsx`

- [x] 先写组件测试：家长在能力启用时看到模板入口，系统与个人模板分组展示，选用后只回填可复用字段，日期、学生和审核人不被模板覆盖。
- [x] 增加操作测试：把当前任务配置保存为个人模板；新增、编辑、删除、上移/下移均调用正确契约；能力停用和小程序契约不显示入口。
- [x] 实现模板 API 类型，全部标识和版本按字符串/整数边界处理，不把 19 位标识转换为 JavaScript 数字。
- [x] 实现模板库和个人模板表单。系统模板只提供选用；个人模板提供选用、编辑、删除、上移和下移，图标按钮带中文提示，删除二次确认。
- [x] 扩展任务编辑器支持模板预填和保存当前可复用字段。模板不覆盖 `sourceType`、`sourceOrganizationId`、`scheduledDate`、`reviewerUserId`、`recurrenceEnabled`、`recurrenceEndDate` 或 `targets`。
- [x] Web 全量测试和生产构建通过。

## 任务四：三端回归与中文文档收口

**文件：**
- 修改：`README.md`
- 修改：`docs/design/00-设计文档体系与需求追溯-V1.0.md`
- 修改：`docs/design/01-功能详细设计-FSD-V1.0.md`
- 修改：`docs/design/02-Web与小程序交互说明-V1.0.md`
- 修改：`docs/design/03-系统架构设计-HLD-V1.0.md`
- 修改：`docs/design/04-数据库设计-V1.0.md`
- 修改：`docs/design/05-Flyway迁移规范-V1.0.md`
- 修改：`docs/design/06-API接口设计-V1.0.md`
- 修改：`docs/design/07-权限与安全设计-V1.0.md`
- 修改：`docs/design/09-统计口径与报表设计-V1.0.md`
- 修改：`docs/design/10-测试方案与验收用例-V1.0.md`
- 修改：`docs/design/12-当前实现一致性核对-V1.0.md`
- 修改：`docs/superpowers/plans/2026-08-08-lingdong-learning-master-development.md`

- [x] 执行后端 `mvn test`：193 项测试通过，Flyway 从空库连续执行 V1-V34，共 60 张表。
- [x] 执行 Web 全量测试与生产构建；执行 uni-app `type-check`、`build:h5`、`build:mp-weixin`。
- [x] 执行 `git diff --check`、V34 自增语法、19 位基础标识、Web/小程序产物和旧基线口径扫描；已知本地配置中的用户指定凭证不在本版本改动范围。
- [x] 中文文档同步系统/个人模板边界、API、权限、数据模型、测试与端侧隔离；纠正总计划中无原始素材依据的机构模板表述。
- [x] 按固定 1000 分口径更新为 650/1000（65.0%），下一工作包为 WBS-04 认证与家校关系。
