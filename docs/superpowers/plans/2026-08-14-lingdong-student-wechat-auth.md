# 灵动伴随学生微信授权登录实施计划

> **智能执行说明：** 按任务逐项实施本计划，建议使用 `superpowers:subagent-driven-development`，也可使用 `superpowers:executing-plans`；步骤统一使用复选框（`- [x]`）跟踪。

**目标：** 按 V46 设计完成学生微信自助双重绑定、学生快捷登录和主监护人双端解绑。

**架构：** 后端使用两段式 Redis 一次性票据隔离微信身份交换、学生登录码校验和主监护人短信校验；MySQL 只保存当前有效绑定，不可变审计保存绑定生命周期。Web 只承载家长管理操作，小程序同时承载学生认证和家长解绑，所有入口受统一功能开关控制。

**技术栈：** Spring Boot 3、JDK 17、MyBatis XML、MySQL 8、Redis、Flyway、React/Ant Design Pro、Vue 3/uni-app、JUnit 5、Vitest。

---

### 任务 1：V46 数据契约

**文件：**
- 新增：`server/src/main/resources/db/migration/V46__add_student_wechat_auth.sql`
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

- [x] **步骤 1：写迁移失败测试**，断言两个表、唯一约束、默认停用开关、`BOTH` 解绑权限和家长角色授权存在，且主键为 `BIGINT`。
- [x] **步骤 2：运行 `mvn -Dtest=FlywayMigrationTest test`**，确认因 V46 对象不存在而失败。
- [x] **步骤 3：添加 V46 迁移**，使用连续 19 位雪花基础数据 ID，不使用自增、触发器或环境数据。
- [x] **步骤 4：重跑迁移测试**，确认通过并执行 `rg -n "AUTO_INCREMENT|IDENTITY" server/src/main/resources/db/migration/V46__add_student_wechat_auth.sql`，预期无输出。

### 任务 2： 学生微信领域和持久化

**文件：**
- 新增： `server/src/main/java/com/lingdong/learning/auth/application/StudentWechat*.java`
- 新增： `server/src/main/java/com/lingdong/learning/auth/infrastructure/persistence/StudentWechatBindingMapper.java`
- 新增： `server/src/main/resources/mapper/auth/StudentWechatBindingMapper.xml`
- 测试： `server/src/test/java/com/lingdong/learning/auth/application/StudentWechatBindingTicketServiceTest.java`

- [x] **步骤 1： 写失败测试**，覆盖不完整身份拒绝、票据只保存摘要、票据单次消费和过期拒绝。
- [x] **步骤 2： 运行目标测试**，确认类或行为缺失导致失败。
- [x] **步骤 3： 最小实现微信绑定实体、审计实体、Mapper 和两类票据服务**；第二类票据固定学生、主监护人、微信身份和设备。
- [x] **步骤 4： 重跑目标测试**，确认通过。

### 任务 3： 学生登录码预认证复用

**文件：**
- 修改： `server/src/main/java/com/lingdong/learning/auth/application/StudentCodeLoginApplicationService.java`
- 测试： `server/src/test/java/com/lingdong/learning/auth/application/StudentCodeLoginApplicationServiceTest.java`

- [x] **步骤 1： 写失败测试**，证明预认证成功返回学生身份但不创建会话，失败仍更新验证码和锁定状态。
- [x] **步骤 2： 运行目标测试**，确认预认证 API 尚不存在。
- [x] **步骤 3： 提取共享校验状态机**，由普通登录在成功后创建会话，微信绑定预认证只返回已验证学生。
- [x] **步骤 4： 重跑学生账号和二维码登录测试**，确认原流程无回归。

### 任务 4： 学生微信绑定与快捷登录服务

**文件：**
- 新增： `server/src/main/java/com/lingdong/learning/auth/application/StudentWechatAuthenticationService.java`
- 修改： `server/src/main/java/com/lingdong/learning/auth/application/ParentSmsPurpose.java`
- 修改： `server/src/main/java/com/lingdong/learning/student/infrastructure/persistence/ParentStudentMapper.java`
- 修改： `server/src/main/resources/mapper/student/ParentStudentMapper.xml`
- 测试： `server/src/test/java/com/lingdong/learning/auth/application/StudentWechatAuthenticationServiceTest.java`

- [x] **步骤 1： 写失败测试**，覆盖已绑定快捷登录、未绑定签发票据、无主监护人拒绝、手机号不可用拒绝、账号登录码校验、手机号不可由客户端指定、关系变化拒绝、短信错误拒绝和并发双绑拒绝。
- [x] **步骤 2： 运行目标测试**，确认服务缺失而失败。
- [x] **步骤 3： 实现三段状态机**，复用微信网关和集成可用性，新增 `STUDENT_WECHAT_BIND` 短信用途，最终事务写绑定与审计。
- [x] **步骤 4： 重跑目标测试及家长微信测试**，确认新旧微信认证互不干扰。

### 任务 5： 主监护人解绑服务

**文件：**
- 新增： `server/src/main/java/com/lingdong/learning/auth/application/StudentWechatBindingManagementService.java`
- 测试： `server/src/test/java/com/lingdong/learning/auth/web/StudentWechatAuthenticationControllerTest.java`

