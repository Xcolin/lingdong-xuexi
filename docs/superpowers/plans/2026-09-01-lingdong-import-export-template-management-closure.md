# 灵动学习 V55 导入导出模板管理闭环实施计划

> **执行要求：** 使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐项实施，并使用复选框（`- [ ]`）跟踪进度。

**目标：**在不修改 V1 至 V54 历史迁移的前提下，完成导入导出模板的版本化管理、统一附件、动态权限、功能开关、REST/OpenAPI 和独立 React Web 页面闭环。

**架构：**沿用 Spring Boot 应用服务、MyBatis XML、Flyway 和动态 RBAC 模式；V55 只扩展模板版本锁和配置基础数据。模板文件通过可复用附件内容服务登记、保存、建立业务关系和受控下载；Web 通过公共能力与权限共同裁剪入口，小程序不承载模板管理。

**技术栈：**Spring Boot 3、JDK 17、Maven、MyBatis XML、Flyway、MySQL 8/H2 测试、React、TypeScript、Ant Design Pro 风格组件、Vitest、uni-app。

---

## 文件结构

### 后端新增

- `server/src/main/resources/db/migration/V55__add_import_export_template_management.sql`：版本锁、功能开关、动态权限、字典及 19 位种子。
- `server/src/main/java/com/lingdong/learning/attachment/application/ManagedAttachmentContentService.java`：统一附件内容保存、读取和失败补偿。
- `server/src/main/java/com/lingdong/learning/templateconfig/application/ImportExportTemplateQuery.java`：模板组合查询条件。
- `server/src/main/java/com/lingdong/learning/templateconfig/application/ImportExportTemplateOption.java`：字典选项视图。
- `server/src/main/java/com/lingdong/learning/templateconfig/application/ImportExportTemplateOptions.java`：三类选项集合。
- `server/src/main/java/com/lingdong/learning/templateconfig/application/CreateImportExportTemplateUploadCommand.java`：带文件内容的创建命令。
- `server/src/main/java/com/lingdong/learning/templateconfig/application/ImportExportTemplateContent.java`：受控下载内容。
- `server/src/main/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateManagementController.java`：七个管理接口。
- `server/src/main/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateResponse.java`：安全模板响应。
- `server/src/main/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateOptionsResponse.java`：选项响应。
- `server/src/main/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateVersionRequest.java`：乐观锁请求。
- `server/src/test/java/com/lingdong/learning/attachment/application/ManagedAttachmentContentServiceTest.java`：公共附件内容服务测试。
- `server/src/test/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateManagementControllerTest.java`：接口、权限、开关和 OpenAPI 测试。

### 后端修改

- `server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`：V55 迁移断言。
- `server/src/main/java/com/lingdong/learning/templateconfig/domain/ImportExportTemplateRecord.java`：增加 `versionNo`。
- `server/src/main/java/com/lingdong/learning/templateconfig/application/ImportExportTemplate.java`：增加文件安全元数据、版本和审计时间。
- `server/src/main/java/com/lingdong/learning/templateconfig/application/ImportExportTemplateApplicationService.java`：动态权限、字典、筛选、上传创建、启停、默认切换和下载。
- `server/src/main/java/com/lingdong/learning/templateconfig/infrastructure/persistence/ImportExportTemplateMapper.java`：查询、分组锁和条件更新。
- `server/src/main/resources/mapper/templateconfig/ImportExportTemplateMapper.xml`：全部模板 SQL。
- `server/src/main/java/com/lingdong/learning/attachment/application/TaskAttachmentApplicationService.java`：复用公共内容服务，保留任务图片格式和业务鉴权。
- `server/src/test/java/com/lingdong/learning/templateconfig/application/ImportExportTemplateApplicationServiceTest.java`：生命周期、并发、字典、附件和权限测试。
- `server/src/test/java/com/lingdong/learning/attachment/application/TaskAttachmentApplicationServiceTest.java`：公共服务重构回归。
- `server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityResponse.java`：增加模板管理能力字段。
- `server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityController.java`：仅 Web 且双开关启用时返回能力。
- `server/src/test/java/com/lingdong/learning/feature/web/PublicCapabilityControllerTest.java`：双开关和客户端隔离测试。

### Web 新增

- `web/src/api/import-export-templates.ts`：查询、multipart 创建、状态操作和下载 API。
- `web/src/features/import-export-templates/ImportExportTemplateManagementPage.tsx`：模板管理页面。
- `web/src/features/import-export-templates/ImportExportTemplateManagementPage.test.tsx`：页面行为测试。

