# 灵动伴随组织节点生命周期实施计划

> **智能执行说明：** 按任务逐项实施本计划，步骤统一使用复选框（`- [ ]`）跟踪；每个生产行为必须先有失败测试，再完成最小实现。

**目标：** 按 V47 详细设计完成组织节点直接编辑、有效状态、系统任务审批停用/移动/受控删除、Web 管理和现有业务有效性拦截。

**架构：** `sys_organization` 保存自身状态、有效状态和乐观锁版本；高风险变更使用独立快照表关联现有系统任务，审批通过后重新锁定并生效。后端统一以有效状态控制新增业务，Web 分离系统管理员维护态和系统审核员审核态，小程序不增加组织树管理页面。

**技术栈：** Spring Boot 3、JDK 17、MyBatis XML、MySQL 8、H2 测试库、Redis、Flyway、React 18、Ant Design Pro、Vitest、uni-app。

---

### 任务 1：V47 数据契约

**文件：**
- 新增：`server/src/main/resources/db/migration/V47__add_organization_node_lifecycle.sql`
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

- [x] **步骤 1：先写迁移失败测试。** 在 `FlywayMigrationTest` 断言 V47 唯一执行一次、`sys_organization.effective_status/version_no` 存在、两张变更表存在、三个新权限和一个默认启用开关存在，全部新增主键与基础数据标识为 19 位非自增 `BIGINT`。

```java
assertThat(migrationCount).isEqualTo(1);
assertThat(tableCount).isEqualTo(2);
assertThat(nonIdentityBigintIdCount).isEqualTo(2);
assertThat(permissionCount).isEqualTo(3);
assertThat(featureCount).isEqualTo(1);
```

- [x] **步骤 2：运行 `mvn -Dtest=FlywayMigrationTest test`。** 预期因 V47 表或字段不存在而失败。
- [x] **步骤 3：新增 V47 迁移。** 扩展组织表，创建 `sys_organization_change`、`sys_organization_change_audit`，初始化有效状态，新增 `ORGANIZATION_MANAGEMENT`、`ORG_NODE_UPDATE`、`ORG_NODE_CHANGE_SUBMIT`、`ORG_NODE_CHANGE_REVIEW` 及内置角色授权。
- [x] **步骤 4：重跑迁移测试。** 预期全部通过；执行 `rg -n "AUTO_INCREMENT|\\bIDENTITY\\b" server/src/main/resources/db/migration/V47__add_organization_node_lifecycle.sql`，预期无输出。

### 任务 2：组织领域模型与直接编辑

**文件：**
- 修改：`server/src/main/java/com/lingdong/learning/organization/domain/Organization.java`
- 新增：`server/src/main/java/com/lingdong/learning/organization/domain/OrganizationEffectiveStatus.java`
- 新增：`server/src/main/java/com/lingdong/learning/organization/application/UpdateOrganizationCommand.java`
- 修改：`server/src/main/java/com/lingdong/learning/organization/application/OrganizationApplicationService.java`
- 修改：`server/src/main/java/com/lingdong/learning/organization/infrastructure/persistence/OrganizationMapper.java`
- 修改：`server/src/main/resources/mapper/organization/OrganizationMapper.xml`
- 修改并测试：`server/src/test/java/com/lingdong/learning/organization/application/OrganizationApplicationServiceTest.java`

- [x] **步骤 1：写直接编辑失败测试。** 覆盖名称和排序更新、版本递增、同级重名、错误版本、非系统管理员和编码/类型/父级不可修改。

```java
Organization updated = service.updateOrganization(new UpdateOrganizationCommand(
        ORGANIZATION_ID, "新名称", 30, 1));
assertThat(updated.versionNo()).isEqualTo(2);
assertThat(updated.code()).isEqualTo("SCHOOL_A");
```

