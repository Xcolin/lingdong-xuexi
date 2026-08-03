# 灵动学习缓存管理实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 建立系统管理员可按缓存类型刷新或清除、可追溯记录结果，并对全量清除和用户会话清除执行系统审核的缓存管理核心能力。

**Architecture:** 新增独立 `cache` 模块，使用 `CacheDomain`、`CacheOperationType`、`CacheOperationStatus` 描述固定的业务缓存范围和操作结果，并以 `ManagedCacheHandler` 隔离各缓存域的实际实现。普通模块操作同步执行并写入台账；全量清除和用户会话清除先创建 `CACHE_CLEAR` 系统任务，审核通过后才执行，失败时保留失败台账且不把任务标记为已生效。当前仅字典缓存已真实接入 Redis，未接入的权限、组织、功能开关、会话和统计缓存会明确记录为失败，不能伪造成功。

**Tech Stack:** Spring Boot 3、JDK 17、Spring Cache/Redis、MyBatis XML、MySQL 8+、Flyway、JUnit 5/H2。

---

### Task 1: 建立缓存管理失败测试

**Files:**
- Create: `server/src/test/java/com/lingdong/learning/cache/application/CacheOperationApplicationServiceTest.java`

- [x] **Step 1: 写入字典缓存刷新、全量清除审批和失败留痕的失败测试**

```java
@Test
void refreshesDictionaryCacheAndRecordsSucceededOperation() {
    User administrator = createUserWithRole("cache_dictionary_admin", "缓存管理员", "SYS_ADMIN");
    DictionaryType type = createDictionaryTypeWithItem(administrator, "TASK_SOURCE", "来源", "FAMILY", "家庭");
    dictionaryQueryService.findEnabledItems(type.code());

    CacheOperation operation = cacheOperationApplicationService.execute(
            new ExecuteCacheOperationCommand(
                    administrator.id(), CacheDomain.DICTIONARY, CacheOperationType.REFRESH, "刷新任务来源字典缓存"));

    assertThat(operation.status()).isEqualTo(CacheOperationStatus.SUCCEEDED);
    assertThat(cacheManager.getCache(DictionaryItemCache.CACHE_NAME).get(type.code())).isNotNull();
}

@Test
void requiresSystemTaskBeforeClearingAllCaches() {
    User administrator = createUserWithRole("cache_global_admin", "全量缓存管理员", "SYS_ADMIN");
    User auditor = createUserWithRole("cache_global_auditor", "全量缓存审核员", "SYS_AUDITOR");

    CacheOperation draft = cacheOperationApplicationService.createHighRiskDraft(
            new CreateHighRiskCacheOperationCommand(
                    administrator.id(), CacheDomain.ALL, CacheOperationType.CLEAR,
                    "全量清除缓存", "发布后清除全部已注册缓存", true));
    cacheOperationApplicationService.submit(draft.taskId(), administrator.id());
    CacheOperation executed = cacheOperationApplicationService.approveAndExecute(
            draft.taskId(), auditor.id(), "同意清理");

    assertThat(executed.status()).isEqualTo(CacheOperationStatus.SUCCEEDED);
    assertThat(systemTaskMapper.findById(draft.taskId()).status()).isEqualTo(SystemTaskStatus.EFFECTIVE);
}

@Test
void recordsFailureWhenNoHandlerExistsForRequestedCacheDomain() {
    User administrator = createUserWithRole("cache_permission_admin", "权限缓存管理员", "SYS_ADMIN");

    CacheOperation operation = cacheOperationApplicationService.execute(
            new ExecuteCacheOperationCommand(
                    administrator.id(), CacheDomain.PERMISSION, CacheOperationType.CLEAR, "清除权限缓存"));

    assertThat(operation.status()).isEqualTo(CacheOperationStatus.FAILED);
    assertThat(operation.failureMessage()).contains("未注册");
    assertThat(cacheOperationMapper.findById(operation.id()).status()).isEqualTo(CacheOperationStatus.FAILED);
}

private DictionaryType createDictionaryTypeWithItem(
        User administrator, String typeCode, String typeName, String itemCode, String itemName) {
    DictionaryType type = dictionaryApplicationService.createType(
            new CreateDictionaryTypeCommand(administrator.id(), typeCode, typeName, 10));
    dictionaryApplicationService.createItem(
            new CreateDictionaryItemCommand(administrator.id(), type.id(), itemCode, itemName, 10, true));
    return type;
}

private User createUserWithRole(String username, String displayName, String roleCode) {
    User user = userAccessApplicationService.createUser(
            new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
    Role role = roleMapper.findByCode(roleCode);
    userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
    return user;
}
```

- [x] **Step 2: 运行专测，确认因缓存管理类型、服务和表尚不存在而失败**

