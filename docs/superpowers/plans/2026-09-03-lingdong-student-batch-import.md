# 灵动学习学员批量导入实施计划

> **执行说明：** 按任务逐项实施并更新复选框；代码、测试、构建和中文文档全部通过后才可标记专项完成。

**目标：** 在 V56 校验作业之上交付机构学员批量开户、单班绑定、失败重试和一次性凭证下载闭环。

**架构：** 使用独立学员导入执行聚合保存业务入库状态，逐行新事务复用现有学员开户和班级绑定能力；凭证以 AES-GCM 密文短期保存，最终加密文件仍由统一附件模块托管。

**技术栈：** Spring Boot 3、JDK 17、MyBatis XML、MySQL 8、Flyway、Apache POI、React、Ant Design Pro、Vitest。

---

## 任务 1：V58 数据模型、权限和开关

**文件：**

- 新增：`server/src/main/resources/db/migration/V58__add_student_batch_import.sql`
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

- [x] 1.1 先扩展迁移测试，断言两张业务表、唯一约束、状态约束、三个权限、功能开关、机构管理员授权和附件规则存在。
- [x] 1.2 运行 `mvn -Dtest=FlywayMigrationTest test`，确认因 V58 缺失而失败。
- [x] 1.3 新增 V58 迁移，所有主键和种子标识使用19位数字，禁止 `AUTO_INCREMENT`。
- [x] 1.4 重跑迁移测试，确认空库连续执行58个迁移。

