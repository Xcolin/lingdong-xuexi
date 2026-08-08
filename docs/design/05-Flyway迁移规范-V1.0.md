# 灵动学习 Flyway 数据库迁移规范

**版本**：V1.0（设计基线草案）  
**状态**：待评审  
**适用范围**：开发、测试、预生产、生产的数据库结构、初始化数据和受控数据修正  
**关联设计**：[数据库设计](04-数据库设计-V1.0.md)、[部署运维与发布方案](11-部署运维与发布方案-V1.0.md)

## 1. 强制规则

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
| 当前最高版本 | 当前最高版本是 `V34`；后续迁移从 `V35__...sql` 开始。 |
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

## 9. 验收清单

- [ ] 空库执行所有迁移成功，`validate` 成功。
- [ ] 升级库执行新增迁移成功，历史数据与约束符合预期。
- [ ] 每个迁移都有来源设计、验证查询和受影响模块说明。
- [ ] 新增/修改基础数据有唯一约束和可追溯业务来源。
- [ ] 不修改已在共享环境执行的脚本。
- [ ] 不包含密钥、生产个人信息、手工执行说明或不可审计 SQL。
- [ ] 迁移后后端集成测试和受影响业务流程通过。
