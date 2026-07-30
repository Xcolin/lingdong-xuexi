# 灵动学习数据字典核心实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立由系统管理员维护的数据字典类型和字典项核心能力，支持排序、启停、默认项、关键字典拦截和 Redis 缓存失效。

**Architecture:** 使用 `dictionary` 模块承载领域对象、应用服务、MyBatis XML 和缓存边界。`sys_dictionary_type` 与 `sys_dictionary_item` 由 Flyway V10 创建；读服务缓存启用字典项，写服务在事务提交前后清理同类型缓存。关键字典代码的直接变更被拒绝，等待通用系统任务执行器实现审批后生效。

**Tech Stack:** Spring Boot 3、JDK 17、MyBatis XML、MySQL 8+、Redis Spring Cache、Flyway、JUnit 5/H2。

---

### Task 1: 建立失败测试和迁移验收

**Files:**
- Create: `server/src/test/java/com/lingdong/learning/dictionary/application/DictionaryApplicationServiceTest.java`
- Modify: `server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

- [ ] **Step 1: 写入字典类型、默认项与系统管理员边界的失败测试**

```java
@Test
void letsSystemAdministratorCreateTypeAndKeepsOnlyLatestDefaultItem() {
    User administrator = createUserWithRole("dictionary_admin", "字典管理员", "SYS_ADMIN");
    DictionaryType type = dictionaryApplicationService.createType(
            new CreateDictionaryTypeCommand(administrator.id(), "TASK_CATEGORY", "任务分类", 10));
    DictionaryItem first = dictionaryApplicationService.createItem(
            new CreateDictionaryItemCommand(administrator.id(), type.id(), "READING", "阅读", 10, true));
    DictionaryItem second = dictionaryApplicationService.createItem(
            new CreateDictionaryItemCommand(administrator.id(), type.id(), "MATH", "数学", 20, true));

    assertThat(dictionaryItemMapper.findById(first.id()).defaultItem()).isFalse();
    assertThat(dictionaryItemMapper.findById(second.id()).defaultItem()).isTrue();
}
```

- [ ] **Step 2: 增加 Flyway 表存在性断言**

```java
@Test
void createsDictionaryTablesThroughFlyway() {
    Integer count = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_name in ('sys_dictionary_type', 'sys_dictionary_item')",
            Integer.class);
    assertThat(count).isEqualTo(2);
}
```

- [ ] **Step 3: 运行专测，确认因字典服务和迁移尚不存在而失败**

Run:
```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
mvn test "-Dtest=DictionaryApplicationServiceTest,FlywayMigrationTest"
```

Expected: 编译失败并明确提示 `DictionaryApplicationService`、字典领域类型或 V10 表缺失。

### Task 2: 创建 V10 结构与领域持久化边界

**Files:**
- Create: `server/src/main/resources/db/migration/V10__create_dictionary_tables.sql`
- Create: `server/src/main/java/com/lingdong/learning/dictionary/domain/DictionaryStatus.java`
- Create: `server/src/main/java/com/lingdong/learning/dictionary/domain/DictionaryType.java`
- Create: `server/src/main/java/com/lingdong/learning/dictionary/domain/DictionaryItem.java`
- Create: `server/src/main/java/com/lingdong/learning/dictionary/infrastructure/persistence/DictionaryTypeMapper.java`
- Create: `server/src/main/java/com/lingdong/learning/dictionary/infrastructure/persistence/DictionaryItemMapper.java`
- Create: `server/src/main/resources/mapper/dictionary/DictionaryTypeMapper.xml`
- Create: `server/src/main/resources/mapper/dictionary/DictionaryItemMapper.xml`

- [ ] **Step 1: 创建 V10 表结构**

```sql
CREATE TABLE sys_dictionary_type (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type_code VARCHAR(64) NOT NULL,
    type_name VARCHAR(50) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_dictionary_type_code UNIQUE (type_code)
);

CREATE TABLE sys_dictionary_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type_id BIGINT NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_name VARCHAR(50) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_default TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_dictionary_item_code UNIQUE (type_id, item_code),
    CONSTRAINT fk_sys_dictionary_item_type FOREIGN KEY (type_id) REFERENCES sys_dictionary_type (id)
);

CREATE INDEX idx_sys_dictionary_item_type_status_sort
    ON sys_dictionary_item (type_id, status, sort_order);
```

- [ ] **Step 2: 定义不可变领域对象与 Mapper 需要的读写方法**

```java
public record DictionaryItem(
        Long id, Long typeId, String code, String name, int sortOrder,
        boolean defaultItem, DictionaryStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
}

