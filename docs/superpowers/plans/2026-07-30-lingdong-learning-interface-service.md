# 灵动伴随接口服务核心实施计划

> **执行说明：** 建议使用 `superpowers:subagent-driven-development` 或 `superpowers:executing-plans` 逐项实施，并用复选框（`- [x]`）记录状态。

**目标：** 实现可审计的接口服务登记、授权范围变更、停用操作和隐私安全的调用结果记录。

**架构：** V12 创建三张使用雪花主键的表。`InterfaceServiceApplicationService` 为每个高风险服务变更创建关联的 `INTERFACE_SERVICE_CHANGE` 系统任务，仅在持久化变更成功后标记生效；独立调用日志方法为后续适配器校验服务已启用提供基础，不在核心中固化供应商行为。

**技术栈：** Java 17、Spring Boot 3.4、MyBatis XML、Flyway、MySQL 8、H2、JUnit 5、AssertJ。

---

### 任务 1：新增 V12 表结构和迁移失败测试

**文件：**
- 新建：`server/src/main/resources/db/migration/V12__create_interface_service_tables.sql`
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

- [x] **步骤 1：编写预期失败的 Flyway 断言**

增加测试，断言 `sys_interface_service`、`sys_interface_service_change` 和 `sys_interface_call_log` 存在；每张表都使用非自增 `BIGINT id`；变更表的 `task_id` 唯一；调用日志表包含 `service_id`、`result`、`error_summary` 和 `trace_id` 字段。

- [x] **步骤 2：运行专测并确认预期失败**

运行：`$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; & mvn test "-Dtest=FlywayMigrationTest"`

预期：V12 尚不存在，新表断言失败。

- [x] **步骤 3：创建 V12 表结构**

创建设计中定义的三张表。所有标识均为 `BIGINT NOT NULL PRIMARY KEY`；增加指向 `sys_user`、`sys_system_task` 和 `sys_interface_service` 的外键；为服务状态与用途、通过 `task_id` 查询变更、按服务和时间查询调用日志建立索引。不得增加凭据、网址、请求正文、响应正文、位置数据或基础种子。

- [x] **步骤 4：确认迁移测试通过**

运行任务 1 命令。预期：Flyway 在空 H2 数据库连续执行 V1-V12，全部迁移断言通过。

