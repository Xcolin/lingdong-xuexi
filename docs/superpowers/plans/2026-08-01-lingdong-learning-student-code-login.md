# 灵动学习学生账号与登录码登录实施计划

> **执行要求：** 本计划按测试驱动方式逐项实施；每个任务先运行失败测试，再完成最小实现并执行回归。当前工作区包含既有未提交改动，实施期间不清理、不回退其他文件，也不在未获用户明确授权时执行 Git 暂存、提交或推送。

**目标：** 完成 V21 学生独立账号、4 位登录码、凭证初始化与重置、图形验证码风控、`MINIAPP` 会话和 uni-app 学生登录闭环。

**架构：** 学生档案继续由 `student` 模块管理，学生身份复用 `sys_user` 和内置 `STUDENT` 角色；账号流水与凭证摘要分别落入新增表。登录认证复用现有可撤销设备会话，增加学生登录专用应用服务、Redis 风控适配器和测试内存适配器。Web 管理接口、小程序公开认证接口和 uni-app 页面保持独立，不复用 Web 前端状态。

**技术栈：** Spring Boot 3.4、JDK 17、MyBatis XML、Flyway、MySQL 8/H2 MySQL 兼容模式、Redis、Spring Security、uni-app、Vue 3、TypeScript。

**执行边界：** 数据库迁移和集成测试只针对本地 H2；不加载 `application-local.yml`，不连接远程 MySQL、Redis、微信、共享测试、预生产或生产环境。生产登录码密钥只接受环境变量或受控密钥配置，测试使用独立固定密钥。

---

## 任务一：建立 V21 数据库迁移基线

**文件：**

- 新增：`server/src/main/resources/db/migration/V21__create_student_code_login.sql`
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

### 步骤 1：先补充失败的 Flyway 迁移测试

在 `FlywayMigrationTest` 增加以下断言：

1. 存在 `auth_student_account_sequence` 和 `auth_student_credential`；
2. 两表 `id` 均为非自增 `BIGINT`；
3. `sequence_year`、`student_user_id` 分别具备唯一约束；
4. 凭证表外键指向 `sys_user.id`；
5. 存在 `STUDENT_CREDENTIAL_INITIALIZE`、`STUDENT_LOGIN_CODE_RESET` 权限及 `PARENT`、`ORG_ADMIN` 默认授权；
6. 存在全局启用的 `STUDENT_CODE_LOGIN` 功能开关；
7. 全表雪花主键总数由 22 调整为 24。

运行：

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -Dtest=FlywayMigrationTest test
```

预期：测试因 V21 表和基础数据不存在而失败。

### 步骤 2：实现 V21 迁移

迁移脚本必须满足：

```sql
CREATE TABLE auth_student_account_sequence (
    id BIGINT NOT NULL,
    sequence_year INT NOT NULL,
    current_value INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_auth_student_account_sequence PRIMARY KEY (id),
    CONSTRAINT uk_auth_student_account_sequence_year UNIQUE (sequence_year),
    CONSTRAINT ck_auth_student_account_sequence_value CHECK (current_value BETWEEN 1 AND 999999)
);
```

`auth_student_credential` 保存 `student_user_id`、`code_hash`、`code_salt`、`key_version`、失败次数、验证码要求、锁定截止时间及审计时间，不保存明文登录码。脚本使用固定 19 位雪花编号写入权限、角色授权和功能开关基础数据，编号不得与 V1-V20 冲突。

### 步骤 3：运行迁移测试并检查脚本

运行：

```powershell
mvn -Dtest=FlywayMigrationTest test
git diff --check -- server/src/main/resources/db/migration/V21__create_student_code_login.sql server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java
```

预期：Flyway 测试通过；仅允许工作区既有的换行提示，不允许空白错误。

---

## 任务二：实现登录码安全生成与带密钥摘要

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/auth/infrastructure/config/StudentLoginCodeProperties.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/infrastructure/security/StudentLoginCodeGenerator.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/infrastructure/security/StudentLoginCodeHasher.java`
- 新增：`server/src/test/java/com/lingdong/learning/auth/infrastructure/security/StudentLoginCodeHasherTest.java`
- 修改：`server/src/main/resources/application.yml`
- 修改：`server/src/test/resources/application-test.yml`

### 步骤 1：编写密码学组件失败测试

测试至少覆盖：

