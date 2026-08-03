# 灵动学习机构邀请家长绑定实施计划

> **执行要求：**逐项执行，每个行为先写失败测试并确认失败，再编写最小实现。完成一项立即更新复选框；不启用本机 `local` profile，不连接远程环境。

**目标：**让直接管理学生所属组织的机构管理员签发一次性家长绑定邀请，并让已登录家长安全地接受或拒绝，建立唯一主家长关系。

**架构：**在现有 `student` 模块中新增邀请领域对象、MyBatis 映射和应用服务；Controller 从 Bearer 会话取得机构管理员或家长身份。邀请令牌只在创建响应中明文返回一次，持久层仅保存 SHA-256 摘要。V20 不改造平台密码认证，不创建小程序会话。

**技术栈：**Spring Boot 3、Java 17、MyBatis XML、Flyway、H2 MySQL 兼容模式、既有雪花 ID、Spring Security Bearer 与动态 RBAC。

---

## 文件职责

| 文件 | 职责 |
|---|---|
| `server/src/main/resources/db/migration/V20__create_parent_binding_invitation.sql` | 邀请表、索引、V20 权限及角色授权。 |
| `server/src/main/java/com/lingdong/learning/student/domain/ParentBindingInvitation*.java` | 邀请状态和不可变领域对象。 |
| `server/src/main/java/com/lingdong/learning/student/infrastructure/security/InvitationTokenService.java` | 高熵令牌生成和 SHA-256 摘要，不记录明文。 |
| `server/src/main/java/com/lingdong/learning/student/infrastructure/persistence/ParentBindingInvitationMapper.java` | 邀请写入、状态推进和条件查询。 |
| `server/src/main/resources/mapper/student/ParentBindingInvitationMapper.xml` | 参数化邀请 SQL。 |
| `server/src/main/java/com/lingdong/learning/student/application/ParentBindingInvitationApplicationService.java` | 创建、接受、拒绝、过期处理和事务边界。 |
| `server/src/main/java/com/lingdong/learning/student/web/*ParentBindingInvitation*.java` | Web 请求、仅创建时返回令牌的响应和 Controller。 |
| `server/src/test/java/com/lingdong/learning/student/web/ParentBindingInvitationControllerTest.java` | 机构、家长、令牌、范围、状态和权限集成测试。 |
| `docs/design/01、04、05、06、07、10、12` | 中文设计、迁移、接口、安全、验收和实现记录。 |

## 任务 1：定义失败用例

- [x] **步骤 1：创建邀请控制器集成测试**

创建 `ParentBindingInvitationControllerTest`。准备具备 `ORG_ADMIN` 的机构管理员、具备 `PARENT` 的家长、无权限用户及两个直接组织；通过既有平台密码会话获得测试 Bearer 凭证。

```java
MvcResult issued = mockMvc.perform(post("/api/v1/students/{id}/parent-invitations", studentId)
        .header("Authorization", "Bearer " + organizationAdminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"organizationId\":\"%s\"}".formatted(organizationId)))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.id").isString())
    .andExpect(jsonPath("$.acceptToken").isString())
    .andReturn();

mockMvc.perform(post("/api/v1/parent-invitations/{id}/accept", invitationId)
        .header("Authorization", "Bearer " + parentToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"acceptToken\":\"%s\"}".formatted(acceptToken)))
    .andExpect(status().isNoContent());
```

断言接受后家长可读取该学生，错误令牌不改变状态，跨机构创建邀请返回 403，已有主家长或待处理邀请返回 409，拒绝后可重新签发，无邀请权限的已认证用户返回 403。

- [x] **步骤 2：运行失败用例**

