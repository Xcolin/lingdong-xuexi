# 灵动学习任务定义与下发实施计划

> **执行要求：** 使用 `superpowers:executing-plans` 按任务逐项实施；每个任务先补充失败测试，再完成最小实现并执行局部回归。当前工作区包含既有未提交改动，实施期间不清理、不回退其他文件，也不在未获用户明确授权时执行 Git 暂存、提交或推送。

**目标：** 完成 V22 学习任务第一条端到端纵向能力：主家长、机构管理员和教师在 Web 端创建、编辑和发布任务，后端按受控目标展开学生任务实例，学生在 uni-app 小程序查看本人待认领任务。

**架构：** 采用“不可变已发布任务定义 + 原始目标 + 按学生展开实例”模型。任务定义、目标、标签和实例由独立 `learningtask` 模块管理；学生当前班级继续复用学生组织关系，教师班级关系使用独立表；所有候选项和任务数据均在后端按家长关系、组织树或教师班级关系裁剪。Web 与小程序继续使用各自独立的客户端会话和前端工程。

**技术栈：** Spring Boot 3.4、JDK 17、Maven、MyBatis XML、Flyway、MySQL 8/H2 MySQL 兼容模式、Redis、Spring Security、React、Ant Design、Vitest、uni-app、Vue 3、TypeScript。

**执行边界：** 所有新表主键由应用层雪花算法生成 19 位 `BIGINT`，API 中按字符串传输。数据库迁移和集成测试只使用本地 H2；不加载远程环境配置，不连接远程 MySQL、Redis、微信、共享测试、预生产或生产环境。V22 不实现认领、计时、打卡、审核、积分发放、周期任务或模板。

---

## 任务一：建立 V22 数据库迁移基线

**文件：**

- 新增：`server/src/main/resources/db/migration/V22__create_learning_task_foundation.sql`
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

### 步骤 1：补充失败的迁移测试

断言 V1 至 V22 可从空库连续迁移，并校验：

1. 新增 `edu_teacher_class`、`learn_task`、`learn_task_target`、`learn_task_tag`、`learn_task_assignment`；
2. 五张表主键均为非自增 `BIGINT`，全库应用层雪花主键表数量由 24 增至 29；
3. 教师班级、任务标签和学生任务实例具有业务唯一约束；
4. 任务、目标、标签、实例和既有用户、学生、组织之间外键完整；
5. 新增 `TASK_CATEGORY`、`TASK_TAG` 字典类型及最小启用字典项；
6. 新增 `LEARNING_TASK_MANAGEMENT` 功能开关且初始启用；
7. 新增六项权限，并按设计授予 `PARENT`、`ORG_ADMIN`、`TEACHER`、`STUDENT`；
8. 固定基础数据标识均为 19 位且不与 V1 至 V21 冲突。

运行：

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -Dtest=FlywayMigrationTest test
```

预期：因 V22 迁移不存在而失败。

### 步骤 2：实现 V22 迁移

按设计规格建立字段、外键、状态检查、查询索引和唯一约束。迁移只写结构和基础种子，不写演示学生、教师或任务数据，不使用数据库自增、触发器或数据库特定序列。

### 步骤 3：运行迁移回归

```powershell
mvn -Dtest=FlywayMigrationTest test
git diff --check -- server/src/main/resources/db/migration/V22__create_learning_task_foundation.sql server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java
```

预期：迁移测试通过，无空白错误。

---

## 任务二：实现学生当前班级切换

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/student/application/StudentClassAssignmentService.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/application/AssignStudentClassCommand.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/web/StudentClassController.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/web/AssignStudentClassRequest.java`
- 修改：`server/src/main/java/com/lingdong/learning/student/infrastructure/persistence/StudentOrganizationMapper.java`
- 修改：`server/src/main/resources/mapper/student/StudentOrganizationMapper.xml`
- 新增：`server/src/test/java/com/lingdong/learning/student/application/StudentClassAssignmentServiceTest.java`
- 新增：`server/src/test/java/com/lingdong/learning/student/web/StudentClassControllerTest.java`

### 步骤 1：编写失败测试

