# 灵动伴随 V40 机构管理员小程序认证与工作台基础实施计划

> **执行说明：** 使用 `superpowers:executing-plans` 按任务顺序逐项实施，并用复选框（`- [ ]`）记录状态。

**目标：** 为具有启用 `ORG_ADMIN` 角色和启用管理组织的机构账号提供独立、安全、可即时失效的 uni-app 密码认证与基础工作台。

**架构：** 机构小程序登录使用专用公开端点，但复用 `AuthenticationApplicationService.createSession`、`auth_device_session` 和 V39 安全事件。机构管理员有效性由用户类型、启用角色、直接管理的启用组织和 `ORGANIZATION_MINIAPP_AUTH` 功能开关共同决定，并在登录、访问令牌认证和刷新时持续校验；工作台只返回当前用户直接管理的启用组织摘要。

**技术栈：** Spring Boot 3、JDK 17、Maven 4、MyBatis XML、Flyway、H2/MySQL 兼容 SQL、React/Ant Design Pro、uni-app/Vue 3/TypeScript。

---

## 文件结构

### 后端新增文件

- `server/src/main/resources/db/migration/V40__add_organization_miniapp_auth.sql`：仅登记机构小程序认证功能开关。
- `server/src/main/java/com/lingdong/learning/auth/application/OrganizationPasswordLoginCommand.java`：机构小程序密码登录命令。
- `server/src/main/java/com/lingdong/learning/auth/web/OrganizationPasswordLoginRequest.java`：专用登录请求及字段校验。
- `server/src/main/java/com/lingdong/learning/organization/application/OrganizationWorkbenchContext.java`：工作台用户和组织摘要。
- `server/src/main/java/com/lingdong/learning/organization/application/OrganizationWorkbenchOrganization.java`：直接管理组织摘要。
- `server/src/main/java/com/lingdong/learning/organization/application/OrganizationWorkbenchQueryService.java`：当前机构管理员工作台查询边界。
- `server/src/main/java/com/lingdong/learning/organization/infrastructure/persistence/OrganizationAdminSummaryRow.java`：MyBatis 查询行。
- `server/src/main/java/com/lingdong/learning/organization/web/OrganizationWorkbenchController.java`：机构小程序工作台接口。
- `server/src/main/java/com/lingdong/learning/organization/web/OrganizationWorkbenchContextResponse.java`：字符串标识响应。
- `server/src/main/java/com/lingdong/learning/organization/web/OrganizationWorkbenchOrganizationResponse.java`：组织摘要响应。
- `server/src/test/java/com/lingdong/learning/auth/application/OrganizationMiniappAuthenticationTest.java`：登录和持续失效集成测试。
- `server/src/test/java/com/lingdong/learning/organization/web/OrganizationWorkbenchControllerTest.java`：工作台认证、隔离和响应测试。

### 小程序新增文件

- `miniapp/src/session/organization-session.ts`：机构会话和设备标识独立存储。
- `miniapp/src/api/organization-workbench.ts`：机构工作台认证请求。
- `miniapp/src/pages/organization-login/organization-login.vue`：机构账号密码登录。
- `miniapp/src/pages/organization-home/organization-home.vue`：机构管理员基础工作台。

### 现有文件修改边界

- 认证服务只增加机构专用登录和机构 MINIAPP 持续校验，不改变 Web 登录准入。
- `OrganizationAdminMapper` 只增加启用组织判定和摘要查询，不扩大组织树权限。
- Web 只同步公开能力类型，不新增页面。
- uni-app 首页、账号安全页和路由只接入第三种独立身份，不改变家长、学生流程。

## 任务 1：V40 Flyway 迁移和全局约束

**文件：**
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`
- 新增：`server/src/main/resources/db/migration/V40__add_organization_miniapp_auth.sql`

- [x] **步骤 1：先写迁移失败断言**

在 `FlywayMigrationTest` 增加 V40 断言，必须检查迁移版本、全局启用、19 位雪花标识、表数量不变和无自增：

```java
Integer migrationCount = jdbcTemplate.queryForObject("""
        select count(*) from flyway_schema_history
        where version = '40' and success = true
        """, Integer.class);
