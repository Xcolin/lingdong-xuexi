# 灵动学习 V17 组织管理 API 实施计划

> **执行要求**：按任务顺序实施。每一个行为变更先增加失败测试，再做最小实现；完成一个步骤后更新复选框。

**目标：**为 Web 管理端提供系统管理员专属、动态 RBAC 路由保护的组织类型与组织树查询、新增 API。

**架构：**V17 以 4 个固定 19 位雪花权限和 `SYS_ADMIN` 授权初始化组织管理入口。控制器使用 `@RequirePermission` 处理动态 RBAC，组织管理应用服务额外验证 `SYS_ADMIN`，防止尚未接入数据范围条件的全量组织树被委派读取。查询服务与 MyBatis XML 负责目录和树数据读取，已有组织领域服务继续负责树结构与唯一性规则。

**技术栈：**Spring Boot 3、Spring MVC、Spring Security、MyBatis XML、Flyway、MySQL 8、H2、JUnit 5、AssertJ、MockMvc。

---

## 文件职责

| 文件 | 职责 |
|---|---|
| `server/src/main/resources/db/migration/V17__seed_organization_management_permissions.sql` | 初始化 4 个组织管理权限及系统管理员授权。 |
| `server/src/main/java/com/lingdong/learning/organization/infrastructure/persistence/*Mapper.java` 与 XML | 新增组织类型与组织节点的稳定排序查询。 |
| `server/src/main/java/com/lingdong/learning/organization/application/OrganizationQueryApplicationService.java` | 提供组织类型目录和组织节点集合查询。 |
| `server/src/main/java/com/lingdong/learning/organization/application/OrganizationManagementApplicationService.java` | 强制系统管理员约束并组合组织读写用例。 |
| `server/src/main/java/com/lingdong/learning/organization/web/*` | 组织类型、组织树的请求/响应 DTO 与 REST Controller。 |
| `server/src/test/java/com/lingdong/learning/organization/web/OrganizationManagementControllerTest.java` | 覆盖权限、系统管理员约束、树形响应与 404。 |
| `server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java` | 验证 V17 权限种子、授权和雪花常量。 |
| `docs/design/03-系统架构设计-HLD-V1.0.md` 至 `docs/design/12-当前实现一致性核对-V1.0.md` | 回填 V17、接口、权限边界和回归证据。 |

## 任务 1：固定 V17 组织管理权限种子

- [x] **步骤 1：先写 Flyway 失败测试**

在 `server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java` 增加测试，断言 `ORG_TYPE_READ`、`ORG_TYPE_CREATE`、`ORG_NODE_READ`、`ORG_NODE_CREATE` 都是 `WEB`、`OPERATION`、`ENABLED` 权限，且内置 `SYS_ADMIN` 获得全部 4 条授权；权限与授权关联主键均为 19 位数字。

- [x] **步骤 2：运行失败测试**

执行：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
Set-Location server
mvn test '-Dtest=FlywayMigrationTest'
```

预期：失败，因为 V17 迁移尚不存在。

- [x] **步骤 3：创建 V17 权限迁移**

新建 `server/src/main/resources/db/migration/V17__seed_organization_management_permissions.sql`，使用 `1874244142494646312` 至 `1874244142494646315` 创建 4 条权限，使用 `1874244142494646316` 至 `1874244142494646319` 建立到 `SYS_ADMIN` 角色 `1874244142494646273` 的授权关联。每条权限设置 `resource_type='OPERATION'`、`client_type='WEB'`、`status='ENABLED'`。

- [x] **步骤 4：验证迁移测试通过**

重新执行步骤 2 的命令。预期：Flyway 从 V1 迁移至 V17，组织管理权限种子测试通过。

## 任务 2：补齐组织查询与资源不存在语义

- [x] **步骤 1：先写组织资源语义失败测试**

在 `server/src/test/java/com/lingdong/learning/organization/application/OrganizationApplicationServiceTest.java` 增加两个测试：使用不存在的 `typeCode` 创建根组织时断言 `ResourceNotFoundException`，使用存在类型但不存在的 `parentId` 创建组织时也断言 `ResourceNotFoundException`。

- [x] **步骤 2：运行失败测试**

执行：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
Set-Location server
mvn test '-Dtest=OrganizationApplicationServiceTest'
```

预期：失败，因为组织服务仍将缺失类型或父级按通用异常处理。

- [x] **步骤 3：添加排序查询与应用服务**

在 `OrganizationTypeMapper` 添加 `List<OrganizationType> findAll()`，SQL 使用 `ORDER BY sort_order ASC, type_code ASC`；在 `OrganizationMapper` 添加 `List<Organization> findAll()`，SQL 使用 `ORDER BY organization_path ASC, sort_order ASC, organization_code ASC`。创建 `OrganizationQueryApplicationService` 公开 `listOrganizationTypes()` 与 `listOrganizations()`。

