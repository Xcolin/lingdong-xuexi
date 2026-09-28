# 灵动学习 Flyway 数据库迁移规范

最新增量为 V77 导入导出模板台账导出，后续编号 V78；不得修改已执行迁移。V77 增加导出类型及范围约束、IMPORT_EXPORT_TEMPLATE_EXPORT Web 权限和仅系统管理员默认授权、TEMPLATE_REPORT 模板模块选项；三个种子均为 19 位，不新增业务表。V1-V77 已在本地 H2 MySQL 兼容模式连续迁移，未执行远程库迁移。V76 字典台账及 V74/V75 接口变更快照保留，下文各版本说明保留为历史记录。

当前增量：V66 新增周报订阅偏好表、默认关闭开关及家长订阅权限，本地 H2 已验证 V1-V66 连续迁移；业务主键表 92 个。后续使用 V67，不修改 V1-V66。本轮未对远程 MySQL 或共享测试/预生产/生产库执行。

**版本**：V1.0（设计基线草案）  
**状态**：待评审  
**适用范围**：开发、测试、预生产、生产的数据库结构、初始化数据和受控数据修正  
**关联设计**：[数据库设计](04-数据库设计-V1.0.md)、[部署运维与发布方案](11-部署运维与发布方案-V1.0.md)

## 1. 强制规则

最新迁移增量（2026-09-08）：`V65__add_growth_review_pdf_job_type.sql` 扩展作业类型和范围约束，仅为导出结果类别补 PDF/ZIP 格式，两个种子使用 19 位标识。V1-V65 本地 H2 连续应用通过，未在远程 MySQL 执行；后续迁移从 V66 开始，业务工作包仍为 V63。以下 V63/V64 说明保留为历史记录。

当前迁移增量（2026-09-08）：`V64__add_growth_review_pdf_feature.sql` 注册默认停用的复盘 PDF 新导出全局开关，种子为 19 位雪花标识，不新增表或权限，不改 V1-V63。后续迁移从 V65 连续新增；业务工作包仍是 V63。以下 2026-09-07 的 V63 记录作为历史证据保留。

当前迁移增量（2026-09-07）：`V63__add_export_job_payload.sql` 新增导出内容一对一扩展表，未修改 V1-V62，未添加权限种子或开放 PDF 接口。本地 H2 已验证 V1-V63 连续应用，业务主键表共 91 张；远程 MySQL 未执行本迁移。下一数据库迁移从 V64 开始，业务工作包编号仍为 V63 成长复盘扩展，两者独立记录。

1. 所有数据库结构、索引、约束、基础角色、基础权限、基础字典、受控配置和受控数据修正必须通过 Flyway 发布。
2. 禁止将手工改库作为常规发布方式。紧急修复也必须形成可追溯脚本、审批记录和后续 Flyway 正式迁移。
3. 已在任一共享环境执行的版本化脚本不可修改、重命名或删除；修复通过新的前向版本完成。
4. `flyway_schema_history` 是环境数据库版本事实源。发布、验收和故障排查均以其中记录为准。
5. 迁移脚本不得包含账号密码、微信密钥、对象存储密钥、真实个人信息或生产数据样本。
6. 所有新表必须使用应用层雪花算法生成的 `id BIGINT NOT NULL PRIMARY KEY`；不得使用自增、联合主键或外键字段作为主键，关联业务键使用唯一约束保留。

## 2. 当前目录与命名

当前迁移目录：`server/src/main/resources/db/migration/`  
当前已执行命名：`V<正整数>__<英文下划线描述>.sql`

| 项目 | 规定 |
|---|---|
| 当前最高版本 | 当前本地版本是 `V57`；后续迁移从 `V58__...sql` 开始。V57 未在远程 MySQL、Redis、共享测试、预生产或生产环境执行。 |
| 版本号 | 按单调递增整数分配；不得补写已低于共享环境版本的脚本。 |
| 描述 | 使用英文小写与下划线，准确说明变更目的，例如 `V10__create_dictionary_tables.sql`。 |
| 一个脚本的范围 | 一个可独立验证的业务数据变更单元；不把无关模块改动混在同一脚本。 |
| SQL 风格 | 使用一致缩进、显式约束名、可读注释；避免将业务常量隐含在难以审计的 SQL 中。 |
| 初始化数据 | 与其依赖的表结构放在同一版本或紧随其后的独立版本，且应有业务依据。 |

## 3. 迁移类型

| 类型 | 允许内容 | 示例 |
|---|---|---|
| 结构迁移 | 建表、加字段、索引、约束、视图等 | 创建字典类型和字典项表。 |
| 基础数据迁移 | 内置角色、基础组织类型、功能开关、字典初始化 | 初始化地理考勤和轨迹为关闭。 |
| 受控数据修正 | 可审计、可验证的历史数据回填或修复 | 为已有任务实例回填来源类型。 |
| 数据库方言适配 | 经评审的目标数据库兼容实现 | 为切换目标数据库建立独立方言迁移方案。 |

受控数据修正必须在脚本头部说明：变更原因、影响对象、前置版本、预期行数、验证查询、是否需要备份和恢复方案。不得用无条件全表更新处理不明确的数据问题。

## 4. 脚本结构要求