### Web 修改

- `web/src/api/http.ts`：正确处理 `FormData` 并提供 `postForm`。
- `web/src/api/capability.ts`：增加模板管理能力字段。
- `web/src/app/App.tsx`：懒加载、菜单、路由、权限裁剪。
- `web/src/app/App.test.tsx`：入口和直达路由组合测试。
- `web/src/styles/index.css`：模板页面桌面与窄屏样式。

### 文档修改

- `README.md`
- `docs/design/00-设计文档体系与需求追溯-V1.0.md` 至 `docs/design/12-当前实现一致性核对-V1.0.md`
- `docs/superpowers/plans/2026-08-08-lingdong-learning-master-development.md`
- 本实施计划。

---

### 任务 1：用失败测试定义 V55 数据基线

**文件：**
- 新建：`server/src/main/resources/db/migration/V55__add_import_export_template_management.sql`
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

- [x] **步骤 1：先增加失败的迁移测试**

在 `FlywayMigrationTest` 增加 `addsImportExportTemplateManagementThroughV55()`，断言以下结果：

```java
assertThat(migrationCount).isEqualTo(1);
assertThat(versionColumnCount).isEqualTo(1);
assertThat(featureCount).isEqualTo(1);
assertThat(permissionCount).isEqualTo(2);
assertThat(administratorGrantCount).isEqualTo(2);
assertThat(dictionaryTypeCount).isEqualTo(3);
assertThat(dictionaryItemCount).isEqualTo(7);
```

- [x] **步骤 2：运行测试并确认红灯**

运行：

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
mvn "-Dtest=FlywayMigrationTest#addsImportExportTemplateManagementThroughV55" test
```

预期：失败，原因是 V55 尚不存在。

- [x] **步骤 3：实现 V55 迁移**

迁移必须包含以下结构，实际插入语句全部使用 `1874244142494646610` 至 `1874244142494646624` 的不重复 19 位标识：

```sql
ALTER TABLE sys_import_export_template
    ADD COLUMN version_no BIGINT NOT NULL DEFAULT 0;

INSERT INTO sys_feature_toggle (...)
VALUES (..., 'IMPORT_EXPORT_TEMPLATE_MANAGEMENT', '导入导出模板管理',
        'GLOBAL', 'GLOBAL', 'ENABLED', 1, '统一控制导入导出模板配置能力。');

-- 三个字典类型、七个字典项。
-- 两项 WEB 权限 IMPORT_EXPORT_TEMPLATE_READ、IMPORT_EXPORT_TEMPLATE_MANAGE。
-- 两项 SYS_ADMIN ALLOW 授权。
```

不修改 V14，也不在 V55 猜测附件扩展名白名单。

- [x] **步骤 4：运行迁移测试并确认绿灯**

运行同一步骤 2。预期：通过，Flyway 当前版本为 55。

---

### 任务 2：用失败测试定义模板查询、字典和生命周期

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/templateconfig/application/ImportExportTemplateQuery.java`
- 新建：`server/src/main/java/com/lingdong/learning/templateconfig/application/ImportExportTemplateOption.java`
- 新建：`server/src/main/java/com/lingdong/learning/templateconfig/application/ImportExportTemplateOptions.java`
- 修改：`server/src/main/java/com/lingdong/learning/templateconfig/domain/ImportExportTemplateRecord.java`
- 修改：`server/src/main/java/com/lingdong/learning/templateconfig/application/ImportExportTemplate.java`
- 修改：`server/src/main/java/com/lingdong/learning/templateconfig/infrastructure/persistence/ImportExportTemplateMapper.java`
- 修改：`server/src/main/resources/mapper/templateconfig/ImportExportTemplateMapper.xml`
- 修改：`server/src/main/java/com/lingdong/learning/templateconfig/application/ImportExportTemplateApplicationService.java`
- 修改：`server/src/test/java/com/lingdong/learning/templateconfig/application/ImportExportTemplateApplicationServiceTest.java`

- [x] **步骤 1：增加失败的应用服务测试**

覆盖：动态读取/管理权限、名称/类型/模块/状态组合筛选、启用字典选项、停用字典拒绝创建、创建新版本、重复版本冲突、启用、停用、唯一默认、停用默认项、过期版本冲突和幂等提交。

关键断言：