- 生成值始终为 4 位纯数字，并允许前导零；
- 同一登录码在不同盐下摘要不同；
- 正确登录码常量时间比较成功，错误值失败；
- 未配置活动密钥、密钥过短或密钥版本不存在时启动失败；
- 摘要结果不包含原始登录码。

运行：

```powershell
mvn -Dtest=StudentLoginCodeHasherTest test
```

预期：测试因安全组件不存在而失败。

### 步骤 2：实现安全组件

配置结构采用版本化密钥：

```yaml
lingdong:
  auth:
    student-code:
      active-key-version: ${STUDENT_LOGIN_CODE_ACTIVE_KEY_VERSION:v1}
      keys:
        v1: ${STUDENT_LOGIN_CODE_SECRET_V1:}
```

实现要求：

- `SecureRandom` 生成 `0000` 至 `9999`；
- 每次签发生成不少于 16 字节随机盐；
- 使用 `HmacSHA256(服务端密钥, 盐 || UTF-8登录码)`；
- 摘要和盐以 Base64 保存；
- 校验使用 `MessageDigest.isEqual`；
- 配置属性启动时校验活动密钥至少 32 字节；
- 测试配置写入独立测试密钥，主配置不得写真实密钥或默认弱密钥。

### 步骤 3：运行单元测试

运行：

```powershell
mvn -Dtest=StudentLoginCodeHasherTest test
```

预期：全部通过，测试日志和失败信息不出现登录码或密钥。

---

## 任务三：实现年度账号流水与学生身份签发

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/student/domain/StudentAccountSequence.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/domain/IssuedStudentCredential.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/application/StudentAccountAllocator.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/application/StudentIdentityProvisioningService.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/infrastructure/persistence/StudentAccountSequenceMapper.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/infrastructure/persistence/StudentCredentialMapper.java`
- 新增：`server/src/main/resources/mapper/student/StudentAccountSequenceMapper.xml`
- 新增：`server/src/main/resources/mapper/student/StudentCredentialMapper.xml`
- 新增：`server/src/test/java/com/lingdong/learning/student/application/StudentAccountAllocatorTest.java`
- 新增：`server/src/test/java/com/lingdong/learning/student/application/StudentIdentityProvisioningServiceTest.java`

### 步骤 1：编写账号分配失败测试

覆盖：

- 某年度首个账号为 `YY000001`；
- 同年度连续分配严格递增且保持 8 位；
- 不同年度分别从 1 开始；
- `current_value = 999999` 时拒绝分配且不循环；
- 并发分配不重复；
- 所有新增记录主键为 19 位雪花编号。

运行：

```powershell
mvn -Dtest=StudentAccountAllocatorTest test
```

预期：因 Mapper 和分配器不存在而失败。

### 步骤 2：实现数据库原子分配

Mapper 使用参数绑定和行锁：

```java
StudentAccountSequence findByYearForUpdate(int sequenceYear);
int insert(StudentAccountSequence sequence);
int updateCurrentValue(Long id, int expectedValue, int nextValue);
```

首次创建年度记录出现唯一约束竞争时，只重试一次读取；更新使用期望旧值保护。账号格式由完整年份后两位和 6 位左补零流水组成，不从雪花主键、组织编号或 Redis 派生。

### 步骤 3：编写学生身份签发失败测试

覆盖一次签发同时创建：

- `sys_user.type = STUDENT`、8 位唯一 `username`、空 `password_hash`；
- 内置 `STUDENT` 角色授权；
- `auth_student_credential` 带盐摘要；
- 响应对象仅本次包含明文登录码；
- 任一步异常时事务不留下用户、角色授权或凭证半成品。

### 步骤 4：实现身份签发服务

`StudentIdentityProvisioningService.issue(String studentName)` 返回：

```java
public record IssuedStudentCredential(
        Long studentUserId,
        String studentAccount,
        String plainLoginCode
) {}
```

服务复用 `User`、`UserMapper`、`RoleMapper`、`UserRoleMapper` 和雪花 `IdGenerator`，不得调用平台密码接口。凭证入库后只将明文保留在当前方法返回值中，不写日志、不缓存。

### 步骤 5：运行身份与流水测试

运行：

```powershell
mvn -Dtest=StudentAccountAllocatorTest,StudentIdentityProvisioningServiceTest test
```

预期：全部通过。

---

## 任务四：把账号签发接入学生创建事务

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/student/application/CreatedStudent.java`
- 修改：`server/src/main/java/com/lingdong/learning/student/domain/Student.java`
- 修改：`server/src/main/java/com/lingdong/learning/student/application/StudentApplicationService.java`
- 修改：`server/src/main/java/com/lingdong/learning/student/web/StudentManagementController.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/web/CreatedStudentResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/student/web/StudentResponse.java`
- 修改：`server/src/test/java/com/lingdong/learning/student/web/StudentManagementControllerTest.java`