- [x] **步骤 2：运行 `OrganizationApplicationServiceTest`。** 预期因更新命令和 Mapper 方法不存在而失败。
- [x] **步骤 3：实现最小直接编辑。** 使用 `findByIdForUpdate` 锁行，规范化名称和排序，按 `id/version_no` 更新并写 `DIRECT_UPDATE` 审计。
- [x] **步骤 4：重跑目标测试。** 预期通过，现有创建组织测试保持通过。

### 任务 3：有效状态与重新启用

**文件：**
- 新增：`server/src/main/java/com/lingdong/learning/organization/application/OrganizationOperationalStatusService.java`
- 修改：`server/src/main/java/com/lingdong/learning/organization/application/OrganizationApplicationService.java`
- 修改：`server/src/main/resources/mapper/organization/OrganizationMapper.xml`
- 修改并测试：`server/src/test/java/com/lingdong/learning/organization/application/OrganizationApplicationServiceTest.java`

- [x] **步骤 1：写有效状态失败测试。** 覆盖父级停用时启用子节点仍为有效停用、父级重新启用时按各节点自身状态重算、版本冲突拒绝和子树批量更新。
- [x] **步骤 2：运行目标测试。** 预期因 `effectiveStatus` 和重新启用 API 不存在而失败。
- [x] **步骤 3：实现有效状态服务。** 对单节点提供 `requireOperational(id)`，对重新启用和移动后的子树按路径重算；组织创建继承父级有效状态。

```java
public void requireOperational(Long organizationId) {
    Organization organization = requireOrganization(organizationId);
    if (organization.status() != ENABLED || organization.effectiveStatus() != ENABLED) {
        throw new OrganizationNotOperationalException("组织当前不可开展新业务");
    }
}
```

- [x] **步骤 4：重跑目标测试。** 预期通过。

### 任务 4：高风险组织变更状态机

**文件：**
- 新增：`server/src/main/java/com/lingdong/learning/organization/application/OrganizationChange*.java`
- 新增：`server/src/main/java/com/lingdong/learning/organization/domain/OrganizationChange*.java`
- 新增：`server/src/main/java/com/lingdong/learning/organization/infrastructure/persistence/OrganizationChangeMapper.java`
- 新增：`server/src/main/resources/mapper/organization/OrganizationChangeMapper.xml`
- 修改：`server/src/main/java/com/lingdong/learning/audit/application/SystemTaskType.java`
- 新增测试：`server/src/test/java/com/lingdong/learning/organization/application/OrganizationChangeApplicationServiceTest.java`

- [x] **步骤 1：写申请和角色失败测试。** 覆盖只有系统管理员可发起、创建后直接提交为待审核、系统审核员不能提交、重复活动申请拒绝、原因和版本必填。
- [x] **步骤 2：运行目标测试。** 预期因变更服务不存在而失败。
- [x] **步骤 3：实现申请事务。** 创建系统任务和组织变更快照，调用现有 `submit`，新增 `ORGANIZATION_MOVE`、`ORGANIZATION_DELETE` 任务类型；变更记录初始执行状态为 `PENDING`。
- [x] **步骤 4：写并运行审批失败测试。** 覆盖非审核员拒绝、驳回意见必填、审批后版本变化、执行失败标记 `FAILED` 且不改组织、成功标记 `APPLIED/EFFECTIVE`。
- [x] **步骤 5：实现批准、驳回和失败记录事务。** 复用现有系统任务审批；生效事务失败后使用独立事务记录中性失败原因。
- [x] **步骤 6：重跑目标测试及 `SystemTaskApplicationServiceTest`。** 预期通过。

### 任务 5：停用、移动和受控删除

**文件：**
- 修改：`server/src/main/java/com/lingdong/learning/organization/application/OrganizationChangeApplicationService.java`
- 修改：`server/src/main/java/com/lingdong/learning/organization/infrastructure/persistence/OrganizationMapper.java`
- 修改：`server/src/main/resources/mapper/organization/OrganizationMapper.xml`
- 修改并测试：`server/src/test/java/com/lingdong/learning/organization/application/OrganizationChangeApplicationServiceTest.java`
- 新增持久化测试：`server/src/test/java/com/lingdong/learning/organization/application/OrganizationChangePersistenceTest.java`

