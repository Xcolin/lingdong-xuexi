# 灵动伴随 V39 设备会话与账号安全管理实施计划

> **执行要求：** 使用 `superpowers:executing-plans` 按任务逐项实施，步骤以复选框跟踪。严格 TDD，测试只使用本地 H2、测试短信/微信适配器和内存保护存储，不对远程 MySQL、Redis、共享测试、预生产或生产环境执行迁移或写操作。

**目标：** 在现有可撤销会话基础上完成全角色设备自助管理、新设备站内风险提醒和 Web/uni-app 独立交互。

**架构：** `auth_device_session` 继续作为认证事实，V39 新增 `auth_security_event` 保存事件和已读状态。统一会话创建方法判断历史设备并记录首次设备事件；设备与事件接口只使用当前认证用户，前端按能力开关展示。

**技术栈：** Spring Boot 3、JDK 17、MyBatis XML、Flyway、H2/MySQL 兼容 SQL、React/Ant Design、uni-app/Vue 3。

---

## 任务 1：V39 迁移与全局约束

**文件：**
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`
- 新增：`server/src/main/resources/db/migration/V39__add_account_security_event.sql`

- [x] 在 `FlywayMigrationTest` 增加失败断言：V39 成功、`auth_security_event` 存在、显式非自增 `BIGINT id`、事件类型/级别/状态检查、事件范围唯一、设备历史索引、`ACCOUNT_SECURITY_MANAGEMENT` 为全局启用、67 张主键表和 19 位基础标识。
- [x] 执行 `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; mvn -q -Dtest=FlywayMigrationTest test`，确认因 V39 不存在而失败。
- [x] 新增 V39：创建安全事件表和索引，为设备会话增加历史设备索引，插入 19 位功能开关基础数据；禁止 `AUTO_INCREMENT`。
- [x] 重新执行迁移测试，确认空库连续执行 V1-V39 且全部断言通过。

## 任务 2：安全事件领域与真实 MyBatis 持久化

**文件：**
- 新增：`server/src/main/java/com/lingdong/learning/auth/domain/AccountSecurityEvent.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/domain/AccountSecurityEventType.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/domain/AccountSecurityRiskLevel.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/domain/AccountSecurityEventStatus.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/infrastructure/persistence/AccountSecurityEventMapper.java`
- 新增：`server/src/main/resources/mapper/auth/AccountSecurityEventMapper.xml`
- 修改：`server/src/main/java/com/lingdong/learning/auth/infrastructure/persistence/DeviceSessionMapper.java`
- 修改：`server/src/main/resources/mapper/auth/DeviceSessionMapper.xml`
- 新增测试：`server/src/test/java/com/lingdong/learning/auth/application/AccountSecurityEventPersistenceTest.java`

- [x] 先写真实 MyBatis 测试，覆盖首次设备事件条件插入、同设备唯一、最近 50 条、未读过滤、单条/全部已读幂等、用户隔离、历史设备判定及过期刷新会话不出现在设备列表。
- [x] 执行目标测试，确认领域对象和 Mapper 缺失而失败。
- [x] 实现枚举、记录和 XML；事件查询不选择 `device_fingerprint_hash` 与 `event_scope_key` 以外的敏感字段到响应层，设备列表增加当前时间过滤。
- [x] 重新执行目标测试，确认 SQL 在 H2 MySQL 兼容模式通过。

## 任务 3：统一登录接入与设备下线事件

**文件：**
- 新增：`server/src/main/java/com/lingdong/learning/auth/application/AccountSecurityEventService.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/application/AccountSecurityEventView.java`
- 修改：`server/src/main/java/com/lingdong/learning/auth/application/AuthenticationApplicationService.java`
- 修改：`server/src/main/java/com/lingdong/learning/auth/application/DeviceSession.java`
- 修改测试：`server/src/test/java/com/lingdong/learning/auth/application/AuthenticationApplicationServiceTest.java`
- 修改测试：家长手机号、家长微信、学生账号和学生扫码认证测试。

- [x] 先写失败测试：首次设备登录记录一个 `WARNING`，同用户同客户端同设备再次登录不重复，不同客户端分别记录；当前退出不记录风险，指定下线与全部下线记录 `INFO`。
- [x] 实现 `AccountSecurityEventService`，使用 `SessionTokenService.hash` 生成设备摘要和内部范围键，唯一竞争返回已有事实而不泄露冲突。
- [x] 在统一 `createSession` 接入历史设备判断和事件记录；所有平台、家长、学生登录路径不各自复制逻辑。
- [x] 设备列表只返回刷新凭证仍有效的活动会话；指定设备下线和全部下线成功后记录事件，当前退出保持原行为。
- [x] 回归全部认证目标测试，确认会话签发、刷新、撤销、验证码和微信票据行为不变。

## 任务 4：安全事件 API、能力开关与响应脱敏

**文件：**
- 新增：`server/src/main/java/com/lingdong/learning/auth/web/AccountSecurityEventResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/auth/web/AuthenticationController.java`
- 修改：`server/src/main/java/com/lingdong/learning/auth/web/DeviceSessionResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityController.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityResponse.java`
- 修改测试：`server/src/test/java/com/lingdong/learning/auth/web/AuthenticationControllerTest.java`
- 修改测试：`server/src/test/java/com/lingdong/learning/auth/web/StudentAuthenticationControllerTest.java`

- [x] 先写控制器测试：设备响应含字符串标识与 `current`，不含 `deviceId`；事件列表、单条已读、全部已读映射当前用户；越权对象不可见；功能停用拒绝管理接口但允许当前退出。
- [x] 为设备列表、指定下线、全部下线和事件接口接入 `ACCOUNT_SECURITY_MANAGEMENT` 后端开关；内部事件记录不受展示开关影响。
- [x] 实现三个安全事件端点和脱敏响应；查询参数 `unreadOnly` 默认 `false`，最多返回 50 条。
- [x] 扩展公开能力响应并回归 WEB/MINIAPP 能力测试。

## 任务 5：Web 工作台设备和安全事件闭环

**文件：**
- 修改：`web/src/api/auth.ts`
- 修改：`web/src/api/capability.ts`
- 修改：`web/src/features/dashboard/DashboardPage.tsx`
- 新增测试：`web/src/features/dashboard/DashboardPage.test.tsx`
- 修改：`web/src/app/App.tsx`
- 修改：`web/src/app/App.test.tsx`
- 修改：`web/src/styles/index.css`

- [x] 先写组件测试：开关控制设备区；当前设备标签和退出命令；其他设备二次确认下线；全部下线清本地会话；新设备警告、单条已读和全部已读。
- [x] 更新设备与事件 TypeScript 契约，所有标识保持字符串，不声明或展示原始设备标识。
- [x] 实现工作台安全提醒和事件列表；风险提醒优先，普通事件保持可扫描；提交失败保留当前页面状态并显示中文错误。
- [x] 执行 `npm test` 与 `npm run build`，确认工作台和原有路由回归通过。

## 任务 6：uni-app 家长与学生设备管理

**文件：**
- 修改：`miniapp/src/api/auth.ts`
- 修改：`miniapp/src/api/capability.ts`
- 新增：`miniapp/src/pages/account-security/account-security.vue`
- 修改：`miniapp/src/pages.json`
- 修改：`miniapp/src/pages/parent-home/parent-home.vue`
- 修改：`miniapp/src/pages/student-home/student-home.vue`

- [x] 先通过 TypeScript 契约固定设备、事件和显式 `parent|student` 身份参数；所有认证请求必须显式传入对应访问令牌。
- [x] 实现设备列表、当前设备、其他设备下线、全部下线、风险提醒和已读操作，危险操作使用二次确认。
- [x] 家长全部下线只清家长会话，学生全部下线只清学生会话；功能关闭隐藏两类入口，直达页重新读取能力后返回。
- [x] 执行 `npm run type-check`、`npm run build:h5`、`npm run build:mp-weixin`，扫描双端产物包含设备页且不混用会话存储键。

## 任务 7：全量回归与中文文档

- [x] 执行后端目标测试和 `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; mvn -q test`，汇总 Surefire 精确数量并确认 V1-V39。
- [x] 执行 Web 全量测试/构建和 uni-app 类型检查/双目标构建。
- [x] 扫描 67 张主键表、自增语法、令牌/设备摘要日志、功能开关、端侧会话隔离和越权下线。
- [x] 更新 README、设计文档 00-12、主开发计划、V39 专项清单和整体进度；真实外部通知和环境联调继续单列未完成。