覆盖机构管理员将学生设置到授权子树内启用 `CLASS`、切换时停用旧活动班级、重复设置幂等、学生不属于班级上级机构时拒绝、跨组织伪装为不存在、非 `WEB` 会话拒绝和缺少动态权限拒绝。

### 步骤 2：实现应用服务和 MyBatis 行锁

事务内锁定学生活动 `CLASS` 关系，校验学生机构关系、目标班级类型和 `OrganizationDataScopeService`，再停用旧关系并新增或重新启用目标关系。新增关系标识使用 `SnowflakeIdGenerator`。

### 步骤 3：实现接口并回归

实现 `PUT /api/v1/students/{studentId}/class`，19 位标识按字符串接收。运行：

```powershell
mvn -Dtest=StudentClassAssignmentServiceTest,StudentClassControllerTest test
```

---

## 任务三：实现教师班级关系

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/learningtask/domain/TeacherClassRelation.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/application/TeacherClassAssignmentService.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/infrastructure/persistence/TeacherClassMapper.java`
- 新增：`server/src/main/resources/mapper/learningtask/TeacherClassMapper.xml`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/web/TeacherClassController.java`
- 新增：`server/src/test/java/com/lingdong/learning/learningtask/application/TeacherClassAssignmentServiceTest.java`
- 新增：`server/src/test/java/com/lingdong/learning/learningtask/web/TeacherClassControllerTest.java`

### 步骤 1：编写失败测试

覆盖机构管理员绑定、重复绑定幂等、解除后重新启用、教师必须启用且具有 `TEACHER` 角色、目标必须为授权子树内启用班级、教师本人仅能读取自己的活动班级、跨范围和客户端隔离。

### 步骤 2：实现关系持久化与服务

所有写操作检查 `TEACHER_CLASS_ASSIGN`，使用逻辑状态保留历史关系。查询教师本人班级只返回活动关系；机构管理员查询还必须通过组织数据范围校验。

### 步骤 3：实现接口并回归

实现 `PUT`、`DELETE`、`GET /api/v1/teachers/{teacherUserId}/classes/{classId}` 对应接口，运行：

```powershell
mvn -Dtest=TeacherClassAssignmentServiceTest,TeacherClassControllerTest test
```

---

## 任务四：实现受数据范围保护的任务候选项

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/learningtask/application/LearningTaskOptionService.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/application/OrganizationOption.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/application/StudentOption.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/application/TeacherOption.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/infrastructure/persistence/LearningTaskOptionMapper.java`
- 新增：`server/src/main/resources/mapper/learningtask/LearningTaskOptionMapper.xml`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskOptionController.java`
- 新增：`server/src/test/java/com/lingdong/learning/learningtask/application/LearningTaskOptionServiceTest.java`
- 新增：`server/src/test/java/com/lingdong/learning/learningtask/web/LearningTaskOptionControllerTest.java`

### 步骤 1：编写失败测试

覆盖家长只见活动主关系学生、机构管理员只见授权组织子树、教师只见活动授权班级及班级学生、关键字与组织类型筛选、停用数据排除、教师候选仅机构管理员可读、响应不含手机号和家庭关系等敏感字段。

### 步骤 2：实现受控查询

MyBatis 查询以当前用户关系和组织路径为起点，不提供无范围的全量查询分支。学生账号只返回脱敏展示值；没有可用范围时返回空集合，不降级为全量数据。

### 步骤 3：实现三个只读接口并回归

```powershell
mvn -Dtest=LearningTaskOptionServiceTest,LearningTaskOptionControllerTest test
```

---

