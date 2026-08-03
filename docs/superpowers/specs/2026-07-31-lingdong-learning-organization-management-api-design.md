# 灵动学习组织管理 API 设计

## 1. 目标与范围

本轮在已有组织类型与区域、学校树形组织应用服务基础上，为 Web 管理端开放最小的组织管理接口：查询组织类型、创建组织类型、查询组织树和创建组织节点。

本轮不实现组织类型或组织节点的编辑、启停、删除、移动、批量导入、分页列表、组织管理员查询，以及重要组织停用任务。以上动作涉及历史关系、组织范围扩大或系统任务审批，当前应用服务没有完整状态规则，必须后续独立设计。

## 2. 授权与数据范围

Flyway `V17` 创建 4 个 Web `OPERATION` 权限并授予内置 `SYS_ADMIN`：

| 权限编码 | 受控动作 |
|---|---|
| `ORG_TYPE_READ` | 查询组织类型目录。 |
| `ORG_TYPE_CREATE` | 创建自定义组织类型。 |
| `ORG_NODE_READ` | 查询组织树。 |
| `ORG_NODE_CREATE` | 创建组织节点。 |

控制器方法使用既有 `@RequirePermission` 完成动态 RBAC 判断，用户级 `DENY` 仍优先于角色授权。由于当前组织树查询尚未在 SQL 中接入角色数据范围、用户组织关联和组织管理员范围交集，以上四个接口在应用服务层额外要求操作者具有 `SYS_ADMIN`。这能避免被动态授予读取权限的非系统管理员读取全量区域、学校树。

后续实现组织数据范围拦截时，应先为组织查询提供按组织根节点过滤的 Mapper 与集成测试，再评估去除该系统管理员专属限制；不得直接放宽 V17 的全量组织树接口。

## 3. API 契约

所有接口位于 `/api/v1`，需要有效 Bearer 会话。请求与响应中所有业务 ID 都以 JSON 字符串表达，以保护 19 位雪花标识精度。

| 接口 | 权限 | 成功响应 | 流程 |
|---|---|---|---|
| `GET /organization-types` | `ORG_TYPE_READ` | 200，组织类型数组 | 校验系统管理员后按排序值、编码返回内置和自定义类型。 |
| `POST /organization-types` | `ORG_TYPE_CREATE` | 201，组织类型 | 校验编码、名称、排序值及唯一性，创建自定义启用类型。 |
| `GET /organizations` | `ORG_NODE_READ` | 200，嵌套组织树数组 | 校验系统管理员后按排序值、编码构建区域/学校等根节点及子节点。 |
| `POST /organizations` | `ORG_NODE_CREATE` | 201，组织节点 | 校验类型启用、父节点可用、编码唯一和同级名称唯一，生成物化路径。 |

HTTP 错误沿用现有统一响应：字段或枚举错误为 400 `VALIDATION_ERROR`；不存在的组织类型或父级组织为 404 `RESOURCE_NOT_FOUND`；重复编码、重复同级名称、停用类型或停用父级导致的状态冲突为 409 `STATE_CONFLICT`；动态 RBAC 或系统管理员约束拒绝为 403 `ACCESS_DENIED`。

## 4. 分层与持久化

新增 `OrganizationQueryApplicationService`，只负责组织类型平铺目录与组织节点集合查询。类型 Mapper 新增按 `sort_order`、`type_code` 排序的 `findAll`，组织 Mapper 新增按路径、排序、编码排序的 `findAll`；控制器不写 SQL。

新增 `OrganizationManagementApplicationService` 作为 HTTP 管理用例边界。它从当前 Bearer 身份的用户标识验证 `SYS_ADMIN`，再调用已有 `OrganizationApplicationService` 与查询服务。已有组织领域服务继续保留编码、树路径、父子可用性和唯一性规则。

当创建组织时，缺失组织类型应抛出 `ResourceNotFoundException`，停用类型保持状态冲突；缺失父级组织同样抛出 `ResourceNotFoundException`，停用父级保持状态冲突。这样不将资源不存在误报为参数错误。

组织树响应以 DTO 构建，不暴露内部 `parent_scope_key`。每个节点包含 `id`、`parentId`、编码、名称、类型、路径、排序、状态、创建更新时间和 `children`；根节点的 `parentId` 为 `null`。

## 5. 测试与非目标

新增 MockMvc 集成测试覆盖系统管理员创建类型、创建区域和学校、查询嵌套组织树、返回字符串 ID、普通用户的路由拒绝、已具备路由权限但不是系统管理员的专属规则拒绝，以及缺失组织类型或父节点的 404。Flyway 测试验证 V17 的 4 个权限和 4 条 `SYS_ADMIN` 角色授权均使用 19 位雪花常量。

本轮不连接共享测试、预生产或生产数据库；迁移与集成测试只在本地 H2 MySQL 兼容模式执行。H2 的 Flyway 版本告警作为已知测试环境提示，不构成 MySQL 8 发布验证。
