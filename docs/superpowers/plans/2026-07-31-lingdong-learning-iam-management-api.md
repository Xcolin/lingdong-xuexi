# 灵动学习 V16 后台管理权限与 IAM 接口实施计划

> **执行要求**：逐项执行本计划，所有行为变更先写失败测试，再做最小实现；每项完成后更新复选框。

**目标：**为已认证的 Web 管理端提供受动态 RBAC 权限保护的用户、角色、权限和数据范围管理 API。

**架构：**V16 以固定 19 位雪花常量初始化 12 个 Web 操作权限，并授予 `SYS_ADMIN`。Spring MVC 拦截器按控制器方法上的 `@RequirePermission` 调用既有 `PermissionDecisionService`，让角色授权与用户级 `DENY` 在 HTTP 边界生效；控制器只负责协议，既有应用服务继续保留系统管理员等业务约束。

**技术栈：**Spring Boot 3、Spring MVC、Spring Security、MyBatis XML、Flyway、MySQL 8、H2、JUnit 5、AssertJ、MockMvc。

---

## 文件职责

| 文件 | 职责 |
|---|---|
| `server/src/main/resources/db/migration/V16__seed_iam_management_permissions.sql` | 初始化 12 个后台管理权限和内置系统管理员授权。 |
| `server/src/main/java/com/lingdong/learning/common/security/RequirePermission.java` | 声明控制器方法所需权限。 |
| `server/src/main/java/com/lingdong/learning/common/security/PermissionAuthorizationInterceptor.java` | 在进入控制器前调用权限决策服务并返回统一 403。 |
| `server/src/main/java/com/lingdong/learning/common/security/WebMvcSecurityConfiguration.java` | 注册权限拦截器。 |
| `server/src/main/java/com/lingdong/learning/common/web/*` | 资源不存在、系统级拒绝和统一 400/403/404/409 JSON 响应。 |
| `server/src/main/java/com/lingdong/learning/iam/application/IamQueryApplicationService.java` | 为控制器提供用户、角色、权限的查询用例。 |
| `server/src/main/java/com/lingdong/learning/iam/web/*` | 用户、角色、权限、数据范围的请求 DTO、响应 DTO 和 REST Controller。 |
| `server/src/main/java/com/lingdong/learning/**/infrastructure/persistence/*Mapper.java` 与 XML | 新增无 SQL 拼接的目录查询。 |
| `server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java` | 验证 V16 权限种子和角色授权。 |
| `server/src/test/java/com/lingdong/learning/iam/web/IamManagementControllerTest.java` | 验证权限拦截、动态角色、用户级拒绝和受控写操作。 |
| `docs/design/03-系统架构设计-HLD-V1.0.md` 至 `docs/design/12-当前实现一致性核对-V1.0.md` | 回填 V16、接口、错误契约与当前实现边界。 |

## 任务 1：用迁移测试固定后台管理权限目录

- [x] **步骤 1：先写 V16 失败测试**

在 `server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java` 增加测试，连接测试数据库后断言以下条件：`sys_permission` 中有 12 条 `client_type='WEB'`、`resource_type='OPERATION'` 的 `IAM_` 权限；`SYS_ADMIN` 角色通过 `sys_role_permission` 拥有全部 12 条；权限主键和角色权限关联主键均不是 identity。

```java
assertThat(jdbcTemplate.queryForObject(
        "select count(*) from sys_permission where permission_code like 'IAM_%'", Integer.class
)).isEqualTo(12);
```

- [x] **步骤 2：运行失败测试**