```sql
-- 用途：创建 FSD-SYS-03 所需的数据字典表。
-- 来源：docs/design/04-数据库设计-V1.0.md。
-- 验证：字典类型、字典项表及其约束存在且有效。

CREATE TABLE sys_dictionary_type (
    id BIGINT NOT NULL PRIMARY KEY,
    type_code VARCHAR(64) NOT NULL,
    type_name VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_dictionary_type_code UNIQUE (type_code)
);
```

示例仅说明结构和注释方式；`id` 由应用服务在写入前分配。实际脚本须以评审后的数据库设计、字段定义和目标数据库方言为准。对于非幂等 Flyway 版本脚本，不应通过 `IF NOT EXISTS` 掩盖版本漂移；应让迁移失败暴露环境不一致问题并按流程处理。

## 5. 编写与发布流程

```mermaid
flowchart LR
    A[数据库/FSD/API 评审] --> B[分配 Flyway 版本]
    B --> C[编写迁移脚本与验证查询]
    C --> D[空库 migrate]
    D --> E[升级库 migrate]
    E --> F[应用集成测试]
    F --> G[预生产发布验证]
    G --> H[生产变更审批与发布]
```

### 5.1 开发阶段

1. 先更新数据库设计和受影响 FSD/API，再创建新的版本脚本。
2. 在空数据库执行 `migrate`，验证所有历史脚本可连续执行。
3. 在包含前一版本数据的升级数据库执行 `migrate`，验证现有数据、约束和应用兼容性。
4. 运行 Flyway `validate`、后端迁移集成测试和受影响模块测试。
5. 代码评审中必须包含脚本、验证结果和对已有环境的影响说明。

### 5.2 预生产与生产阶段

1. 发布前确认目标环境的 `flyway_schema_history`、备份状态、当前应用版本和待执行版本。
2. 数据库迁移与应用发布使用同一发布单记录；应用仅在所需迁移完成后启动或开放相关新功能。
3. 对大表、长时间 DDL、数据回填和敏感数据修正，必须先在等量级预生产数据上测量影响和锁表风险。
4. 生产执行后运行迁移验证查询、健康检查和最小业务回归；失败处理遵循第 7 节，不手工篡改历史表。

## 6. 多数据库适配策略

1. 当前默认目标为 MySQL 8+，脚本位于默认迁移目录。
2. 若正式启用 PostgreSQL、达梦、人大金仓等数据库，必须单独评审方言差异，包括主键生成、时间类型、索引、分页、布尔类型、JSON/几何类型和 DDL 在线能力。
3. 不允许在一个 SQL 文件中混杂多数据库不可执行语法。启用多数据库前，应通过专门迁移调整 Flyway locations，并建立通用脚本与数据库特定脚本的清晰目录和 CI 验证矩阵。
4. MyBatis XML 中不得依赖未被数据库设计批准的 MySQL 私有函数；需要方言 SQL 时集中在持久化适配层。

## 7. 回退、修复与恢复

| 情形 | 处理方式 |
|---|---|
| 应用发布失败但迁移可兼容 | 回退应用版本，保留已执行的前向迁移；由兼容性设计保证旧应用可读取。 |
| 迁移逻辑需要修正 | 新建下一个版本的补偿/修正脚本，保留原脚本和历史记录。 |
| 数据回填错误 | 停止相关写入，按备份与影响范围评估；数据恢复属于高风险系统任务，需要系统管理员发起、系统审核员审批，并形成新的可审计修正脚本。 |
| 校验和不一致 | 禁止直接修改生产历史；先比对脚本和环境，确认合规原因后按受控流程使用修复工具并留存审计。 |
| 需要清空数据库 | 仅允许本地临时开发环境；测试、预生产、生产禁止常规执行 Flyway clean。 |

Flyway 不提供业务意义上的自动回滚。设计时应优先使用向前兼容迁移：先加字段/表、部署兼容代码、完成回填、再在后续版本清理废弃结构。

## 8. V1-V28 基线与当前迁移核对

当前 V1-V25 基线保持既有表和基础数据历史；V26 调整积分纠错约束；V27 新增家庭奖励与兑换；V28 新增成长复盘逻辑记录、不可变快照、分类、趋势和补录，并写入两个开关、四项权限及最小角色授权。全部表仍使用应用层生成或一对一关系确定性复用的 19 位雪花 `BIGINT` 主键，不使用自增、identity、序列或触发器。当前只在本地 H2 MySQL 兼容模式执行 V1-V28，尚未在共享测试、预生产或生产数据库执行；首次进入受控环境后，任何已执行版本均不得修改。

