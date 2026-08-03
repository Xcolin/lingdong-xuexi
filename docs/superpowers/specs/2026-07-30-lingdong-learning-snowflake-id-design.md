# 灵动学习 19 位雪花主键设计

## 1. 决策与边界

用户要求所有表的主键统一采用雪花算法生成的 19 位数字。本项目的 V1-V11 Flyway 迁移尚未在共享测试、预生产或生产数据库执行，因此本次直接重建历史迁移的初始数据库基线，而不新增仅用于兼容旧数据的过渡迁移。以后任一迁移进入共享环境后，已执行脚本不得修改，后续结构变化必须新增版本迁移。

本次范围覆盖当前 18 张表及后续新建表：`sys_config`、组织与 IAM 表、系统任务、功能开关、权限关联表、字典表和缓存操作表。所有表均拥有 `id BIGINT NOT NULL PRIMARY KEY`；不再使用 `AUTO_INCREMENT`、联合主键或以外键字段作为主键。

## 2. ID 算法与配置

运行期由后端应用在任何插入操作前分配 ID，数据库不提供默认生成规则。算法采用 64 位雪花结构：41 位毫秒时间差、5 位数据中心号、5 位工作节点号和 12 位同毫秒序列号。自定义纪元固定为 `1288834974657`（UTC 2010-11-04T01:42:54.657Z），在当前项目运行期产生正整数 19 位 `BIGINT`；单节点同毫秒最多生成 4096 个 ID。

配置键如下，两个节点标识均为 0 至 31 的整数；部署到多实例环境时，任意同时运行的实例必须使用不同的 `(datacenter-id, worker-id)` 组合：

```yaml
lingdong:
  id:
    snowflake:
      datacenter-id: ${SNOWFLAKE_DATACENTER_ID:0}
      worker-id: ${SNOWFLAKE_WORKER_ID:0}
```

生成器检测到系统时钟回拨时拒绝分配新号并抛出明确异常，不生成重复 ID。序列耗尽时等待下一毫秒后继续。应用服务依赖抽象 `IdGenerator`，测试可注入固定时间源验证位段、并发序列和回拨行为。

## 3. 表结构与唯一性

原本已有单列 `id` 的表保留列名和所有外键类型，但去除自增属性。`sys_role_permission`、`sys_organization_admin`、`sys_user_organization`、`sys_user_permission`、`sys_role_data_scope` 新增独立 `id` 主键，并保留原联合字段的唯一约束。`sys_feature_toggle_change` 新增 `id` 主键，原 `task_id` 改为唯一外键，继续保证一条系统任务只对应一条功能开关变更。

| 表 | 主键调整 | 保留的业务唯一性 |
| --- | --- | --- |
| `sys_role_permission` | 新增雪花 `id` | `(role_id, permission_id)` |
| `sys_organization_admin` | 新增雪花 `id` | `(organization_id, user_id)` |
| `sys_user_organization` | 新增雪花 `id` | `(user_id, organization_id)` |
| `sys_feature_toggle_change` | 新增雪花 `id` | `task_id` |
| `sys_user_permission` | 新增雪花 `id` | `(user_id, permission_id)` |
| `sys_role_data_scope` | 新增雪花 `id` | `(role_id, organization_id)` |

所有 `*_id` 关联字段继续为 `BIGINT`，且父实体在创建子实体或关联记录前已获得雪花 ID。查询、去重和权限判断继续按原业务键进行，不以新的代理主键替代业务约束。

## 4. 初始化数据

Flyway 的内置角色、组织类型和功能开关必须显式写入合法雪花 ID。固定值由同一位段算法在 `2025-01-01T00:00:00Z`、数据中心 0、工作节点 0 下预生成，序列号依次递增：

| 数据 | ID |
| --- | ---: |
| `SYS_ADMIN` 至 `STUDENT` 六个内置角色 | `1874244142494646273` 至 `1874244142494646278` |
| `REGION` 至 `CLASS` 五个内置组织类型 | `1874244142494646279` 至 `1874244142494646283` |
| `GEO_ATTENDANCE`、`STUDENT_LOCATION_TRACK` | `1874244142494646284`、`1874244142494646285` |

这些值是仅用于版本化初始化数据的常量；任何管理端运行期新增记录仍必须调用 `IdGenerator`。

## 5. 应用与持久化约束

所有写入由应用服务显式取得 ID 后传入领域对象或关联 Mapper。覆盖用户、角色、权限、组织、组织类型、系统任务、功能开关、功能开关审批变更、字典类型、字典项、缓存操作，以及全部用户/角色/权限/组织关联表。MyBatis `INSERT` 必须显式包含 `id` 列；不得依赖数据库主键回填，也不得在 Mapper 中隐式生成 ID。

目前没有对外 REST 业务接口。后续 API 设计中，所有返回和入参中的 ID 均以 JSON 字符串表示，服务端仍使用 `Long`，避免 Web 和小程序 JavaScript 的 `Number` 精度损失。

## 6. 验证与发布

测试覆盖生成器产生 19 位正数、同毫秒唯一性、节点参数非法和时钟回拨；Flyway 测试覆盖 18 张表都有非 identity 的 `BIGINT id` 主键、关联表唯一约束和初始化数据的 19 位 ID；既有应用服务测试增加新建对象 ID 断言。全量 `mvn test` 通过后，使用新的空 MySQL 8 数据库验证 V1-V11 可完整建库。

本次重建基线只适用于用户已确认的“无共享数据库”状态。第一次共享环境部署后，发布流程必须固定迁移校验和校验和，不得再修改 V1-V11。