Run:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
mvn test "-Dtest=CacheOperationApplicationServiceTest"
```

Expected: 编译失败，明确指出缺少 `CacheOperationApplicationService`、缓存操作命令、领域类型或 Mapper。

### Task 2: 创建 V11 缓存操作台账和领域模型

**Files:**
- Create: `server/src/main/resources/db/migration/V11__create_cache_operation_log.sql`
- Create: `server/src/main/java/com/lingdong/learning/cache/domain/CacheDomain.java`
- Create: `server/src/main/java/com/lingdong/learning/cache/domain/CacheOperationType.java`
- Create: `server/src/main/java/com/lingdong/learning/cache/domain/CacheOperationStatus.java`
- Create: `server/src/main/java/com/lingdong/learning/cache/domain/CacheOperation.java`
- Create: `server/src/main/java/com/lingdong/learning/cache/infrastructure/persistence/CacheOperationMapper.java`
- Create: `server/src/main/resources/mapper/cache/CacheOperationMapper.xml`
- Modify: `server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

- [x] **Step 1: 创建受控缓存操作台账表**

```sql
CREATE TABLE sys_cache_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operation_code VARCHAR(36) NOT NULL,
    task_id BIGINT,
    cache_domain VARCHAR(32) NOT NULL,
    operation_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    impact_description VARCHAR(1000) NOT NULL,
    requested_by BIGINT NOT NULL,
    executed_by BIGINT,
    failure_message VARCHAR(1000),
    executed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_cache_operation_code UNIQUE (operation_code),
    CONSTRAINT uk_sys_cache_operation_task UNIQUE (task_id),
    CONSTRAINT fk_sys_cache_operation_task FOREIGN KEY (task_id) REFERENCES sys_system_task (id),
    CONSTRAINT fk_sys_cache_operation_requester FOREIGN KEY (requested_by) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_cache_operation_executor FOREIGN KEY (executed_by) REFERENCES sys_user (id)
);

CREATE INDEX idx_sys_cache_operation_created_at ON sys_cache_operation_log (created_at);
CREATE INDEX idx_sys_cache_operation_domain_status ON sys_cache_operation_log (cache_domain, status);
```

`CacheDomain` 固定为 `PERMISSION`、`DICTIONARY`、`ORGANIZATION`、`FEATURE_TOGGLE`、`USER_SESSION`、`BUSINESS_STATISTICS`、`ALL`；`CacheOperationType` 固定为 `CLEAR`、`REFRESH`；状态固定为 `PENDING`、`SUCCEEDED`、`FAILED`。
`CacheOperation` 同时保存 `operationCode`，应用服务用 UUID 生成该值、插入后通过 `findByCode` 回读，避免依赖数据库私有的主键回填语法。

- [x] **Step 2: 为 V11 添加 Flyway 表存在性断言并运行迁移专测**

```java
@Test
void createsCacheOperationTableThroughFlyway() {
    Integer count = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_name = 'sys_cache_operation_log'",
            Integer.class);
    assertThat(count).isEqualTo(1);
}
```

Run:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
mvn test "-Dtest=FlywayMigrationTest"
```

Expected: PASS，空库迁移至 V11，台账表存在。

### Task 3: 实现缓存处理器边界与普通模块操作

**Files:**
- Create: `server/src/main/java/com/lingdong/learning/cache/application/ManagedCacheHandler.java`
- Create: `server/src/main/java/com/lingdong/learning/cache/application/DictionaryCacheHandler.java`
- Create: `server/src/main/java/com/lingdong/learning/cache/application/ExecuteCacheOperationCommand.java`
- Create: `server/src/main/java/com/lingdong/learning/cache/application/CacheOperationApplicationService.java`
- Modify: `server/src/main/java/com/lingdong/learning/dictionary/infrastructure/persistence/DictionaryTypeMapper.java`
- Modify: `server/src/main/resources/mapper/dictionary/DictionaryTypeMapper.xml`

- [x] **Step 1: 定义缓存域处理器契约**

```java
public interface ManagedCacheHandler {
    CacheDomain domain();

    void clear();