```java
assertThat(service.listTemplates(new ImportExportTemplateQuery(
        operator.id(), "学生", TemplateType.IMPORT, "STUDENT", ImportExportTemplateStatus.ENABLED)))
        .extracting(ImportExportTemplate::id)
        .containsExactly(template.id());

assertThatThrownBy(() -> service.disableTemplate(operator.id(), template.id(), template.versionNo() + 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("版本已变化");
```

- [x] **步骤 2：运行应用服务测试并确认红灯**

运行：

```powershell
mvn "-Dtest=ImportExportTemplateApplicationServiceTest" test
```

预期：编译失败或新行为断言失败。

- [x] **步骤 3：扩展领域视图和查询对象**

记录结构统一为：

```java
public record ImportExportTemplate(
        Long id, String templateName, TemplateType templateType, String moduleCode,
        String version, Long fileId, String fileName, String contentType, Long sizeBytes,
        boolean defaultTemplate, ImportExportTemplateStatus status, Long versionNo,
        LocalDateTime createdAt, LocalDateTime updatedAt
) { }
```

查询对象包含 `operatorId`、`templateName`、`templateType`、`moduleCode`、`status`。所有代码和名称在应用服务标准化，查询最多返回 200 条。

- [x] **步骤 4：扩展 MyBatis XML**

新增并实现：

```java
List<ImportExportTemplateRecord> findAll(String templateName, TemplateType templateType,
                                         String moduleCode, ImportExportTemplateStatus status);
List<ImportExportTemplateRecord> lockGroup(String moduleCode, TemplateType templateType);
int enable(Long id, Long expectedVersion);
int disable(Long id, Long expectedVersion);
int markAsDefault(Long id, Long expectedVersion);
```

列表连接 `sys_file` 返回安全文件元数据；任何 SQL 都不得投影 `storage_key` 或 `content_sha256`。状态更新使用 `version_no = version_no + 1` 和期望版本条件。

- [x] **步骤 5：实现动态权限与生命周期**

应用服务使用：

```java
permissionDecisionService.isAllowed(operatorId, PermissionClient.WEB, permissionCode)
```

替换原 `SYS_ADMIN` 静态角色判断。设置默认前调用 `lockGroup`，检查目标版本与状态，再清除旧默认并设置目标。`findCurrentDefault` 保留为后续业务执行内部查询，只返回启用默认模板。

- [x] **步骤 6：运行应用服务测试并确认绿灯**

运行步骤 2 命令。预期：全部通过。

---

### 任务 3：提取统一附件内容服务并保持任务图片行为

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/attachment/application/ManagedAttachmentContentService.java`
- 新建：`server/src/test/java/com/lingdong/learning/attachment/application/ManagedAttachmentContentServiceTest.java`
- 修改：`server/src/main/java/com/lingdong/learning/attachment/application/TaskAttachmentApplicationService.java`
- 修改：`server/src/test/java/com/lingdong/learning/attachment/application/TaskAttachmentApplicationServiceTest.java`

- [x] **步骤 1：增加失败的公共服务测试**

测试登记、保存、摘要、完成确认、读取、存储失败补偿和物理内容清理。服务接口固定为：

```java
ManagedFile store(Long uploaderId, String moduleCode, String fileCategory,
                  String originalName, String contentType, byte[] content);
AttachmentContentView read(Long fileId);
void discardContent(String storageKey);
```

- [x] **步骤 2：运行公共服务与任务附件测试并确认红灯**

```powershell
mvn "-Dtest=ManagedAttachmentContentServiceTest,TaskAttachmentApplicationServiceTest" test
```

- [x] **步骤 3：实现公共内容服务**

服务调用 `AttachmentFileApplicationService.registerUpload`、`AttachmentContentStorage.store` 和 `completeUpload`，摘要使用 SHA-256。空内容立即拒绝；存储或完成确认失败时删除已写入内容并继续抛出原异常。

- [x] **步骤 4：重构任务图片服务**

任务服务继续执行 JPG/PNG 魔数、扩展名和 MIME 一致性校验，再调用公共内容服务。读取权限、打卡关系、附件开关、学习任务开关和空附件列表提前返回规则保持不变。

- [x] **步骤 5：运行测试并确认绿灯**

运行步骤 2，另运行：

```powershell
mvn "-Dtest=LearningTaskControllerTest#attachmentFeatureDisabledStillAllowsTextOnlyCheckIn" test
```

预期：公共服务、任务附件和纯文字打卡回归全部通过。

---

### 任务 4：实现模板上传创建、业务关系和受控下载

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/templateconfig/application/CreateImportExportTemplateUploadCommand.java`
- 新建：`server/src/main/java/com/lingdong/learning/templateconfig/application/ImportExportTemplateContent.java`
- 修改：`server/src/main/java/com/lingdong/learning/templateconfig/application/ImportExportTemplateApplicationService.java`
- 修改：`server/src/test/java/com/lingdong/learning/templateconfig/application/ImportExportTemplateApplicationServiceTest.java`

