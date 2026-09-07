# 灵动学习附件统一管理闭环实施计划

> **执行要求：** 使用 `superpowers:executing-plans` 和 `superpowers:test-driven-development` 逐项实施，所有步骤使用复选框记录。

**目标：** 在 V13/V31 基础上完成 V54 附件规则、文件台账、受控下载、统一开关、动态权限和 React Web 管理闭环。

**架构：** 继续复用四张附件事实表和 `AttachmentContentStorage` 端口。后台管理控制器只提供规则配置和安全元数据台账；业务附件内容继续走业务数据权限，功能开关关闭时父业务返回空附件集合而不是整体失败。

**技术栈：** Java 17、Spring Boot 3.4、MyBatis XML、Flyway、H2/MySQL 兼容 SQL、React 18、Ant Design Pro、uni-app/Vue 3、JUnit 5、Vitest。

**设计基线：** [附件统一管理闭环设计](../specs/2026-08-31-lingdong-attachment-management-closure-design.md)

---

### 任务 1：V54 迁移与失败测试

**文件：**
- 新建：`server/src/main/resources/db/migration/V54__add_attachment_management.sql`
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

- [x] **步骤 1：增加 V54 预期失败断言**

在 `FlywayMigrationTest` 断言当前版本为 54；`sys_attachment_rule.version_no` 为非空 `BIGINT` 且默认值为 0；存在 `ATTACHMENT_SERVICE`、`ATTACHMENT_RULE_READ`、`ATTACHMENT_RULE_MANAGE`、`ATTACHMENT_FILE_LEDGER_READ`；系统管理员拥有三项允许授权；V54 的 7 个种子标识均为 19 位数字。

- [x] **步骤 2：运行迁移专测并确认失败**

运行：

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
mvn "-Dtest=FlywayMigrationTest" test
```

预期：V54 尚不存在或版本字段、开关、权限断言失败。

- [x] **步骤 3：实现 V54 最小迁移**

迁移先为 `sys_attachment_rule` 增加 `version_no BIGINT NOT NULL DEFAULT 0`，再写入 1 个功能开关、3 个 Web 操作权限和 3 条系统管理员允许授权。全部标识使用连续但不与历史重复的 19 位雪花数字，不新建业务表，不修改 V1-V53。

- [x] **步骤 4：运行迁移专测至通过**

预期：空 H2 MySQL 兼容库连续执行 V1-V54，迁移断言通过。

### 任务 2：附件规则完整生命周期

**文件：**
- 修改：`server/src/main/java/com/lingdong/learning/attachment/domain/AttachmentRuleRecord.java`
- 修改：`server/src/main/java/com/lingdong/learning/attachment/application/AttachmentRule.java`
- 新建：`server/src/main/java/com/lingdong/learning/attachment/application/UpdateAttachmentRuleCommand.java`
- 新建：`server/src/main/java/com/lingdong/learning/attachment/application/AttachmentRuleQuery.java`
- 修改：`server/src/main/java/com/lingdong/learning/attachment/application/AttachmentRuleApplicationService.java`
- 修改：`server/src/main/java/com/lingdong/learning/attachment/infrastructure/persistence/AttachmentRuleMapper.java`
- 修改：`server/src/main/resources/mapper/attachment/AttachmentRuleMapper.xml`
- 修改：`server/src/test/java/com/lingdong/learning/attachment/application/AttachmentRuleApplicationServiceTest.java`

- [x] **步骤 1：编写规则生命周期失败测试**

覆盖按名称、模块、分类和状态查询；新增后版本为 0；编辑名称、扩展名、大小、数量和预览配置后版本加 1；旧版本编辑冲突；停用后拒绝新上传；携带当前版本重新启用；重复目标状态返回当前规则；模块和分类不可编辑。

- [x] **步骤 2：运行规则专测并确认缺少查询、更新和启用能力**

运行 `AttachmentRuleApplicationServiceTest`，预期编译失败或新增场景失败。

- [x] **步骤 3：实现规则领域和 MyBatis 条件更新**

为规则记录和应用视图增加 `versionNo`。Mapper 增加最多 200 条条件查询、完整配置更新、按版本及原状态更新状态、替换扩展名白名单所需删除方法。应用服务统一规范化编码和扩展名，在一个事务内更新规则及扩展名；更新行数为 0 时抛出版本冲突。

- [x] **步骤 4：用动态权限替代固定系统管理员判断**

注入 `PermissionDecisionService`，查询要求 `ATTACHMENT_RULE_READ`，新增、编辑和启停要求 `ATTACHMENT_RULE_MANAGE`。保留系统管理员默认授权，但允许自定义运维角色按 RBAC 使用；`DENY` 继续优先。

- [x] **步骤 5：运行规则专测至通过**

预期：规则查询、生命周期、校验、并发冲突和动态权限测试全部通过。

### 任务 3：文件与业务关系安全台账

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/attachment/application/AttachmentFileQuery.java`
- 新建：`server/src/main/java/com/lingdong/learning/attachment/application/AttachmentFileLedgerView.java`
- 新建：`server/src/main/java/com/lingdong/learning/attachment/application/AttachmentRelationLedgerView.java`
- 新建：`server/src/main/java/com/lingdong/learning/attachment/application/AttachmentLedgerApplicationService.java`
- 修改：`server/src/main/java/com/lingdong/learning/attachment/infrastructure/persistence/ManagedFileMapper.java`
- 修改：`server/src/main/resources/mapper/attachment/ManagedFileMapper.xml`
- 修改：`server/src/main/java/com/lingdong/learning/attachment/infrastructure/persistence/FileRelationMapper.java`
- 修改：`server/src/main/resources/mapper/attachment/FileRelationMapper.xml`
- 修改：`server/src/test/java/com/lingdong/learning/attachment/application/AttachmentFileApplicationServiceTest.java`