Integer featureCount = jdbcTemplate.queryForObject("""
        select count(*) from sys_feature_toggle
        where feature_code = 'ORGANIZATION_MINIAPP_AUTH'
          and scope_type = 'GLOBAL'
          and scope_key = 'GLOBAL'
          and status = 'ENABLED'
          and built_in = 1
          and id >= 1000000000000000000
        """, Integer.class);

assertThat(migrationCount).isEqualTo(1);
assertThat(featureCount).isEqualTo(1);
```

- [x] **步骤 2：执行测试确认红灯**

运行：

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; mvn -q -Dtest=FlywayMigrationTest test
```

预期：失败，原因是 V40 尚不存在或 `ORGANIZATION_MINIAPP_AUTH` 数据缺失。

- [x] **步骤 3：新增最小 V40 迁移**

```sql
-- V40：机构管理员小程序认证与工作台基础。
-- 基础数据标识使用 19 位雪花数字，不使用数据库自增列。
INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES (
    1874244142494646548, 'ORGANIZATION_MINIAPP_AUTH', '机构管理员小程序认证',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '控制机构管理员小程序登录入口、会话认证和工作台访问。'
);
```

- [x] **步骤 4：重新执行迁移测试确认绿灯**

运行同一步骤 2，预期 `FlywayMigrationTest` 全部通过，V1-V40 连续执行，仍为 67 张显式非自增 `BIGINT id` 主键表。

## 任务 2：机构管理员认证判定和专用登录

**文件：**
- 新增：`server/src/main/java/com/lingdong/learning/auth/application/OrganizationPasswordLoginCommand.java`
- 修改：`server/src/main/java/com/lingdong/learning/auth/application/AuthenticationApplicationService.java`
- 修改：`server/src/main/java/com/lingdong/learning/datascope/infrastructure/persistence/OrganizationAdminMapper.java`
- 修改：`server/src/main/resources/mapper/datascope/OrganizationAdminMapper.xml`
- 新增测试：`server/src/test/java/com/lingdong/learning/auth/application/OrganizationMiniappAuthenticationTest.java`

- [x] **步骤 1：写登录成功和统一失败测试**

测试数据显式创建：启用机构账号、启用 `ORG_ADMIN` 角色、启用学校和 `sys_organization_admin` 关系。核心调用和断言：

```java
AuthenticatedSession session = authenticationApplicationService.loginOrganizationByPassword(
        new OrganizationPasswordLoginCommand(
                "school_admin", "ValidPass123!", "organization-device", "机构管理员手机"));

assertThat(session.sessionId()).isPositive();
assertThat(deviceSessionMapper.findById(session.sessionId()).clientType())
        .isEqualTo(AuthClientType.MINIAPP);
```

使用参数化用例验证以下情况全部抛出 `AuthenticationFailedException`，不暴露具体原因：错误密码、`PLATFORM`、`FAMILY`、`STUDENT`、机构用户无 `ORG_ADMIN`、角色停用、无管理组织、只管理停用组织、用户停用。

- [x] **步骤 2：运行目标测试确认红灯**

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; mvn -q -Dtest=OrganizationMiniappAuthenticationTest test
```

预期：编译失败，缺少 `OrganizationPasswordLoginCommand` 和 `loginOrganizationByPassword`。

- [x] **步骤 3：增加最小命令和 Mapper 判定**

```java
/** 机构管理员小程序账号密码登录命令。 */
public record OrganizationPasswordLoginCommand(
        String username,
        String password,
        String deviceId,
        String deviceName
) { }
```

`OrganizationAdminMapper` 增加：

```java
boolean existsEnabledManagedOrganization(@Param("userId") Long userId);
```

XML 使用真实启用组织关系：

```xml
<select id="existsEnabledManagedOrganization" resultType="boolean">
    SELECT EXISTS(
        SELECT 1
        FROM sys_organization_admin organization_admin
        JOIN sys_organization organization
          ON organization.id = organization_admin.organization_id
        WHERE organization_admin.user_id = #{userId}
          AND organization.status = 'ENABLED'
    )