| 版本 | 核对要点 |
|---|---|
| V1 | `sys_config` 可保存通用配置，不应保存或打印真实第三方密钥。 |
| V2-V4 | 组织、角色、用户、权限及用户组织关联约束可从空库顺序建立。 |
| V5 | 系统任务提交人与审核人关联可用，状态索引存在。 |
| V6-V7 | 地理考勤、轨迹默认关闭；开关变更必须关联系统任务。 |
| V8 | 用户权限显式允许/禁止可建立。 |
| V9 | 角色自定义组织范围可建立。 |
| V10 | 数据字典类型、字典项、唯一约束和按类型/状态/排序的查询索引可建立；不预置无业务依据的字典数据。 |
| V11 | 缓存操作台账可关联高风险系统任务、请求人和执行人，并记录成功或失败结果；不保存 Redis 内容或会话数据。 |
| V12 | 接口服务、变更提案与调用结果摘要可建立；变更任务唯一关联，调用日志不保存凭证、请求或响应报文。 |
| V13 | 附件规则、格式白名单、文件元数据和业务关系可建立；对象键唯一，业务关系解除保留历史元数据。 |
| V14 | 导入导出模板表可建立；模板附件关联已存在文件，模块/类型/版本唯一，范围键约束同模块同类型最多一个默认模板。 |
| V15 | `auth_device_session` 可建立；`id` 为应用层雪花主键，访问/刷新令牌摘要分别唯一，存在用户与状态组合索引，且通过用户外键关联 `sys_user`。 |
| V16 | 可在既有权限表中建立 12 个 `IAM_` Web 操作权限，并为内置 `SYS_ADMIN` 建立 12 条角色权限关联；权限和关联标识均为预生成的 19 位雪花常量。 |
| V17 | 可在既有权限表中建立 4 个 `ORG_` Web 操作权限，并为内置 `SYS_ADMIN` 建立 4 条角色权限关联；权限和关联标识均为预生成的 19 位雪花常量。 |
| V18 | 可在既有权限表中建立 `IAM_USER_LIST`、`IAM_USER_STATUS_CHANGE` 两个 Web 操作权限，并为内置 `SYS_ADMIN` 建立 2 条角色权限关联；权限和关联标识均为预生成的 19 位雪花常量。 |
| V19 | 可建立 `edu_student`、`edu_parent_student`、`edu_student_organization`；三表 `id` 均为应用层雪花 `BIGINT` 主键。`STUDENT_CREATE` 授予家长、机构管理员，`STUDENT_READ` 另授予系统管理员；家长关系和学生机构关系均有活动查询索引与业务唯一约束。 |
| V20 | 可建立 `edu_parent_binding_invitation`；其 `id` 为应用层雪花 `BIGINT` 主键，令牌摘要唯一，`(student_id, pending_scope_key)` 保证每名学生仅有一条待处理邀请。`STUDENT_PARENT_INVITE_CREATE` 授予 `ORG_ADMIN`，`STUDENT_PARENT_INVITE_RESPOND` 授予 `PARENT`。 |
| V21 | 可建立 `auth_student_account_sequence`、`auth_student_credential`；两表 `id` 均为应用层雪花 `BIGINT` 主键。年份、学生用户唯一，失败次数和验证码标识具备检查约束；新增 `STUDENT_CREDENTIAL_INITIALIZE`、`STUDENT_LOGIN_CODE_RESET` 并授权家长与机构管理员，初始化 `STUDENT_CODE_LOGIN` 全局启用开关。迁移不批量生成历史学生账号或登录码。 |

### 8.3 V22 学习任务迁移

`V22__create_learning_task_foundation.sql` 新增 `edu_teacher_class`、`learn_task`、`learn_task_target`、`learn_task_tag` 和 `learn_task_assignment`。五张表的 `id` 均为非 identity `BIGINT`，只允许应用层雪花算法写入；脚本不生成演示任务、班级关系或学生实例。

V22 同时初始化 `TASK_CATEGORY`、`TASK_TAG` 字典及最小启用项，初始化 `LEARNING_TASK_MANAGEMENT` 全局开关，新增学生/教师班级配置、任务创建/管理查询/发布和学生本人任务查询共 6 项权限，并向 `ORG_ADMIN`、`PARENT`、`TEACHER`、`STUDENT` 内置角色写入对应授权。迁移测试已在本地 H2 MySQL 兼容模式从空库连续执行 V1 至 V22，并核对新表、非自增主键、约束、索引和基础授权。

### 8.4 V23 任务执行与审核基础迁移

`V23__create_task_execution_foundation.sql` 扩展 `learn_task_assignment.current_status` 检查约束并新增 `last_transition_at`、`version_no`；新增 `learn_task_assignment_event`、`learn_task_pause`、`learn_task_checkin`、`learn_task_reviewer_transfer`。四张表主键均为非 identity `BIGINT`，暂停时间、打卡序号、事件类型、审核转交人与状态均有检查、唯一或外键约束。

V23 新增 `TASK_ASSIGNMENT_EXECUTE_SELF`、`TASK_ASSIGNMENT_REVIEW`、`TASK_ASSIGNMENT_EXEMPT`，分别授权学生及家长、教师、机构管理员。迁移测试已从空库连续执行 V1 至 V23，并核对 4 张新表、任务实例扩展字段、3 项权限、7 条角色授权和非自增主键。

### 8.5 V24 任务奖励积分迁移

`V24__create_growth_point_account_and_ledger.sql` 新增 `growth_point_account` 和 `growth_point_ledger`，两张表主键均为非 identity `BIGINT`。积分账户与学生一对一并复用学生雪花标识，迁移为既有学生回填零余额账户；V24 初始唯一约束在 V26 前用于防止重复发奖，V26 改由任务状态版本和事务行锁控制审核幂等，以允许纠错后重新发奖。

V24 同时扩展打卡状态为 `APPROVED`、任务事件类型为 `REVIEW_APPROVED`。迁移测试已从空库连续执行 V1 至 V24，核对 44 张表全部具有显式非自增 `BIGINT id` 主键，并验证历史学生账户回填、积分非负约束、任务奖励唯一约束和新增状态约束。

### 8.6 V25 积分查询访问基线迁移