修改 `OrganizationApplicationService#createOrganization`：类型 Mapper 查询为 `null` 时抛出 `ResourceNotFoundException("组织类型不存在：" + typeCode)`；类型已停用时仍抛出 `IllegalStateException`。`resolveParent` 的父级查询为 `null` 时抛出 `ResourceNotFoundException("父级组织不存在：" + parentId)`；父级停用时仍抛出 `IllegalStateException`。

- [x] **步骤 4：验证资源查询和 404 通过**

重新执行步骤 2 的命令。预期：两个缺失资源场景均通过 `ResourceNotFoundException` 固定下来；停用类型和停用父级仍保持 `IllegalStateException`。

## 任务 3：实现系统管理员专属组织管理 API


- [x] **步骤 1：先写组织管理 HTTP 失败测试**

在 `OrganizationManagementControllerTest` 增加系统管理员流程：查询组织类型、创建 `COMMUNITY` 自定义类型、创建 `REGION_EAST` 根区域和其下 `SCHOOL_EAST_1` 学校，最后查询 `/api/v1/organizations` 并断言学校位于区域节点的 `children` 中，所有 ID 均为 JSON 字符串。

同时准备普通平台用户；该用户访问组织类型目录必须收到控制器前的 403。再创建自定义角色、授予 `ORG_NODE_READ` 后访问组织树，预期因非系统管理员专属限制得到 403。

- [x] **步骤 2：运行失败测试**

执行 `mvn test '-Dtest=OrganizationManagementControllerTest'`。预期：失败，因为 Controller、DTO 与系统管理员专属用例边界尚不存在。

- [x] **步骤 3：实现管理服务、DTO 与 Controller**

创建 `OrganizationManagementApplicationService`，依赖 `UserRoleMapper`、`OrganizationApplicationService` 与 `OrganizationQueryApplicationService`。公开以下方法，并先执行 `userRoleMapper.hasRoleCode(operatorId, "SYS_ADMIN")`；不满足时抛出 `SystemOperationAccessDeniedException("仅系统管理员可管理组织")`：

```java
public List<OrganizationType> listOrganizationTypes(Long operatorId)
public OrganizationType createOrganizationType(Long operatorId, CreateOrganizationTypeCommand command)
public List<Organization> listOrganizations(Long operatorId)
public Organization createOrganization(Long operatorId, CreateOrganizationCommand command)
```

创建 `OrganizationTypeResponse`、`OrganizationTreeNodeResponse`、`CreateOrganizationTypeRequest`、`CreateOrganizationRequest`，所有 ID 输出使用 `ToStringSerializer`。树构造以 `LinkedHashMap<Long, OrganizationTreeNodeBuilder>` 暂存节点，先按查询结果创建节点，再按 `parentId` 连接，根节点保留在结果数组；遇到查询结果中不存在的父节点时抛出 `IllegalStateException("组织树数据不完整")`，不静默把节点提升为根节点。

创建 `OrganizationManagementController`：

```java
@RequirePermission("ORG_TYPE_READ")
@GetMapping("/organization-types")
public List<OrganizationTypeResponse> listOrganizationTypes(@AuthenticationPrincipal AuthenticatedUser currentUser)

@RequirePermission("ORG_NODE_CREATE")
@PostMapping("/organizations")
@ResponseStatus(HttpStatus.CREATED)
public OrganizationTreeNodeResponse createOrganization(@AuthenticationPrincipal AuthenticatedUser currentUser,
        @Valid @RequestBody CreateOrganizationRequest request)
```

其余两个方法分别映射 `POST /organization-types` 与 `GET /organizations`，使用 `ORG_TYPE_CREATE`、`ORG_NODE_READ`。任何写接口都不接受操作者标识。

- [x] **步骤 4：验证组织管理流程通过**

重新执行 `mvn test '-Dtest=OrganizationManagementControllerTest'`。预期：系统管理员得到 200/201、树结构正确、普通用户及具备仅路由权限的非系统管理员都得到 403、缺失资源得到 404。

## 任务 4：同步中文文档并完成回归

- [x] **步骤 1：更新正式中文设计文档**

更新 `docs/design/03-系统架构设计-HLD-V1.0.md`、`04-数据库设计-V1.0.md`、`05-Flyway迁移规范-V1.0.md`、`06-API接口设计-V1.0.md`、`07-权限与安全设计-V1.0.md`、`12-当前实现一致性核对-V1.0.md`，写明 V17 仅补充权限基础数据、组织管理接口、系统管理员专属原因、未接入组织数据范围拦截和下一版本为 V18。

- [x] **步骤 2：执行静态检查**

执行：

```powershell
git diff --check
if (rg -n 'AUTO_INCREMENT|PRIMARY KEY \([^)]*_id' server/src/main/resources/db/migration) { exit 1 }
```

预期：无空白错误，迁移不包含自增或外键主键。

- [x] **步骤 3：执行完整本地回归**

执行：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
Set-Location server
mvn test
```

预期：全部测试通过。记录真实测试数量，仅表述为本地 H2/Flyway 验证，不表述为共享测试、预生产或生产 MySQL 验证。