- [x] **步骤 1：增加失败测试**

覆盖：使用 `IMPORT_EXPORT_TEMPLATE/TEMPLATE_FILE` 保存文件、上传人必须是操作人、模板创建后建立 `TEMPLATE_FILE/SYSTEM_CONFIGURATION` 关系、下载已停用模板、拒绝非 `AVAILABLE` 文件，以及模板插入失败时删除物理内容。

- [x] **步骤 2：运行测试并确认红灯**

```powershell
mvn "-Dtest=ImportExportTemplateApplicationServiceTest" test
```

- [x] **步骤 3：实现事务编排**

核心方法签名：

```java
@Transactional
public ImportExportTemplate createTemplate(CreateImportExportTemplateUploadCommand command);

@Transactional(readOnly = true)
public ImportExportTemplateContent downloadTemplate(Long operatorId, Long templateId);
```

创建流程为：权限与字典校验 -> 唯一版本预检 -> 公共附件保存 -> 模板插入 -> 文件关系插入 -> 可选默认切换。捕获异常后调用 `discardContent(storageKey)`，重新抛出异常使数据库事务回滚。

- [x] **步骤 4：运行测试并确认绿灯**

运行步骤 2。预期：全部通过，响应和异常不包含存储键或摘要。

---

### 任务 5：实现功能能力、REST 与 OpenAPI

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateManagementController.java`
- 新建：`server/src/main/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateResponse.java`
- 新建：`server/src/main/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateOptionsResponse.java`
- 新建：`server/src/main/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateVersionRequest.java`
- 新建：`server/src/test/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateManagementControllerTest.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityController.java`
- 修改：`server/src/test/java/com/lingdong/learning/feature/web/PublicCapabilityControllerTest.java`

- [x] **步骤 1：增加失败的 Controller 和能力测试**

测试七个路径、字符串雪花标识、multipart 创建、文件下载响应头、读取/管理权限分离、用户 DENY、两个开关分别停用、Web 能力为真、小程序能力固定为假及 OpenAPI 路径存在。

- [x] **步骤 2：运行测试并确认红灯**

```powershell
mvn "-Dtest=ImportExportTemplateManagementControllerTest,PublicCapabilityControllerTest" test
```

- [x] **步骤 3：实现公共能力**

响应增加：

```java
boolean importExportTemplateManagementEnabled
```

Controller 只在 `WEB` 且 `IMPORT_EXPORT_TEMPLATE_MANAGEMENT`、`ATTACHMENT_SERVICE` 均启用时返回 `true`。

- [x] **步骤 4：实现七个管理接口**

所有入口先校验两个功能开关，使用 `@RequirePermission`，并让应用服务重复校验动态权限。下载使用：

```java
ContentDisposition.attachment()
        .filename(content.originalName(), StandardCharsets.UTF_8)
        .build();