- [x] **步骤 1：写停用失败测试。** 断言申请时不改组织，批准后自身停用、全部后代有效停用、后代自身状态不变、版本递增和审计落库。
- [x] **步骤 2：实现停用。** 锁定目标节点，按版本更新自身，按旧路径批量更新后代有效状态。
- [x] **步骤 3：写移动失败测试。** 覆盖移动到自身/后代、目标父级有效停用、同级重名、路径超长、审批前版本变化和子树路径原子替换。
- [x] **步骤 4：实现移动。** 先锁目标和父级，使用旧路径前缀替换目标及后代路径，更新根节点父级范围键并重算有效状态。
- [x] **步骤 5：写删除失败测试。** 覆盖非叶子以及设计列明的每类当前引用拒绝；空叶子批准后删除，申请快照和审计保留。
- [x] **步骤 6：实现受控删除。** 使用显式引用计数和数据库外键双重保护，绝不级联删除业务数据。
- [x] **步骤 7：运行两个组织变更测试。** 预期全部通过。

### 任务 6：后端 API、权限与功能开关

**文件：**
- 修改：`server/src/main/java/com/lingdong/learning/organization/application/OrganizationManagementApplicationService.java`
- 修改：`server/src/main/java/com/lingdong/learning/organization/web/OrganizationManagementController.java`
- 修改：`server/src/main/java/com/lingdong/learning/organization/web/OrganizationTreeNodeResponse.java`
- 新增：`server/src/main/java/com/lingdong/learning/organization/web/OrganizationChange*.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityController.java`
- 修改并测试：`server/src/test/java/com/lingdong/learning/organization/web/OrganizationManagementControllerTest.java`
- 新增测试：`server/src/test/java/com/lingdong/learning/organization/web/OrganizationChangeControllerTest.java`

- [x] **步骤 1：写 MockMvc 失败测试。** 覆盖编辑、启用、申请、列表、批准、驳回、字符串雪花标识、角色边界、功能关闭和审核员只读组织快照。
- [x] **步骤 2：运行两个控制器测试。** 预期因路由和能力字段不存在而失败。
- [x] **步骤 3：实现 DTO、控制器和能力字段。** Web 权限严格区分提交与审核；管理服务继续做角色二次校验。
- [x] **步骤 4：重跑控制器测试。** 预期通过。

### 任务 7：现有业务接入有效状态