### 步骤 1：扩展学生创建接口失败测试

对家长和机构创建两条路径分别断言：

- 响应含 `studentAccount` 与 `initialLoginCode`；
- 账号匹配 `^\\d{8}$`，登录码匹配 `^\\d{4}$`；
- `studentUserId` 已写入学生档案；
- 家长创建的无机构学生同样生成账号；
- 详情和目录接口不返回 `initialLoginCode`；
- Web 密码登录接口拒绝学生账号。

运行：

```powershell
mvn -Dtest=StudentManagementControllerTest test
```

预期：新增断言失败。

### 步骤 2：接入同一事务

调整创建流程为：先通过现有角色和机构范围校验，再签发学生身份和凭证，随后创建带 `studentUserId` 的学生档案及首个关系。`StudentApplicationService.createStudent` 返回 `CreatedStudent`，Controller 使用专用创建响应；`StudentResponse` 继续只表示可重复查询的非敏感档案。

任何步骤失败必须由现有 `@Transactional` 整体回滚。

### 步骤 3：运行学生创建回归

运行：

```powershell
mvn -Dtest=StudentManagementControllerTest test
```

预期：原有数据范围断言与新增账号断言全部通过。

---

## 任务五：实现历史初始化和登录码重置

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/student/application/StudentCredentialManagementService.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/application/StudentCredentialIssueResult.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/application/StudentCredentialStateConflictException.java`
- 修改：`server/src/main/java/com/lingdong/learning/student/infrastructure/persistence/StudentMapper.java`
- 修改：`server/src/main/resources/mapper/student/StudentMapper.xml`
- 修改：`server/src/main/java/com/lingdong/learning/student/infrastructure/persistence/ParentStudentMapper.java`
- 修改：`server/src/main/resources/mapper/student/ParentStudentMapper.xml`
- 修改：`server/src/main/java/com/lingdong/learning/student/web/StudentManagementController.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/web/StudentCredentialIssueResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/common/web/ApiExceptionHandler.java`
- 新增：`server/src/test/java/com/lingdong/learning/student/web/StudentCredentialManagementControllerTest.java`

### 步骤 1：编写权限和状态失败测试

覆盖：

- 活动主家长可初始化历史学生和重置登录码；
- 学生当前活动机构的直接机构管理员可执行；
- 副家长、跨家庭家长、上级区域管理员、跨机构管理员、已离校机构、教师、系统管理员均被拒绝；
- 无对象范围统一返回 `404 RESOURCE_NOT_FOUND`；
- 已初始化学生重复初始化返回 `409 STATE_CONFLICT`；
- 未初始化学生重置返回 `409 STATE_CONFLICT`；
- 两个并发初始化请求只成功一个；
- 初始化和重置响应明文只出现一次。

### 步骤 2：实现对象范围与条件更新

新增 Mapper 能力：

```java
int bindStudentUserIfAbsent(Long studentId, Long studentUserId);
boolean existsActivePrimaryByParentAndStudent(Long parentUserId, Long studentId);
```

机构范围继续复用“直接机构管理员 + 学生当前活动机构”的现有查询。RBAC 权限只是入口校验，应用服务必须再次校验对象关系。

### 步骤 3：实现初始化和重置事务

- 初始化：锁定学生或使用条件更新，签发身份后绑定 `student_user_id`；条件更新失败时回滚签发结果并返回状态冲突。
- 重置：生成新登录码和新盐，更新摘要、密钥版本及风控状态，并调用 `AuthenticationApplicationService.revokeAllActiveSessionsForUser`；任一步失败整体回滚。
- 两个接口分别使用 `STUDENT_CREDENTIAL_INITIALIZE` 和 `STUDENT_LOGIN_CODE_RESET`。

### 步骤 4：运行管理接口测试

运行：

```powershell
mvn -Dtest=StudentCredentialManagementControllerTest test
```

预期：范围、并发、一次性明文和会话撤销测试全部通过。

---

## 任务六：实现验证码与预认证限流基础设施

**文件：**

- 修改：`server/pom.xml`
- 新增：`server/src/main/java/com/lingdong/learning/auth/application/StudentLoginProtectionStore.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/application/CaptchaChallengeService.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/application/IssuedCaptchaChallenge.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/infrastructure/captcha/CaptchaImageGenerator.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/infrastructure/captcha/EasyCaptchaImageGenerator.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/infrastructure/redis/RedisStudentLoginProtectionStore.java`
- 新增：`server/src/test/java/com/lingdong/learning/auth/infrastructure/InMemoryStudentLoginProtectionStore.java`
- 新增：`server/src/test/java/com/lingdong/learning/auth/application/CaptchaChallengeServiceTest.java`
- 新增：`server/src/test/java/com/lingdong/learning/auth/application/StudentLoginRateLimitTest.java`
- 修改：`server/src/main/resources/application.yml`
- 修改：`server/src/test/resources/application-test.yml`

### 步骤 1：编写验证码存储契约测试

测试：

- 挑战一次消费；
- 过期后失效；
- 绑定账号摘要和设备标识，跨账号、跨设备失败；
- 答案错误也消费当前挑战；
- 存储异常转换为 `AUTH_PROTECTION_UNAVAILABLE`；
- 登录和验证码申请达到阈值时返回限流结果。

### 步骤 2：实现图形生成与抽象

仅引入 `com.github.whvcse:easy-captcha:1.6.2` 作为可替换的图片生成组件，通过 `CaptchaImageGenerator` 隔离第三方 API。应用自身负责挑战标识、答案摘要、绑定、一次性消费、TTL 和限流；不得使用组件的 HttpSession 能力。

### 步骤 3：实现 Redis 与测试适配器

生产适配器使用 `StringRedisTemplate` 和 Lua/原子操作实现：

- `captcha:{challengeId}` 短 TTL 一次性消费；
- `student-login:account-device:{digest}` 每分钟 10 次；
- `student-login:source:{digest}` 每分钟 60 次；
- `student-captcha:account-device:{digest}` 每 5 分钟 10 次。

键只保存不可逆摘要，不保存账号原文、登录码、验证码答案或令牌。`test` Profile 使用线程安全内存适配器和可控时间，不访问真实 Redis。

### 步骤 4：运行风控基础测试

运行：

```powershell
mvn -Dtest=CaptchaChallengeServiceTest,StudentLoginRateLimitTest test
```

预期：全部通过，测试过程不建立远程 Redis 连接。

---

## 任务七：实现学生登录状态机与小程序会话

**文件：**

- 修改：`server/src/main/java/com/lingdong/learning/auth/domain/AuthClientType.java`
- 修改：`server/src/main/java/com/lingdong/learning/auth/application/AuthenticationApplicationService.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/application/StudentCodeLoginCommand.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/application/StudentCodeLoginApplicationService.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/application/StudentAuthenticationFailedException.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/application/CaptchaRequiredException.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/application/StudentAccountLockedException.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/application/RateLimitedException.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/application/AuthProtectionUnavailableException.java`
- 修改：`server/src/main/java/com/lingdong/learning/student/infrastructure/persistence/StudentCredentialMapper.java`
- 修改：`server/src/main/resources/mapper/student/StudentCredentialMapper.xml`
- 新增：`server/src/test/java/com/lingdong/learning/auth/application/StudentCodeLoginApplicationServiceTest.java`
- 修改：`server/src/test/java/com/lingdong/learning/auth/application/AuthenticationApplicationServiceTest.java`

### 步骤 1：编写登录状态机失败测试

严格覆盖：

1. 正确账号和登录码创建 `MINIAPP` 会话；
2. 平台账号、未知账号、停用学生用户、停用学生档案和错误登录码统一失败；
3. 第 1 至 4 次错误返回 401；
4. 第 5 次起返回验证码要求；
5. 第 6 至 9 次必须先成功消费验证码，错误登录码继续累计；
6. 第 10 次锁定 15 分钟并返回截止时间；
7. 锁定期内不比较登录码、不累计失败；
8. 锁定到期后开始新周期；
9. 成功登录清零失败状态；
10. 验证码错误不增加登录码失败次数；
11. 多并发错误请求不丢失失败计数；
12. 已要求验证码而保护存储不可用时失败关闭。

### 步骤 2：实现凭证行锁状态机

对已存在账号，在事务中锁定学生凭证行后读取和更新失败状态，避免“先查后写”丢失并发更新。未知账号仍执行一次固定虚拟 HMAC 比较，降低明显时序差异。所有认证失败不回显账号是否存在。

### 步骤 3：泛化现有设备会话创建和校验

将私有会话创建方法改为显式接收 `AuthClientType`。校验规则：

- `WEB` 会话只接受启用的平台用户；
- `MINIAPP` 会话只接受启用的学生用户，且关联学生档案必须启用；
- 刷新、访问令牌认证和主动退出继续复用现有会话表；
- 学生停用或登录码重置后旧会话均不可继续使用。

### 步骤 4：运行认证服务测试

运行：

```powershell
mvn -Dtest=StudentCodeLoginApplicationServiceTest,AuthenticationApplicationServiceTest test
```

预期：登录状态机和原 Web 密码会话测试同时通过。

---

## 任务八：公开能力、验证码和学生登录 HTTP 接口

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/auth/web/StudentAuthenticationController.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/web/StudentCaptchaRequest.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/web/StudentCaptchaResponse.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/web/StudentCodeLoginRequest.java`
- 新增：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityController.java`
- 新增：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/auth/web/AuthenticationExceptionHandler.java`
- 修改：`server/src/main/java/com/lingdong/learning/common/security/SecurityConfiguration.java`
- 新增：`server/src/test/java/com/lingdong/learning/auth/web/StudentAuthenticationControllerTest.java`
- 新增：`server/src/test/java/com/lingdong/learning/feature/web/PublicCapabilityControllerTest.java`