`V25__seed_growth_point_query_access.sql` 初始化全局启用的 `GROWTH_POINT_QUERY` 功能开关；新增 `MINIAPP` 客户端 `GROWTH_POINT_READ_SELF` 和 `WEB` 客户端 `GROWTH_POINT_READ_CHILD`，分别授权内置 `STUDENT`、`PARENT` 角色。开关、权限和角色权限关联均使用预生成的 19 位雪花常量，不新增表、不回填业务数据。

迁移测试已从空库连续执行 V1 至 V25，核对 V25 迁移历史、开关、两项权限及两条角色授权；44 张现有业务表仍全部具有显式非自增 `BIGINT id` 主键。

### 8.7 V26 积分纠错基线迁移

`V26__add_growth_point_correction.sql` 删除 `(source_assignment_id, change_type)` 旧唯一索引，新增 `correction_of_id` 唯一约束和 `CORRECTION` 完整性检查，扩展 `POINT_CORRECTED` 任务事件，并初始化 `GROWTH_POINT_CORRECTION`、`GROWTH_POINT_CORRECT_CHILD` 及主家长授权。三个新增基础数据标识均为 19 位雪花常量。

迁移测试已从空库连续执行 V1 至 V26，核对 V26 迁移历史、旧索引移除、新唯一/检查约束、任务事件约束、纠错开关、权限及主家长授权；44 张现有业务表仍全部具有显式非自增 `BIGINT id` 主键。

### 8.8 V27 家庭奖励与积分兑换迁移

`V27__add_reward_exchange.sql` 新增 `growth_reward`、`growth_reward_exchange`，两表均使用应用层 19 位雪花 `BIGINT id`；兑换表保存名称、所需积分和说明快照，六种状态、驳回字段和审批截止时间具有检查约束。`growth_point_ledger` 新增可空 `source_exchange_id` 外键及唯一约束，每笔兑换最多对应一笔 `REDEMPTION` 台账。

V27 调整台账金额约束，使兑换和未来休眠清零的累计积分变化为 0，同时要求兑换可用积分变化小于 0、来源为家庭且有审核人。脚本初始化 `REWARD_EXCHANGE`、`REWARD_MANAGE_CHILD`、`REWARD_EXCHANGE_REVIEW_CHILD`、`REWARD_EXCHANGE_SELF` 及主家长/学生最小授权，全部基础数据标识为预生成 19 位雪花常量。

迁移测试已从空库连续执行 V1 至 V27，核对 46 张表均具有显式非自增 `BIGINT id` 主键，并验证新表、索引、外键、检查/唯一约束、开关、权限和角色授权。

### 8.9 V28 成长复盘迁移

`V28__add_growth_review.sql` 新增 `growth_review`、`growth_review_snapshot`、`growth_review_category_stat`、`growth_review_daily_trend`、`growth_review_supplement`。五张表均使用应用层 19 位雪花 `BIGINT id`；逻辑复盘按学生、周期类型和周期边界唯一，快照按复盘和内容版本唯一，分类和日趋势按快照维度唯一，补录保留编辑人、角色、类型和时间。

V28 初始化 `DAILY_GROWTH_REVIEW`、`PERIODIC_GROWTH_REPORT` 两个全局开关，以及学生本人读取/补录、主家长孩子读取/补录四项最小权限。历史查询不受开关关闭影响，自动生成和新增补录受对应开关拦截。全部基础数据标识均为预生成的 19 位雪花常量。

迁移测试已从空库连续执行 V1 至 V28，核对 51 张表均具有显式非自增 `BIGINT id` 主键，并验证复盘表、索引、外键、检查/唯一约束、开关、权限和角色授权。后续变更必须从 `V29` 顺序新增，不重写 V1-V28。

### 8.10 V29 积分生命周期迁移

`V29__add_point_lifecycle.sql` 新增 `growth_point_decay_rule`、`growth_point_dormancy_state`、`growth_point_dormancy_notice`，三张表均使用应用层 19 位雪花 `BIGINT id`。脚本为既有学生初始化沉睡周期状态，为历史任务奖励回填任务标识、基础积分、0% 衰减和连续 1 天快照，并初始化 `POINT_LIFECYCLE` 开关及第 8 天 20%、第 16 天 40% 两条生效规则。

V29 扩展 `growth_point_ledger` 的衰减和沉睡审计字段，将任务实例唯一约束调整为 `(task_id, student_id, scheduled_date)`，使同一任务可跨自然日产生实例但同日仍保持唯一。迁移测试从空库连续执行 V1 至 V29，核对 54 张表均具有显式非自增 `BIGINT id` 主键，并验证历史回填、规则、开关、外键、索引和唯一约束。后续变更必须从 `V30` 顺序新增，不重写 V1-V29。

### 8.11 V30 每日固定任务迁移

`V30__add_recurring_task.sql` 为 `learn_task` 增加固定任务启用标识和可选结束日，新建 `learn_task_recurrence`。计划表主键由应用层雪花算法生成，任务外键唯一，状态、频率和结束日均有检查约束，并按状态、下一生成日和计划标识建立到期扫描索引。

迁移测试从空库连续执行 V1 至 V30，核对 55 张表均具有显式非自增 `BIGINT id` 主键，并验证任务配置列、任务唯一计划、停止审计、版本字段和到期索引。后续变更必须从 `V31` 顺序新增，不重写 V1-V30。

### 8.12 V31 图片打卡附件迁移