## 任务五：实现任务领域模型、校验与草稿持久化

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/learningtask/domain/LearningTask.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/domain/LearningTaskSourceType.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/domain/LearningTaskStatus.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/domain/LearningTaskTarget.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/domain/LearningTaskTargetType.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/application/LearningTaskValidator.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/infrastructure/persistence/LearningTaskMapper.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/infrastructure/persistence/LearningTaskTargetMapper.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/infrastructure/persistence/LearningTaskTagMapper.java`
- 新增：`server/src/main/resources/mapper/learningtask/LearningTaskMapper.xml`
- 新增：`server/src/main/resources/mapper/learningtask/LearningTaskTargetMapper.xml`
- 新增：`server/src/main/resources/mapper/learningtask/LearningTaskTagMapper.xml`
- 新增：`server/src/test/java/com/lingdong/learning/learningtask/application/LearningTaskValidatorTest.java`
- 新增：`server/src/test/java/com/lingdong/learning/learningtask/infrastructure/persistence/LearningTaskPersistenceTest.java`

### 步骤 1：编写失败测试

覆盖标题、难度、时长、计划日期、备注、目标数量、标签数量与去重；校验启用的 `TASK_CATEGORY` 和 `TASK_TAG` 字典；基础积分由难度乘以 10 生成；审核超时固定 72 小时；所有新增记录使用 19 位雪花标识。

### 步骤 2：实现领域校验和持久化

Mapper 只使用参数绑定。任务详情聚合查询不得形成每行单独查询；目标和标签按任务批量加载。任务行锁提供独立 `findByIdForUpdate` 方法供发布事务使用。

### 步骤 3：运行局部回归

```powershell
mvn -Dtest=LearningTaskValidatorTest,LearningTaskPersistenceTest test
```

---

## 任务六：实现三类任务的创建、查询与编辑

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/learningtask/application/LearningTaskManagementService.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/application/CreateLearningTaskCommand.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/application/UpdateLearningTaskCommand.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/application/LearningTaskPage.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/web/LearningTaskController.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/web/CreateLearningTaskRequest.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/web/UpdateLearningTaskRequest.java`
- 新增：`server/src/test/java/com/lingdong/learning/learningtask/application/LearningTaskManagementServiceTest.java`
- 新增：`server/src/test/java/com/lingdong/learning/learningtask/web/LearningTaskControllerTest.java`

### 步骤 1：编写失败测试

覆盖：

1. 家庭任务只能选择活动主关系学生，审核人固定当前家长；
2. 机构任务来源和目标必须在机构管理员授权组织子树内；
3. 教师任务来源必须是本人活动班级，目标必须在该班级内；
4. 机构显式指定教师审核人时，所有目标均属于该教师活动班级；
5. 多角色用户必须显式传来源类型，角色与来源不匹配拒绝；
6. 只允许编辑 `DRAFT`，已发布任务返回 `409 STATE_CONFLICT`；
7. 跨范围的任务详情和编辑统一返回 `404`；
8. 目录分页、固定排序和筛选正确。

### 步骤 2：实现管理服务

每个入口依次校验 `WEB` 客户端、功能开关、动态权限、来源角色和数据范围。创建和编辑在单事务中保存定义、目标和标签；编辑采用替换目标和标签方式，但必须先锁定并确认仍为草稿。

### 步骤 3：实现 HTTP 契约并回归

实现创建、分页列表、详情和编辑接口，所有标识字段使用字符串 DTO。运行：

```powershell
mvn -Dtest=LearningTaskManagementServiceTest,LearningTaskControllerTest test
```

---

## 任务七：实现目标展开与单任务发布事务

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/learningtask/domain/LearningTaskAssignment.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/application/LearningTaskPublishService.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/application/LearningTaskTargetExpansionService.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/application/PublishLearningTaskResult.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/infrastructure/persistence/LearningTaskAssignmentMapper.java`
- 新增：`server/src/main/resources/mapper/learningtask/LearningTaskAssignmentMapper.xml`
- 新增：`server/src/test/java/com/lingdong/learning/learningtask/application/LearningTaskTargetExpansionServiceTest.java`
- 新增：`server/src/test/java/com/lingdong/learning/learningtask/application/LearningTaskPublishServiceTest.java`

### 步骤 1：编写失败测试

覆盖家庭学生、组织子树、班级和指定学生目标展开；重叠目标按学生去重；停用学生和非活动关系排除；发布前重新校验来源、目标和审核人；零学生拒绝且不更新状态；重复和并发发布只有一次成功；任一实例插入失败时整单回滚。

### 步骤 2：实现发布事务

发布服务使用 `findByIdForUpdate` 锁定任务，重新执行权限和业务校验，展开并排序学生标识，批量生成 `PENDING_CLAIM` 实例，最后更新任务为 `PUBLISHED`。任务定义和实例均保留来源、审核人及发布时必要快照。

### 步骤 3：接入单任务发布接口并回归

```powershell
mvn -Dtest=LearningTaskTargetExpansionServiceTest,LearningTaskPublishServiceTest,LearningTaskControllerTest test
```

---

## 任务八：实现批量独立发布

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/learningtask/application/LearningTaskBatchPublishService.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/application/LearningTaskPublishTransaction.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/web/BatchPublishLearningTaskRequest.java`
- 新增：`server/src/test/java/com/lingdong/learning/learningtask/application/LearningTaskBatchPublishServiceTest.java`

