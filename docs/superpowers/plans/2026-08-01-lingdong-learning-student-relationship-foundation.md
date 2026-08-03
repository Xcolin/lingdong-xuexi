# 灵动学习学生与家校关系基础实施计划

> **执行要求**：按任务顺序实施。所有行为变更先增加失败测试，再做最小实现；每完成一个步骤立即更新复选框。

**目标：**建立学生档案、主家长关系、机构关联和受关系范围约束的学生查询 API，不实现登录码、邀请、转班或前端录入页面。

**架构：**在独立 `student` 模块中定义学生与关系领域对象、应用服务和 MyBatis 持久化边界；Controller 从 Bearer 身份取得操作者，不接受请求体中的家长或机构管理员标识。动态 RBAC 先决定操作权限，应用服务再以亲子关系、组织管理员关系执行 SQL 范围校验。

**技术栈：**Spring Boot 3、Java 17、MyBatis XML、Flyway、H2 MySQL 兼容测试、既有 Snowflake ID 与 Spring Security Bearer 认证。

---

## 文件职责

| 文件 | 职责 |
|---|---|
| `server/src/main/resources/application-local.yml` | 被忽略的本机 MySQL/Redis profile；不放入版本控制，不主动启用或连接。 |
| `server/src/main/resources/db/migration/V19__create_student_relationship_foundation.sql` | 新增学生、亲子、机构关系表及学生操作权限种子。 |
| `server/src/main/java/com/lingdong/learning/student/domain/*` | 学生与关系的不可变领域对象及枚举。 |
| `server/src/main/java/com/lingdong/learning/student/infrastructure/persistence/*` | 学生与关系的 MyBatis Mapper 边界。 |
| `server/src/main/resources/mapper/student/*Mapper.xml` | 参数化写入、范围过滤、稳定分页 SQL。 |
| `server/src/main/java/com/lingdong/learning/student/application/*` | 家庭/机构创建分流、对象范围校验和学生查询。 |
| `server/src/main/java/com/lingdong/learning/student/web/*` | 受 RBAC 保护的学生 HTTP 请求、响应与 Controller。 |
| `server/src/test/java/com/lingdong/learning/student/web/StudentManagementControllerTest.java` | 家庭、机构、权限、范围和脱敏边界的集成测试。 |
| `docs/design/04-数据库设计-V1.0.md` 等 | 同步 V19、API、安全和当前实现记录。 |

## 任务 1：建立本机配置与失败用例

- [x] **步骤 1：创建被忽略的本机连接 profile**

创建 `server/src/main/resources/application-local.yml`，仅覆盖 `spring.datasource` 和 `spring.data.redis`。使用已提供的本机凭证和现有 `lingdong_learning` 数据库名；该文件已被根目录 `.gitignore` 忽略。不得将凭证复制到 `application.yml`、Flyway 脚本、测试资源、前端环境文件或设计文档。

- [x] **步骤 2：为学生创建、范围读取和权限拒绝写失败集成测试**

创建 `StudentManagementControllerTest`，准备 `PARENT`、`ORG_ADMIN`、普通用户和 `SYS_ADMIN`：

```java
mockMvc.perform(post("/api/v1/students")
        .header("Authorization", "Bearer " + parentAccessToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"studentName\":\"家庭学生\",\"gradeCode\":\"G3\"}"))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.id").isString());

mockMvc.perform(get("/api/v1/students")
        .header("Authorization", "Bearer " + ordinaryAccessToken))
    .andExpect(status().isForbidden())
    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
```

增加机构管理员向其已配置管理员关系的启用组织创建学生、父/机构跨范围读取返回 404、系统管理员读取全量目录的断言。

- [x] **步骤 3：运行失败用例**

执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn test '-Dtest=StudentManagementControllerTest'
```

预期：学生路由不存在，测试失败；不添加 `local` profile，不连接远程数据库。

## 任务 2：新增 V19 表结构与权限种子

- [x] **步骤 1：创建 V19 Flyway 迁移**

创建 `V19__create_student_relationship_foundation.sql`：

```sql
CREATE TABLE edu_student (
    id BIGINT NOT NULL PRIMARY KEY,
    student_name VARCHAR(64) NOT NULL,
    grade_code VARCHAR(64),
    student_user_id BIGINT,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_edu_student_user UNIQUE (student_user_id),
    CONSTRAINT fk_edu_student_user FOREIGN KEY (student_user_id) REFERENCES sys_user (id)
);
```

继续创建 `edu_parent_student` 和 `edu_student_organization`，二者均使用雪花主键、外键、活动关系查询索引和数据库唯一约束；V19 只写入主家长关系，使用非空 `primary_scope_key='PRIMARY'` 与 `(student_id, primary_scope_key)` 唯一约束保证唯一。副家长及关系变更属于后续迁移，当前不写入其他关系。

插入 `STUDENT_CREATE`、`STUDENT_READ` 两个 `WEB`、`OPERATION` 权限；创建权限授予 `PARENT`、`ORG_ADMIN`，读取权限授予 `SYS_ADMIN`、`PARENT`、`ORG_ADMIN`。为全部 `sys_permission`、`sys_role_permission` 行使用 V18 后连续的预生成 19 位常量。

- [x] **步骤 2：扩展 Flyway 迁移断言**

更新 `FlywayMigrationTest`，断言 V1-V19 可应用、两项学生权限和其三类内置角色授权存在，并查询三张新表的 `id` 为唯一 `BIGINT` 主键。更新既有 IAM 权限总数断言。

- [x] **步骤 3：运行 Flyway 测试确认通过**

执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn test '-Dtest=FlywayMigrationTest'
```