    void refresh();
}
```

`DictionaryCacheHandler.clear()` 只清理 `dictionary-items`；`refresh()` 先清理该缓存，再读取所有启用的字典类型编码并通过 `DictionaryQueryService.findEnabledItems` 预热。为此在 `DictionaryTypeMapper` 增加 `List<String> findEnabledCodes()`，SQL 固定按类型排序返回 `status = 'ENABLED'` 的编码。

- [x] **Step 2: 实现普通模块缓存操作和失败记录**

```java
@Transactional
public CacheOperation execute(ExecuteCacheOperationCommand command) {
    requireSystemAdministrator(command.operatorId());
    requireDirectOperationAllowed(command.cacheDomain());
    String operationCode = UUID.randomUUID().toString();
    cacheOperationMapper.insert(CacheOperation.pending(
            operationCode, null, command.cacheDomain(), command.operationType(),
            command.impactDescription(), command.operatorId()));
    CacheOperation operation = cacheOperationMapper.findByCode(operationCode);
    return executeAndRecord(operation, command.operatorId());
}
```

`requireDirectOperationAllowed` 仅允许非 `ALL`、非 `USER_SESSION` 的缓存域直接操作；处理器不存在或实际清理失败时，`executeAndRecord` 捕获异常、写入 `FAILED` 与不超过 1000 字的失败信息后返回台账记录，不伪造成功。

- [x] **Step 3: 运行字典刷新和未注册处理器失败记录专测**

Run:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
mvn test "-Dtest=CacheOperationApplicationServiceTest#refreshesDictionaryCacheAndRecordsSucceededOperation,CacheOperationApplicationServiceTest#recordsFailureWhenNoHandlerExistsForRequestedCacheDomain"
```

Expected: PASS，字典缓存被预热，未实现的权限缓存保留失败记录。

### Task 4: 实现高风险缓存操作的系统任务流程

**Files:**
- Create: `server/src/main/java/com/lingdong/learning/cache/application/CreateHighRiskCacheOperationCommand.java`
- Modify: `server/src/main/java/com/lingdong/learning/cache/application/CacheOperationApplicationService.java`
- Modify: `server/src/main/java/com/lingdong/learning/cache/infrastructure/persistence/CacheOperationMapper.java`
- Modify: `server/src/main/resources/mapper/cache/CacheOperationMapper.xml`

- [x] **Step 1: 创建全量/用户会话清除的待审核缓存操作**

```java
@Transactional
public CacheOperation createHighRiskDraft(CreateHighRiskCacheOperationCommand command) {
    requireHighRiskOperation(command.cacheDomain(), command.operationType(), command.confirmed());
    SystemTask task = systemTaskApplicationService.createDraft(new CreateSystemTaskCommand(
            command.submitterId(), SystemTaskType.CACHE_CLEAR,
            required(command.title(), "任务标题", 100),
            required(command.description(), "影响说明", 1000), ImpactScope.GLOBAL));
    String operationCode = UUID.randomUUID().toString();
    cacheOperationMapper.insert(CacheOperation.pending(
            operationCode, task.id(), command.cacheDomain(), command.operationType(),
            command.description().trim(), command.submitterId()));
    return cacheOperationMapper.findByCode(operationCode);
}
```

高风险条件固定为：`ALL + CLEAR` 或 `USER_SESSION + CLEAR`；两者必须 `confirmed = true`。不支持 `ALL + REFRESH` 或 `USER_SESSION + REFRESH`，避免以模糊语义执行全局影响操作。

- [x] **Step 2: 审批通过后执行并保留失败结果**

```java
@Transactional
public CacheOperation approveAndExecute(Long taskId, Long auditorId, String comment) {
    systemTaskApplicationService.approve(taskId, auditorId, comment);
    CacheOperation operation = requirePendingOperation(taskId);
    CacheOperation result = executeAndRecord(operation, auditorId);
    if (result.status() == CacheOperationStatus.SUCCEEDED) {
        systemTaskApplicationService.markEffective(taskId);
    }
    return result;
}
```

`ALL + CLEAR` 通过 Spring `CacheManager.getCacheNames()` 清理全部已注册缓存；`USER_SESSION` 在会话缓存尚未实现时返回失败记录，不标记任务已生效。执行失败不能抛出导致审批和失败台账一起回滚。

- [x] **Step 3: 运行全量清除审批专测**

Run:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
mvn test "-Dtest=CacheOperationApplicationServiceTest#requiresSystemTaskBeforeClearingAllCaches"
```

Expected: PASS，草稿提交、审核、全量缓存清理、台账成功和系统任务已生效按顺序完成。

### Task 5: 完整验证与设计回填

**Files:**
- Modify: `docs/superpowers/plans/2026-07-30-lingdong-learning-cache-management.md`
- Modify: `docs/design/03-系统架构设计-HLD-V1.0.md`
- Modify: `docs/design/05-Flyway迁移规范-V1.0.md`
- Modify: `docs/design/12-当前实现一致性核对-V1.0.md`

- [x] **Step 1: 运行缓存模块和完整后端回归**

Run:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
mvn test "-Dtest=CacheOperationApplicationServiceTest,FlywayMigrationTest"
mvn test
```

Expected: PASS，Flyway 至 V11，缓存管理用例和既有模块均无回归。

- [x] **Step 2: 回写实现状态**

将本计划所有步骤标为完成；在一致性核对中记录：字典缓存可刷新/清除、全量清除走审批、未实现缓存域会留下失败台账；认证与会话缓存仍未实现，不能把 `USER_SESSION` 写为可成功执行。

- [x] **Step 3: 不提交代码**

按当前协作方式保留工作区改动，不创建 Git 提交。