`V31__add_task_checkin_attachment.sql` 为 `sys_file` 增加模块、文件分类和内容摘要字段，对历史数据安全回填后设置非空约束；同时将 `learn_task_checkin.content` 调整为可空，并初始化 `LEARNING_TASK_CHECKIN/IMAGE` 的 JPG/JPEG/PNG、10 MB、最多 9 张、允许预览规则及上传/读取权限。

V31 不新建数据表，当前仍为 55 张显式非自增 `BIGINT id` 主键表。初始化数据标识均为 19 位数字，权限客户端使用受支持的 `WEB/MINIAPP/BOTH` 值。

### 8.13 V32 待优化与顺延迁移

`V32__add_task_overdue_defer.sql` 扩展任务定义和任务实例的来源、顺延类型、次数、隔夜标记、操作人与时间字段，新增 `learn_task_defer_history` 不可变历史表，并允许系统自动写入待优化事件时操作人为空。迁移初始化 Web 手动顺延权限及家长、教师、机构管理员最小授权。

V32 迁移后共有 56 张显式非自增 `BIGINT id` 主键表；新增基础数据使用 4 个 19 位数字标识，不包含 `AUTO_INCREMENT`、`IDENTITY` 或 `SERIAL`。后续变更必须从 `V33` 顺序新增，不重写 V1-V32。

### 8.14 V33 按学生复制昨日任务迁移

`V33__add_previous_day_task_copy.sql` 将任务生成类型扩展为 `NORMAL/DEFERRED/COPIED`，新增复制批次表与复制条目表。批次按学生和目标日期唯一，条目按批次和源任务唯一；两表均使用应用层 19 位雪花 `BIGINT id`，不使用数据库自增。

V33 初始化 `COPY_PREVIOUS_DAY_TASK` 功能开关、`LEARNING_TASK_COPY_PREVIOUS_DAY` 权限和家长角色授权。迁移后共有 58 张显式非自增 `BIGINT id` 主键表；后续变更必须从 `V34` 顺序新增，不重写 V1-V33。

### 8.15 V34 任务模板迁移

`V34__add_learning_task_template.sql` 新增 `learn_task_template` 和 `learn_task_template_tag`。系统模板拥有者为空，个人模板以 `owner_scope_key` 隔离；活动名称键与拥有者作用域联合唯一，逻辑删除时清空活动名称键，从而允许名称复用。编辑、删除和排序使用 `version_no` 乐观版本。

V34 初始化 `LEARNING_TASK_TEMPLATE` 功能开关、读取与个人管理双权限、家长角色授权，以及“每日阅读30分钟”“口算练习”两条系统模板。新增 9 个基础标识均为 19 位数字；迁移后共有 60 张显式非自增 `BIGINT id` 主键表。后续变更必须从 `V35` 顺序新增，不重写 V1-V34。

### 8.16 V35 学生扫码登录迁移

`V35__add_student_qr_login.sql` 新增 `auth_student_qr_ticket`，保存学生、学生用户、票据摘要、活动/已消费/已撤销状态、到期与消费时间及签发人。票据摘要唯一，状态与消费时间通过检查约束保持一致；学生、学生用户和签发人均使用外键。

V35 初始化 `STUDENT_QR_LOGIN` 功能开关、`STUDENT_LOGIN_QR_CREATE` Web 权限及家长、机构管理员授权。新增 4 个基础数据标识均为 19 位数字；迁移后共有 61 张显式非自增 `BIGINT id` 主键表。

### 8.17 V36 家长手机号认证数据基础

`V36__add_parent_phone_auth.sql` 新增协议接受事实表与家长首次引导档案表，复用 V4 已建立的手机号唯一约束；初始化当前协议版本配置和 `PARENT_PHONE_AUTH` 功能开关。两张新表使用显式非自增 `BIGINT id`，迁移后共有 63 张主键表。V36 业务实现与本地回归已完成，后续迁移从 V37 新增，不修改 V1-V36。

### 8.18 V37 家长微信授权绑定

`V37__add_parent_wechat_auth.sql` 新增家长微信绑定表，建立用户唯一约束和 `app_id + open_id` 联合唯一约束，初始化默认停用的 `PARENT_WECHAT_AUTH` 功能开关。新表主键为应用层 19 位雪花 `BIGINT`，不保存临时凭证、`session_key`、绑定票据或验证码；迁移后共有 64 张主键表。

## 9. 验收清单

- [ ] 空库执行所有迁移成功，`validate` 成功。
- [ ] 升级库执行新增迁移成功，历史数据与约束符合预期。
- [ ] 每个迁移都有来源设计、验证查询和受影响模块说明。
- [ ] 新增/修改基础数据有唯一约束和可追溯业务来源。
- [ ] 不修改已在共享环境执行的脚本。
- [ ] 不包含密钥、生产个人信息、手工执行说明或不可审计 SQL。
- [ ] 迁移后后端集成测试和受影响业务流程通过。

V38 已验证从空库连续执行 38 个迁移，66 张主键表均不含自增语法；本轮未在共享测试、预生产或生产数据库执行。后续数据库变更必须从 `V39__...sql` 开始，且不得修改 V1-V38。

### 8.19 V38 家长关系生命周期迁移