执行：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
Set-Location server
mvn test '-Dtest=FlywayMigrationTest'
```

预期：失败，因为 V16 迁移和 `IAM_` 权限尚不存在。

- [x] **步骤 3：新增 V16 种子迁移**

新建 `server/src/main/resources/db/migration/V16__seed_iam_management_permissions.sql`。使用 `1874244142494646279` 至 `1874244142494646290` 创建下列权限：

```sql
('IAM_USER_READ', '查询用户'),
('IAM_USER_CREATE', '创建用户'),
('IAM_USER_ORGANIZATION_ASSIGN', '关联用户组织'),
('IAM_USER_ROLE_ASSIGN', '授予用户角色'),
('IAM_USER_PASSWORD_SET', '设置用户密码'),
('IAM_ROLE_READ', '查询角色'),
('IAM_ROLE_CREATE', '创建角色'),
('IAM_PERMISSION_READ', '查询权限'),
('IAM_PERMISSION_CREATE', '创建权限'),
('IAM_ROLE_PERMISSION_GRANT', '授予角色权限'),
('IAM_USER_PERMISSION_CONFIGURE', '配置用户权限'),
('IAM_DATA_SCOPE_CONFIGURE', '配置数据范围')
```

每条记录的 `resource_type` 为 `OPERATION`、`client_type` 为 `WEB`、`status` 为 `ENABLED`，并以 `1874244142494646300` 至 `1874244142494646311` 建立到内置 `SYS_ADMIN` 角色 `1874244142494646273` 的 `sys_role_permission` 关联。

- [x] **步骤 4：验证迁移测试通过**

重新执行步骤 2 的命令。预期：Flyway 可从 V1 顺序迁移至 V16，新增测试通过。

## 任务 2：建立通用路由权限与错误响应边界

- [x] **步骤 1：先写权限拦截失败测试**

创建 `server/src/test/java/com/lingdong/learning/iam/web/IamManagementControllerTest.java`。准备一个拥有 `SYS_ADMIN` 的平台账号和一个没有 `IAM_USER_READ` 的平台账号；登录后分别请求尚未实现的 `GET /api/v1/users/{id}`。测试期望前者最终可访问，后者得到 HTTP 403、`ACCESS_DENIED` 和非空 `traceId`。

```java
mockMvc.perform(get("/api/v1/users/{id}", targetUser.id())
        .header("Authorization", "Bearer " + unauthorizedAccessToken))
    .andExpect(status().isForbidden())
    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
```

- [x] **步骤 2：运行失败测试**

执行：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
Set-Location server
mvn test '-Dtest=IamManagementControllerTest'
```

预期：失败，因为控制器、`@RequirePermission` 和 MVC 拦截器尚不存在。

- [x] **步骤 3：实现权限注解与拦截器**

创建 `RequirePermission`，仅可标注方法：

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String value();
}
```

创建 `PermissionAuthorizationInterceptor`：仅处理带注解的 `HandlerMethod`；从 `SecurityContextHolder` 取得 `AuthenticatedUser`，调用 `PermissionDecisionService.isAllowed(userId, permissionCode)`。无当前身份或不允许时使用既有 `SecurityErrorResponseWriter` 写入 401 或 403 并返回 `false`，允许时返回 `true`。创建 `WebMvcSecurityConfiguration`，通过 `WebMvcConfigurer#addInterceptors` 注册该拦截器。

- [x] **步骤 4：实现统一业务异常类型与映射**

创建 `ResourceNotFoundException extends IllegalArgumentException` 和 `SystemOperationAccessDeniedException extends IllegalStateException`。创建 `ApiExceptionHandler`，优先映射前者为 404/`RESOURCE_NOT_FOUND`、后者和 `AccessDeniedException` 为 403/`ACCESS_DENIED`、`IllegalArgumentException` 与 Bean Validation 异常为 400/`VALIDATION_ERROR`、其他 `IllegalStateException` 为 409/`STATE_CONFLICT`。响应复用安全错误响应的 `code`、`message`、`traceId` 结构与 `X-Request-Id`。

- [x] **步骤 5：收紧既有系统级服务异常语义**

修改 `AuthenticationApplicationService`、`PermissionAdministrationService`、`DataScopeAdministrationService` 的“仅系统管理员”分支，使其抛出 `SystemOperationAccessDeniedException`；修改用户、角色、权限、组织查询不到的分支，使其抛出 `ResourceNotFoundException`。新异常保持对既有 `IllegalArgumentException` 或 `IllegalStateException` 的继承，避免破坏现有领域测试的类型预期。

- [x] **步骤 6：验证权限拦截测试通过**

重新执行步骤 2 的命令。预期：未授权用户在控制器前被拒绝，错误响应不暴露内部异常。

## 任务 3：实现 IAM 查询与用户管理 API

- [x] **步骤 1：先扩展失败测试覆盖用户管理流程**

在 `IamManagementControllerTest` 中增加系统管理员流程：创建平台用户、查询该用户、关联已启用组织、为已关联组织授予角色、设置密码后使用新密码登录。断言所有响应 ID 为 JSON 字符串，用户响应不含 `passwordHash`。

- [x] **步骤 2：运行失败测试**

执行 `mvn test '-Dtest=IamManagementControllerTest'`。预期：失败，因为用户管理 Controller、DTO 与查询应用服务尚不存在。

- [x] **步骤 3：补齐查询 Mapper 与应用服务**

在 `UserMapper` 保留既有 `findById`，在 `RoleMapper` 和 `PermissionMapper` 增加 `List<Role> findAll()`、`List<Permission> findAll()`，并在对应 MyBatis XML 中按编码排序查询。创建 `IamQueryApplicationService`，对 `findUser`、`listRoles`、`listPermissions` 提供应用服务边界；`findUser` 未找到时抛出 `ResourceNotFoundException`。

- [x] **步骤 4：创建用户管理 Controller 与 DTO**

