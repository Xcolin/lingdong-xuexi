# V59 教师账号与班级管理实施计划

> **执行要求：** 使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐项实施，并使用复选框（`- [ ]`）跟踪进度。

**目标：** 完成机构管理员组织范围内的教师账号单项维护、班级数据范围、Web 批量管理和 uni-app 高频单项管理。

**架构：** 新增独立 `teacher` 模块协调既有用户、角色、组织和班级关系；教师目录在 SQL 层按组织路径裁剪，批量操作逐项使用 `REQUIRES_NEW`。既有教师班级服务补齐功能开关、待审核保护和不可变审计。

**技术栈：** Spring Boot 3、JDK 17、MyBatis XML、Flyway、MySQL 8 兼容 SQL、React、Ant Design Pro、uni-app、Vitest、JUnit 5。

---

### 任务 1：V59 迁移与迁移测试

**文件：**
- 新建：`server/src/main/resources/db/migration/V59__add_teacher_management.sql`
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

- [x] 先增加失败测试，断言 V59 连续迁移、`edu_teacher_class_change_log`、开关、六项权限、机构管理员授权、85 张非自增主键表和全部新增 19 位标识。
- [x] 运行 `mvn -q -Dtest=FlywayMigrationTest test`，确认因 V59 不存在而失败。
- [x] 新增 V59 脚本、约束、索引和基础数据，不修改 V1-V58。
- [x] 重跑迁移测试并确认通过。

