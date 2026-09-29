# 灵动伴随接口服务管理闭环实施计划

> **执行要求：** 使用 superpowers:executing-plans 和 superpowers:test-driven-development 逐项实施，所有步骤用复选框记录。

**目标：** 在 V12 核心上完成 V53 动态权限、功能开关、REST/OpenAPI 和 React Web 管理闭环。

**设计：** [接口服务管理闭环设计](../specs/2026-08-31-lingdong-interface-service-management-design.md)

### 任务 1：V53 与后端失败测试

- [x] 在 `FlywayMigrationTest` 增加 V53、功能开关、三项权限、四项角色授权和 19 位种子断言。
- [x] 新建 `InterfaceServiceManagementControllerTest`，覆盖查询、登记、启停、授权变更、审核、驳回、权限、开关和 OpenAPI。
- [x] 扩展领域测试覆盖重新启用和并发状态保护。
- [x] 运行专测，确认缺少 V53、查询接口和控制器时失败。

### 任务 2：后端最小实现

- [x] 新建 `V53__add_interface_service_management.sql`，只写 19 位开关、权限和角色授权种子。
- [x] 为服务、变更和调用台账 Mapper 增加受限查询，为状态机增加重新启用与条件更新。
- [x] 在应用服务接入动态权限，补齐创建并提交、审核通过、驳回和只读查询。
- [x] 新建中文请求、响应和 `InterfaceServiceManagementController`，接入功能开关与三类动态权限。
- [x] 扩展公共能力与 OpenAPI，运行后端专测至通过。

### 任务 3：Web 失败测试与实现

- [x] 新建 `InterfaceServiceManagementPage.test.tsx`，先覆盖管理员提交、审核员审批、只读状态和错误反馈。
- [x] 新建独立接口服务 API 与管理页面，服务、变更、审核和调用台账使用四个页签。
- [x] 修改 App、能力类型和路由测试，按三项动态权限控制入口和操作。
- [x] 运行页面、App、类型检查和生产构建至通过，并完成桌面及 390px 窄屏检查。

### 任务 4：全量验收与文档

- [x] 执行后端全量、Web 全量与构建、uni-app 类型检查和双目标构建。
- [x] 扫描 V53 标识、敏感信息、定位能力和补丁格式。
- [x] 同步 README、00-12 中文设计文档、总计划和本专项结论。

## 实施结论

V53 已完成接口服务元数据管理本地闭环，后端全量 418 项、Web 全量 89 项、Web 类型检查与生产构建、uni-app 类型检查和 H5/微信小程序双目标构建均通过。桌面与 390px 窄屏已检查服务宽表、登记长表单、审核宽表和审核弹窗，页面无整体横向溢出；临时视觉预览文件已删除。远程 MySQL、Redis、共享测试、预生产、生产和业务 UAT 未执行。调用方鉴权、签名、限流、幂等和熔断不属于本专项，继续归 WBS-09。