`V38__add_parent_relationship_lifecycle.sql` 扩展当前关系角色和范围键长度，新增关系邀请表、关系变更日志表、默认停用功能开关及关系管理权限。邀请状态、类型和待处理唯一性由检查与唯一约束兜底；变更日志不提供更新或删除 Mapper。新增两张表均使用应用层 19 位雪花 `BIGINT id`，迁移后共有 66 张显式非自增主键表。

### 8.20 V39 账号安全事件迁移

`V39__add_account_security_event.sql` 新增账号安全事件表、查询索引和设备历史索引，并写入默认启用的 `ACCOUNT_SECURITY_MANAGEMENT` 全局开关。空 H2 MySQL 兼容库已连续执行 V1-V39，67 张主键表均为显式非自增 `BIGINT id`。本轮未对远程 MySQL、Redis、共享测试、预生产或生产环境执行迁移或写入；后续迁移从 V40 开始且不得修改 V1-V39。

### 8.21 V40 机构管理员小程序认证迁移

`V40__add_organization_miniapp_auth.sql` 只写入默认启用的 `ORGANIZATION_MINIAPP_AUTH` 全局内置开关，不创建或修改业务表。空 H2 MySQL 兼容库已连续执行 V1-V40，仍为 67 张显式非自增 `BIGINT id` 主键表；新增基础数据标识为 19 位数字。本轮未对远程 MySQL、Redis、共享测试、预生产或生产环境执行迁移或写入；后续迁移从 V41 开始且不得修改 V1-V40。

### 8.22 V41 学生机构关系生命周期迁移

`V41__add_student_organization_lifecycle.sql` 新增只追加的学生机构关系变更表、默认启用的 `STUDENT_ORGANIZATION_RELATIONSHIP` 开关、`BOTH` 客户端管理权限及机构管理员授权。空 H2 MySQL 兼容库已连续执行 V1-V41，68 张主键表均为显式非自增 `BIGINT id`，新增基础数据标识均为 19 位数字。未对远程 MySQL、Redis、共享测试、预生产或生产环境执行迁移或写入；后续迁移从 V42 开始且不得修改 V1-V41。

### 8.23 V42 家长账号生命周期迁移

`V42__add_parent_account_lifecycle.sql` 新增手机号换绑审计表、注销申请表、默认启用的 `PARENT_ACCOUNT_LIFECYCLE` 开关、`BOTH` 权限及家长角色授权。空 H2 MySQL 兼容库已连续执行 V1-V42，70 张主键表均为显式非自增 `BIGINT id`，新增基础数据标识均为 19 位数字。未对远程 MySQL、Redis、共享测试、预生产或生产环境执行迁移或写入；后续迁移从 V43 开始且不得修改 V1-V42。

### 8.24 V43 家长账号最终注销迁移

`V43__finalize_parent_account_cancellation.sql` 只扩展既有注销申请表，增加终结尝试、下次执行和业务错误码字段，回填活动申请的下次执行时间并建立到期扫描索引；不新增表或主键。空 H2 MySQL 兼容库已连续执行 V1-V43，70 张主键表均为显式非自增 `BIGINT id`。未对远程 MySQL、Redis、共享测试、预生产或生产环境执行迁移或写入；后续迁移从 V44 开始且不得修改 V1-V43。

### 8.25 V44 家长手机号人工核验换绑迁移

`V44__add_parent_mobile_manual_recovery.sql` 新增不可变摘要审计表、默认停用的 `PARENT_MOBILE_MANUAL_RECOVERY` 开关、`BOTH` 管理权限及机构管理员授权。空 H2 MySQL 兼容库已连续执行 V1-V44，71 张主键表均为显式非自增 `BIGINT id`，新增基础标识均为 19 位数字。未对远程 MySQL、Redis、共享测试、预生产或生产环境执行迁移或写入；后续迁移从 V45 开始且不得修改 V1-V44。

### 8.26 V45 学生账号注销迁移

`V45__add_student_account_cancellation.sql` 新增不可变注销审计表、默认停用的 `STUDENT_ACCOUNT_CANCELLATION` 开关、`BOTH` 管理权限及机构管理员授权。空 H2 MySQL 兼容库已连续执行 V1-V45，72 张主键表均为显式非自增 `BIGINT id`，V45 三个基础标识均为 19 位数字。未对远程 MySQL、Redis、共享测试、预生产或生产环境执行迁移或写入；后续迁移从 V46 开始且不得修改 V1-V45。

## V46 迁移记录

`V46__add_student_wechat_auth.sql` 创建学生微信当前绑定表和不可变审计表，初始化默认停用的 `STUDENT_WECHAT_AUTH` 功能开关、`BOTH` 客户端的 `STUDENT_WECHAT_UNBIND` 权限并只授予内置家长角色。迁移和基础数据 ID 均为 19 位雪花数字；该迁移未在共享测试、预生产或生产库执行，后续不得修改已发布环境中的历史脚本。

## V47 迁移记录

`V47__add_organization_node_lifecycle.sql` 为组织表增加有效状态和乐观锁版本，创建组织变更及不可变审计表，并初始化 `ORGANIZATION_MANAGEMENT` 全局开关以及编辑、提交、审核三个 Web 权限。迁移中的新表主键和基础数据标识均为 19 位雪花数字，不使用自增列；V1-V47 当前共 76 张显式非自增 `BIGINT id` 主键表。

V47 只在本地 H2 MySQL 兼容测试库中验证，未在远程 MySQL、共享测试、预生产或生产数据库执行。脚本进入外部环境后禁止修改。

