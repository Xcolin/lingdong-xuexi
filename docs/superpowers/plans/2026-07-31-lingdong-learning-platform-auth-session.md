# 灵动学习平台认证与设备会话实施计划

**目标：**为启用的平台账号提供密码登录、可撤销令牌会话、当前用户查询和设备下线能力。

**架构：**复用 `sys_user.password_hash` 保存 BCrypt 密码散列；V15 仅建立 `auth_device_session`，数据库保存访问凭证和刷新凭证的 SHA-256 摘要与会话状态。Spring Security 通过自定义 Bearer 凭证过滤器查询活动会话建立身份，不使用无法即时撤销的自包含令牌。

**技术栈：**Spring Boot 3、Spring Security、Spring MVC、MyBatis XML、Flyway、MySQL 8、H2、JUnit 5、AssertJ、MockMvc。

---

## 文件职责

| 文件 | 职责 |
|---|---|
| `server/pom.xml` | 引入 Spring Security。 |
| `server/src/main/resources/db/migration/V15__create_auth_device_session.sql` | 建立会话表、唯一令牌摘要约束、用户外键和查询索引。 |
| `server/src/main/java/com/lingdong/learning/auth/domain/*` | 客户端类型、会话状态和持久化会话记录。 |
| `server/src/main/java/com/lingdong/learning/auth/application/*` | 密码设置、登录、刷新、退出、设备会话用例及其命令/视图。 |
| `server/src/main/java/com/lingdong/learning/auth/infrastructure/*` | MyBatis 映射器、令牌散列/生成器和认证配置属性。 |
| `server/src/main/java/com/lingdong/learning/auth/web/*` | 登录、刷新、当前用户和设备会话 REST 接口。 |
| `server/src/main/java/com/lingdong/learning/common/security/*` | Spring Security 配置、Bearer 过滤器、当前认证主体和 JSON 安全错误响应。 |
| `server/src/main/java/com/lingdong/learning/user/infrastructure/persistence/UserMapper.java`、`server/src/main/resources/mapper/user/UserMapper.xml` | 受控更新既有 `password_hash`。 |
| `server/src/main/resources/mapper/auth/DeviceSessionMapper.xml` | 会话 SQL，保持 MyBatis XML 约束。 |
| `server/src/test/java/com/lingdong/learning/...` | 迁移、认证用例和 HTTP 安全链回归。 |
| `docs/design/03-系统架构设计-HLD-V1.0.md`、`docs/design/04-数据库设计-V1.0.md`、`docs/design/05-Flyway迁移规范-V1.0.md`、`docs/design/06-API接口设计-V1.0.md`、`docs/design/07-权限与安全设计-V1.0.md`、`docs/design/12-当前实现一致性核对-V1.0.md` | 同步 V15、真实接口和已实现边界。 |

## 任务 1：V15 会话表与迁移测试

- [x] 在 `server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java` 新增失败测试，断言 `auth_device_session` 存在，`id` 为非 identity 的 `BIGINT`，`access_token_hash`、`refresh_token_hash` 分别具有唯一约束，并存在 `(user_id, status)` 索引。
- [x] 执行 `mvn test -Dtest=FlywayMigrationTest`，预期因 V15 表不存在而失败。
- [x] 新建 `server/src/main/resources/db/migration/V15__create_auth_device_session.sql`，包含以下核心定义：

```sql
CREATE TABLE auth_device_session (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    client_type VARCHAR(16) NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    device_name VARCHAR(100) NOT NULL,
    access_token_hash CHAR(64) NOT NULL,
    refresh_token_hash CHAR(64) NOT NULL,
    access_expires_at TIMESTAMP NOT NULL,
    refresh_expires_at TIMESTAMP NOT NULL,
    status VARCHAR(16) NOT NULL,
    last_active_at TIMESTAMP NOT NULL,
    signed_out_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_auth_session_access_token_hash UNIQUE (access_token_hash),
    CONSTRAINT uk_auth_session_refresh_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);
CREATE INDEX idx_auth_session_user_status ON auth_device_session (user_id, status);
```

- [x] 重新执行 `mvn test -Dtest=FlywayMigrationTest`，预期 V1-V15 可在本地 H2 空库顺序迁移且测试通过。

## 任务 2：密码与会话应用服务

- [x] 新建 `server/src/test/java/com/lingdong/learning/auth/application/AuthenticationApplicationServiceTest.java`，先覆盖以下失败场景：
  - 仅 `SYS_ADMIN` 能为 `UserType.PLATFORM` 用户设置符合规则的密码，保存结果为 BCrypt 散列而非明文。
  - 密码登录只允许启用的平台用户；错误密码、停用用户和未设置密码统一抛出认证失败。
  - 登录返回 19 位会话标识和两类不同原始令牌，数据库只保存 64 位 SHA-256 摘要。
  - 刷新令牌轮换后旧刷新令牌失效；退出、指定设备下线和一键下线后访问令牌失效；用户不能下线他人的会话。
