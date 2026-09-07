# 灵动学习缓存管理闭环实施计划

> **执行要求：** 使用 superpowers:executing-plans 按任务逐项实施，所有步骤使用复选框记录。

**目标：** 在现有 V11 缓存核心上完成受动态 RBAC、固定审核角色和功能开关保护的 REST/OpenAPI 与 React Web 闭环。

**架构：** V52 只增加功能开关、权限和内置角色授权；后端扩展查询、驳回状态与管理控制器；Web 使用独立 API 和页面，uni-app 不承载后台缓存管理。

**技术栈：** Spring Boot 3.4、JDK 17、MyBatis XML、Flyway、Springdoc OpenAPI、React 18、Ant Design Pro、Vitest。

---

### 任务 1：V52 与后端失败测试

- [x] 在 `FlywayMigrationTest` 增加 V52、功能开关、三项权限、四项角色授权和 19 位种子断言。
- [x] 新建 `CacheManagementControllerTest`，覆盖普通执行、历史查询、高风险提交、审核通过、审核驳回、动态权限、功能停用和 OpenAPI。
- [x] 运行专测，确认因 V52、查询接口、驳回状态和控制器尚不存在而失败。

### 任务 2：后端最小实现

- [x] 新建 `V52__add_cache_management.sql`，只写 19 位开关、权限和角色授权种子。
- [x] 为缓存台账增加最近记录、待审核任务查询和 `REJECTED` 状态更新。
- [x] 将普通操作改为 `CACHE_MANAGE` 动态权限复核，高风险提交和审批继续执行固定角色约束。
- [x] 新建中文请求、响应和 `CacheManagementController`，接入功能开关与三类动态权限。
- [x] 扩展公共能力和 OpenAPI，运行后端专测至通过；补齐用户会话清除处理器及全局会话撤销测试。

### 任务 3：Web 失败测试与实现

- [x] 新建 `CacheManagementPage.test.tsx`，先覆盖管理员执行、高风险提交、审核员审批、只读状态和错误反馈。
- [x] 新建独立 `web/src/api/cache-management.ts` 与缓存管理页面。
- [x] 修改 App、能力类型和路由测试，按 `CACHE_READ/CACHE_MANAGE/CACHE_REVIEW` 控制入口和操作。
- [x] 运行缓存页面、App、类型检查和生产构建至通过，并完成桌面及 390px 窄屏检查。

### 任务 4：全量验收与文档

- [x] 执行后端全量、Web 全量与构建、uni-app 类型检查和双目标构建。
- [x] 扫描 V52 标识、敏感信息、定位能力和补丁格式。
- [x] 同步 README、00-12 中文设计文档、总计划和本专项结论。

**验收结论：** 后端全量 `412` 项、Web 全量 `85` 项、Web 类型检查与生产构建、uni-app 类型检查和 H5/微信小程序构建全部通过；桌面与 `390px` 窄屏检查通过。V52 的 8 个基础数据标识均为 19 位数字，敏感信息、定位能力和补丁格式扫描无错误。远程 MySQL、Redis、共享测试、预生产、生产及业务 UAT 未执行。