## V48 迁移记录

`V48__add_class_management.sql` 不新增表，扩展任务实例、任务事件和组织审计约束，初始化 `CLASS_MANAGEMENT` 全局开关、四项 `BOTH` 班级权限及机构管理员授权，并将既有 `TEACHER_CLASS_ASSIGN` 客户端范围调整为 `BOTH`。V48 基础标识均为 19 位数字，不使用自增列；V1-V48 空 H2 MySQL 兼容库连续迁移成功，当前 76 张主键表均为显式非自增 `BIGINT id`。

V48 未在远程 MySQL、Redis、共享测试、预生产或生产环境执行。历史迁移 `V1-V48` 不再修改；后续数据库变更从 `V49__...sql` 连续新增。

## V49 迁移记录

`V49__add_permission_grant_boundary.sql` 为 `sys_role_permission` 增加非空 `effect` 字段，历史数据默认回填 `ALLOW`，并增加只允许 `ALLOW/DENY` 的检查约束。V49 不新增表、主键或基础数据，不使用自增列；V1-V49 空 H2 MySQL 兼容库连续迁移成功，当前 76 张主键表均为显式非自增 `BIGINT id`。

V49 未在远程 MySQL、Redis、共享测试、预生产或生产环境执行。历史迁移 `V1-V49` 不再修改；后续数据库变更从 `V50__...sql` 连续新增。

## 通用组织数据范围专项迁移说明

本专项仅重构应用层范围模型和 MyBatis 查询，不改变数据库结构或基础数据，因此不创建无意义的 V50 空迁移。`V1-V49` 保持不可变，下一次真实数据库变更仍使用 `V50__...sql`。

## V50 迁移记录

`V50__add_iam_change_audit.sql` 新增不可变身份权限审计表、三组查询索引、`IAM_AUDIT_READ` 权限及系统管理员授权。V1-V50 已在空 H2 MySQL 兼容库连续迁移，77 张主键表均为显式非自增 `BIGINT id`。V50 未在远程 MySQL、Redis、共享测试、预生产或生产执行；下一迁移从 V51 连续新增。

## 统一访问边界专项迁移说明

本专项没有数据库变更，不生成 V51。历史 V1-V50 保持不可变，下一次真实数据库变更仍使用 `V51__...sql`。

## V51 迁移记录

`V51__add_dictionary_management.sql` 为数据字典管理新增 `DICTIONARY_MANAGEMENT`、`DICTIONARY_READ`、`DICTIONARY_MANAGE` 及系统管理员显式允许授权，不新建表。V1-V51 已在空 H2 MySQL 兼容库连续迁移，历史脚本保持不可变；当前未在远程 MySQL、共享测试、预生产或生产执行，下一迁移从 V52 连续新增。

## V52 迁移记录

`V52__add_cache_management.sql` 新增 `CACHE_MANAGEMENT` 功能开关，新增 `CACHE_READ`、`CACHE_MANAGE`、`CACHE_REVIEW` 三个 Web 操作权限；系统管理员获得读取和管理权限，系统审核员获得读取和审核权限。迁移不新建表，8 个基础数据标识均为 19 位雪花数字。V1-V52 已在空 H2 MySQL 兼容库连续迁移，历史脚本保持不可变；未在远程 MySQL、共享测试、预生产或生产执行，下一迁移从 V53 连续新增。

## V53 迁移记录

`V53__add_interface_service_management.sql` 新增 `INTERFACE_SERVICE_MANAGEMENT` 功能开关，新增 `INTERFACE_SERVICE_READ`、`INTERFACE_SERVICE_MANAGE`、`INTERFACE_SERVICE_REVIEW` 三个 Web 操作权限；系统管理员获得读取和管理权限，系统审核员获得读取和审核权限。迁移不新建表，8 个基础数据标识均为 19 位雪花数字。V1-V53 已在空 H2 MySQL 兼容库连续迁移，历史脚本保持不可变；未在远程 MySQL、共享测试、预生产或生产执行，下一迁移从 V54 连续新增。

## V54 迁移记录

`V54__add_attachment_management.sql` 为 `sys_attachment_rule` 增加 `version_no BIGINT NOT NULL DEFAULT 0`，新增 `ATTACHMENT_SERVICE` 功能开关和 `ATTACHMENT_RULE_READ`、`ATTACHMENT_RULE_MANAGE`、`ATTACHMENT_FILE_LEDGER_READ` 三个 Web 操作权限；系统管理员获得三项允许授权。迁移不新建表，7 个新增基础数据标识均为 19 位雪花数字。V1-V54 已在空 H2 MySQL 兼容库连续迁移，历史脚本保持不可变；未在远程 MySQL、共享测试、预生产或生产执行，下一迁移从 V55 连续新增。

## V55 迁移记录

`V55__add_import_export_template_management.sql` 为 `sys_import_export_template` 增加 `version_no BIGINT NOT NULL DEFAULT 0`，新增 `IMPORT_EXPORT_TEMPLATE_MANAGEMENT` 功能开关、`IMPORT_EXPORT_TEMPLATE_READ` 与 `IMPORT_EXPORT_TEMPLATE_MANAGE` 两个 Web 权限及系统管理员允许授权，并新增模板类型、适用模块、模板状态三个字典类型和七个字典项。迁移不新建表，新增基础数据标识均为 19 位数字。V1-V55 已在空 H2 MySQL 兼容库连续验证并迁移，历史脚本保持不可变；未在远程 MySQL、Redis、共享测试、预生产或生产执行，下一迁移从 V56 连续新增。

