# 灵动学习用户目录与账号状态管理实施计划

> **执行要求**：按任务顺序实施。所有行为变更先增加失败测试，再做最小实现；每完成一个步骤立即更新复选框。

**目标：**提供受 RBAC 保护的用户分页目录、账号状态变更与 Web 用户管理页面。

**架构：**后端在既有 `user`、`auth`、`iam.web` 分层中扩展查询和状态变更；状态变更通过认证应用服务撤销活动会话。Web 独立调用真实 `/api/v1/users` 接口，不在浏览器端保存或计算数据权限。

**技术栈：**Spring Boot 3、Java 17、MyBatis XML、Flyway、H2 MySQL 兼容测试、React、TypeScript、Ant Design、Vitest。

---

## 文件职责

| 文件 | 职责 |
|---|---|
| `server/src/main/resources/db/migration/V18__seed_user_directory_permissions.sql` | 新增用户目录与账号状态权限，并授予系统管理员。 |
| `server/src/main/java/com/lingdong/learning/user/infrastructure/persistence/UserMapper.java` | 定义用户分页查询、计数和状态更新持久化方法。 |
| `server/src/main/resources/mapper/user/UserMapper.xml` | 使用参数化 SQL 执行筛选、稳定排序、分页和状态更新。 |
| `server/src/main/java/com/lingdong/learning/iam/application/IamQueryApplicationService.java` | 校验目录查询参数并组装分页结果。 |
| `server/src/main/java/com/lingdong/learning/user/application/UserAccessApplicationService.java` | 修改账号状态并协调活动会话撤销。 |
| `server/src/main/java/com/lingdong/learning/auth/application/AuthenticationApplicationService.java` | 按用户撤销活动设备会话。 |
| `server/src/main/java/com/lingdong/learning/iam/web/UserManagementController.java` | 暴露目录和状态 HTTP 接口。 |
| `server/src/main/java/com/lingdong/learning/iam/web/UserResponse.java` | 输出脱敏手机号与字符串化用户标识。 |
| `web/src/api/users.ts` | 封装用户目录、创建和状态接口。 |
| `web/src/features/users/UserManagementPage.tsx` | 提供筛选、分页、新增和状态操作页面。 |
| `web/src/app/App.tsx` | 注册用户管理路由与导航。 |

## 任务 1：先建立后端失败用例与权限迁移

- [x] **步骤 1：为目录和状态变更写失败的控制器测试**

在 `server/src/test/java/com/lingdong/learning/iam/web/IamManagementControllerTest.java` 新增测试：系统管理员创建三名不同类型和状态用户后，调用 `GET /api/v1/users?keyword=...&status=...&page=1&pageSize=20`，断言仅返回匹配项、`total` 正确、`id` 为字符串、手机号为 `138****8000`；调用 `PATCH /api/v1/users/{id}/status` 为 `DISABLED` 后，原访问令牌请求 `/api/v1/auth/me` 返回 401。新增无 `IAM_USER_LIST` 权限的请求返回 403。

- [x] **步骤 2：运行控制器测试确认失败**

执行：`$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; mvn test '-Dtest=IamManagementControllerTest'`

预期：目录和状态路由不存在，或权限种子缺失导致断言失败。

- [x] **步骤 3：新增 V18 权限种子迁移**

创建 `V18__seed_user_directory_permissions.sql`，以连续 19 位雪花常量插入 `IAM_USER_LIST`、`IAM_USER_STATUS_CHANGE` 两条 WEB `OPERATION` 权限，并向 `SYS_ADMIN` 插入对应角色授权。迁移不改动业务表结构，也不插入账号、密码或令牌。

- [x] **步骤 4：验证 Flyway 迁移可应用**

执行：`$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; mvn test '-Dtest=FlywayMigrationTest'`

预期：V1 至 V18 迁移通过，全部新标识为 19 位数字。

## 任务 2：实现后端目录与状态闭环

- [x] **步骤 1：扩展用户分页持久化边界**