- [x] 执行 `mvn test -Dtest=AuthenticationApplicationServiceTest`，预期因认证服务、领域模型和会话映射器尚不存在而失败。
- [x] 新建领域类型：

```java
public enum AuthClientType { WEB }
public enum DeviceSessionStatus { ACTIVE, SIGNED_OUT, REVOKED, EXPIRED }
public record DeviceSessionRecord(
        Long id, Long userId, AuthClientType clientType, String deviceId, String deviceName,
        String accessTokenHash, String refreshTokenHash, LocalDateTime accessExpiresAt,
        LocalDateTime refreshExpiresAt, DeviceSessionStatus status, LocalDateTime lastActiveAt,
        LocalDateTime signedOutAt, LocalDateTime createdAt, LocalDateTime updatedAt
) { }
```

- [x] 扩展 `UserMapper` 与 XML，新增参数绑定的 `updatePasswordHash(id, passwordHash)`；不新增密码表，不在业务服务拼接 SQL。
- [x] 新建 `DeviceSessionMapper` 与 `mapper/auth/DeviceSessionMapper.xml`，提供 `insert`、按访问/刷新摘要查找、刷新时条件更新、当前用户活动会话查询、会话归属撤销、全部撤销和过期条件查询。所有状态更新必须带 `status = 'ACTIVE'` 条件。
- [x] 新建 `PasswordPolicy`、`SessionTokenService` 与 `AuthenticationApplicationService`。令牌生成使用 `SecureRandom`，摘要使用 SHA-256 十六进制编码，密码使用 `BCryptPasswordEncoder`，默认访问期限 30 分钟、刷新期限 7 天；生产配置通过 `lingdong.auth` 覆盖，测试配置使用相同安全算法但缩短测试无关参数。
- [x] 实现以下最小用例，不引入短信、微信或学生登录：`setPlatformUserPassword`、`loginByPassword`、`refreshSession`、`logoutCurrentSession`、`currentUser`、`listCurrentUserDevices`、`signOutDevice`、`signOutAllDevices`。
- [x] 重新执行 `mvn test -Dtest=AuthenticationApplicationServiceTest`，预期全部通过。

## 任务 3：Spring Security 请求身份链与认证接口

- [x] 新建 `server/src/test/java/com/lingdong/learning/auth/web/AuthenticationControllerTest.java`，先覆盖以下失败 HTTP 行为：
  - 匿名访问 `GET /api/v1/auth/me` 返回 401 与 `AUTH_REQUIRED`。
  - `GET /api/v1/health` 保持 200 且不受认证拦截。
  - 密码登录成功后，Bearer 访问凭证可访问 `/api/v1/auth/me` 并只返回用户标识、账号名、展示名、角色代码、会话标识和客户端类型。
  - 刷新、退出和设备下线接口均从请求身份读取会话，不接受客户端传入的用户标识作为授权依据。
- [x] 执行 `mvn test -Dtest=AuthenticationControllerTest`，预期因缺少安全配置、控制器和过滤器而失败。
- [x] 在 `server/pom.xml` 引入 `spring-boot-starter-security`，新增 `SecurityConfiguration`：无状态、关闭 CSRF、健康检查与登录/刷新公开、其余 `/api/v1/**` 必须认证。
- [x] 新建 Bearer 会话过滤器和当前认证主体：过滤器从 `Authorization` 提取访问凭证，调用认证服务验证活动会话后建立 `Authentication`；无效凭证交由统一 401 JSON 响应处理，不输出令牌或内部异常。
- [x] 新建 `AuthenticationController` 与请求/响应 DTO，严格实现设计中的七个接口。仅返回必要字段；密码、原始令牌只在登录和刷新成功响应中返回，不进入日志或设备列表。
- [x] 新建认证入口、认证失败和拒绝访问处理器，响应至少含 `code`、`message` 和 `traceId`；`AUTH_REQUIRED` 返回 401，`ACCESS_DENIED` 返回 403。
- [x] 重新执行 `mvn test -Dtest=AuthenticationControllerTest`，预期全部通过。

## 任务 4：文档、静态检查与完整回归

- [x] 将 V15 会话表、访问令牌摘要、刷新轮换、设备撤销和已开放的认证接口写入中文 HLD、数据库、Flyway、API、安全与一致性核对文档；明确短信、微信、学生登录和二维码仍未实现。
- [x] 执行 `git diff --check`，预期无空白错误。
- [x] 执行以下迁移主键静态检查，预期没有输出匹配项：

```powershell
rg 'AUTO_INCREMENT|PRIMARY KEY \([^)]*_id' server/src/main/resources/db/migration
```

- [x] 在 `server/` 设置 JDK 17 后执行 `mvn test`，预期所有测试通过；记录真实测试数量与本地 H2/Flyway 限制，不将其表述为共享测试、预生产或生产验证。
