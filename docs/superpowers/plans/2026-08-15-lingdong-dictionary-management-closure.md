# 灵动学习数据字典管理闭环实施计划

> **执行要求：** 使用 superpowers:executing-plans 按任务逐项实施，所有步骤使用复选框记录。

**目标：** 将现有字典核心补齐为受动态 RBAC 和功能开关保护的 REST/OpenAPI 与 Web 管理闭环。

**架构：** V51 只新增功能开关与权限种子；后端复用字典领域和 MyBatis XML，新增管理查询及 HTTP 契约；React Web 使用独立 API 和页面，小程序不增加后台配置能力。

**技术栈：** Spring Boot 3.4、JDK 17、MyBatis XML、Flyway、Springdoc OpenAPI、React 18、Ant Design Pro、Vitest。

---

### 任务 1：V51 与后端失败测试

- [x] 在 `FlywayMigrationTest` 先增加 V51、权限、开关、19 位种子和表数量断言。
- [x] 新建 `DictionaryManagementControllerTest`，先覆盖六个接口、动态权限、功能停用和 OpenAPI 契约。
- [x] 运行专测，确认因 V51、控制器、全量查询和 OpenAPI 尚不存在而按预期失败。

### 任务 2：最小后端实现

- [x] 新建 `V51__add_dictionary_management.sql`，只写 19 位开关、权限和系统管理员授权种子。
- [x] 为字典类型和字典项 Mapper 增加稳定排序的全量查询。
- [x] 将字典应用服务管理门禁改为 `DICTIONARY_MANAGE` 动态权限复核，并保留关键字典审批边界。
- [x] 新建中文注释的请求、响应和 `DictionaryManagementController`，接入功能开关及 `@RequirePermission`。
- [x] 接入 Springdoc JSON 规范到 `/api/v1/openapi`，运行后端专测至通过。

### 任务 3：Web 失败测试与实现

- [x] 新建 `DictionaryManagementPage.test.tsx`，先验证加载、切换、创建、编辑和错误反馈并观察失败。
- [x] 新建 `web/src/api/dictionaries.ts` 与 `DictionaryManagementPage.tsx`，实现左右工作区、表单和稳定状态。
- [x] 修改 `App.tsx`、能力类型和相关测试，增加开关控制的导航和直达路由。
- [x] 运行字典页面、App 和类型检查测试至通过。

### 任务 4：文档和全量验收

- [x] 同步 README、00-12 中文设计文档、总计划和本专项结论。
- [x] 执行后端全量、Web 全量与构建、uni-app 类型检查和双目标构建。
- [x] 扫描 V51 主键、敏感地址、小程序后台配置引用和补丁格式；按证据更新进度。

### 专项结论

V51 已完成本地闭环。后端相关 69 项和全量 405 项测试通过，Web 专项 13 项和全量 80 项测试通过，Web 类型检查及生产构建、uni-app 类型检查、H5 构建和微信小程序构建均通过。V51 标识均为 19 位数字，未发现已知远程地址或明文凭证，小程序源码及构建产物未引用定位地图能力，补丁格式无错误。远程 MySQL、Redis、微信、共享测试、预生产、生产、业务 UAT 和浏览器视觉验收未执行。