</select>
```

- [x] **步骤 4：实现专用登录并复用统一会话创建**

认证服务增加常量和依赖：

```java
private static final String ORGANIZATION_ADMIN_ROLE = "ORG_ADMIN";
private static final String ORGANIZATION_MINIAPP_AUTH_FEATURE = "ORGANIZATION_MINIAPP_AUTH";
private final OrganizationAdminMapper organizationAdminMapper;
```

登录只签发 `MINIAPP` 会话：

```java
@Transactional
public AuthenticatedSession loginOrganizationByPassword(OrganizationPasswordLoginCommand command) {
    Objects.requireNonNull(command, "机构管理员登录请求不能为空");
    featureAccessService.requireEnabled(ORGANIZATION_MINIAPP_AUTH_FEATURE, null);
    String username = requiredText(command.username(), "用户账号", 64);
    String deviceId = requiredText(command.deviceId(), "设备标识", 128);
    String deviceName = requiredText(command.deviceName(), "设备名称", 100);
    User user = userMapper.findByUsername(username);
    if (!isEnabledOrganizationAdministrator(user)
            || !matchesPassword(user, command.password())) {
        throw authenticationFailed();
    }
    return createSession(user.id(), AuthClientType.MINIAPP, deviceId, deviceName);
}

private boolean isEnabledOrganizationAdministrator(User user) {
    return user != null
            && user.status() == UserStatus.ENABLED
            && user.type() == UserType.ORGANIZATION
            && userRoleMapper.hasRoleCode(user.id(), ORGANIZATION_ADMIN_ROLE)
            && organizationAdminMapper.existsEnabledManagedOrganization(user.id());
}

private boolean matchesPassword(User user, String password) {
    if (user == null || user.passwordHash() == null || password == null) {
        return false;
    }
    try {
        return passwordEncoder.matches(password, user.passwordHash());
    } catch (IllegalArgumentException exception) {
        return false;
    }
}
```

密码匹配封装捕获非法哈希，失败仍统一返回认证失败；不得写日志。

- [x] **步骤 5：运行目标测试确认绿灯**

运行同一步骤 2，预期所有机构管理员登录正反用例通过，并确认 V39 首次设备安全事件由统一 `createSession` 自动产生。

## 任务 3：机构 MINIAPP 会话持续校验

**文件：**
- 修改：`server/src/main/java/com/lingdong/learning/auth/application/AuthenticationApplicationService.java`
- 修改测试：`server/src/test/java/com/lingdong/learning/auth/application/OrganizationMiniappAuthenticationTest.java`

- [x] **步骤 1：写旧会话即时失效测试**

每个用例先登录，再执行一种状态变化：关闭功能、移除 `ORG_ADMIN`、停用角色、删除全部 `sys_organization_admin`、停用全部受管组织、停用用户。随后分别验证：

```java
assertThatThrownBy(() -> authenticationApplicationService.authenticateAccessToken(session.accessToken()))
        .isInstanceOf(AuthenticationFailedException.class);
assertThatThrownBy(() -> authenticationApplicationService.refreshSession(
        new RefreshSessionCommand(session.refreshToken())))
        .isInstanceOf(AuthenticationFailedException.class);
```

另写回归用例：同一机构用户的 `WEB` 会话在关闭机构小程序开关后仍可认证；家长和学生 `MINIAPP` 会话不受此开关影响。

- [x] **步骤 2：执行测试确认红灯**

运行任务 2 的目标命令，预期旧机构 MINIAPP 令牌仍可认证或刷新而失败。

- [x] **步骤 3：按用户类型收紧 `isUsableForClient`**

```java
if (clientType == AuthClientType.MINIAPP) {
    if (user.type() == UserType.ORGANIZATION) {
        return featureAccessService.isEnabled(ORGANIZATION_MINIAPP_AUTH_FEATURE, null)
                && isEnabledOrganizationAdministrator(user);
    }
    if (user.type() == UserType.FAMILY) {
        return isEnabledParent(user);
    }
    if (user.type() == UserType.STUDENT) {
        Student student = studentMapper.findByStudentUserId(user.id());
        return student != null && student.status() == StudentStatus.ENABLED;
    }
}
```

WEB 分支保持当前平台、机构和已启用家长规则，不读取 `ORGANIZATION_MINIAPP_AUTH`。

- [x] **步骤 4：重新执行认证测试确认绿灯**

运行：

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; mvn -q -Dtest=OrganizationMiniappAuthenticationTest,AuthenticationApplicationServiceTest,ParentPhoneAuthenticationServiceTest,StudentCodeLoginApplicationServiceTest test
```

