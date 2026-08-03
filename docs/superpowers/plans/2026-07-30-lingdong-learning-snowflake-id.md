# Lingdong Learning Snowflake ID Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every current Lingdong Learning table use a 19-digit Snowflake `BIGINT` primary key and make every runtime write allocate that ID in the application layer.

**Architecture:** Because V1-V11 have not run in any shared database, rebuild the Flyway baseline in place: each table has `id BIGINT NOT NULL PRIMARY KEY`, and legacy association keys become unique constraints. A synchronized `IdGenerator` abstraction produces IDs before MyBatis writes; static Flyway data uses precomputed outputs from the same bit layout.

**Tech Stack:** Java 17, Spring Boot 3.4, MyBatis XML, Flyway, MySQL 8, H2 test database, JUnit 5, AssertJ.

---

### Task 1: Define and test the Snowflake generator

**Files:**
- Create: `server/src/main/java/com/lingdong/learning/common/id/IdGenerator.java`
- Create: `server/src/main/java/com/lingdong/learning/common/id/SnowflakeIdGenerator.java`
- Modify: `server/src/main/resources/application.yml`
- Test: `server/src/test/java/com/lingdong/learning/common/id/SnowflakeIdGeneratorTest.java`

- [ ] **Step 1: Write failing generator tests**

```java
@Test
void generatesDistinctNineteenDigitIdsWithinOneMillisecond() {
    SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 2, () -> 1_753_840_000_000L);
    long first = generator.nextId();
    long second = generator.nextId();
    assertThat(Long.toString(first)).hasSize(19);
    assertThat(second).isGreaterThan(first);
}

@Test
void rejectsOutOfRangeNodeConfiguration() {
    assertThatIllegalArgumentException().isThrownBy(() -> new SnowflakeIdGenerator(32, 0, () -> 1_753_840_000_000L));
}

@Test
void rejectsClockRollback() {
    AtomicLong time = new AtomicLong(1_753_840_000_000L);
    SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0, 0, time::get);
    generator.nextId();
    time.decrementAndGet();
    assertThatIllegalStateException().isThrownBy(generator::nextId);
}
```

- [ ] **Step 2: Run the focused test and verify it fails because the class does not exist**

Run: `$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; & mvn test "-Dtest=SnowflakeIdGeneratorTest"`

Expected: compilation failure referring to missing `SnowflakeIdGenerator`.

- [ ] **Step 3: Implement the minimal generator**

```java
public interface IdGenerator {
    long nextId();
}

@Component
public final class SnowflakeIdGenerator implements IdGenerator {
    static final long EPOCH = 1_288_834_974_657L;
    private static final long MAX_NODE_ID = 31L;
    private static final long SEQUENCE_MASK = 4_095L;
    // Constructor validates both node IDs; nextId synchronizes timestamp and sequence state.
}
```

Use `System::currentTimeMillis` in the Spring constructor, a package-visible `LongSupplier` constructor for tests, a 22-bit timestamp shift, 17-bit data-center shift, 12-bit worker shift, and `Thread.onSpinWait()` while waiting for the next millisecond after sequence exhaustion.

- [ ] **Step 4: Add runtime configuration and run generator tests**

Add the `lingdong.id.snowflake.datacenter-id` and `worker-id` YAML settings specified in the design. Re-run the Task 1 command; expected result: all `SnowflakeIdGeneratorTest` tests pass.

### Task 2: Lock the database baseline to explicit Snowflake IDs

**Files:**
- Modify: `server/src/main/resources/db/migration/V1__create_system_config.sql`
- Modify: `server/src/main/resources/db/migration/V2__create_iam_rbac_tables.sql`
- Modify: `server/src/main/resources/db/migration/V3__create_organization_types.sql`
- Modify: `server/src/main/resources/db/migration/V4__create_user_organization_relations.sql`
- Modify: `server/src/main/resources/db/migration/V5__create_system_task_audit.sql`
- Modify: `server/src/main/resources/db/migration/V6__create_feature_toggles.sql`
- Modify: `server/src/main/resources/db/migration/V7__create_feature_toggle_changes.sql`
- Modify: `server/src/main/resources/db/migration/V8__create_user_permissions.sql`
- Modify: `server/src/main/resources/db/migration/V9__create_role_data_scopes.sql`
- Modify: `server/src/main/resources/db/migration/V10__create_dictionary_tables.sql`
- Modify: `server/src/main/resources/db/migration/V11__create_cache_operation_log.sql`
- Test: `server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

- [ ] **Step 1: Write failing schema and seed-data assertions**

```java
@Test
void createsEveryCurrentTableWithAnExplicitNonIdentityBigintPrimaryId() {
    Integer idColumnCount = jdbcTemplate.queryForObject("""
        select count(*) from information_schema.columns
        where table_name in ('sys_config','sys_organization','sys_user','sys_role','sys_permission',
          'sys_user_role','sys_role_permission','sys_organization_admin','sys_organization_type',
          'sys_user_organization','sys_system_task','sys_feature_toggle','sys_feature_toggle_change',
          'sys_user_permission','sys_role_data_scope','sys_dictionary_type','sys_dictionary_item',
          'sys_cache_operation_log')
          and column_name = 'id' and data_type = 'BIGINT' and is_identity = 'NO'
        """, Integer.class);
    assertThat(idColumnCount).isEqualTo(18);
}