**文件：**
- 修改：`server/src/main/java/com/lingdong/learning/user/application/UserAccessApplicationService.java`
- 修改：`server/src/main/java/com/lingdong/learning/student/application/StudentApplicationService.java`
- 修改：`server/src/main/java/com/lingdong/learning/student/application/ParentBindingInvitationApplicationService.java`
- 修改：`server/src/main/java/com/lingdong/learning/student/application/StudentClassAssignmentService.java`
- 修改：`server/src/main/java/com/lingdong/learning/student/application/StudentOrganizationLifecycleService.java`
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/application/LearningTaskOptionService.java`
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/application/LearningTaskScopeService.java`
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/application/TeacherClassAssignmentService.java`
- 修改：`server/src/main/resources/mapper/datascope/OrganizationAdminMapper.xml`
- 修改：`server/src/main/resources/mapper/learningtask/LearningTaskOptionMapper.xml`
- 修改：`server/src/main/resources/mapper/student/StudentOrganizationMapper.xml`
- 修改：`server/src/main/resources/mapper/auth/ParentMobileManualRecoveryMapper.xml`
- 修改：`server/src/main/resources/mapper/student/StudentAccountCancellationMapper.xml`
- 修改相关现有服务测试。

- [x] **步骤 1：为每类入口增加失败用例。** 构造“自身启用但有效停用”的组织，断言组织管理员授权、学生创建/邀请/分班/转班、教师班级、机构/教师任务和机构安全操作均拒绝或不返回候选。
- [x] **步骤 2：运行目标测试。** 执行 `mvn -Dtest=UserAccessApplicationServiceTest,OrganizationMiniappAuthenticationTest,ParentMobileManualRecoveryServiceTest,StudentAccountCancellationServiceTest,StudentManagementControllerTest,LearningTaskControllerTest,LearningTaskOptionControllerTest,TeacherClassControllerTest test`，预期现有代码仅看自身状态，新增用例失败。
- [x] **步骤 3：接入 `OrganizationOperationalStatusService` 和查询条件。** 历史只读查询不附加有效状态条件，只有新增业务和候选查询附加。
- [x] **步骤 4：运行组织、学生、任务、认证目标测试。** 预期通过。

### 任务 8：Web 组织维护与审核

**文件：**
- 修改：`web/src/api/organization.ts`
- 修改：`web/src/api/capability.ts`
- 修改：`web/src/features/organizations/OrganizationManagementPage.tsx`
- 修改：`web/src/features/organizations/OrganizationManagementPage.test.tsx`
- 新增：`web/src/features/organizations/OrganizationNodeEditorDrawer.tsx`
- 新增：`web/src/features/organizations/OrganizationChangeReviewPanel.tsx`
- 新增：`web/src/features/organizations/OrganizationChangeReviewPanel.test.tsx`
- 修改：`web/src/app/App.tsx`
- 修改：`web/src/app/App.test.tsx`

- [x] **步骤 1：写 Web 失败测试。** 覆盖系统管理员编辑/启用/申请，系统审核员只见审核列表，其他角色无入口，自身状态与上级停用状态分开展示，功能停用隐藏并阻止 API 加载。
- [x] **步骤 2：运行目标 Vitest。** 预期因组件和 API 不存在而失败。
- [x] **步骤 3：实现 API 和界面。** 使用 lucide 图标按钮、抽屉编辑、危险操作确认弹窗和审核意见弹窗；不在卡片内嵌套卡片。
- [x] **步骤 4：运行 Web 目标测试、全量测试和生产构建。** 预期通过，仅允许现有分包体积警告。

### 任务 9：中文文档与全量验收

**文件：**
- 修改：`README.md`
- 修改：`docs/superpowers/plans/2026-08-08-lingdong-learning-master-development.md`
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

- [x] **步骤 1：同步中文文档。** 记录自身/有效状态、审批边界、移动路径、删除引用、版本冲突、Web/小程序边界和未执行外部环境。
- [x] **步骤 2：执行后端全量测试。** 98 个测试套件、377 项测试，失败、错误和跳过均为 0；V1-V47 从空库连续迁移成功。
- [x] **步骤 3：执行 Web 全量测试与构建。** 21 个测试文件、69 项测试全部通过，TypeScript 检查和 Vite 生产构建成功；仅保留既有大分包提示。
- [x] **步骤 4：执行 uni-app 验证。** 类型检查、H5 和微信小程序构建均成功；源码未发现定位、地图和平台组织管理入口，学生微信可见控件继续由 `MP-WEIXIN` 条件编译控制。
- [x] **步骤 5：执行静态扫描。** V47 无自增声明，13 个基础标识均为 19 位数字；提交配置无真实远程参数，真实本地参数文件受 Git 忽略；小程序无定位/地图和平台组织管理接口；H5 学生微信可见控件受条件编译保护；`git diff --check` 无空白错误，仅有行尾转换提示。
- [x] **步骤 6：按证据勾选计划并更新进度。** V47 专项全部完成并回填总计划，当前进度保持 715/1000（71.5%）；V47 未在共享测试、预生产或生产数据库执行，后续迁移从 V48 新增。

## 执行方式

采用当前会话内联执行。每个任务按红、绿、重构顺序推进；不自动提交、不暂存、不回退工作区中与 V47 无关的现有修改。