- [x] **步骤 1： 写失败测试**，通过端到端控制器用例覆盖主监护人查询和解绑、权限边界、固定确认语句、功能停用拒绝和审计落库。
- [x] **步骤 2： 运行目标测试**，确认服务缺失而失败。
- [x] **步骤 3： 实现查询与解绑事务**，锁定家长关系并删除当前绑定，审计不记录微信标识或手机号。
- [x] **步骤 4： 重跑目标测试**，确认通过。

### 任务 6： 后端 HTTP 和能力开关

**文件：**
- 修改： `server/src/main/java/com/lingdong/learning/auth/web/StudentAuthenticationController.java`
- 新增： `server/src/main/java/com/lingdong/learning/auth/web/StudentWechat*.java`
- 新增： `server/src/main/java/com/lingdong/learning/auth/web/StudentWechatBindingManagementController.java`
- 修改： `server/src/main/java/com/lingdong/learning/common/security/SecurityConfiguration.java`
- 修改： `server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityResponse.java`
- 修改： `server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityController.java`
- 测试： `server/src/test/java/com/lingdong/learning/auth/web/StudentWechatAuthenticationControllerTest.java`
- 测试： `server/src/test/java/com/lingdong/learning/auth/web/StudentWechatBindingManagementControllerTest.java`

- [x] **步骤 1： 写 MockMvc 失败测试**，覆盖三个公开认证接口、两个受保护管理接口、能力字段、权限、功能停用和脱敏响应。
- [x] **步骤 2： 运行两个目标测试**，确认路由不存在而失败。
- [x] **步骤 3： 添加请求响应 DTO、控制器、公开路由和能力字段**，所有数字 ID 以字符串序列化。
- [x] **步骤 4： 重跑目标测试与全部后端测试**，确认通过。

### 任务 7： Web 家长解绑

**文件：**
- 修改： `web/src/api/capability.ts`
- 修改： `web/src/features/student-login/api.ts`
- 修改： `web/src/features/student-login/StudentLoginManagementPage.tsx`
- 修改： `web/src/features/student-login/StudentLoginManagementPage.test.tsx`

- [x] **步骤 1： 写失败测试**，当前用户为主监护人且功能启用时显示绑定状态和解绑按钮，其他角色或功能停用时隐藏，确认后调用解绑 API。
- [x] **步骤 2： 运行 `npm test -- StudentLoginManagementPage.test.tsx`**，确认新行为缺失而失败。
- [x] **步骤 3： 实现状态列、图标操作、确认弹窗和刷新**，不新增 Web 学生登录入口。
- [x] **步骤 4： 运行 Web 目标测试、全量测试和生产构建**，确认通过。

### 任务 8： uni-app 学生微信登录

**文件：**
- 修改： `miniapp/src/api/auth.ts`
- 修改： `miniapp/src/api/capability.ts`
- 修改： `miniapp/src/pages/student-login/student-login.vue`

- [x] **步骤 1： 先由后端端到端失败测试锁定已绑定直接登录、未绑定双重校验、掩码手机号、票据清理和错误回退契约。
- [x] **步骤 2： 运行 uni-app 类型检查**，确认新增 API 和页面状态尚未接入时出现类型或引用失败。
- [x] **步骤 3： 实现 `wx.login` 交换及两步绑定表单**，敏感输入离页清空，H5 通过条件编译排除微信入口。
- [x] **步骤 4： 运行小程序类型检查、H5 构建和微信小程序构建**，并静态确认 H5 不包含微信授权入口。

### 任务 9： uni-app 家长解绑

**文件：**
- 新增： `miniapp/src/api/student-wechat-binding.ts`
- 新增： `miniapp/src/pages/parent-student-wechat/parent-student-wechat.vue`
- 修改： `miniapp/src/pages/parent-home/parent-home.vue`
- 修改： `miniapp/src/pages.json`

- [x] **步骤 1： 先由后端端到端失败测试锁定主监护学生列表、已绑定状态、固定确认语句和解绑后刷新契约。
- [x] **步骤 2： 运行 uni-app 类型检查**，确认页面与 API 尚未接入时出现类型或引用失败。
- [x] **步骤 3： 实现独立家长管理页面和 API**，停用时入口隐藏且直接页面访问退回家长首页。
- [x] **步骤 4： 运行小程序全量测试、H5 构建和微信小程序构建**，确认通过。

### 任务 10： 文档、静态校验和全量回归

**文件：**
- 修改： `README.md`
- 修改： `docs/00-开发实施计划.md`
- 修改： `docs/01-系统架构设计-HLD.md`
- 修改： `docs/02-数据库设计.md`
- 修改： `docs/03-Flyway数据库迁移规范.md`
- 修改： `docs/04-API接口设计.md`
- 修改： `docs/05-权限与安全设计.md`
- 修改： `docs/06-第三方集成设计.md`
- 修改： `docs/08-测试方案与验收用例.md`
- 修改： `docs/10-原型与交互说明.md`

- [x] **步骤 1： 按实际实现补充中文文档**，明确默认停用、个人主体上线约束、两段票据、短信目的隔离、一对一绑定和主监护人解绑。
- [x] **步骤 2： 执行后端全量测试、Web 全量测试与构建、小程序类型检查与双端构建**。
- [x] **步骤 3： 执行敏感信息、19 位主键、自增列、未完成清单、定位 API 和 `git diff --check` 静态扫描**。
- [x] **步骤 4： 更新计划勾选和项目进度**，只依据已通过验证的工作量调整完成度，不修改已执行 Flyway 迁移。

本计划按用户已确认的“直接继续推进”采用当前会话内联执行；不自动提交或推送 Git。