- [x] **步骤 1：编写文件台账失败测试**

构造已完成、待上传和已退役文件以及活动、已解除关系，断言按文件名、模块、分类、状态、上传人和时间范围过滤；返回结果包含 `contentSha256Present`，但不含 `storageKey` 和摘要原文；关系查询同时返回活动和已解除记录；无 `ATTACHMENT_FILE_LEDGER_READ` 时拒绝。

- [x] **步骤 2：运行专测并确认缺少台账查询能力**

预期：台账服务或 Mapper 查询不存在。

- [x] **步骤 3：实现受限查询和安全投影**

Mapper 只选择页面需要的列，条件查询最多 200 条并按创建时间、标识倒序。关系按文件标识查询并按创建时间排序。应用服务校验动态权限，不返回存储键、实际路径或 SHA-256 原文。

- [x] **步骤 4：运行文件台账专测至通过**

预期：筛选、关系历史、安全投影和越权测试通过。

### 任务 4：统一开关、下载与父业务降级

**文件：**
- 修改：`server/src/main/java/com/lingdong/learning/attachment/application/TaskAttachmentApplicationService.java`
- 修改：`server/src/main/java/com/lingdong/learning/attachment/web/TaskAttachmentController.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityController.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityResponse.java`
- 修改：`server/src/test/java/com/lingdong/learning/attachment/application/TaskAttachmentApplicationServiceTest.java`
- 修改：`server/src/test/java/com/lingdong/learning/feature/web/PublicCapabilityControllerTest.java`

- [x] **步骤 1：编写开关和下载失败测试**

覆盖附件总开关关闭时上传、单文件元数据、预览、下载和删除均拒绝；`findByCheckInId` 返回空集合；学习任务文字详情不受影响；附件开关开启但学习任务开关关闭时仍按既有学习任务边界拒绝上传。下载响应使用 `attachment` 内容处置，内容和权限与预览一致。

- [x] **步骤 2：运行专测并确认附件总开关和下载接口缺失**

预期：能力字段、`ATTACHMENT_SERVICE` 校验或 `/download` 端点断言失败。

- [x] **步骤 3：实现双层业务开关和安全下载**

附件应用服务首先校验 `ATTACHMENT_SERVICE`，任务上传继续同时校验 `LEARNING_TASK_MANAGEMENT`。父业务附件集合查询在附件开关关闭时返回空列表。控制器新增 `/attachments/{id}/download`，复用 `readContent`，只改变 `Content-Disposition` 为 `attachment`，不得读取客户端路径。

- [x] **步骤 4：扩展公共能力并运行专测至通过**

公共能力增加 `attachmentServiceEnabled`，对 Web 和 miniapp 均返回附件总开关结果；相关专测通过。

