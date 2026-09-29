# 灵动伴随数据字典管理闭环设计

**日期：** 2026-08-15  
**需求依据：** `BRD-3.2-07`、FSD-SYS-03、WBS-06 第一项。

## 1. 目标与范围

本专项把 V10 已存在的数据字典核心补齐为可独立验收的 Web 管理闭环：迁移种子、动态 RBAC、功能开关、REST、OpenAPI、React 页面、自动化测试和中文文档。字典继续作为业务下拉统一来源，但本专项不改造所有业务页面，也不在小程序增加后台配置入口。

## 2. 方案比较与选择

1. **推荐方案：按公共能力逐模块闭环。** 先完成字典，再依次完成附件规则、缓存、模板和接口服务。每个模块独立测试、独立权限，风险最小。
2. **单一“大配置中心”一次交付。** 页面看似集中，但五类状态、审批和安全边界不同，测试与回归范围过大。
3. **先只做通用 CRUD 框架。** 可减少初期代码，但会把关键字典审批、默认项和动态权限压平，无法满足现有需求。

采用方案一。Web 导航最终可逐步形成“系统配置”能力集合，但后端边界保持模块独立。

## 3. 数据与迁移

V51 不新增业务表，复用 `sys_dictionary_type` 和 `sys_dictionary_item`。迁移新增：

- 全局 Web 功能开关 `DICTIONARY_MANAGEMENT`；
- `DICTIONARY_READ` 和 `DICTIONARY_MANAGE` 两项 Web 权限；
- 系统管理员角色的明确允许授权。

新增基础数据和授权关系全部使用 19 位雪花数字。V1-V50 不修改，V51 空库连续迁移后主键表总数仍为 77。

## 4. 后端设计

新增 `DictionaryManagementController`，契约如下：

| 方法与路径 | 权限 | 行为 |
|---|---|---|
| `GET /api/v1/dictionaries/types` | `DICTIONARY_READ` | 返回全部类型，按排序和标识稳定排序。 |
| `POST /api/v1/dictionaries/types` | `DICTIONARY_MANAGE` | 创建普通字典类型。 |
| `PUT /api/v1/dictionaries/types/{typeId}` | `DICTIONARY_MANAGE` | 修改名称、排序和状态，编码不变。 |
| `GET /api/v1/dictionaries/types/{typeId}/items` | `DICTIONARY_READ` | 返回该类型全部字典项，包含停用历史项。 |
| `POST /api/v1/dictionaries/types/{typeId}/items` | `DICTIONARY_MANAGE` | 创建字典项。 |
| `PUT /api/v1/dictionaries/items/{itemId}` | `DICTIONARY_MANAGE` | 修改名称、排序、状态和默认项。 |

控制器在权限切面后校验 `DICTIONARY_MANAGEMENT` 开关。应用服务使用 `PermissionDecisionService` 复核动态管理权限，允许系统管理员将该权限授予自定义运维角色；不再用角色编码代替权限。关键字典编码仍拒绝直接变更，等待既有 `KEY_DICTIONARY_CHANGE` 系统任务执行器接入。

全部 HTTP 标识以字符串返回。不存在资源返回中性错误，重复编码或非法状态保持统一校验/冲突响应。

## 5. OpenAPI

引入与 Spring Boot 3.4 官方兼容矩阵匹配的 `springdoc-openapi-starter-webmvc-api 2.8.17`，将 JSON 规范固定到 `/api/v1/openapi`。该路径进入现有认证边界，不开放 Swagger UI；测试断言六个字典路径进入生成规范。其他现有控制器也会被自动收录，不改业务行为。

## 6. Web 交互

新增独立路由 `/dictionaries` 和“数据字典”导航，仅在 Web 能力开关启用且当前用户具有系统管理员或后续可用权限信息时展示。当前 `/auth/me` 尚未返回权限编码，因此首版入口按平台系统管理员展示，后端动态权限仍是最终边界；后续统一菜单权限专项改为服务端菜单裁剪。

页面采用左右工作区：左侧字典类型表，右侧当前类型的字典项表。支持类型与字典项新增、编辑、排序、启停和默认项；关键字典操作被后端拒绝时展示原始中文业务提示。表格使用固定列宽和稳定空态，不在小程序增加页面或 API 调用。

## 7. 测试与验收

- Flyway：V51 连续迁移、19 位种子、77 张非自增主键表、系统管理员授权。
- 后端：列表包含停用项、动态读写权限、非授权拒绝、开关停用拒绝、唯一默认项、关键字典拒绝、标识字符串化、OpenAPI 路径。
- Web：初始列表、切换类型、创建类型、创建字典项、编辑启停/默认、错误反馈、入口和直达路由开关。
- 回归：后端全量、Web 全量与生产构建、uni-app 类型检查和双目标构建。
- 环境：只使用本地 H2、MockMvc 和前端测试替身，不连接远程 MySQL、Redis、共享测试、预生产或生产。