### 任务 2：实现可审计的接口服务变更应用层

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/interfaceconfig/domain/InterfaceDirection.java`
- 新建：`server/src/main/java/com/lingdong/learning/interfaceconfig/domain/InterfacePurpose.java`
- 新建：`server/src/main/java/com/lingdong/learning/interfaceconfig/domain/InterfaceAuthorizationScope.java`
- 新建：`server/src/main/java/com/lingdong/learning/interfaceconfig/domain/InterfaceServiceStatus.java`
- 新建：`server/src/main/java/com/lingdong/learning/interfaceconfig/domain/InterfaceServiceChangeType.java`
- 新建：`server/src/main/java/com/lingdong/learning/interfaceconfig/domain/InterfaceService.java`
- 新建：`server/src/main/java/com/lingdong/learning/interfaceconfig/domain/InterfaceServiceChange.java`
- 新建：`server/src/main/java/com/lingdong/learning/interfaceconfig/application/CreateInterfaceServiceChangeCommand.java`
- 新建：`server/src/main/java/com/lingdong/learning/interfaceconfig/application/CreateInterfaceServiceDisableCommand.java`
- 新建：`server/src/main/java/com/lingdong/learning/interfaceconfig/application/CreateInterfaceServiceAuthorizationChangeCommand.java`
- 新建：`server/src/main/java/com/lingdong/learning/interfaceconfig/application/InterfaceServiceApplicationService.java`
- 新建：`server/src/main/java/com/lingdong/learning/interfaceconfig/infrastructure/persistence/InterfaceServiceMapper.java`
- 新建：`server/src/main/java/com/lingdong/learning/interfaceconfig/infrastructure/persistence/InterfaceServiceChangeMapper.java`
- 新建：`server/src/main/resources/mapper/interfaceconfig/InterfaceServiceMapper.xml`
- 新建：`server/src/main/resources/mapper/interfaceconfig/InterfaceServiceChangeMapper.xml`
- 测试：`server/src/test/java/com/lingdong/learning/interfaceconfig/application/InterfaceServiceApplicationServiceTest.java`

- [x] **步骤 1：编写预期失败的审批流程测试**

使用既有辅助方法创建系统管理员和系统审核员。验证创建草稿返回任务标识但不创建服务；提交并批准执行后，服务以 19 位标识和 `ENABLED` 状态存在。验证停用和授权范围变更草稿在关联任务批准执行前不改变现有服务。

- [x] **步骤 2：运行专测并确认预期失败**

运行：`$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; & mvn test "-Dtest=InterfaceServiceApplicationServiceTest"`

预期：接口服务模块尚不存在，编译失败。

- [x] **步骤 3：实现命令、领域记录、Mapper 和应用服务**

创建草稿时要求 `SYS_ADMIN`。服务名称和调用方名称最长 100 字符，授权范围值最长 128 字符，并校验责任人存在。创建 `SystemTaskType.INTERFACE_SERVICE_CHANGE` 任务和雪花标识变更记录。`submit` 委托关联任务提交；`approveAndApply` 批准后只执行登记、停用或授权变更之一，且仅在对应 Mapper 更新一行后调用 `markEffective`。

- [x] **步骤 4：确认审批流程测试通过**

运行任务 2 命令。预期：全部流程测试通过，已驳回或仅批准但未执行的变更不得报告为服务已生效。

### 任务 3：增加已启用服务调用日志并完成验证

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/interfaceconfig/domain/InterfaceCallResult.java`
- 新建：`server/src/main/java/com/lingdong/learning/interfaceconfig/domain/InterfaceServiceCallLog.java`
- 新建：`server/src/main/java/com/lingdong/learning/interfaceconfig/application/RecordInterfaceServiceCallCommand.java`
- 修改：`server/src/main/java/com/lingdong/learning/interfaceconfig/application/InterfaceServiceApplicationService.java`
- 新建：`server/src/main/java/com/lingdong/learning/interfaceconfig/infrastructure/persistence/InterfaceServiceCallLogMapper.java`
- 新建：`server/src/main/resources/mapper/interfaceconfig/InterfaceServiceCallLogMapper.xml`
- 修改：`docs/design/03-系统架构设计-HLD-V1.0.md`
- 修改：`docs/design/04-数据库设计-V1.0.md`
- 修改：`docs/design/05-Flyway迁移规范-V1.0.md`
- 修改：`docs/design/12-当前实现一致性核对-V1.0.md`
- 测试：`server/src/test/java/com/lingdong/learning/interfaceconfig/application/InterfaceServiceApplicationServiceTest.java`

- [x] **步骤 1：编写预期失败的调用日志测试**

断言为未登记或已停用服务记录调用时抛出异常。已启用服务批准生效后，记录一次失败调用，断言持久化记录包含 19 位标识、预期结果、调用方、追踪标识和受限长度异常摘要；表中不存在报文或凭据字段。

- [x] **步骤 2：运行专测并确认预期失败**

运行任务 2 命令。预期：调用记录 API 尚不存在，或无法完成预期的已启用服务流程。

- [x] **步骤 3：实现调用日志写入路径并记录 V12**

按标识读取服务，拒绝不存在或已停用服务，校验调用方、异常和追踪字段长度，通过 `IdGenerator` 分配标识，只插入允许的摘要字段。更新设计文档，将接口服务元数据、审批流程和调用结果记录标记为已实现，同时明确当时尚无具体供应商适配器或 REST 控制器。

- [x] **步骤 4：运行静态检查和全量测试**

运行：`rg -n "AUTO_INCREMENT|PRIMARY KEY \\([^)]*_id" src/main/resources/db/migration`

预期：无匹配。然后运行：`$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; & mvn test`

预期：Flyway V1-V12 执行后全部测试通过。
