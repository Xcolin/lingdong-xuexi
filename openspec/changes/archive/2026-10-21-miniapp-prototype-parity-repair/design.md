# 设计：小程序原型 1:1 施工差距修复

## Context

78 视图审计（proposal.md - Why）已定性 4 个实现缺陷、1 个授权模型缺口与 6 个视图域的"原型有/实现无"差距。技术栈为单体 Spring Boot 3.4.5 后端（MyBatis XML、Flyway、authorization-model.json 权限目录）+ uni-app/Vue 3 小程序。施工须在 8081 克隆库与 proto_verify fixture 验证，真实 8080 只读；临时 DB 改动必须 on/restore 或 grant/revoke 还原。

已核实的现状事实：

- `authorization-model.json` 中家长关系域 policies（PARENT_RELATIONSHIP_READ / SECONDARY_PARENT_INVITE_CREATE / SECONDARY_PARENT_UNBIND / PRIMARY_PARENT_TRANSFER_CREATE / PRIMARY_PARENT_SELF_UNBIND / STUDENT_PARENT_INVITE_RESPOND）均声明单值 `"client": "WEB"`；同文件 policies[119]（STUDENT_PARENT_INVITE_CREATE）**不带 client 字段**，即目录层支持"不声明 client = 不限客户端"。
- 权限判定链（PermissionDecisionService.usable）：supportsClient(catalog.client, requestClient) → findAllowedCodes 只读 sys_role_permission / sys_user_permission 关联。
- 内置 PARENT / PARENT_ADMIN 角色对 PARENT_RELATIONSHIP_READ / SECONDARY_PARENT_INVITE_CREATE 的 sys_role_permission 关联数为 0。
- Flyway 最新版本 V135，新迁移从 V136 起编。

## Goals / Non-Goals

**Goals**

- 修复 4 个缺陷，使受影响视图行为恢复正确（无需接口改动）。
- 打通小程序端家长关系授权链（策略声明 + 内置角色种子授权）。
- 按视图域补齐"原型有/实现无"的字段、视图与交互，逐屏可对照验收。

**Non-Goals**

- 不改 Web 管理端既有授权能力（迁移只增不删）。
- 不实现"实现增强优于原型"的回退（如 06-01 明细维度、06-07 多状态 filter 保持现状，记录为 accepted deviation）。
- 不处理语义不同需产品决策的视图（04-04 账号关联 vs 候选加入、07-05 培训申请 vs 入学管理、01-03/01-07/01-08 注册字段）。
- 不触碰 01 组本地不可达项（01-04/01-05/01-09 需真实微信授权或平台审核环境）。

## Decisions

### D1 授权缺口：去掉 policies 的 client 限制 + Flyway 种子内置角色授权

- 家长关系域 6 条 policy 的 `"client": "WEB"` 删除（对齐 policies[119] 先例：无 client 字段 = 不限客户端）。不引入"client 为数组"的新语义，避免改动目录解析器。
- 新增 Flyway 迁移（V136 起）为内置 PARENT / PARENT_ADMIN 角色 INSERT sys_role_permission 关联行（仅新增，幂等写法 NOT EXISTS 保护）。PARENT_ACCOUNT_LIFECYCLE_MANAGE 既有种子迁移作为格式先例。
- 备选（否决）：仅改 sys_user_permission 逐用户授权——不可扩展，新家长仍缺权限。

### D2 缺陷修复：组件级最小改动

- OrganizationOwnerTransfer：组件增加 mounted 兜底 load（organizationId 就绪即加载），不重构页面 onShow 时序。
- unified-login：`await import('@/api/http')` 改为模块顶部静态导入（organization-login 已验证此形态可用），错误分支 `e instanceof ApiError` 行为不变。
- 03-06 操作溢出 / 03-10 标题重复：纯布局/模板修正。

### D3 视图域差距：前后端按域成对施工，DTO 先行

- 涉及新字段的任务域（描述/类型/日期区间/重复/可见范围/要求清单/参考图/顺延/放弃类型）与奖励域（分类/库存/图片/上架）先做后端 DTO 与 Flyway 补列（沿用 19 位雪花 BIGINT、JSON 标识字符串化约定），再施工小程序 UI。
- 纯前端差距（Tab/筛选/时间线可视化/checklist 呈现/引导页/确认页/统计展示）不动后端。
- 工作台统计（03-01/03-02）新增汇总查询接口，复用既有 read 权限，不新设权限码。
- 小程序页面路由/组件按 uni-app 既有模式（pages 注册、eager 组件同页切换形态按原型改为独立视图时同步注册路由）。

### D4 验收方式：逐屏重拍对照

- 每个视图域施工完成后，沿用 78 审计的 automator 截图 + make-cmp 拼图流程与 reference 原型对照，回写 all-screen-catalog.json 的 implementationStatus；不引入新的验收工具。

## Risks / Trade-offs

- [授权放开后 Web 端行为变化] 仅删除 client 声明不缩小既有 Web 能力（原先 Web 可用 → 两端可用），迁移只增不删；上线前在克隆库回归 Web 家长关系页。
- [任务域补列的存量数据兼容] 新列全部允许 NULL，存量任务展示空态；不做数据回填。
- [奖励库存/分类引入的双端一致性] 学生端商城展示与家长端表单共用同一字典，避免两套分类；分类先以固定枚举落地（对齐原型 chips），不做动态字典管理。
- [一次 change 范围大] tasks 按 7 期拆分（缺陷/授权/6 视图域），每期独立可验收可暂停。

## Migration Plan

1. 后端先行：authorization-model.json 修改 + V136 迁移（内置角色种子授权）在克隆库验证家长关系链路后部署。
2. 任务/奖励域按 D3 各自出迁移补列（V137+，版本施工时顺延），DTO 扩展向后兼容（新字段可空）。
3. 小程序按视图域逐期施工，每期结束逐屏重拍对照。
4. 回滚策略：迁移只增不删，回滚仅需回退代码包；client 声明变更可按 git 还原。

## Open Questions

（无——授权修复方案与迁移基线均已按代码事实核实；任务域具体列名在实施 D3 时按既有表结构对齐，不影响规格与任务拆分。）