### 任务 2：教师领域、范围查询与单项创建

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/teacher/domain/*`
- 新建：`server/src/main/java/com/lingdong/learning/teacher/infrastructure/persistence/TeacherManagementMapper.java`
- 新建：`server/src/main/resources/mapper/teacher/TeacherManagementMapper.xml`
- 新建：`server/src/main/java/com/lingdong/learning/teacher/application/TeacherManagementAccessService.java`
- 新建：`server/src/main/java/com/lingdong/learning/teacher/application/TeacherManagementApplicationService.java`
- 新建：`server/src/test/java/com/lingdong/learning/teacher/application/TeacherManagementApplicationServiceTest.java`

- [x] 先写失败测试，覆盖授权学校创建、跨学校班级拒绝、重复账号/手机号、事务回滚和响应不含密码。
- [x] 实现固定 `ORGANIZATION` 类型、全局教师角色、学校人员关系、BCrypt 密码和可选多班级原子创建。
- [x] 增加 SQL 层组织范围分页，覆盖关键词、学校、班级和状态筛选。
- [x] 运行教师应用服务测试并确认通过。

### 任务 3：资料、状态、密码和待审核保护

**文件：**
- 修改：`server/src/main/java/com/lingdong/learning/teacher/application/TeacherManagementApplicationService.java`
- 修改：`server/src/main/resources/mapper/teacher/TeacherManagementMapper.xml`
- 新建：`server/src/main/java/com/lingdong/learning/teacher/application/TeacherPendingReviewGuard.java`
- 修改：`server/src/test/java/com/lingdong/learning/teacher/application/TeacherManagementApplicationServiceTest.java`

- [x] 先写失败测试，覆盖姓名/手机号修改、唯一手机号、密码策略、密码重置会话撤销、启停锁定和待审核任务冲突。
- [x] 实现资料更新、密码摘要更新、状态变更和会话撤销。
- [x] 实现待审核任务保护查询，越权目标统一按不存在处理。
- [x] 运行教师应用服务测试并确认通过。

### 任务 4：班级关系审计与批量独立事务

**文件：**
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/application/TeacherClassAssignmentService.java`
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/infrastructure/persistence/TeacherClassMapper.java`
- 修改：`server/src/main/resources/mapper/learningtask/TeacherClassMapper.xml`
- 新建：`server/src/main/java/com/lingdong/learning/teacher/application/TeacherBatchItemService.java`
- 新建：`server/src/main/java/com/lingdong/learning/teacher/application/TeacherBatchApplicationService.java`
- 修改：对应 JUnit 测试

- [x] 先写失败测试，覆盖双开关、绑定/解绑审计、待审核解绑拒绝、重复绑定幂等和跨组织隐藏。
- [x] 实现班级变更日志并将管理写操作改为 `TEACHER_MANAGEMENT + CLASS_MANAGEMENT` 联合拦截。
- [x] 先写批量失败测试，覆盖最多 100 项、逐项独立事务、部分成功和中性失败结果。
- [x] 实现五类批量操作与 `REQUIRES_NEW` 条目服务并运行目标测试。

### 任务 5：REST/OpenAPI 与公共能力

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/teacher/web/*`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityController.java`
- 新建：`server/src/test/java/com/lingdong/learning/teacher/web/TeacherManagementControllerTest.java`
- 修改：公共能力测试

- [x] 先写控制器失败测试，覆盖六项权限、Web 批量限制、客户端类型、组织越权和字段脱敏。
- [x] 实现分页、详情、创建、编辑、状态、密码、班级与批量 API。
- [x] 增加 `teacherManagementEnabled` 公共能力，并验证开关停用时接口和入口共同关闭。
- [x] 运行控制器及公共能力测试并确认通过。

### 任务 6：React Web 教师管理

**文件：**
- 新建：`web/src/api/teachers.ts`
- 新建：`web/src/features/teachers/TeacherManagementPage.tsx`
- 新建：`web/src/features/teachers/TeacherManagementPage.test.tsx`
- 修改：`web/src/app/App.tsx`
- 修改：`web/src/api/capability.ts`
- 修改：`web/src/styles/index.css`

- [x] 先写失败测试，覆盖无开关/无权限隐藏、分页筛选、创建、编辑、状态、密码、班级和批量部分失败展示。
- [x] 实现独立页面、图标按钮、二次确认、稳定表格尺寸和移动宽度降级。
- [x] 将菜单和直达路由同时接入公共能力、机构管理员角色和动态权限。
- [x] 运行 Web 目标测试、类型检查和生产构建。

### 任务 7：uni-app 高频单项管理

**文件：**
- 新建：`miniapp/src/api/teacher-management.ts`
- 新建：`miniapp/src/pages/organization-teachers/organization-teachers.vue`
- 修改：`miniapp/src/pages/organization-home/organization-home.vue`
- 修改：`miniapp/src/api/capability.ts`
- 修改：`miniapp/src/pages.json`

- [x] 增加机构管理员教师管理入口，开关停用时隐藏且直达页面复核会话和能力。
- [x] 实现教师查询、创建、编辑、状态、密码及单项班级绑定，不加入批量选择和导入逻辑。
- [x] 运行 uni-app 类型检查、H5 构建和微信小程序构建。

### 任务 8：全量验证与文档回填

**文件：**
- 修改：`README.md`
- 修改：`docs/design/00-设计文档体系与需求追溯-V1.0.md` 至 `docs/design/12-当前实现一致性核对-V1.0.md` 中受 V59 影响的章节
- 修改：`docs/superpowers/plans/2026-08-08-lingdong-learning-master-development.md`

- [x] 运行后端 `mvn -q test`，核对测试总数和 59 个连续迁移。
- [x] 运行 Web 全量测试、类型检查、生产构建及桌面/移动视口检查。
- [x] 运行 miniapp 类型检查、H5 和微信小程序构建，扫描产物不含 Web 批量入口和定位能力。
- [x] 扫描 V59 无自增主键、全部新增种子为 19 位数字、仓库不含已提供真实密钥。
- [x] 更新设计追溯、测试、发布、一致性、总计划和进度；明确未连接远程 MySQL、Redis、微信、共享测试、预生产或生产环境。

## 执行结果

2026-09-06 完成 V59 本地验收：后端 149 个测试套件、552 项全部通过；Web 31 个测试文件、121 项全部通过并完成类型检查、生产构建及 1440×900、390×844 视觉检查；uni-app 类型检查、H5 和微信小程序构建通过。下一迁移从 V60 开始。