```

- [x] **步骤 5：运行测试并确认绿灯**

运行步骤 2。预期：全部通过。

---

### 任务 6：实现 Web API、模板页面和路由裁剪

**文件：**
- 新建：`web/src/api/import-export-templates.ts`
- 新建：`web/src/features/import-export-templates/ImportExportTemplateManagementPage.tsx`
- 新建：`web/src/features/import-export-templates/ImportExportTemplateManagementPage.test.tsx`
- 修改：`web/src/api/http.ts`
- 修改：`web/src/api/capability.ts`
- 修改：`web/src/app/App.tsx`
- 修改：`web/src/app/App.test.tsx`
- 修改：`web/src/styles/index.css`

- [x] **步骤 1：增加失败的页面和入口测试**

覆盖：初始选项与列表加载、组合筛选、重置、新增 multipart、只读模式、设默认、启停、下载、409 冲突刷新、加载失败重试，以及菜单/直达路由要求能力和读取权限。

- [x] **步骤 2：运行测试并确认红灯**

```powershell
npm test -- --run src/features/import-export-templates/ImportExportTemplateManagementPage.test.tsx src/app/App.test.tsx
```

- [x] **步骤 3：扩展 HTTP 与模板 API**

`http.ts` 仅对非 `FormData` 请求自动设置 JSON 内容类型，并增加：

```typescript
postForm<T>(path: string, body: FormData): Promise<T> {
  return request<T>(path, { method: 'POST', body });
}
```

模板 API 提供 `options`、`list`、`create`、`enable`、`disable`、`setDefault` 和 `download`。

- [x] **步骤 4：实现页面**

页面使用 Ant Design `Form`、`Select`、`Input`、`Upload`、`Switch`、`Table`、`Modal`、`Tag` 和 Lucide 图标。新增弹窗只接收单个文件；没有管理权限时不渲染任何变更按钮；下载使用返回 Blob 和记录中的安全文件名。

- [x] **步骤 5：接入菜单和路由**

新增 `FileSpreadsheet` 图标菜单与 `/import-export-templates` 路由。访问函数固定为：

```typescript
export function canAccessImportExportTemplates(user: CurrentUser, capabilities: ClientCapabilities): boolean {
  return capabilities.importExportTemplateManagementEnabled === true
    && user.permissionCodes.includes('IMPORT_EXPORT_TEMPLATE_READ');
}
```

- [x] **步骤 6：实现响应式样式并运行测试**

模板表使用内部横向滚动；390px 下筛选项单列、弹窗 `max-width` 不超过视口。运行步骤 2，预期全部通过。

---

### 任务 7：执行全量回归、构建和视觉验收

**文件：**
- 仅在发现缺陷时修改对应实现或测试文件。

- [x] **步骤 1：后端全量测试**

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
mvn test
```

预期：全部通过，Flyway 成功验证 55 个迁移。

- [x] **步骤 2：Web 全量验证**

```powershell
npm test -- --run
npm run typecheck
npm run build
```

预期：测试、类型检查和生产构建通过；仅允许既有的包体积提示。

- [x] **步骤 3：uni-app 隔离验证**

```powershell
npm run typecheck
npm run build:h5
npm run build:mp-weixin
```

预期：全部通过，小程序没有新增模板管理页面、路由或管理 API。

- [x] **步骤 4：浏览器视觉验收**

在 1280px 和 390px 检查模板列表、筛选、新增弹窗、只读状态、默认切换和下载按钮。确认 `document.documentElement.scrollWidth === window.innerWidth`，宽表只在表格内部滚动，文字和操作不重叠。

- [x] **步骤 5：静态安全审计**

```powershell
git diff --check
rg -n "password:|app-secret:|app-id:" README.md docs server/src web/src miniapp/src
rg -n -i "getLocation|chooseLocation|openLocation|<map" miniapp/src miniapp/dist
```

预期：补丁无格式错误，已知密钥和定位调用均无匹配。

---

### 任务 8：同步中文文档和主计划

**文件：**
- 修改：`README.md`
- 修改：`docs/design/00-设计文档体系与需求追溯-V1.0.md` 至 `docs/design/12-当前实现一致性核对-V1.0.md`
- 修改：`docs/superpowers/plans/2026-08-08-lingdong-learning-master-development.md`
- 修改：`docs/superpowers/plans/2026-09-01-lingdong-import-export-template-management-closure.md`

- [x] **步骤 1：记录 V55 实际实现边界**

文档必须明确：模板管理闭环已完成；字段映射、导入校验、错误文件、异步导出任务、脱敏审批、对象存储和内容处理仍未完成，不把管理页面等同于导入导出执行能力。

- [x] **步骤 2：同步验证基线**

只记录本轮实际执行成功的后端测试数、Web 测试数、构建、视觉检查和静态扫描。继续声明未执行远程 MySQL、Redis、共享测试、预生产、生产和业务 UAT。

- [x] **步骤 3：更新总计划**

勾选 WBS-06“为导入导出模板补齐 REST/OpenAPI、动态权限和 Web 管理页面”，将 `BRD-3.2-07` 更新为“本地完成”；“模板版本、字段映射、导入校验、错误文件、异步导出任务和下载权限”复合项保持部分完成或未完成。下一迁移从 V56 开始。

- [x] **步骤 4：最终自检**

```powershell
rg -n "^- \[ \]" docs/superpowers/plans/2026-09-01-lingdong-import-export-template-management-closure.md
git diff --check
```

预期：V55 计划无未完成步骤，补丁格式通过。
