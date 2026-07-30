# 灵动学习用户与角色授权核心实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立用户账号、用户-组织关联和用户-角色授予的后端应用服务，使学校用户必须先关联组织才可获得组织范围角色，家庭独立主体可获得全局角色。

**Architecture:** 用户账号、组织关系、角色授予分为三项独立业务动作。`sys_user_organization`记录人员归属，`sys_user_role`仅表达角色及其全局或组织范围；应用服务负责引用完整性与最小组织范围约束，不在认证尚未完成时伪造当前操作者授权判断。

**Tech Stack:** Java 17, Spring Boot 3, MyBatis XML, Flyway, H2 MySQL compatibility mode, JUnit 5, AssertJ.

---

### Task 1: 先写用户角色授权失败测试

**Files:**
- Create: `server/src/test/java/com/lingdong/learning/user/application/UserAccessApplicationServiceTest.java`

- [x] **Step 1: 写入测试**

测试验证创建用户后可关联已启用组织并授予该组织范围角色；未关联组织的用户不能获得组织范围角色；独立主体可获得全局`PARENT`角色。

- [x] **Step 2: 运行测试并确认失败**

Run: `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; Set-Location server; mvn test -Dtest=UserAccessApplicationServiceTest`

Expected: FAIL，用户应用服务和关联 Mapper 尚不存在。

### Task 2: 通过 Flyway 增加用户组织关系约束

**Files:**
- Create: `server/src/main/resources/db/migration/V4__create_user_organization_relations.sql`
- Modify: `server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

- [x] **Step 1: 创建用户组织关联表**

创建`sys_user_organization`，以用户和组织复合主键保证一个用户只关联一次同一组织；为`sys_user.mobile`增加唯一约束，允许多个空值但不允许同一手机号绑定多个用户。

- [x] **Step 2: 扩展迁移测试**

验证关联表经 Flyway 创建。

### Task 3: 实现用户、组织关联和角色授予服务

**Files:**
- Create: `server/src/main/java/com/lingdong/learning/user/domain/User.java`
- Create: `server/src/main/java/com/lingdong/learning/user/domain/UserStatus.java`
- Create: `server/src/main/java/com/lingdong/learning/user/domain/UserType.java`
- Create: `server/src/main/java/com/lingdong/learning/user/application/CreateUserCommand.java`
- Create: `server/src/main/java/com/lingdong/learning/user/application/AssociateUserWithOrganizationCommand.java`
- Create: `server/src/main/java/com/lingdong/learning/user/application/AssignRoleToUserCommand.java`
- Create: `server/src/main/java/com/lingdong/learning/user/application/UserAccessApplicationService.java`
- Create: `server/src/main/java/com/lingdong/learning/user/infrastructure/persistence/UserMapper.java`
- Create: `server/src/main/java/com/lingdong/learning/user/infrastructure/persistence/UserOrganizationMapper.java`
- Create: `server/src/main/java/com/lingdong/learning/user/infrastructure/persistence/UserRoleMapper.java`
- Create: `server/src/main/resources/mapper/user/UserMapper.xml`
- Create: `server/src/main/resources/mapper/user/UserOrganizationMapper.xml`
- Create: `server/src/main/resources/mapper/user/UserRoleMapper.xml`
- Modify: `server/src/main/java/com/lingdong/learning/iam/infrastructure/persistence/RoleMapper.java`
- Modify: `server/src/main/resources/mapper/iam/RoleMapper.xml`

- [x] **Step 1: 实现领域对象与命令对象**

用户账号在系统内唯一；用户初始状态为`ENABLED`。角色授予可选组织节点：无组织节点即全局范围，有组织节点则必须先完成用户-组织关联。

- [x] **Step 2: 实现 Mapper 和 XML**

Mapper 查询用户、角色、组织关联和已有角色授予，全部 SQL 使用参数绑定。角色授予范围键使用`GLOBAL`或`ORG:{组织ID}`，与数据库唯一约束一致。

- [x] **Step 3: 实现事务型应用服务**

服务校验角色与组织的启用状态，拦截重复账号、重复组织关联和重复角色授予；拒绝给未关联组织的用户授予组织范围角色。

### Task 4: 运行授权测试与全量回归

- [x] **Step 1: 运行用户角色授权测试并确认通过**

Run: `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; Set-Location server; mvn test -Dtest=UserAccessApplicationServiceTest`

- [x] **Step 2: 运行全部后端测试**

Run: `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; Set-Location server; mvn test`

Expected: PASS，基础、RBAC、组织树和用户角色授权测试均通过。