### 步骤 1：编写失败测试

覆盖空列表、重复标识、超过 100 条、部分成功、失败原因中性化，以及中间任务失败不回滚先前成功任务。

### 步骤 2：实现独立事务边界

通过独立 Spring Bean 的 `REQUIRES_NEW` 方法逐项调用单任务发布，避免同类自调用导致事务注解失效。结果返回成功数、失败数及每个任务的成功实例数或中性失败原因，不返回其他用户任务是否存在。

### 步骤 3：实现接口并回归

```powershell
mvn -Dtest=LearningTaskBatchPublishServiceTest,LearningTaskControllerTest test
```

---

## 任务九：实现学生本人任务查询

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/learningtask/application/StudentTaskAssignmentQueryService.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/application/StudentTaskAssignmentPage.java`
- 新增：`server/src/main/java/com/lingdong/learning/learningtask/web/StudentTaskAssignmentController.java`
- 新增：`server/src/test/java/com/lingdong/learning/learningtask/application/StudentTaskAssignmentQueryServiceTest.java`
- 新增：`server/src/test/java/com/lingdong/learning/learningtask/web/StudentTaskAssignmentControllerTest.java`
- 修改：`server/src/main/java/com/lingdong/learning/auth/infrastructure/config/SecurityConfiguration.java`

### 步骤 1：编写失败测试

覆盖学生只读本人实例、来源和日期筛选、固定分页排序、详情敏感字段排除、其他学生实例返回 `404`、`WEB` 会话拒绝、非学生角色拒绝和功能关闭拒绝。

### 步骤 2：实现查询服务与接口

服务从 `AuthenticatedUser` 解析学生用户身份，不接受请求参数中的学生标识。响应仅返回任务展示快照、审核人显示名和学生本人状态。

### 步骤 3：运行学习任务后端测试集

```powershell
mvn -Dtest='*LearningTask*,*TaskAssignment*,*TeacherClass*,*StudentClass*' test
```

---

## 任务十：完成 Web 学习任务管理与班级配置

**文件：**

- 新增：`web/src/features/learning-tasks/api.ts`
- 新增：`web/src/features/learning-tasks/types.ts`
- 新增：`web/src/features/learning-tasks/LearningTaskManagementPage.tsx`
- 新增：`web/src/features/learning-tasks/LearningTaskEditorDrawer.tsx`
- 新增：`web/src/features/learning-tasks/BatchPublishResultModal.tsx`
- 新增：`web/src/features/learning-tasks/LearningTaskManagementPage.test.tsx`
- 新增：`web/src/features/organizations/StudentClassAssignmentDrawer.tsx`
- 新增：`web/src/features/organizations/TeacherClassAssignmentDrawer.tsx`
- 修改：`web/src/features/organizations/OrganizationManagementPage.tsx`
- 修改：`web/src/app/App.tsx`
- 修改：`web/src/app/App.test.tsx`

### 步骤 1：编写失败的组件测试

覆盖来源按角色显示、功能关闭隐藏菜单和路由阻断、筛选分页、创建编辑抽屉、目标联动、草稿发布确认、已发布只读、批量部分失败明细，以及班级配置控件只向具备权限的机构管理员显示。

### 步骤 2：实现 API、页面和路由

采用 Ant Design 紧凑表格、筛选栏、抽屉和确认弹窗。候选项只调用 V22 受控接口；标识保持字符串；不在浏览器计算权限范围、基础积分或发布实例集合。菜单和直达路由同时检查公开能力和当前权限。

### 步骤 3：执行 Web 测试和构建

```powershell
Set-Location web
npx vitest run --pool=forks --maxWorkers=1 --minWorkers=1
npm run build
```

---

## 任务十一：完成 uni-app 学生任务列表与详情

**文件：**

- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityController.java`
- 修改：`server/src/test/java/com/lingdong/learning/feature/web/PublicCapabilityControllerTest.java`
- 新增：`miniapp/src/api/learning-task.ts`
- 修改：`miniapp/src/api/types.ts`
- 修改：`miniapp/src/pages/student-home/index.vue`
- 新增：`miniapp/src/pages/task-list/index.vue`
- 新增：`miniapp/src/pages/task-detail/index.vue`
- 修改：`miniapp/src/pages.json`

