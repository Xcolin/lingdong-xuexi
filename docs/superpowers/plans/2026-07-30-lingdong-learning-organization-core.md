# 灵动学习组织树核心实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付可配置组织类型、可创建区域到班级树形节点的后端应用服务，并以组织路径、父节点状态和同级名称唯一性保障组织边界。

**Architecture:** `sys_organization_type` 维护可用组织类型，`sys_organization` 存储物化代码路径以支持后续按组织子树进行数据权限过滤。应用服务负责层级、长度和重复约束；MyBatis XML 仅负责参数化 SQL。当前不暴露 HTTP 接口，也不实现用户、班级成员绑定或组织管理员授权，这些功能依赖认证与用户模型。

**Tech Stack:** Java 17, Spring Boot 3, MyBatis XML, Flyway, H2 MySQL compatibility mode, JUnit 5, AssertJ.

---

### Task 1: 先写组织树失败测试

**Files:**
- Create: `server/src/test/java/com/lingdong/learning/organization/application/OrganizationApplicationServiceTest.java`

- [x] **Step 1: 写入测试**

测试验证系统可以新增自定义组织类型；在内置“区域”类型下新增根区域，在该区域下新增“学校”节点，学校路径由父路径和节点编码组成；同一父节点下的重复组织名称必须被拦截。

- [x] **Step 2: 运行测试并确认失败**

Run: `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; Set-Location server; mvn test -Dtest=OrganizationApplicationServiceTest`

Expected: FAIL，组织类型与组织树应用服务尚不存在。

### Task 2: 通过 Flyway 扩展组织类型基线

**Files:**
- Create: `server/src/main/resources/db/migration/V3__create_organization_types.sql`
- Modify: `server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

- [x] **Step 1: 创建组织类型表和内置类型**

新增`sys_organization_type`，初始化`REGION`、`SCHOOL`、`CAMPUS`、`GRADE`、`CLASS`五个启用状态的内置类型；为现有`sys_organization.organization_type`增加外键，确保每个组织节点只能引用已配置的类型编码。

- [x] **Step 2: 扩展迁移测试**

验证组织类型表经 Flyway 创建，且五个内置类型均处于启用状态。

### Task 3: 实现组织类型和组织树应用服务

**Files:**
- Create: `server/src/main/java/com/lingdong/learning/organization/domain/Organization.java`
- Create: `server/src/main/java/com/lingdong/learning/organization/domain/OrganizationStatus.java`
- Create: `server/src/main/java/com/lingdong/learning/organization/domain/OrganizationType.java`
- Create: `server/src/main/java/com/lingdong/learning/organization/application/CreateOrganizationCommand.java`
- Create: `server/src/main/java/com/lingdong/learning/organization/application/CreateOrganizationTypeCommand.java`
- Create: `server/src/main/java/com/lingdong/learning/organization/application/DuplicateOrganizationNameException.java`
- Create: `server/src/main/java/com/lingdong/learning/organization/application/OrganizationApplicationService.java`
- Create: `server/src/main/java/com/lingdong/learning/organization/infrastructure/persistence/OrganizationMapper.java`
- Create: `server/src/main/java/com/lingdong/learning/organization/infrastructure/persistence/OrganizationTypeMapper.java`
- Create: `server/src/main/resources/mapper/organization/OrganizationMapper.xml`
- Create: `server/src/main/resources/mapper/organization/OrganizationTypeMapper.xml`

- [x] **Step 1: 实现不可变领域对象与命令**

组织编码使用与角色编码相同的`3-64`位大写编码规范；根节点路径为`/组织编码/`，子节点路径为`父路径 + 组织编码 + /`。区域、班级名称最大50字符，学校名称最大100字符，其余组织类型名称最大100字符。

- [x] **Step 2: 实现 Mapper 和 XML**

Mapper 查询父节点、启用组织类型、同级名称是否已存在，并插入组织类型或组织节点。全部 SQL 使用 MyBatis 参数绑定；根节点的同级查询必须使用`parent_id IS NULL`，不能错误地与`NULL`做等值比较。

- [x] **Step 3: 实现事务型组织应用服务**

创建子节点前验证父节点存在且启用，组织类型存在且启用，并在事务内检查同级重名后创建节点。由数据库唯一约束作为并发提交的最终兜底。

### Task 4: 运行组织树与全量回归

**Files:**
- Test: `server/src/test/java/com/lingdong/learning/organization/application/OrganizationApplicationServiceTest.java`

- [x] **Step 1: 运行组织树测试并确认通过**

Run: `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; Set-Location server; mvn test -Dtest=OrganizationApplicationServiceTest`

Expected: PASS，组织类型创建、区域学校树创建、路径生成和同级重名拦截均通过。

- [x] **Step 2: 运行全部后端测试**

Run: `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; Set-Location server; mvn test`

Expected: PASS，健康接口、Flyway、RBAC和组织树测试均通过。