在 `UserMapper` 增加 `findPage`、`count`、`updateStatus`。创建不可变查询记录，包含归一化后的 `keyword`、可选 `UserType`、可选 `UserStatus`、`offset` 和 `limit`；XML 以 `username`、`display_name`、`user_type`、`status` 条件组合查询，固定 `created_at DESC, id DESC` 排序。

- [x] **步骤 2：实现查询参数校验和分页响应**

在 `IamQueryApplicationService` 增加 `listUsers`：`page` 必须大于等于 1，`pageSize` 必须在 1 至 100，关键字去除首尾空格且最长 64 字符；返回包含 `items`、`page`、`pageSize`、`total` 的分页记录。`items` 中不得包含密码散列。

- [x] **步骤 3：实现状态变更和活动会话撤销**

增加 `UpdateUserStatusCommand`。`UserAccessApplicationService` 校验目标用户存在后更新状态；目标状态不是 `ENABLED` 时调用认证应用服务按用户撤销所有 `ACTIVE` 设备会话。重复状态更新返回当前用户状态，不产生错误。

- [x] **步骤 4：补齐控制器和安全响应**

在 `UserManagementController` 增加 `GET /api/v1/users` 与 `PATCH /api/v1/users/{id}/status`，分别声明 `IAM_USER_LIST`、`IAM_USER_STATUS_CHANGE`。新增状态请求 DTO 的 `@NotNull UserStatus status` 校验。`UserResponse` 对非空手机号保留前三位和后四位，中段使用四个星号；不足七位时只输出星号。

- [x] **步骤 5：运行控制器测试确认通过**

执行：`$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; mvn test '-Dtest=IamManagementControllerTest,FlywayMigrationTest'`

预期：目录、状态、会话撤销、权限拒绝和迁移断言通过。

## 任务 3：实现 Web 用户管理页面

- [x] **步骤 1：先写页面失败测试**

创建 `web/src/features/users/UserManagementPage.test.tsx`，替代 `usersApi`：断言页面加载后显示脱敏手机号；输入关键字并点击查询后使用 `page=1` 请求；创建用户后调用 `create`；确认停用后调用 `updateStatus(id, 'DISABLED')` 并刷新目录。

- [x] **步骤 2：运行页面测试确认失败**

执行：`npm.cmd test -- UserManagementPage.test.tsx`

预期：因 API 模块和页面尚不存在而失败。

- [x] **步骤 3：实现 API 模块、路由和页面**

创建 `web/src/api/users.ts`，定义列表筛选、分页、用户状态和创建用户类型。创建用户管理页面，使用筛选表单、分页表格、创建弹窗以及启用/停用/锁定确认操作；添加 Lucide 图标按钮并用 `aria-label` 描述操作。修改 `App.tsx`，注册 `/users` 路由和侧边导航项，并保持页面懒加载。

- [x] **步骤 4：运行 Web 验证**

执行：`npm.cmd test` 与 `npm.cmd run build`

预期：全部 Web 测试通过，TypeScript 与 Vite 构建成功。

## 任务 4：同步中文设计与最终回归

- [x] **步骤 1：更新 API、权限、数据库和一致性文档**

更新 `docs/design/04-数据库设计-V1.0.md`、`05-Flyway迁移规范-V1.0.md`、`06-API接口设计-V1.0.md`、`07-权限与安全设计-V1.0.md`、`12-当前实现一致性核对-V1.0.md`，记录 V18 权限种子、目录契约、手机号脱敏、停用会话撤销和未实现的组织范围 SQL 边界。

- [x] **步骤 2：执行最终本地回归**

执行 `git diff --check`、Web `npm.cmd test`、Web `npm.cmd run build`、小程序 `npm.cmd run type-check`、小程序 `npm.cmd run build:mp-weixin`，以及带 JDK 17 临时环境变量的后端 `mvn test`。仅记录本地结果，不执行共享测试、预生产或生产数据库迁移。

**执行结果（2026-08-01）**：后端 `mvn test` 通过 79 项测试；Web `npm.cmd test` 通过 3 个测试文件、8 项测试，`npm.cmd run build` 成功；小程序类型检查和微信小程序构建成功；`git diff --check` 通过。所有数据库验证均使用本地 H2 MySQL 兼容模式，未连接共享测试、预生产或生产数据库。