预期：机构会话即时失效和既有 Web/家长/学生回归全部通过。

## 任务 4：专用登录 HTTP 接口和公开能力

**文件：**
- 新增：`server/src/main/java/com/lingdong/learning/auth/web/OrganizationPasswordLoginRequest.java`
- 修改：`server/src/main/java/com/lingdong/learning/auth/web/AuthenticationController.java`
- 修改：`server/src/main/java/com/lingdong/learning/common/security/SecurityConfiguration.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityController.java`
- 修改测试：`server/src/test/java/com/lingdong/learning/auth/web/AuthenticationControllerTest.java`
- 修改测试：`server/src/test/java/com/lingdong/learning/auth/web/StudentAuthenticationControllerTest.java`

- [x] **步骤 1：先写 MockMvc 失败测试**

```java
mockMvc.perform(post("/api/v1/auth/organization-sessions/password")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
                {"username":"school_admin","password":"ValidPass123!",
                 "deviceId":"organization-device","deviceName":"机构管理员手机"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").isString())
        .andExpect(jsonPath("$.accessToken").isNotEmpty());

mockMvc.perform(get("/api/v1/public/capabilities").param("client", "MINIAPP"))
        .andExpect(jsonPath("$.organizationMiniappAuthEnabled").value(true));
mockMvc.perform(get("/api/v1/public/capabilities").param("client", "WEB"))
        .andExpect(jsonPath("$.organizationMiniappAuthEnabled").value(false));
```

同时覆盖字段空白/超长返回 400、功能关闭返回 `FEATURE_DISABLED`、不携带认证也可调用登录端点。

- [x] **步骤 2：运行控制器测试确认红灯**

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; mvn -q -Dtest=AuthenticationControllerTest,StudentAuthenticationControllerTest test
```

预期：专用路由 401/404，能力字段不存在。

- [x] **步骤 3：新增请求、路由和安全白名单**

```java
/** 机构管理员小程序密码登录请求。 */
public record OrganizationPasswordLoginRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 64) String password,
        @NotBlank @Size(max = 128) String deviceId,
        @NotBlank @Size(max = 100) String deviceName
) { }
```

```java
@PostMapping("/organization-sessions/password")
public SessionResponse loginOrganizationByPassword(
        @Valid @RequestBody OrganizationPasswordLoginRequest request
) {
    return toSessionResponse(authenticationApplicationService.loginOrganizationByPassword(
            new OrganizationPasswordLoginCommand(
                    request.username(), request.password(), request.deviceId(), request.deviceName())));
}
```

将精确路径 `/api/v1/auth/organization-sessions/password` 加入 `permitAll()`，不放宽其他机构接口。

- [x] **步骤 4：增加客户端限定的能力字段**

`PublicCapabilityResponse` 在账号安全字段前增加：

```java
boolean organizationMiniappAuthEnabled,
```

控制器值固定为：

```java
MINIAPP_CLIENT.equals(client)
        && featureAccessService.isEnabled("ORGANIZATION_MINIAPP_AUTH", null)