### 任务 5：后台 REST/OpenAPI

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/attachment/web/AttachmentManagementController.java`
- 新建：`server/src/main/java/com/lingdong/learning/attachment/web/AttachmentRuleResponse.java`
- 新建：`server/src/main/java/com/lingdong/learning/attachment/web/AttachmentFileLedgerResponse.java`
- 新建：`server/src/main/java/com/lingdong/learning/attachment/web/AttachmentRelationLedgerResponse.java`
- 新建：`server/src/main/java/com/lingdong/learning/attachment/web/CreateAttachmentRuleRequest.java`
- 新建：`server/src/main/java/com/lingdong/learning/attachment/web/UpdateAttachmentRuleRequest.java`
- 新建：`server/src/main/java/com/lingdong/learning/attachment/web/AttachmentRuleVersionRequest.java`
- 新建：`server/src/test/java/com/lingdong/learning/attachment/web/AttachmentManagementControllerTest.java`

- [x] **步骤 1：编写控制器失败测试**

测试七个后台接口的查询、新增、编辑、启停、文件台账和关系台账；覆盖功能关闭、读写权限拆分、无文件台账权限、19 位字符串标识、版本冲突和受认证 OpenAPI 路径。

- [x] **步骤 2：运行控制器专测并确认失败**

预期：控制器和请求响应类型不存在。

- [x] **步骤 3：实现中文 REST 契约**

控制器统一在入口校验 `ATTACHMENT_SERVICE`，将查询参数限制为设计字段，所有列表最多 200 条。请求使用 Bean Validation 校验名称、扩展名、大小、数量和版本；响应将全部雪花标识序列化为字符串。

- [x] **步骤 4：运行附件后端专测至通过**

运行迁移、规则、台账、任务附件和控制器专测，预期全部通过。

### 任务 6：React Web 管理页面

**文件：**
- 新建：`web/src/api/attachment-management.ts`
- 新建：`web/src/features/attachments/AttachmentManagementPage.tsx`
- 新建：`web/src/features/attachments/AttachmentManagementPage.test.tsx`
- 修改：`web/src/api/capability.ts`
- 修改：`web/src/app/App.tsx`
- 修改：`web/src/app/App.test.tsx`

- [x] **步骤 1：编写页面和路由失败测试**

页面测试覆盖规则加载、新增、编辑、启停、只读状态、文件筛选、关系抽屉和错误重试。App 测试覆盖功能开启且有读取权限时显示菜单、功能关闭或无权限时隐藏菜单并拒绝直达路由。

- [x] **步骤 2：运行 Web 专测并确认页面与 API 缺失**

运行：

```powershell
npm test -- --run src/features/attachments/AttachmentManagementPage.test.tsx src/app/App.test.tsx
```

预期：附件管理 API、页面和路由不存在。

- [x] **步骤 3：实现独立 API 和双页签管理页面**

规则页使用查询表单、表格、新增/编辑弹窗和启停图标按钮；文件页使用筛选表单、安全元数据表格和关系抽屉。表格设置稳定列宽和内部横向滚动；缺少管理权限时隐藏写操作，缺少文件台账权限时不创建文件页签。

- [x] **步骤 4：接入菜单、懒加载路由和能力字段**

新增 `/attachment-management`，入口同时要求 `attachmentServiceEnabled` 和规则读取或文件台账读取权限；页面按三项动态权限控制页签和操作。

- [x] **步骤 5：运行 Web 专测、类型检查和生产构建至通过**

运行相关 Vitest、`npx tsc --noEmit` 和 `npm run build`，预期全部通过。

### 任务 7：uni-app 附件开关

**文件：**
- 修改：`miniapp/src/api/capability.ts`
- 修改：`miniapp/src/pages/task-detail/task-detail.vue`

- [x] **步骤 1：为能力状态增加附件字段并接入任务详情**

`MiniappCapabilities` 增加可选 `attachmentServiceEnabled`。任务详情加载公共能力；字段明确为 `false` 时不渲染图片选择、待上传列表、历史图片网格和预览操作，也不下载历史附件；字段缺失时按兼容旧后端处理为启用。

- [x] **步骤 2：运行 uni-app 类型检查和双目标构建**

运行 `npm run type-check`、`npm run build:h5` 和 `npm run build:mp-weixin`，预期全部成功；源码和构建产物不得出现定位或地图调用。

### 任务 8：视觉、全量验收与中文文档

**文件：**
- 修改：`README.md`
- 修改：`docs/design/00-设计文档体系与需求追溯-V1.0.md` 至 `docs/design/12-当前实现一致性核对-V1.0.md`
- 修改：`docs/superpowers/plans/2026-08-08-lingdong-learning-master-development.md`
- 修改：本计划

- [x] **步骤 1：完成浏览器视觉验收**

使用脱敏模拟数据检查 1280px 桌面和 390px 窄屏的规则表格、编辑弹窗、文件宽表和关系抽屉；确认无页面级横向溢出、按钮遮挡或文本溢出，临时预览文件验收后删除。

- [x] **步骤 2：执行三端最终全量验证**

后端运行 `mvn test`，Web 运行全量 Vitest、类型检查和生产构建，uni-app 运行类型检查及 H5/微信小程序构建。全部命令必须退出码为 0。

- [x] **步骤 3：完成静态扫描**

扫描 V54 标识长度、敏感配置、定位地图调用、遗留临时预览文件和 `git diff --check`。换行符提示可记录但不能存在空白错误。

- [x] **步骤 4：同步中文文档和客观进度**

README、00-12、总计划和专项结论记录 V54 的实际能力、测试数量、未接云对象存储等边界。勾选 WBS-06 附件规则 REST/OpenAPI/动态权限/Web 页面子项；统一附件能力按实际完成度描述，不把供应商适配、病毒扫描、转码或物理删除计为完成。下一迁移从 V55 开始。