### 步骤 1：编写 HTTP 契约失败测试

覆盖：

- `GET /api/v1/public/capabilities?client=MINIAPP` 无需登录；
- `POST /api/v1/auth/student-captchas` 无需登录但受功能开关与限流保护；
- `POST /api/v1/auth/student-sessions/code` 无需现有会话；
- 登录成功返回既有会话响应结构；
- 401、423、428、429、503 使用设计约定错误码；
- 19 位会话主键以 JSON 字符串返回；
- 错误响应不包含登录码、验证码答案、用户主键或凭证摘要；
- 功能开关关闭后能力摘要为关闭，验证码和登录接口拒绝新请求。

### 步骤 2：实现公开路由和异常映射

Security 仅放行明确的三个公开入口及既有密码/刷新入口。来源限流默认使用 `request.getRemoteAddr()`；在未配置可信反向代理前不直接信任客户端可伪造的 `X-Forwarded-For`。

异常映射：

- `STUDENT_AUTH_FAILED` -> 401；
- `STUDENT_ACCOUNT_LOCKED` -> 423，附锁定截止时间；
- `CAPTCHA_REQUIRED` -> 428；
- `RATE_LIMITED` -> 429；
- `AUTH_PROTECTION_UNAVAILABLE` -> 503。

### 步骤 3：运行 Web 层认证测试