```

- [x] **步骤 5：重新执行控制器测试确认绿灯**

运行同一步骤 2，预期专用公开端点、字符串会话标识、请求校验和 WEB/MINIAPP 能力差异全部通过。

## 任务 5：机构工作台最小上下文 API

**文件：**
- 修改：`server/src/main/java/com/lingdong/learning/datascope/infrastructure/persistence/OrganizationAdminMapper.java`
- 修改：`server/src/main/resources/mapper/datascope/OrganizationAdminMapper.xml`
- 新增：`server/src/main/java/com/lingdong/learning/organization/infrastructure/persistence/OrganizationAdminSummaryRow.java`
- 新增：`server/src/main/java/com/lingdong/learning/organization/application/OrganizationWorkbenchOrganization.java`
- 新增：`server/src/main/java/com/lingdong/learning/organization/application/OrganizationWorkbenchContext.java`
- 新增：`server/src/main/java/com/lingdong/learning/organization/application/OrganizationWorkbenchQueryService.java`
- 新增：`server/src/main/java/com/lingdong/learning/organization/web/OrganizationWorkbenchOrganizationResponse.java`
- 新增：`server/src/main/java/com/lingdong/learning/organization/web/OrganizationWorkbenchContextResponse.java`
- 新增：`server/src/main/java/com/lingdong/learning/organization/web/OrganizationWorkbenchController.java`
- 新增测试：`server/src/test/java/com/lingdong/learning/organization/web/OrganizationWorkbenchControllerTest.java`

- [x] **步骤 1：写工作台隔离和端型测试**

使用真实 MockMvc 和数据库准备两个机构管理员、启用/停用组织、直接/非直接组织。验证：

```java
mockMvc.perform(get("/api/v1/organization-workbench/context")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + organizationSession.accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").isString())
        .andExpect(jsonPath("$.username").value("school_admin"))
        .andExpect(jsonPath("$.organizations.length()").value(2))
        .andExpect(jsonPath("$.organizations[0].id").isString());
```

断言响应不含完整组织树、其他管理员、停用组织和非直接组织；WEB 会话、平台/家长/学生 MINIAPP 会话返回 403 或认证失败；功能关闭后旧机构会话返回 401。

- [x] **步骤 2：运行测试确认红灯**

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; mvn -q -Dtest=OrganizationWorkbenchControllerTest test
```

预期：控制器和工作台类型不存在。

- [x] **步骤 3：实现稳定排序的直接管理组织查询**

查询行：

```java
/** 机构管理员直接管理的启用组织查询行。 */
public record OrganizationAdminSummaryRow(
        Long id,
        String name,
        String typeCode,
        String path,
        Integer sortOrder
) { }
```

Mapper：

```java
List<OrganizationAdminSummaryRow> findEnabledManagedOrganizationSummaries(
        @Param("userId") Long userId);
```

XML：

```xml
<select id="findEnabledManagedOrganizationSummaries"
        resultType="com.lingdong.learning.organization.infrastructure.persistence.OrganizationAdminSummaryRow">
    SELECT organization.id,
           organization.organization_name AS name,
           organization.organization_type AS type_code,
           organization.organization_path AS path,
           organization.sort_order
    FROM sys_organization_admin organization_admin
    JOIN sys_organization organization
      ON organization.id = organization_admin.organization_id
    WHERE organization_admin.user_id = #{userId}
      AND organization.status = 'ENABLED'
    ORDER BY organization.organization_path, organization.sort_order, organization.id
</select>
```

- [x] **步骤 4：实现服务和字符串标识响应**

应用记录：

```java
public record OrganizationWorkbenchOrganization(Long id, String name, String typeCode) { }

public record OrganizationWorkbenchContext(
        Long userId,
        String username,
        String displayName,
        List<OrganizationWorkbenchOrganization> organizations
) { }
```

查询服务不接收任何外部用户或组织参数，按当前认证身份执行以下精确校验：

```java
public OrganizationWorkbenchContext getContext(AuthenticatedUser currentUser) {
    if (currentUser == null
            || currentUser.clientType() != AuthClientType.MINIAPP
            || !currentUser.roleCodes().contains("ORG_ADMIN")) {
        throw new AccessDeniedException("当前身份不能访问机构工作台");
    }
    User user = userMapper.findById(currentUser.userId());
    if (user == null || user.status() != UserStatus.ENABLED || user.type() != UserType.ORGANIZATION) {
        throw new AccessDeniedException("当前身份不能访问机构工作台");
    }
    List<OrganizationWorkbenchOrganization> organizations = organizationAdminMapper
            .findEnabledManagedOrganizationSummaries(user.id()).stream()
            .map(row -> new OrganizationWorkbenchOrganization(row.id(), row.name(), row.typeCode()))
            .toList();
    if (organizations.isEmpty()) {
        throw new AccessDeniedException("当前身份不能访问机构工作台");
    }
    return new OrganizationWorkbenchContext(
            user.id(), user.username(), user.displayName(), organizations);
}
```

响应记录对两个标识字段使用 `ToStringSerializer`：

```java
public record OrganizationWorkbenchOrganizationResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String name,
        String typeCode
) { }

public record OrganizationWorkbenchContextResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long userId,
        String username,
        String displayName,
        List<OrganizationWorkbenchOrganizationResponse> organizations
) { }
```

- [x] **步骤 5：实现当前身份控制器并确认绿灯**

```java
@GetMapping("/context")
public OrganizationWorkbenchContextResponse context(
        @AuthenticationPrincipal AuthenticatedUser currentUser
) {
    return OrganizationWorkbenchContextResponse.from(queryService.getContext(currentUser));
}
```

运行同一步骤 2，预期工作台最小响应、排序、用户隔离和端型限制全部通过。

## 任务 6：Web 能力契约回归

**文件：**
- 修改：`web/src/api/capability.ts`
- 修改测试：`web/src/app/App.test.tsx`

- [x] **步骤 1：先固定 Web 能力类型和回归断言**

```ts
export interface ClientCapabilities {
  client: 'WEB' | 'MINIAPP';
  studentCodeLoginEnabled: boolean;
  studentQrLoginEnabled: boolean;
  parentRelationshipManagementEnabled: boolean;
  organizationMiniappAuthEnabled: boolean;
  accountSecurityManagementEnabled: boolean;
  // 保留现有其余字段
}
```

现有测试能力夹具全部显式设置 `organizationMiniappAuthEnabled: false`，并断言 Web 登录页、路由和菜单没有新增机构小程序入口。

- [x] **步骤 2：运行 Web 测试确认类型红灯**

```powershell
npm test -- --run
```

工作目录：`web`。预期：能力响应夹具或类型因字段缺失失败。

- [x] **步骤 3：实现最小契约并回归**

只增加字段，不修改 `App.tsx`、登录页面或 Web 会话逻辑。再次执行 `npm test -- --run`，预期全部通过。

## 任务 7：uni-app 机构独立会话、API 与首页入口

**文件：**
- 新增：`miniapp/src/session/organization-session.ts`
- 修改：`miniapp/src/api/auth.ts`
- 新增：`miniapp/src/api/organization-workbench.ts`
- 修改：`miniapp/src/api/capability.ts`
- 修改：`miniapp/src/pages/index/index.vue`

- [x] **步骤 1：先用 TypeScript 固定独立契约**

```ts
export interface OrganizationSession extends AuthSession {}

export interface OrganizationWorkbenchOrganization {
  id: string;
  name: string;
  typeCode: string;
}

export interface OrganizationWorkbenchContext {
  userId: string;
  username: string;
  displayName: string;
  organizations: OrganizationWorkbenchOrganization[];
}

export type MiniappAuthIdentity = 'parent' | 'student' | 'organization';
```

能力增加：

```ts
organizationMiniappAuthEnabled: boolean;
```

- [x] **步骤 2：执行类型检查确认红灯**

```powershell
npm run type-check
```

工作目录：`miniapp`。预期：机构会话/API/身份分支缺失导致类型或产物契约检查失败。

- [x] **步骤 3：实现机构独立会话存储**

```ts
import type { OrganizationSession } from '@/api/auth';

const SESSION_STORAGE_KEY = 'lingdong.organization.session';
const DEVICE_STORAGE_KEY = 'lingdong.organization.device-id';

export function saveOrganizationSession(session: OrganizationSession): void {
  uni.setStorageSync(SESSION_STORAGE_KEY, session);
}

export function getOrganizationSession(): OrganizationSession | null {
  const value = uni.getStorageSync(SESSION_STORAGE_KEY) as OrganizationSession | undefined;
  return value?.accessToken ? value : null;
}

export function clearOrganizationSession(): void {
  uni.removeStorageSync(SESSION_STORAGE_KEY);
}
```

设备标识生成逻辑与学生会话等价，但只能读写 `lingdong.organization.device-id`。

- [x] **步骤 4：实现机构登录和工作台 API**

```ts
export function loginOrganizationByPassword(payload: {
  username: string;
  password: string;
  deviceId: string;
  deviceName: string;
}): Promise<OrganizationSession> {
  return request('/auth/organization-sessions/password', { method: 'POST', data: payload });
}

export function getOrganizationWorkbenchContext(
  accessToken: string
): Promise<OrganizationWorkbenchContext> {
  return request('/organization-workbench/context', {
    header: { Authorization: `Bearer ${accessToken}` }
  });
}
```

账号安全请求身份守卫同步允许 `organization`，但仍由调用方显式传入该身份令牌。

- [x] **步骤 5：增加首页第三入口**

首页能力加载后设置：

```ts
organizationLoginEnabled.value = capabilityResult.status === 'fulfilled'
  && capabilityResult.value.organizationMiniappAuthEnabled;
organizationSessionExists.value = Boolean(getOrganizationSession());
```

入口仅在能力开启时展示，已有会话跳转 `/pages/organization-home/organization-home`，否则跳转 `/pages/organization-login/organization-login`；家长和学生入口逻辑保持原样。

- [x] **步骤 6：重新执行类型检查确认绿灯**

运行同一步骤 2，预期类型检查通过且三类会话键无交叉引用。

## 任务 8：uni-app 机构登录页、工作台和账号安全闭环

**文件：**
- 新增：`miniapp/src/pages/organization-login/organization-login.vue`
- 新增：`miniapp/src/pages/organization-home/organization-home.vue`
- 修改：`miniapp/src/pages/account-security/account-security.vue`
- 修改：`miniapp/src/pages.json`

- [x] **步骤 1：登记两个独立路由**

```json
{
  "path": "pages/organization-login/organization-login",
  "style": { "navigationBarTitleText": "机构登录" }
},
{
  "path": "pages/organization-home/organization-home",
  "style": { "navigationBarTitleText": "机构工作台" }
}
```

- [x] **步骤 2：实现机构登录页状态机**

页面状态仅包含 `username`、`password` 和 `submitting`。提交逻辑必须为：

```ts
async function submit(): Promise<void> {
  if (submitting.value) return;
  if (!username.value.trim() || !password.value) {
    return void uni.showToast({ title: '请输入账号和密码', icon: 'none' });
  }
  submitting.value = true;
  try {
    const session = await loginOrganizationByPassword({
      username: username.value.trim(),
      password: password.value,
      deviceId: getOrCreateOrganizationDeviceId(),
      deviceName: getOrganizationDeviceName()
    });
    saveOrganizationSession(session);
    await uni.redirectTo({ url: '/pages/organization-home/organization-home' });
  } catch {
    password.value = '';
    uni.showToast({ title: '账号或密码错误', icon: 'none' });
  } finally {
    submitting.value = false;
  }
}
```

页面不得出现验证码、微信授权、角色选择或客户端选择。

- [x] **步骤 3：实现机构工作台**

`onShow` 先读取机构会话，再读取能力和上下文；认证失败只清机构会话并 `reLaunch` 首页。页面展示显示名称、账号、直接管理组织列表、账号安全入口和退出当前会话按钮。

```ts
function openAccountSecurity(): void {
  uni.navigateTo({ url: '/pages/account-security/account-security?identity=organization' });
}

async function signOut(): Promise<void> {
  try {
    await logoutOrganization(session.value!.accessToken);
  } finally {
    clearOrganizationSession();
    await uni.reLaunch({ url: '/pages/index/index' });
  }
}
```

工作台不得展示转班、任务、考勤、统计、定位或地图入口。

- [x] **步骤 4：扩展账号安全身份分支**

```ts
if (query?.identity !== 'parent'
    && query?.identity !== 'student'
    && query?.identity !== 'organization') return leavePage();

const session = identity.value === 'parent'
  ? getParentSession()
  : identity.value === 'student'
    ? getStudentSession()
    : getOrganizationSession();
```

`clearIdentitySession` 的机构分支只能调用 `clearOrganizationSession()`；全部设备下线失败时保留机构会话。

- [x] **步骤 5：执行 uni-app 三项验证**

```powershell
npm run type-check
npm run build:h5
npm run build:mp-weixin
```

工作目录：`miniapp`。预期三项通过。扫描 `dist` 验证包含两个机构页面、`organizationMiniappAuthEnabled` 和两个机构存储键，且不包含定位、地图、轨迹或机构微信登录代码。

## 任务 9：全量回归、安全扫描和中文文档同步

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
- 修改：`docs/design/08-第三方集成设计-V1.0.md`
- 修改：`docs/design/09-统计口径与报表设计-V1.0.md`
- 修改：`docs/design/10-测试方案与验收用例-V1.0.md`
- 修改：`docs/design/11-部署运维与发布方案-V1.0.md`
- 修改：`docs/design/12-当前实现一致性核对-V1.0.md`
- 修改：`docs/superpowers/plans/2026-08-08-lingdong-learning-master-development.md`
- 修改：`docs/superpowers/plans/2026-08-09-lingdong-organization-miniapp-auth.md`

- [x] **步骤 1：执行后端目标测试与全量测试**

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; mvn -q '-Dtest=FlywayMigrationTest,OrganizationMiniappAuthenticationTest,AuthenticationControllerTest,OrganizationWorkbenchControllerTest,StudentAuthenticationControllerTest' test
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; mvn -q test
```

工作目录：`server`。预期目标测试和全部 Surefire 测试零失败，空库迁移停在 V40。

- [x] **步骤 2：执行 Web 与小程序全量验证**

```powershell
npm test -- --run
npm run build
```

工作目录：`web`。

```powershell
npm run type-check
npm run build:h5
npm run build:mp-weixin
```

工作目录：`miniapp`。预期全部通过，仅允许记录既有非阻断构建警告。

- [x] **步骤 3：执行静态安全与边界扫描**

```powershell
rg -n -i "auto_increment|identity\s*\(" server/src/main/resources/db/migration
rg -n "password|accessToken|refreshToken|deviceId" server/src/main/java | Select-String -Pattern "log\.|print"
rg -n "lingdong\.(parent|student|organization)\.(session|device-id)" miniapp/src
rg -n -i "uni\.(getLocation|chooseLocation|openLocation)|wx\.(getLocation|chooseLocation|openLocation)|latitude|longitude|geofence|trajectory" miniapp/src server/src/main/java
```

预期：迁移无自增；日志不打印敏感字段；三类会话各自只读写自己的键；V40 不新增定位地图代码。

- [x] **步骤 4：同步所有中文文档和进度**

文档必须记录：V40 范围、专用登录端点、工作台最小响应、功能开关、即时失效、67 表不变、19 位雪花标识、Web/小程序独立、三类小程序会话隔离、验证结果和环境边界。主开发计划把 V40 标为完成，并把下一项明确为 V41 学生机构关系生命周期。

- [x] **步骤 5：最终一致性检查**

```powershell
rg -n "TO[D]O|TB[D]|待[定]|占[位]|后续补[充]" docs/superpowers/plans/2026-08-09-lingdong-organization-miniapp-auth.md
git diff --check
git status --short
```

预期：专项计划无未决标记，`git diff --check` 无空白错误；工作区中既有 V36-V39 变更全部保留，不回退或覆盖用户及历史工作。

## 任务 10：独立安全审查修正

- [x] 机构 `MINIAPP` 令牌调用 `WEB` 权限接口的回归测试先返回 `201`，随后将当前令牌客户端纳入动态权限决策并转为 `403 ACCESS_DENIED`。
- [x] 未知机构账号先验证为未调用密码哈希，再增加启动期等价摘要并保证每次登录执行一次同算法比较。
- [x] 刷新认证失败后的会话撤销先验证被外层事务回滚，再通过独立新事务持久化 `REVOKED` 状态。
- [x] 审批停用机构小程序开关先验证旧机构小程序会话仍活动，再批量撤销机构 `MINIAPP` 会话并保留机构 `WEB` 会话。
- [x] Flyway V40 保持标准只执行一次的版本迁移语义，不引入 H2/MySQL 不一致的冲突忽略语法；重复版本由 Flyway 历史表阻止执行。

## 完成定义

V40 只有同时满足以下条件才可标记完成：

1. V1-V40 可在本地 H2 空库连续迁移，仍为 67 张显式非自增 `BIGINT id` 主键表。
2. 只有有效机构管理员可从专用端点获得 `MINIAPP` 会话，失败响应保持中性。
3. 功能、用户、角色或启用管理组织条件变化后，旧访问令牌和刷新令牌立即失效。
4. 工作台只返回当前用户直接管理的启用组织摘要，所有标识以字符串响应。
5. Web 只同步契约；uni-app 机构、家长、学生会话和设备标识完全隔离。
6. 后端、Web、H5、微信小程序的全量自动化验证通过。
7. README、设计文档 00-12、主计划和本专项计划均以中文同步真实实现状态。
8. 未访问或写入远程 MySQL、Redis、共享测试、预生产和生产环境。