### 步骤 1：先补能力接口失败测试与前端类型

公开能力响应新增 `learningTaskManagementEnabled`，关闭时小程序主页不展示入口，任务页直接访问显示不可用且不请求任务数据。

### 步骤 2：实现学生任务页面

主页增加任务入口；列表提供全部、家庭、机构、教师来源分段筛选，展示标题、来源、日期、难度、基础积分和待认领状态；详情只读，不出现认领、打卡或审核按钮。会话继续使用已有 `MINIAPP` 学生 Bearer 会话。

### 步骤 3：执行后端能力测试和小程序构建

```powershell
Set-Location server
mvn -Dtest=PublicCapabilityControllerTest test
Set-Location ..\miniapp
npm run type-check
npm run build:h5
npm run build:mp-weixin
```

---

## 任务十二：同步中文设计文档

**文件：**

- 修改：`docs/design/01-功能详细设计-FSD-V1.0.md`
- 修改：`docs/design/03-系统架构设计-HLD-V1.0.md`
- 修改：`docs/design/04-数据库设计-V1.0.md`
- 修改：`docs/design/05-Flyway迁移规范-V1.0.md`
- 修改：`docs/design/06-API接口设计-V1.0.md`
- 修改：`docs/design/07-权限与安全设计-V1.0.md`
- 修改：`docs/design/10-测试方案与验收用例-V1.0.md`
- 修改：`docs/design/12-当前实现一致性核对-V1.0.md`

### 步骤 1：按实际实现同步

记录 V22 数据模型、状态机、发布事务、数据权限、API、Web 与小程序边界、功能开关和验收结果。文档只描述已经实现和验证的行为；未实现内容明确列为后续范围，不写成已完成。

### 步骤 2：检查中文和占位符

```powershell
rg -n "TODO|TBD|待补充|待实现|示例占位" docs server web miniapp
git diff --check
```

预期：V22 文档无未说明占位符；既有换行提示可保留，但无新增空白错误。

---

## 任务十三：执行全量回归与本地视觉验收

### 步骤 1：后端全量回归

```powershell
Set-Location server
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn test
```

### 步骤 2：Web 和小程序全量验证

```powershell
Set-Location ..\web
npx vitest run --pool=forks --maxWorkers=1 --minWorkers=1
npm run build
Set-Location ..\miniapp
npm run type-check
npm run build:h5
npm run build:mp-weixin
```

### 步骤 3：安全和视觉检查

1. 静态扫描源代码与配置，确认不包含已知远程数据库、Redis、微信密钥或测试登录码；
2. 启动本地 H2 后端、Web 和 H5，仅使用本地测试数据；
3. 用 `1280×800` 验证 Web 任务列表、编辑抽屉、发布确认和班级配置；
4. 用 `390×844` 验证小程序入口、来源筛选、空态、列表和详情；
5. 检查功能关闭后的菜单隐藏、直达阻断和后端拒绝一致；
6. 停止临时服务并清理本轮临时日志，不删除用户文件。

### 步骤 4：最终一致性核对

汇总实际通过的测试数量、构建结果、视觉检查和未覆盖风险。只有全部必要验证通过后，才把 V22 标记为完成；不连接、不执行任何远程数据库迁移。