运行：

```powershell
mvn -Dtest=StudentAuthenticationControllerTest,PublicCapabilityControllerTest test
```

预期：HTTP 契约全部通过。

---

## 任务九：实现 uni-app 学生登录体验

**文件：**

- 修改：`miniapp/src/api/http.ts`
- 新增：`miniapp/src/api/auth.ts`
- 新增：`miniapp/src/api/capability.ts`
- 新增：`miniapp/src/session/student-session.ts`
- 修改：`miniapp/src/pages/index/index.vue`
- 新增：`miniapp/src/pages/student-login/student-login.vue`
- 新增：`miniapp/src/pages/student-home/student-home.vue`
- 修改：`miniapp/src/pages.json`
- 修改：`miniapp/src/App.vue`

### 步骤 1：建立类型检查基线

运行：

```powershell
npm run type-check
```

预期：当前基线通过；后续每个页面改动都不得引入新的类型错误。

### 步骤 2：实现独立小程序认证封装

`http.ts` 增加结构化 `ApiError` 和可选 Bearer 令牌；不得引用 Web 的 `sessionStorage`、React 路由或 Web API 模块。`student-session.ts` 只保存访问令牌、刷新令牌及到期时间，不保存学生登录码和验证码答案。

### 步骤 3：实现入口、登录和登录后应用壳