DictionaryType findByIdForUpdate(@Param("id") Long id);
DictionaryItem findById(@Param("id") Long id);
List<DictionaryItem> findEnabledByTypeCode(@Param("typeCode") String typeCode);
int clearDefaultByTypeId(@Param("typeId") Long typeId);
```

- [ ] **Step 3: 运行迁移专测，确认 V10 表可以由 Flyway 创建**

Run:
```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
mvn test "-Dtest=FlywayMigrationTest"
```

Expected: PASS，Flyway 从空库迁移至 V10。

### Task 3: 先实现字典创建与默认项不变量

**Files:**
- Create: `server/src/main/java/com/lingdong/learning/dictionary/application/CreateDictionaryTypeCommand.java`
- Create: `server/src/main/java/com/lingdong/learning/dictionary/application/CreateDictionaryItemCommand.java`
- Create: `server/src/main/java/com/lingdong/learning/dictionary/application/DictionaryApplicationService.java`
- Modify: `server/src/test/java/com/lingdong/learning/dictionary/application/DictionaryApplicationServiceTest.java`

- [ ] **Step 1: 实现系统管理员校验、编码/名称/排序校验和创建用例**

```java
@Transactional
public DictionaryItem createItem(CreateDictionaryItemCommand command) {
    requireSystemAdministrator(command.operatorId());
    DictionaryType type = dictionaryTypeMapper.findByIdForUpdate(command.typeId());
    requireMutableType(type);
    DictionaryItem item = DictionaryItem.create(
            type.id(), normalizeCode(command.code()), requiredText(command.name(), "字典名称", 50),
            normalizeSortOrder(command.sortOrder()), command.defaultItem());
    if (item.defaultItem()) {
        dictionaryItemMapper.clearDefaultByTypeId(type.id());
    }
    dictionaryItemMapper.insert(item);
    return dictionaryItemMapper.findById(item.id());
}
```

- [ ] **Step 2: 运行字典专测，确认创建默认项测试通过**

Run:
```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
mvn test "-Dtest=DictionaryApplicationServiceTest#letsSystemAdministratorCreateTypeAndKeepsOnlyLatestDefaultItem"
```

Expected: PASS，第二个默认项成为唯一默认项。

### Task 4: 以失败测试驱动启停、缓存失效和关键字典拦截

**Files:**
- Modify: `server/src/test/java/com/lingdong/learning/dictionary/application/DictionaryApplicationServiceTest.java`
- Create: `server/src/main/java/com/lingdong/learning/dictionary/application/UpdateDictionaryItemCommand.java`
- Create: `server/src/main/java/com/lingdong/learning/dictionary/application/DictionaryQueryService.java`
- Create: `server/src/main/java/com/lingdong/learning/dictionary/application/DictionaryItemCache.java`
- Create: `server/src/main/java/com/lingdong/learning/common/config/CacheConfiguration.java`
- Modify: `server/pom.xml`
- Modify: `server/src/main/resources/application.yml`
- Modify: `server/src/test/resources/application-test.yml`

- [ ] **Step 1: 写入禁用后读缓存失效与关键字典被拦截的失败测试**

```java
@Test
void evictsEnabledItemCacheAfterDisablingAnItem() {
    DictionaryItem item = createEnabledItem("TASK_CATEGORY", "READING", true);
    assertThat(dictionaryQueryService.findEnabledItems("TASK_CATEGORY")).extracting(DictionaryItem::code)
            .containsExactly("READING");

    dictionaryApplicationService.updateItem(new UpdateDictionaryItemCommand(
            administrator.id(), item.id(), "阅读", 10, DictionaryStatus.DISABLED, false));

    assertThat(dictionaryQueryService.findEnabledItems("TASK_CATEGORY")).isEmpty();
}

@Test
void rejectsDirectManagementOfKeyDictionaryTypes() {
    assertThatThrownBy(() -> dictionaryApplicationService.createType(
            new CreateDictionaryTypeCommand(administrator.id(), "TASK_STATUS", "任务状态", 10)))
            .isInstanceOf(IllegalStateException.class);
}
```

- [ ] **Step 2: 运行专测，确认因更新/缓存/风险策略尚未实现而失败**

Run:
```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
mvn test "-Dtest=DictionaryApplicationServiceTest"
```

Expected: FAIL，指出缺少 `updateItem`、查询服务或关键字典拦截。

- [ ] **Step 3: 实现更新、Redis 缓存和风险策略**

```java
private static final Set<String> KEY_DICTIONARY_CODES = Set.of(
        "TASK_STATUS", "ROLE_TYPE", "ORGANIZATION_TYPE", "AUDIT_STATUS");

private void requireDirectMutationAllowed(String typeCode) {
    if (KEY_DICTIONARY_CODES.contains(typeCode)) {
        throw new IllegalStateException("关键字典变更必须通过系统任务审批：" + typeCode);
    }
}

private void requireMutableType(DictionaryType type) {
    if (type == null) {
        throw new IllegalArgumentException("字典类型不存在");
    }
    requireDirectMutationAllowed(type.code());
}
```

`DictionaryItemCache` 使用 `@Cacheable(cacheNames = "dictionary-items")` 缓存启用项；写服务在类型或项变更后按类型编码驱逐。生产配置使用 Redis，测试配置使用简单内存缓存，避免测试连接真实 Redis。

- [ ] **Step 4: 运行字典专测，确认启停、缓存失效和关键字典拦截通过**

Run:
```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
mvn test "-Dtest=DictionaryApplicationServiceTest"
```

Expected: PASS。

### Task 5: 全量验证与设计回填

**Files:**
- Modify: `docs/superpowers/plans/2026-07-30-lingdong-learning-dictionary-core.md`
- Modify: `docs/design/12-当前实现一致性核对-V1.0.md`

- [ ] **Step 1: 运行完整后端回归与格式检查**

Run:
```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
mvn test
```

Expected: PASS，Flyway 验证至 V10，既有测试不回归。

- [ ] **Step 2: 更新计划和一致性核对**

将本计划全部勾选为完成；在一致性核对中将数据字典从“未实现”改为“已实现核心服务，HTTP API 依赖认证请求上下文后补齐”。

- [ ] **Step 3: 不提交代码**

本次按用户当前协作方式保留工作区改动，不创建 Git 提交。