执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn test '-Dtest=ParentBindingInvitationControllerTest'
```

预期：因 V20 路由尚不存在而返回 404；测试必须明确展示该失败原因。

## 任务 2：建立 V20 数据模型和 RBAC 基础

- [x] **步骤 1：新增 V20 Flyway 迁移**

创建 `V20__create_parent_binding_invitation.sql`，使用以下核心结构：

```sql
CREATE TABLE edu_parent_binding_invitation (
    id BIGINT NOT NULL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    inviter_user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    pending_scope_key VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    responded_at TIMESTAMP NULL,
    responded_by_user_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_edu_parent_invitation_token UNIQUE (token_hash),
    CONSTRAINT uk_edu_parent_invitation_pending UNIQUE (student_id, pending_scope_key),
    CONSTRAINT fk_edu_parent_invitation_student FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT fk_edu_parent_invitation_organization FOREIGN KEY (organization_id) REFERENCES sys_organization (id),
    CONSTRAINT fk_edu_parent_invitation_inviter FOREIGN KEY (inviter_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_edu_parent_invitation_responder FOREIGN KEY (responded_by_user_id) REFERENCES sys_user (id)
);
```

增加 `(student_id, status)` 和 `(expires_at, status)` 索引。插入 `STUDENT_PARENT_INVITE_CREATE`、`STUDENT_PARENT_INVITE_RESPOND` 两项 `WEB/OPERATION` 权限，使用 `1874244142494646331` 至 `1874244142494646334` 连续的预生成 19 位常量：创建权限授权 `ORG_ADMIN`，响应权限授权 `PARENT`。

- [x] **步骤 2：扩展 Flyway 迁移测试**

在 `FlywayMigrationTest` 增加断言：V20 表存在、`id` 是非 identity `BIGINT`、令牌和待处理唯一约束存在、两个权限及其两条授权存在。将所有当前表的雪花主键数由 21 增至 22。

- [x] **步骤 3：确认迁移测试转绿**

执行：

```powershell
mvn test '-Dtest=FlywayMigrationTest'
```

预期：本地 H2 从 V1 顺序迁移至 V20，所有迁移断言通过。

## 任务 3：实现邀请令牌、持久化和用例服务

- [x] **步骤 1：定义领域、命令和令牌服务**

创建 `ParentBindingInvitationStatus`（`PENDING`、`ACCEPTED`、`REJECTED`、`EXPIRED`）、`ParentBindingInvitation`、`CreateParentBindingInvitationCommand(Long organizationId)`、`RespondParentBindingInvitationCommand(Long invitationId, String acceptToken)` 和 `IssuedParentBindingInvitation`。创建 `InvitationTokenService`，以 `SecureRandom` 生成 32 字节 URL-safe Base64 令牌并以 SHA-256 生成 64 位十六进制摘要。

```java
public record IssuedParentBindingInvitation(ParentBindingInvitation invitation, String acceptToken) { }

public interface InvitationTokenService {
    String newToken();
    String hash(String token);
}
```

- [x] **步骤 2：补充 Mapper 与 XML**

新增 `ParentBindingInvitationMapper` 的 `insert`、`findById`、`existsPendingByStudentId`、`expirePendingByStudentId`、`respondIfPending`。扩展 `ParentStudentMapper` 提供 `existsActiveByStudentId`，扩展 `StudentOrganizationMapper` 提供 `existsActiveByStudentAndOrganization`。所有 SQL 使用 `#{}` 参数绑定，状态更新必须带 `status='PENDING'` 与有效期条件。

- [x] **步骤 3：实现创建、接受和拒绝事务**

创建 `ParentBindingInvitationApplicationService`。创建前依次验证 `ORG_ADMIN` 角色、学生存在、目标组织启用、当前用户为该组织管理员、学生与该组织有活动关系、学生无主家长；先过期旧待处理邀请，再拒绝新的未过期待处理邀请。接受或拒绝前验证 `PARENT` 角色、令牌长度不超过 128、摘要匹配、邀请状态和有效期；接受时调用 `ParentStudentMapper.insertPrimary`，拒绝时只关闭邀请。

```java
@Transactional
public void accept(AuthenticatedUser currentUser, RespondParentBindingInvitationCommand command) {
    ParentBindingInvitation invitation = requirePendingInvitationWithToken(currentUser, command);
    if (invitationMapper.respondIfPending(invitation.id(), ACCEPTED, closedScope(invitation.id()),
            currentUser.userId(), LocalDateTime.now()) != 1) {
        throw new IllegalStateException("邀请状态已变化");
    }
    parentStudentMapper.insertPrimary(idGenerator.nextId(), currentUser.userId(), invitation.studentId());
}
```

将重复键异常转换为状态冲突，令牌错误或范围不足转换为 `SystemOperationAccessDeniedException`，不在异常文本、日志或响应中拼入令牌。

- [x] **步骤 4：运行邀请测试确认转绿**

执行：

```powershell
mvn test '-Dtest=ParentBindingInvitationControllerTest,FlywayMigrationTest'
```

预期：创建、正确接受、拒绝/重发、重复防护、令牌保护、权限与组织范围断言全部通过。

## 任务 4：发布 HTTP 契约与中文文档

- [x] **步骤 1：新增 Controller 和 DTO**

在 `StudentManagementController` 或独立 `ParentBindingInvitationController` 中公开三条接口。创建邀请响应只含 `id`、`studentId`、`organizationId`、`status`、`expiresAt`、`createdAt` 和仅本次返回的 `acceptToken`；接受、拒绝接口返回 204。所有 ID 使用 `ToStringSerializer`。

- [x] **步骤 2：同步设计文档**

更新 `01-功能详细设计-FSD-V1.0.md`、`04-数据库设计-V1.0.md`、`05-Flyway迁移规范-V1.0.md`、`06-API接口设计-V1.0.md`、`07-权限与安全设计-V1.0.md`、`10-测试方案与验收用例-V1.0.md`、`12-当前实现一致性核对-V1.0.md`。明确 V20 的既有账号前提、7 天有效期、令牌只返回一次、只保存摘要、无短信/微信投递、无小程序页面和 V21 边界。

- [x] **步骤 3：执行最终本地回归**

已完成记录：V20 迁移、邀请接口与后端全量 84 项测试均已通过。邀请集成测试在测试事务内执行并在用例结束时回滚，确保其测试数据不污染其他共享 H2 范围用例；Web 和小程序构建将在本轮最终跨端回归中再次执行。未加载 `local` profile，未连接远程 MySQL、Redis、微信或消息服务。

执行 `git diff --check`、JDK 17 下的 `mvn test`、Web 单工作进程 `npx vitest run --pool=forks --maxWorkers=1 --minWorkers=1`、`npm run build`、小程序 `npm run type-check` 和 `npm run build:mp-weixin`。预期所有验证通过；不加载 `application-local.yml`，不访问远程 MySQL、Redis、微信或消息服务。
