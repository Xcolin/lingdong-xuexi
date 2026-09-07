# 灵动学习 V42 家长账号生命周期实施计划

> **执行说明：** 按任务顺序逐项实施并更新复选框状态，使用 `superpowers:executing-plans` 约束执行过程。

**目标：** 完成家长自助手机号换绑、全部旧会话撤销、注销前置校验、7 天冷静期和撤销申请。

**架构：** `ParentAccountLifecycleService` 统一编排家长身份、功能开关、短信用途、一次性换号票据、用户行锁和不可变审计。手机号和登录用户名按规则原子更新；注销只记录可撤销申请，不执行最终禁用或匿名化。Web 与 uni-app 使用独立页面，共享 `BOTH` 权限和 API 契约。

**技术栈：** Spring Boot 3、JDK 17、Maven 4、MyBatis XML、Flyway、Redis、H2/MySQL 兼容 SQL、React/Ant Design、uni-app/Vue 3/TypeScript。

---

## 文件结构

- 新增 `V42__add_parent_account_lifecycle.sql`：换号审计、注销申请、功能开关和权限。
- 新增 `ParentMobileChangeTicketStore` 及 Redis/测试内存实现：5 分钟一次性换号票据。
- 新增 `ParentAccountLifecycleService`、命令、状态视图和专用 Mapper：集中业务事务。
- 扩展 `ParentSmsPurpose`、`UserMapper` 和 `ParentStudentMapper`：用途隔离、原子换号、活动关系计数。
- 扩展 `ParentAuthenticationController`：认证态换号和注销接口。
- 扩展 Web 账号安全工作台；新增 uni-app `parent-account-lifecycle` 页面。
- 同步 README、设计文档 00-12、主计划和当前实现核对。

## 任务 1：V42 迁移

- [x] 在 `FlywayMigrationTest` 先断言 V42、两张新表、70 张主键表、开关、`BOTH` 权限及 19 位基础标识，运行确认红灯。
- [x] 新增 V42 迁移；主键显式 `BIGINT`，注销活动范围唯一，审计表不提供更新或删除结构。
- [x] 运行迁移测试确认 V1-V42 空库连续迁移通过。

## 任务 2：换号票据与短信用途隔离

- [x] 先新增票据服务测试，覆盖签发、校验、一次消费、过期、跨用户和跨客户端，确认缺少接口红灯。
- [x] 新增 `CHANGE_MOBILE_CURRENT`、`CHANGE_MOBILE_NEW`、`ACCOUNT_CANCELLATION`，公共短信入口显式拒绝三种认证态用途。
- [x] 实现随机票据、SHA-256 摘要、Redis Lua 原子消费和测试内存实现；原始票据只返回一次。
- [x] 运行短信与票据测试确认绿灯。

## 任务 3：手机号换绑事务

- [x] 先写服务集成测试：旧号校验、新号占用、用户名同步、自定义用户名保留、微信/亲子关系不变、审计摘要和全部会话撤销。
- [x] 扩展 `UserMapper`，用当前用户行锁和期望旧手机号条件原子更新 `mobile`，仅在用户名等于旧手机号时同步用户名。
- [x] 新增换号审计 Mapper 与 `ParentAccountLifecycleService`，数据库冲突映射为状态冲突。
- [x] 运行服务测试确认绿灯并回归家长手机号、微信和关系测试。

## 任务 4：注销前置状态机

- [x] 先写测试：任一活动主/副关系阻止申请、零关系创建 7 天冷静期、重复申请幂等、冷静期撤销、到期计算待执行、已撤销不可重复撤销。
- [x] 新增注销记录领域对象和 Mapper；查询当前活动申请时锁行，撤销只允许 `COOLING_OFF`。
- [x] 实现状态查询、验证码确认、固定确认文本和冷静期计算；不更新用户状态，不删除或匿名化数据。
- [x] 运行状态机测试确认绿灯。

## 任务 5：HTTP、权限和功能开关

- [x] 在 `ParentAuthenticationControllerTest` 增加状态、发送两阶段验证码、签发票据、完成换号及申请与撤销注销用例，先确认接口缺失红灯。
- [x] 新增请求响应类型，所有雪花标识为字符串、手机号只返回掩码。
- [x] 接入 `PARENT_ACCOUNT_LIFECYCLE_MANAGE`，换号成功返回 204 并使当前令牌立即失效。
- [x] 运行控制器、功能开关和动态客户端权限回归确认绿灯。

## 任务 6：Web 与 uni-app 独立页面

- [x] 先写 Web 测试，覆盖两阶段换号、成功清会话和有关联时禁用注销；入口同时受家长角色与功能开关控制。
- [x] 扩展 Web API 和账号安全工作台，使用分步弹窗及危险操作确认。
- [x] 新增 uni-app API、页面、路由和家长首页入口；成功换号后只清理家长会话。
- [x] 运行 Web 目标测试、uni-app 类型检查和双端构建。

## 任务 7：中文文档、全量回归和复核

- [x] 同步 README、00-12、主计划和一致性核对，明确 V42 未执行正式注销、匿名化或机构人工核验。
- [x] 扫描 V1-V42 无自增、无凭据泄漏、无定位调用、无完整手机号审计和无未勾选 V42 任务。
- [x] 顺序执行后端全量、Web 全量与构建、uni-app 类型检查及 H5/微信小程序构建。
- [x] 复核票据重放、验证码用途混用、手机号枚举、并发唯一性、会话撤销和 JavaScript 标识精度，修正重要问题后回归。