@Test
void seedsBuiltInDataWithNineteenDigitSnowflakeIds() {
    Integer roleCount = jdbcTemplate.queryForObject(
        "select count(*) from sys_role where id >= 1000000000000000000", Integer.class);
    assertThat(roleCount).isEqualTo(6);
}
```

- [ ] **Step 2: Run the Flyway test and verify the new assertions fail**

Run: `$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; & mvn test "-Dtest=FlywayMigrationTest"`

Expected: assertion failure because current migrations define identity columns and five relation tables do not have an `id` column.

- [ ] **Step 3: Rebuild V1-V11 DDL and seed inserts**

Replace every `id BIGINT AUTO_INCREMENT PRIMARY KEY` with `id BIGINT NOT NULL PRIMARY KEY`. Add `id BIGINT NOT NULL PRIMARY KEY` to `sys_role_permission`, `sys_organization_admin`, `sys_user_organization`, `sys_user_permission`, and `sys_role_data_scope`; replace their former `PRIMARY KEY (...)` declarations with `UNIQUE (...)`. Change `sys_feature_toggle_change` to an independent `id` primary key plus `UNIQUE (task_id)`.

Update the V2 role insert, V3 organization-type insert and V6 feature-toggle insert to include the fixed IDs from the design document. Preserve every existing foreign key, non-primary unique key, status default, index and comment.

- [ ] **Step 4: Run Flyway and full migration assertions**

Run: `$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; & mvn test "-Dtest=FlywayMigrationTest"`

Expected: all Flyway migration tests pass and H2 applies V1-V11 from an empty database.

### Task 3: Assign Snowflake IDs to direct entity writes

**Files:**
- Modify: `server/src/main/java/com/lingdong/learning/audit/application/SystemTaskApplicationService.java`
- Modify: `server/src/main/java/com/lingdong/learning/cache/application/CacheOperationApplicationService.java`
- Modify: `server/src/main/java/com/lingdong/learning/dictionary/application/DictionaryApplicationService.java`
- Modify: `server/src/main/java/com/lingdong/learning/feature/application/FeatureToggleChangeService.java`
- Modify: `server/src/main/java/com/lingdong/learning/iam/application/RoleApplicationService.java`
- Modify: `server/src/main/java/com/lingdong/learning/organization/application/OrganizationApplicationService.java`
- Modify: `server/src/main/java/com/lingdong/learning/permission/application/PermissionAdministrationService.java`
- Modify: `server/src/main/java/com/lingdong/learning/user/application/UserAccessApplicationService.java`
- Modify: `server/src/main/java/com/lingdong/learning/{audit,cache,dictionary,feature,iam,organization,permission,user}/domain/*.java`
- Modify: `server/src/main/resources/mapper/{audit,cache,dictionary,feature,iam,organization,permission,user}/*Mapper.xml`
- Test: existing application-service test classes under `server/src/test/java/com/lingdong/learning`

- [ ] **Step 1: Add failing 19-digit assertions to existing creation tests**

Add `assertThat(Long.toString(created.id())).hasSize(19);` to the tests that create a role, user, organization type, organization, dictionary type/item, system task, cache operation and feature-toggle change. Keep their existing business assertions intact.

- [ ] **Step 2: Run the focused creation tests and verify they fail with current auto-generated IDs**

Run: `$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; & mvn test "-Dtest=RoleApplicationServiceTest,UserAccessApplicationServiceTest,OrganizationApplicationServiceTest,DictionaryApplicationServiceTest,SystemTaskApplicationServiceTest,FeatureToggleChangeServiceTest,CacheOperationApplicationServiceTest"`

Expected: assertions fail because runtime writes still use small database-generated IDs.

- [ ] **Step 3: Pass `IdGenerator` through direct-write services and Mappers**

Inject `IdGenerator` into each listed application service. Change domain factories to accept a supplied `Long id`, including `Role.custom`, `User.create`, `Organization.create`, `OrganizationType.custom`, `FeatureToggle.organizationOverride`, `DictionaryType.enabled`, `DictionaryItem.enabled`, `CacheOperation.pending`, and the `FeatureToggleChange` record. Construct `SystemTask` and `Permission` with `idGenerator.nextId()`.

Add `id` to every direct-entity Mapper `INSERT` column list and parameter list. The cache application service must allocate an ID in `createPending`; the feature-toggle change service must allocate an ID separate from `taskId`. Tests that call a Mapper directly must obtain an ID from the injected `IdGenerator`.

- [ ] **Step 4: Run focused tests and verify they pass**

Run the Task 3 test command. Expected: all focused creation tests pass, with 19-digit IDs returned from persisted records.

### Task 4: Assign Snowflake IDs to association writes

**Files:**
- Modify: `server/src/main/java/com/lingdong/learning/user/application/UserAccessApplicationService.java`
- Modify: `server/src/main/java/com/lingdong/learning/permission/application/PermissionAdministrationService.java`
- Modify: `server/src/main/java/com/lingdong/learning/datascope/application/DataScopeAdministrationService.java`
- Modify: `server/src/main/java/com/lingdong/learning/user/infrastructure/persistence/UserOrganizationMapper.java`
- Modify: `server/src/main/java/com/lingdong/learning/user/infrastructure/persistence/UserRoleMapper.java`
- Modify: `server/src/main/java/com/lingdong/learning/permission/infrastructure/persistence/RolePermissionMapper.java`
- Modify: `server/src/main/java/com/lingdong/learning/permission/infrastructure/persistence/UserPermissionMapper.java`
- Modify: `server/src/main/java/com/lingdong/learning/datascope/infrastructure/persistence/OrganizationAdminMapper.java`
- Modify: `server/src/main/java/com/lingdong/learning/datascope/infrastructure/persistence/RoleDataScopeMapper.java`
- Modify: `server/src/main/resources/mapper/user/UserOrganizationMapper.xml`
- Modify: `server/src/main/resources/mapper/user/UserRoleMapper.xml`
- Modify: `server/src/main/resources/mapper/permission/RolePermissionMapper.xml`
- Modify: `server/src/main/resources/mapper/permission/UserPermissionMapper.xml`
- Modify: `server/src/main/resources/mapper/datascope/OrganizationAdminMapper.xml`
- Modify: `server/src/main/resources/mapper/datascope/RoleDataScopeMapper.xml`
- Test: `server/src/test/java/com/lingdong/learning/user/application/UserAccessApplicationServiceTest.java`
- Test: `server/src/test/java/com/lingdong/learning/datascope/application/OrganizationDataScopeServiceTest.java`
- Test: `server/src/test/java/com/lingdong/learning/permission/application/PermissionDecisionServiceTest.java`

- [ ] **Step 1: Write failing association-row assertions**

After each existing association flow, use `JdbcTemplate` to read the association record `id` and assert `Long.toString(id).hasSize(19)`. Cover user-organization, user-role, role-permission, user-permission, organization-admin and role-data-scope rows.

- [ ] **Step 2: Run association tests and verify they fail**

Run: `$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; & mvn test "-Dtest=UserAccessApplicationServiceTest,OrganizationDataScopeServiceTest,PermissionDecisionServiceTest"`

Expected: database insertion fails because the rebuilt association schemas require a non-null `id` but mapper SQL has not supplied it.

- [ ] **Step 3: Add ID parameters to association Mapper writes**

Add `@Param("id") Long id` before the existing business-key parameters in each listed Mapper interface. Add `id` to the corresponding XML `INSERT` statements. Inject the shared `IdGenerator` into `UserAccessApplicationService`, `PermissionAdministrationService` and `DataScopeAdministrationService`, then pass `idGenerator.nextId()` for every newly inserted association. Keep update paths such as `UserPermissionMapper.update` unchanged because they do not create rows.

- [ ] **Step 4: Run association tests and verify they pass**

Run the Task 4 test command. Expected: all business uniqueness and scope behavior remains unchanged, and every created association row has a 19-digit `id`.

### Task 5: Align architecture documents and verify the complete build

**Files:**
- Modify: `docs/design/03-系统架构设计-HLD-V1.0.md`
- Modify: `docs/design/04-数据库设计-V1.0.md`
- Modify: `docs/design/05-Flyway迁移规范-V1.0.md`
- Modify: `docs/design/06-API接口设计-V1.0.md`
- Modify: `docs/design/12-当前实现一致性核对-V1.0.md`

- [ ] **Step 1: Update written architecture rules**

State that every table uses an application-generated 19-digit Snowflake `BIGINT id`; `*_id` relations remain `BIGINT`; association business keys are unique constraints; Flyway V1-V11 were rebuilt only because no shared database had executed them; and all HTTP ID fields must be JSON strings. Replace the `AUTO_INCREMENT` example in the Flyway document with explicit `BIGINT NOT NULL PRIMARY KEY` syntax.

- [ ] **Step 2: Run static migration and formatting checks**

Run: `rg -n "AUTO_INCREMENT|PRIMARY KEY \([^)]*_id|task_id BIGINT PRIMARY KEY" server/src/main/resources/db/migration`

Expected: no matches. Run: `git diff --check`; expected: no whitespace errors.

- [ ] **Step 3: Run the full test suite**

Run: `$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; & mvn test`

Expected: `BUILD SUCCESS`, all Flyway migrations V1-V11 apply, and all current tests pass.