创建 `UserManagementController`，每个方法使用如下权限注解并从 `@AuthenticationPrincipal AuthenticatedUser` 获取操作者：

```java
@RequirePermission("IAM_USER_CREATE")
@PostMapping("/api/v1/users")
public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
    return UserResponse.from(userAccessApplicationService.createUser(
            new CreateUserCommand(request.username(), request.displayName(), request.mobile(), request.type())
    ));
}
```

同时实现 `GET /api/v1/users/{id}`、`POST /api/v1/users/{id}/organizations`、`POST /api/v1/users/{id}/roles`、`POST /api/v1/users/{id}/password`。密码接口调用 `setPlatformUserPassword(new SetPlatformUserPasswordCommand(currentUser.userId(), id, password))`，不得接受请求体操作者标识。所有响应 DTO 的 `Long id` 使用 `ToStringSerializer`。

- [x] **步骤 5：验证用户管理流程通过**

重新执行 `mvn test '-Dtest=IamManagementControllerTest'`。预期：系统管理员流程通过，组织范围角色在缺少用户组织关联时返回 `STATE_CONFLICT`。

## 任务 4：实现角色、权限和数据范围管理 API

- [x] **步骤 1：先扩展失败测试覆盖动态角色**

在 `IamManagementControllerTest` 中增加场景：系统管理员创建 `OPS_USER_MANAGER` 自定义角色，将 `IAM_USER_CREATE` 授予它，向运维账号授予该角色；运维账号可创建用户，但访问用户角色授予接口得到 403。再以系统管理员为同一运维账号配置 `DENY IAM_USER_CREATE`，确认创建用户也变为 403。

- [x] **步骤 2：运行失败测试**

执行 `mvn test '-Dtest=IamManagementControllerTest'`。预期：失败，因为角色、权限和数据范围 Controller 尚不存在。

- [x] **步骤 3：创建角色管理 Controller 与 DTO**

创建 `RoleManagementController`：`GET /api/v1/roles` 使用 `IAM_ROLE_READ`，`POST /api/v1/roles` 使用 `IAM_ROLE_CREATE` 并调用 `RoleApplicationService.createCustomRole`，`POST /api/v1/roles/{roleId}/data-scopes` 使用 `IAM_DATA_SCOPE_CONFIGURE` 并调用 `DataScopeAdministrationService.configureRoleCustomScope(currentUser.userId(), roleId, organizationId)`。角色响应只返回角色基础字段与字符串 ID。

- [x] **步骤 4：创建权限与数据范围 Controller 和 DTO**

创建 `PermissionManagementController`：`GET /api/v1/permissions` 使用 `IAM_PERMISSION_READ`；`POST /api/v1/permissions` 使用 `IAM_PERMISSION_CREATE`；`POST /api/v1/roles/{roleId}/permissions` 使用 `IAM_ROLE_PERMISSION_GRANT`；`PUT /api/v1/users/{userId}/permissions/{permissionId}` 使用 `IAM_USER_PERMISSION_CONFIGURE`。所有写操作都从当前身份写入命令中的 `operatorId`。

创建 `DataScopeManagementController`：`POST /api/v1/organization-admins` 使用 `IAM_DATA_SCOPE_CONFIGURE` 并调用 `configureOrganizationAdministrator(currentUser.userId(), userId, organizationId)`。

- [x] **步骤 5：验证动态权限场景通过**

重新执行 `mvn test '-Dtest=IamManagementControllerTest'`。预期：自定义运维角色仅能执行被授予的操作，用户级 `DENY` 覆盖角色允许，系统级权限目录与数据范围操作仍受既有系统管理员约束。

## 任务 5：同步中文设计文档与完成回归

- [x] **步骤 1：更新正式中文设计文档**

更新 `docs/design/03-系统架构设计-HLD-V1.0.md`、`04-数据库设计-V1.0.md`、`05-Flyway迁移规范-V1.0.md`、`06-API接口设计-V1.0.md`、`07-权限与安全设计-V1.0.md`、`12-当前实现一致性核对-V1.0.md`：写明 V16、12 个权限种子、`@RequirePermission`、已开放 IAM 接口、统一业务错误映射和未实现边界。V16 后下一版本为 V17。

- [x] **步骤 2：执行静态检查**

执行：

```powershell
git diff --check
if (rg -n 'AUTO_INCREMENT|PRIMARY KEY \([^)]*_id' server/src/main/resources/db/migration) { exit 1 }
```

预期：无空白错误，迁移脚本没有自增主键或外键主键。

- [x] **步骤 3：执行完整回归**

执行：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
Set-Location server
mvn test
```

预期：所有测试通过。记录真实测试数量与本地 H2/Flyway 验证范围，不将结果表述为共享测试、预生产或生产 MySQL 验证。