## 任务 2：执行领域、持久化和访问控制

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/studentimport/domain/*`
- 新增：`server/src/main/java/com/lingdong/learning/studentimport/infrastructure/persistence/*`
- 新增：`server/src/main/resources/mapper/studentimport/*`
- 新增：`server/src/main/java/com/lingdong/learning/studentimport/application/StudentImportAccessService.java`
- 新增测试：`server/src/test/java/com/lingdong/learning/studentimport/application/StudentImportAccessServiceTest.java`

- [x] 2.1 先写访问失败测试，覆盖功能关闭、缺权限、非机构管理员、越权组织、非本人执行和账号角色撤回。
- [x] 2.2 实现执行、行和凭证状态枚举及不可变记录。
- [x] 2.3 实现 MyBatis Mapper/XML，所有查询参数绑定，领取与凭证消费使用状态加版本条件更新。
- [x] 2.4 实现实时开关、RBAC、角色、操作人归属和组织范围访问控制，并运行测试转绿。

## 任务 3：创建执行和工作簿业务值读取

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/studentimport/application/StudentImportApplicationService.java`
- 新增：`server/src/main/java/com/lingdong/learning/studentimport/application/StudentImportWorkbookReader.java`
- 新增测试：`server/src/test/java/com/lingdong/learning/studentimport/application/StudentImportApplicationServiceTest.java`
- 新增测试：`server/src/test/java/com/lingdong/learning/studentimport/application/StudentImportWorkbookReaderTest.java`

- [x] 3.1 先写失败测试，覆盖非 `VALIDATED`、非 `STUDENT` 模板、无学校、班级越界、缺 `STUDENT_NAME` 和重复创建。
- [x] 3.2 写工作簿读取测试，按字段快照读取姓名和可选年级，忽略空白行，不执行公式。
- [x] 3.3 实现创建执行事务，锁定校验作业并为每个有效源行建立 `PENDING` 记录。
- [x] 3.4 运行两个测试类并确认通过。

## 任务 4：逐行事务、调度和失败重试

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/studentimport/application/StudentImportRowProcessor.java`
- 新增：`server/src/main/java/com/lingdong/learning/studentimport/application/StudentImportBatchService.java`
- 新增：`server/src/main/java/com/lingdong/learning/studentimport/infrastructure/scheduling/StudentImportSchedulingConfiguration.java`
- 修改：`server/src/main/resources/application.yml`
- 新增测试：`server/src/test/java/com/lingdong/learning/studentimport/application/StudentImportRowProcessorTest.java`
- 新增测试：`server/src/test/java/com/lingdong/learning/studentimport/application/StudentImportBatchServiceTest.java`

- [x] 4.1 先写单行原子性失败测试，确保开户、学校关系和班级绑定任一步失败时整行回滚。
- [x] 4.2 写竞争领取、部分成功、全部失败、执行级故障和仅重试失败行测试。
- [x] 4.3 实现逐行 `REQUIRES_NEW` 处理，调用现有学员开户与班级服务，不复制账号规则。
- [x] 4.4 实现可关闭调度器、最大尝试次数和确定终态，运行测试转绿。

## 任务 5：凭证加密、文件生成、一次下载和过期清理

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/studentimport/application/StudentCredentialCipher.java`
- 新增：`server/src/main/java/com/lingdong/learning/studentimport/application/StudentCredentialWorkbookWriter.java`
- 新增：`server/src/main/java/com/lingdong/learning/studentimport/application/StudentImportCredentialService.java`
- 新增：`server/src/main/java/com/lingdong/learning/studentimport/application/StudentImportCredentialCleanupService.java`
- 新增测试：`server/src/test/java/com/lingdong/learning/studentimport/application/StudentCredentialCipherTest.java`
- 新增测试：`server/src/test/java/com/lingdong/learning/studentimport/application/StudentImportCredentialServiceTest.java`

- [x] 5.1 先写 AES-GCM 往返、随机密文、篡改拒绝和错误密钥拒绝测试。
- [x] 5.2 写凭证文件列结构、一次下载、非本人拒绝、24小时过期和物理内容清理测试。
- [x] 5.3 实现行凭证和附件内容分层加密，最终文件保存成功后清除行级密文。
- [x] 5.4 实现原子消费、`no-store` 下载和定时清理，运行测试转绿。

## 任务 6：REST、OpenAPI 和 Web 页面

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/studentimport/web/*`
- 新增测试：`server/src/test/java/com/lingdong/learning/studentimport/web/StudentImportControllerTest.java`
- 新增：`web/src/api/student-imports.ts`
- 修改：`web/src/features/import-jobs/ImportJobManagementPage.tsx`
- 修改：`web/src/features/import-jobs/ImportJobManagementPage.test.tsx`

- [x] 6.1 先写控制器和页面失败测试，覆盖创建、列表、详情、行分页、重试、一次下载确认和权限/开关隐藏。
- [x] 6.2 实现 REST 响应，雪花标识序列化为字符串，错误响应不含姓名、登录码和密文。
- [x] 6.3 在导入校验作业页面增加执行入口和执行详情，不增加独立小程序能力。
- [x] 6.4 运行控制器、Web 页面、类型检查和生产构建。

## 任务 7：全量验收和中文文档

**文件：**

- 修改：`README.md`
- 修改：`docs/design/00-设计文档体系与需求追溯-V1.0.md` 至 `docs/design/12-当前实现一致性核对-V1.0.md` 中受影响章节
- 修改：`docs/superpowers/plans/2026-08-08-lingdong-learning-master-development.md`
- 修改：本计划

- [x] 7.1 运行后端全量测试，记录测试数和58个迁移结果。
- [x] 7.2 运行 Web 全量测试、类型检查和生产构建。
- [x] 7.3 运行 uni-app 类型检查、H5 和微信小程序构建，并扫描不存在 V58 页面、路由和接口。
- [x] 7.4 扫描数据库、配置、日志测试和前端代码，确认不存在初始登录码、微信、数据库或 Redis 密钥新增泄漏。
- [x] 7.5 执行迁移主键、自增、19位种子和 `git diff --check` 检查。
- [x] 7.6 用中文更新设计文档、README、总计划和完成证据；不连接远程数据库、Redis、共享测试、预生产或生产环境。

## 完成定义

V58 只有在数据库、后端、Web、安全凭证、功能开关、动态权限、组织范围、全量测试、双前端隔离和中文文档全部通过后才计入完成进度。