- 首页读取公开能力摘要；关闭时不渲染登录入口；
- 学生登录页校验 8 位账号、4 位登录码，返回 428 后才渲染验证码；
- 验证码支持刷新，提交后清空答案；
- 锁定时显示服务端截止时间并禁用提交；
- 成功后清空登录码状态、保存会话并进入学生应用壳；
- 401 不区分账号不存在和登录码错误；
- 页面直接访问时仍重新检查能力开关，避免仅隐藏首页入口。

界面采用适合学生和家长共同操作的清晰高对比布局，输入框、按钮和验证码区域使用稳定尺寸；不得通过页面文字介绍功能或安全规则。

### 步骤 4：执行小程序构建验证

运行：

```powershell
npm run type-check
npm run build:h5
npm run build:mp-weixin
```

预期：类型检查、H5 构建和微信小程序构建全部通过。

### 步骤 5：执行视觉和交互检查

启动 H5 开发服务，在窄屏与桌面模拟视口检查：

- 无文字、按钮、验证码图片重叠；
- 功能关闭状态不出现登录入口；
- 验证码出现前后页面不发生不可控跳动；
- 最长错误信息可换行且不遮挡操作；
- 登录码输入不被持久化或回显到控制台。

完成后停止本次临时开发服务，不占用用户已有端口。

---

## 任务十：同步中文设计文档和接口约束

**文件：**

- 修改：`docs/design/01-功能详细设计-FSD-V1.0.md`
- 修改：`docs/design/04-数据库设计-V1.0.md`
- 修改：`docs/design/05-Flyway迁移规范-V1.0.md`
- 修改：`docs/design/06-API接口设计-V1.0.md`
- 修改：`docs/design/07-权限与安全设计-V1.0.md`
- 修改：`docs/design/10-测试方案与验收用例-V1.0.md`
- 修改：`docs/design/12-当前实现一致性核对-V1.0.md`

### 步骤 1：按实际代码同步文档

只记录已经实现并验证的内容，至少补齐：

- 学生账号规则、一次性登录码展示和历史初始化；
- 两张新增表、索引、约束和雪花主键；
- V21 迁移边界；
- 五个新增/扩展接口及错误码；
- HMAC、验证码、限流、锁定、会话撤销和功能开关；
- 角色、对象范围和越权验收用例；
- 未实现的扫码、微信绑定和短信能力继续明确列为待建设。

所有说明使用中文；代码标识、接口路径、字段名和标准技术名称保留原文。

### 步骤 2：文档一致性检查

运行：

```powershell
rg -n "TODO|TBD|待确认|明文保存|数据库自增" docs/design docs/superpowers/specs/2026-08-01-lingdong-learning-student-code-login-design.md
git diff --check -- docs/design docs/superpowers
```

预期：没有遗留占位符或与 V21 冲突的描述。

---

## 任务十一：执行全量回归与安全收口

**文件：**

- 核对：`server/`
- 核对：`web/`
- 核对：`miniapp/`
- 核对：`docs/`

### 步骤 1：后端全量回归

运行：

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn test
```

预期：V1-V21 在本地 H2 完整迁移，全部后端测试通过，日志中没有远程 MySQL、Redis 或微信连接尝试。

### 步骤 2：Web 回归

V21 不新增 Web 页面，但要保证管理端既有功能未回归：

```powershell
npx vitest run --pool=forks --maxWorkers=1 --minWorkers=1
npm run type-check
npm run build
```

预期：Web 测试、类型检查和构建全部通过。

### 步骤 3：小程序回归

```powershell
npm run type-check
npm run build:mp-weixin
```

预期：uni-app 类型检查和微信小程序构建通过。

### 步骤 4：静态安全核查

运行定向搜索并人工复核：

```powershell
rg -n "app-secret|DB_PASSWORD|REDIS_PASSWORD|student.*code|plainLoginCode|accessToken|refreshToken" server/src/main server/src/test miniapp/src docs
git diff --check
git status --short
```

核查标准：

- 未新增真实数据库、Redis、微信或 HMAC 密钥；
- 未记录原始登录码、验证码答案和令牌；
- 生产配置没有弱默认登录码密钥；
- 所有新增表主键为应用层 19 位雪花 `BIGINT`；
- 所有公开入口均有功能开关、限流或认证边界；
- 只汇总本轮修改，不处理工作区既有未提交文件。

### 步骤 5：形成验收结果

最终记录：新增测试数、后端总测试数、Web 测试数、构建结果、未执行的远程联调项和剩余风险。任何命令未运行或失败都必须如实说明，不以“理论可行”替代验证结果。