预期：仅本地 H2 从 V1 迁移至 V19，全部断言通过。

## 任务 3：实现学生领域、关系持久化和范围服务

- [x] **步骤 1：定义领域对象和命令**

创建 `Student`、`StudentStatus`、`ParentStudentRelation`、`ParentRelationRole`、`StudentOrganizationRelation`、`StudentOrganizationRelationType`、`CreateStudentCommand`、`StudentDirectoryQuery`、`StudentDirectoryPage`。`CreateStudentCommand` 只包含 `studentName`、`gradeCode`、`organizationId`；不接收家长标识、关系角色、状态或学生账号标识。

- [x] **步骤 2：定义 MyBatis Mapper 与 XML**

创建 `StudentMapper`、`ParentStudentMapper`、`StudentOrganizationMapper` 和相应 XML。目录 SQL 以 `EXISTS` 组合当前用户的 `ACTIVE` 亲子关系与其直接管理组织的 `ACTIVE` 学生机构关系；系统管理员参数为真时不附加范围条件。SQL 固定 `created_at DESC, id DESC`，参数使用 `#{}` 绑定，不拼接用户输入。

- [x] **步骤 3：实现事务创建和读取边界**

创建 `StudentApplicationService`：

```java
@Transactional
public Student createStudent(AuthenticatedUser currentUser, CreateStudentCommand command) {
    if (command.organizationId() == null) {
        requireRole(currentUser, "PARENT");
        Student student = insertStudent(command);
        parentStudentMapper.insertPrimary(idGenerator.nextId(), currentUser.userId(), student.id());
        return student;
    }
    requireRole(currentUser, "ORG_ADMIN");
    requireEnabledOrganization(command.organizationId());
    requireOrganizationAdmin(currentUser.userId(), command.organizationId());
    Student student = insertStudent(command);
    studentOrganizationMapper.insertEnrollment(idGenerator.nextId(), student.id(), command.organizationId());
    return student;
}
```

`findStudent` 与 `listStudents` 对 `SYS_ADMIN`、`PARENT`、`ORG_ADMIN` 计算范围；无可见关系时抛出 `ResourceNotFoundException`。不在内存中聚合、过滤或去重数据库全量学生。

- [x] **步骤 4：运行服务与控制器测试确认通过**

执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn test '-Dtest=StudentManagementControllerTest,FlywayMigrationTest'
```

预期：家庭/机构创建、关系落库、范围读取、404 隐藏和 403 权限拒绝通过。

## 任务 4：发布 HTTP 契约并同步中文文档

- [x] **步骤 1：实现学生 Controller、请求和响应 DTO**

创建 `StudentManagementController`，公开 `POST /api/v1/students`、`GET /api/v1/students`、`GET /api/v1/students/{id}`，并分别标注 `@RequirePermission("STUDENT_CREATE")` 和 `@RequirePermission("STUDENT_READ")`。请求使用 `@Valid`，学生和分页响应的全部标识均转换为 JSON 字符串。

- [x] **步骤 2：更新设计与一致性文档**

更新 `04-数据库设计-V1.0.md`、`05-Flyway迁移规范-V1.0.md`、`06-API接口设计-V1.0.md`、`07-权限与安全设计-V1.0.md`、`10-测试方案与验收用例-V1.0.md`、`12-当前实现一致性核对-V1.0.md`。记录 V19 表结构和权限种子、两条创建路径、范围 SQL、未实现的登录码/邀请/解绑/转学/小程序入口，以及仅本地 H2 验证的事实。

- [x] **步骤 3：执行最终本地回归**

执行 `git diff --check`、带 JDK 17 临时环境变量的 `mvn test`、Web `npm.cmd test` 和 `npm.cmd run build`、小程序 `npm.cmd run type-check` 与 `npm.cmd run build:mp-weixin`。不启用 `local` profile，不执行任何远程数据库或 Redis 连接、迁移、清理或写入。
