# Lingdong Learning Interface Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add audited interface-service registration, authorization-scope changes, disable operations, and privacy-safe call-result logging.

**Architecture:** V12 creates three Snowflake-primary-key tables. `InterfaceServiceApplicationService` creates a linked `INTERFACE_SERVICE_CHANGE` system task for every high-risk service mutation and marks it effective only after its persistence change succeeds; a separate call-log method allows future adapters to enforce enabled registration without embedding supplier behavior.

**Tech Stack:** Java 17, Spring Boot 3.4, MyBatis XML, Flyway, MySQL 8, H2, JUnit 5, AssertJ.

---

### Task 1: Add V12 schema and failing migration tests

**Files:**
- Create: `server/src/main/resources/db/migration/V12__create_interface_service_tables.sql`
- Modify: `server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

- [x] **Step 1: Write failing Flyway assertions**

Add a test asserting `sys_interface_service`, `sys_interface_service_change`, and `sys_interface_call_log` exist; each has a non-identity `BIGINT id`; `task_id` is unique in the change table; and the call-log table has `service_id`, `result`, `error_summary`, and `trace_id` columns.

- [x] **Step 2: Run the focused test and verify it fails**

Run: `$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; & mvn test "-Dtest=FlywayMigrationTest"`

Expected: new table assertions fail because V12 does not exist.

- [x] **Step 3: Create the V12 schema**

Create the three tables defined in the design. All IDs are `BIGINT NOT NULL PRIMARY KEY`; add foreign keys to `sys_user`, `sys_system_task`, and `sys_interface_service`; index service status/purpose, change status lookup through `task_id`, and call-log service/time lookup. Do not add credentials, URLs, request bodies, response bodies, location data, or seed data.

- [x] **Step 4: Verify migration tests pass**

Run the Task 1 command. Expected: Flyway applies V1-V12 from an empty H2 database and all migration assertions pass.

### Task 2: Implement audited interface-service change application

**Files:**
- Create: `server/src/main/java/com/lingdong/learning/interfaceconfig/domain/InterfaceDirection.java`
- Create: `server/src/main/java/com/lingdong/learning/interfaceconfig/domain/InterfacePurpose.java`
- Create: `server/src/main/java/com/lingdong/learning/interfaceconfig/domain/InterfaceAuthorizationScope.java`
- Create: `server/src/main/java/com/lingdong/learning/interfaceconfig/domain/InterfaceServiceStatus.java`
- Create: `server/src/main/java/com/lingdong/learning/interfaceconfig/domain/InterfaceServiceChangeType.java`
- Create: `server/src/main/java/com/lingdong/learning/interfaceconfig/domain/InterfaceService.java`
- Create: `server/src/main/java/com/lingdong/learning/interfaceconfig/domain/InterfaceServiceChange.java`
- Create: `server/src/main/java/com/lingdong/learning/interfaceconfig/application/CreateInterfaceServiceChangeCommand.java`
- Create: `server/src/main/java/com/lingdong/learning/interfaceconfig/application/CreateInterfaceServiceDisableCommand.java`
- Create: `server/src/main/java/com/lingdong/learning/interfaceconfig/application/CreateInterfaceServiceAuthorizationChangeCommand.java`
- Create: `server/src/main/java/com/lingdong/learning/interfaceconfig/application/InterfaceServiceApplicationService.java`
- Create: `server/src/main/java/com/lingdong/learning/interfaceconfig/infrastructure/persistence/InterfaceServiceMapper.java`
- Create: `server/src/main/java/com/lingdong/learning/interfaceconfig/infrastructure/persistence/InterfaceServiceChangeMapper.java`
- Create: `server/src/main/resources/mapper/interfaceconfig/InterfaceServiceMapper.xml`
- Create: `server/src/main/resources/mapper/interfaceconfig/InterfaceServiceChangeMapper.xml`
- Test: `server/src/test/java/com/lingdong/learning/interfaceconfig/application/InterfaceServiceApplicationServiceTest.java`

- [x] **Step 1: Write failing approval-flow tests**

Create a system administrator and system auditor using existing helpers. Test that a create draft returns a task ID but does not create a service; after submit and approve-and-apply, the service exists with a 19-digit ID and `ENABLED` status. Test that disable and authorization-scope-change drafts leave the existing service unchanged until the linked task is approved and applied.

- [x] **Step 2: Run the focused test and verify it fails**

Run: `$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; & mvn test "-Dtest=InterfaceServiceApplicationServiceTest"`

Expected: compilation failure because the interface-service module is absent.

- [x] **Step 3: Implement commands, domain records, Mappers, and service**

Require `SYS_ADMIN` in draft creation. Validate service name and caller name at 100 characters, authorization scope value at 128 characters, and owner existence. Create a `SystemTaskType.INTERFACE_SERVICE_CHANGE` task plus a Snowflake-ID change record. `submit` delegates to the linked task. `approveAndApply` approves, applies exactly one of create/disable/authorization change, and calls `markEffective` only after the corresponding Mapper mutation returns one row.

- [x] **Step 4: Verify approval-flow tests pass**

Run the Task 2 command. Expected: all workflow tests pass and rejected or merely approved changes do not report an effective service mutation.

### Task 3: Add enabled-service call logging and complete verification

**Files:**
- Create: `server/src/main/java/com/lingdong/learning/interfaceconfig/domain/InterfaceCallResult.java`
- Create: `server/src/main/java/com/lingdong/learning/interfaceconfig/domain/InterfaceServiceCallLog.java`
- Create: `server/src/main/java/com/lingdong/learning/interfaceconfig/application/RecordInterfaceServiceCallCommand.java`
- Modify: `server/src/main/java/com/lingdong/learning/interfaceconfig/application/InterfaceServiceApplicationService.java`
- Create: `server/src/main/java/com/lingdong/learning/interfaceconfig/infrastructure/persistence/InterfaceServiceCallLogMapper.java`
- Create: `server/src/main/resources/mapper/interfaceconfig/InterfaceServiceCallLogMapper.xml`
- Modify: `docs/design/03-系统架构设计-HLD-V1.0.md`
- Modify: `docs/design/04-数据库设计-V1.0.md`
- Modify: `docs/design/05-Flyway迁移规范-V1.0.md`
- Modify: `docs/design/12-当前实现一致性核对-V1.0.md`
- Test: `server/src/test/java/com/lingdong/learning/interfaceconfig/application/InterfaceServiceApplicationServiceTest.java`

- [x] **Step 1: Write failing call-log tests**

Assert that recording a call for an unregistered or disabled service throws an exception. After an enabled service has been approved and applied, record a failed call and assert the persisted row has a 19-digit ID, the expected result, caller, trace ID, and bounded error summary; no payload or credential field exists in the table.

- [x] **Step 2: Run the focused test and verify it fails**

Run the Task 2 command. Expected: the call-record API is absent or rejects the expected enabled service flow.

- [x] **Step 3: Implement the call-log write path and document V12**

Read the service by ID, reject missing or disabled services, validate caller/error/trace lengths, allocate an ID through `IdGenerator`, and insert only the allowed summary fields. Update design documents to mark interface-service metadata, approval flow, and call-result logging as implemented while stating that no concrete supplier adapter or REST Controller exists yet.

- [x] **Step 4: Run static checks and the full suite**

Run: `rg -n "AUTO_INCREMENT|PRIMARY KEY \\([^)]*_id" src/main/resources/db/migration`

Expected: no matches. Then run: `$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; & mvn test`

Expected: all tests pass with Flyway V1-V12 applied.