## V56 迁移记录

`V56__add_import_validation_job.sql` 新增模板字段、导入作业和逐行结果三张表，80 张主键表均为显式非自增 `BIGINT id`；新增 `DATA_IMPORT_VALIDATION` 开关、`IMPORT_JOB_READ`、`IMPORT_JOB_CREATE` 两项 Web 权限、系统管理员允许授权和 `IMPORT_JOB/IMPORT_VALIDATION` 的 `.xlsx` 附件规则。字段唯一约束、作业编码唯一约束、外键和领取/范围查询索引已建立，新增基础数据标识均为 19 位数字。V1-V56 已在空 H2 MySQL 兼容库连续验证并迁移，历史脚本保持不可变；未在远程 MySQL、Redis、共享测试、预生产或生产执行，下一迁移从 V57 连续新增。

## V57 迁移记录

`V57__add_async_export_job.sql` 新增导出作业和不可变状态事件两张表，82 张主键表均为显式非自增 `BIGINT id`；新增 `DATA_EXPORT` 开关、`EXPORT_JOB_READ`、`EXPORT_JOB_CREATE`、`EXPORT_SENSITIVE_SUBMIT`、`EXPORT_SENSITIVE_REVIEW` 四项 Web 权限、三角色最小授权和 `EXPORT_JOB/REPORT_EXPORT` 的 `.xlsx` 附件规则。作业编码、系统任务和结果文件唯一约束，状态、进度检查约束，受限外键及队列、本人、数据集、事件查询索引均已建立。V1-V57 已在空 H2 MySQL 兼容库连续验证并迁移，新增基础数据标识均为 19 位数字，历史 V1-V56 不修改；未在远程 MySQL、Redis、共享测试、预生产或生产执行，下一迁移从 V58 连续新增。

## V58 迁移记录

`V58__add_student_batch_import.sql` 新增学员导入执行和逐行结果两张表，84 张主键表均为显式非自增 `BIGINT id`；新增 `STUDENT_BATCH_IMPORT` 开关、三项 Web 权限、机构管理员最小授权和 `STUDENT_IMPORT/INITIAL_CREDENTIAL` 的 `.enc` 附件规则。唯一约束、状态与计数检查约束、受限外键及队列、本人、校验作业、执行行查询索引均已建立。V1-V58 已在空 H2 MySQL 兼容库连续验证，22 个新增种子标识均为 19 位数字，历史 V1-V57 不修改；未在远程 MySQL、Redis、共享测试、预生产或生产执行，下一迁移从 V59 连续新增。

## V59 迁移记录

`V59__add_teacher_management.sql` 新增教师班级变更日志表，85 张主键表均为显式非自增 `BIGINT id`；新增 `TEACHER_MANAGEMENT` 开关、六项教师管理权限及机构管理员授权，并扩展 IAM 审计事件约束以容纳教师资料变更和密码重置。事件约束采用先删除再按完整枚举重建的前向迁移方式，不修改历史 V1-V58。V1-V59 已在空 H2 MySQL 兼容库连续验证，新增种子标识均为 19 位数字；未在远程 MySQL、Redis、共享测试、预生产或生产执行，下一迁移从 V60 连续新增。

## V60 迁移记录

`V60__complete_organization_teacher_tasks.sql` 不新增业务表，将六项既有任务操作权限调整为 Web 与小程序共用，新增 `LEARNING_TASK_PROGRESS_READ` 及机构管理员、教师最小授权，并增加审核人状态来源联合索引。三个新增基础数据标识均为 19 位数字，无 `AUTO_INCREMENT` 或 `IDENTITY`。V1-V60 已在空 H2 MySQL 兼容库连续验证，历史 V1-V59 不修改；未在远程 MySQL、Redis、共享测试、预生产或生产执行，下一迁移从 V61 连续新增。

## V62 迁移记录

`V62__add_manual_attendance.sql` 新增人工考勤事实及不可变动作表、`ATTENDANCE_MANAGEMENT`、`ATTENDANCE_READ/RECORD` 和默认角色授权，九个种子标识均为 19 位数字，两表均显式非自增主键。V1-V62 在空 H2 MySQL 兼容库连续迁移验证，现有 90 张主键表与组织外键引用检查通过。V1-V61 不修改，下一迁移从 V63 连续新增。

本地验证不等同 MySQL 实库兼容和升级发布验收。本轮没有在远程、共享测试、预生产或生产执行。回退保留 V62 表、迁移和事实，可先关闭人工考勤开关；不得删除历史迁移或直接回写旧版本事实。

## V61 迁移记录

`V61__add_student_exception_report.sql` 新增异常报备主表、不可变动作表和本地消息事件表，并登记 `STUDENT_EXCEPTION_REPORT`、`EXCEPTION_REPORT_CREATE/READ/HANDLE` 及教师和机构管理员最小授权。八个新增种子标识均为 19 位数字，三张表使用显式非自增 `BIGINT id`。V1-V61 已在空 H2 MySQL 兼容库连续验证，历史 V1-V60 不修改；未在远程 MySQL、Redis、共享测试、预生产或生产执行，下一迁移从 V62 连续新增。
