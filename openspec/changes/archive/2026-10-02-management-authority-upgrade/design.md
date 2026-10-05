# 管理端权限与组织体系升级设计

## Context

- 权限运行时判定链路为：`@RequirePermission` 注解 → `PermissionDecisionService` → `sys_role_permission` / `sys_user_permission`（后端 246 处引用），本设计不动该链路
- `sys_menu`（V87）已具备 DIRECTORY/PAGE/BUTTON 类型、同级排序接口与审计；按钮目录（V88/V89）编码为 UI actionKey 风格，与权限编码不一致
- `sys_permission` 目录由各迁移手工种子维护；小程序端权限（MINIAPP client）依赖其中
- 组织节点移动现有"申请-审核-执行"流（`OrganizationChange`），无同级排序与直接移动接口
- 本地源码与远程 MySQL 基线 V89，下一个迁移编号 V90；已执行迁移不可修改，失败采用修复迁移不回滚

## Goals / Non-Goals

**Goals**
- 菜单树成为 Web 权限目录唯一入口：编码即权限编码，自动同步，授权 UI 勾选菜单树
- 组织树拖拽（顺序 + 层级）直接生效，行政区划支撑重庆市 38 区县初始化
- 角色侧批量授用户、行内快捷授权

**Non-Goals**
- 不重构 `PermissionDecisionService` 与权限注解拦截
- 不改动小程序权限目录与授予方式
- 不废除组织"申请停用/申请删除"审核流
- 不引入云目录/多级缓存等新基础设施

## Decisions

### D1. 编码-权限同步采用"菜单服务内显式同步"（路线 B）
菜单应用服务在创建/修改 PAGE 与权限按钮时，按编码对 `sys_permission` 做 upsert（编码一致、client=WEB）；停用/删除菜单节点时将对应权限置为 DISABLED 而非物理删除，保留既有授权关系完整性。
- 替代方案 a：DB 触发器同步——逻辑隐藏在库内，违背项目"一切变更走可审计代码"惯例，否决
- 替代方案 b：彻底废弃 `sys_permission`（路线 A）——246 处判定点与小程序权限全量重构，回归风险不可控，否决
- 权限按钮需新增可授权标记（`sys_menu` 增列 `grantable TINYINT`，BUTTON 类型专用），纯 UI 按钮 `grantable=0` 不同步

### D2. 授权视图 = 菜单树投影 + 差量保存
新增接口：`GET /iam/roles/{id}/menu-grants`（返回菜单树含权限按钮、每个节点当前是否已授权）与 `PUT /iam/roles/{id}/menu-grants`（提交勾选集合，服务端与既有授权差量计算后增删 `sys_role_permission`）。授权复用既有授予用例路径以保证审计粒度；批量操作汇总一条 `ROLE_PERMISSION_CONFIGURE` 审计事件 + 明细。
- 替代方案：全删全插——审计不可读、易误伤显式 DENY 语义，否决

### D3. 组织拖拽 = 新增直接生效接口
新增 `PUT /organizations/order`（同级排序）与 `PUT /organizations/{id}/position`（改父级），`@RequirePermission` 组织管理权限，仅限 SYS_ADMIN 数据范围；复用既有变更执行器中的路径重建与唯一名校验逻辑；审计新增 `OrganizationChangeType` 直接生效类型（DIRECT_REORDER / DIRECT_MOVE），记录前后位置。
- 替代方案：拖拽自动生成变更申请（用户已否决——要求直接生效）

### D4. 按钮批量维护 = 单父节点批量提交接口
新增 `POST /iam/menus/{id}/buttons:batch`（批量新增）与 `PUT /iam/menus/buttons:batch`（批量修改），事务内逐条校验编码唯一性；冲突条目返回失败明细，合法条目整批回滚（全部成功或全部失败，避免半批状态难排查）。
- 备选：部分成功——交互复杂、审计碎片化，否决；采用整批原子

### D5. 存量重编码由迁移驱动 + 前端同版本更新
V91 迁移内置"旧 actionKey → 权限码"映射表，更新权限型按钮的 `code` 并同步其 `grantable=1`；同版本前端将 `ConfiguredButton` 的 actionKey 引用同步替换为新编码（权限型按钮），纯 UI 按钮编码不动。迁移幂等（`WHERE code=旧值`）。
- 风险控制点：前端 actionKey 替换量大，用映射表生成脚本批量替换 + 全量按钮相关测试回归

### D6. 行政区划与初始化
- `sys_organization` 增列 `admin_division_code VARCHAR(32) NULL`，逻辑引用字典条目（不加外键，与字典模块既有松耦合方式一致）
- 字典新增类型 `ADMIN_DIVISION`，预置重庆市 38 区县（编码采用 GB/T 2260 行政区划码）
- 组织初始化：重庆市（CITY）节点 + 38 区县（REGION）子节点，`WHERE NOT EXISTS` 幂等插入；节点编码用区划码，避开既有 `organization_code` 冲突
- 内置类型追加 CITY/COUNTRY/FAMILY（`built_in=1`），不动既有五类

### D7. 角色批量授用户 = 循环复用既有授予用例
新增 `POST /iam/roles/{id}/users:batch`，服务端逐用户走既有 `AssignRoleToUser` 用例（保留最小组织范围校验与逐条审计）；组织范围角色请求体必填 `organizationId`。整批事务原子，任一失败整批回滚并报告失败用户及原因。

## Risks / Trade-offs

- [重编码导致前端按钮显示控制错乱] → 迁移映射表与前端替换脚本同源生成；按钮相关 Web 测试全量回归；真机浏览器抽查截图留证
- [菜单编码被改成与既有权限码不同义的新值] → upsert 按编码新建权限但无任何角色授权，不产生越权；停用旧权限提示确认
- [拖拽直接生效绕过双人复核] → 仅 SYS_ADMIN 可操作 + 审计记录前后位置；既有审核流保留，敏感操作（停用/删除）仍走复核
- [批量接口部分失败语义模糊] → 统一整批原子 + 失败明细返回，规格已固化该行为
- [38 区县数据与后续行政区划调整] → 数据走迁移可追加修正脚本，字典条目可界面维护

## Migration Plan

1. V90：组织侧迁移（`admin_division_code` 列、内置类型 CITY/COUNTRY/FAMILY、`ADMIN_DIVISION` 字典与 38 区县、重庆市组织树初始化，全部幂等）
2. V91：菜单侧迁移（`sys_menu.grantable` 列、权限型按钮重编码映射、存量 PAGE 编码对齐、权限同步回填）
3. H2 全量迁移测试 + 真实远程库受控发布（隔离库重演 → 业务库迁移 → 保留核对，沿用既有发布流程）
4. 应用发版与前端同版本发布；健康检查 + 浏览器验证拖拽/授权/按钮控制
5. 回滚策略：遵循"修复迁移不回滚"原则，缺陷以 V92+ 前向修正

## Open Questions

（无——四项关键分歧已由用户确认：路线 B、两类按钮、小程序不动、存量重编码；拖拽直接生效与批量原子语义已在规格中固化。）
