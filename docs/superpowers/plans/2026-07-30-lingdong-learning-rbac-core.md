# 灵动学习 RBAC 核心实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立组织、用户、角色、权限关系的 Flyway 数据模型，并交付可创建自定义角色且防止角色编码重复的事务型应用服务。

**Architecture:** RBAC 持久化模型与业务模块解耦，角色创建通过应用服务调用 MyBatis XML Mapper，不直接暴露 HTTP 接口。内置角色由 Flyway 初始化；自定义角色只使用显式的领域枚举和值对象，不使用字符串散落在业务代码中。

**Tech Stack:** Java 17, Spring Boot 3, MyBatis XML, Flyway, H2 MySQL compatibility mode, JUnit 5, AssertJ.

---

### Task 1: 先写角色创建失败测试

**Files:**
- Create: `server/src/test/java/com/lingdong/learning/iam/application/RoleApplicationServiceTest.java`

- [x] **Step 1: 写入测试**

测试使用真实 Spring 上下文和 H2，验证创建 `OPS_VIEWER` 自定义角色后能按角色编码读回；重复创建相同编码时抛出 `DuplicateRoleCodeException`。

- [x] **Step 2: 运行测试并确认失败**

Run: `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; Set-Location server; mvn test -Dtest=RoleApplicationServiceTest`

Expected: FAIL，角色应用服务、命令对象或 Mapper 尚不存在。

### Task 2: 通过 Flyway 建立 RBAC 数据模型

**Files:**
- Create: `server/src/main/resources/db/migration/V2__create_iam_rbac_tables.sql`

- [x] **Step 1: 创建迁移**

迁移创建`sys_organization`、`sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`和`sys_organization_admin`表；角色编码、组织编码和权限编码必须唯一，用户角色和角色权限关系必须唯一。

- [x] **Step 2: 初始化六类内置角色**

迁移插入`SYS_ADMIN`、`SYS_AUDITOR`、`ORG_ADMIN`、`TEACHER`、`PARENT`、`STUDENT`六个启用状态的内置角色。内置角色只作为初始数据，不限制系统管理员后续创建自定义角色。

### Task 3: 实现角色领域和 MyBatis XML 持久化

**Files:**
- Create: `server/src/main/java/com/lingdong/learning/iam/domain/RoleType.java`
- Create: `server/src/main/java/com/lingdong/learning/iam/domain/RoleStatus.java`
- Create: `server/src/main/java/com/lingdong/learning/iam/domain/RoleDataScope.java`
- Create: `server/src/main/java/com/lingdong/learning/iam/domain/Role.java`
- Create: `server/src/main/java/com/lingdong/learning/iam/application/CreateCustomRoleCommand.java`
- Create: `server/src/main/java/com/lingdong/learning/iam/application/DuplicateRoleCodeException.java`
- Create: `server/src/main/java/com/lingdong/learning/iam/application/RoleApplicationService.java`
- Create: `server/src/main/java/com/lingdong/learning/iam/infrastructure/persistence/RoleMapper.java`
- Create: `server/src/main/resources/mapper/iam/RoleMapper.xml`

- [x] **Step 1: 实现角色领域对象和命令对象**

角色编码只允许大写字母、数字、下划线，长度3至64；`CUSTOM`角色只能通过`CreateCustomRoleCommand`构建，角色状态初始为`ENABLED`。

- [x] **Step 2: 实现 Mapper 和 XML**

Mapper只包含`existsByCode`、`insert`和`findByCode`。XML使用参数绑定和结果映射，禁止拼接 SQL。

- [x] **Step 3: 实现事务型应用服务**

应用服务先校验命令，再检查编码唯一性，最后插入角色。服务方法标注`@Transactional`，并在注释中说明该事务防止重复角色写入过程产生半完成数据。

### Task 4: 运行 RBAC 测试与全量回归

**Files:**
- Test: `server/src/test/java/com/lingdong/learning/iam/application/RoleApplicationServiceTest.java`

- [x] **Step 1: 运行角色测试并确认通过**

Run: `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; Set-Location server; mvn test -Dtest=RoleApplicationServiceTest`

Expected: PASS，创建角色和重复编码拦截均通过。

- [x] **Step 2: 运行全部后端测试**

Run: `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; Set-Location server; mvn test`

Expected: PASS，健康接口、Flyway迁移和角色领域测试均通过。
