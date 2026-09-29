# 灵动伴随 V56 通用导入校验作业实施计划

> **执行要求：** 使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐项实施，并使用复选框（`- [ ]`）跟踪进度。

**目标：**在 V55 模板管理基础上，建设 Web 端通用 XLSX 导入校验能力，完成模板字段映射、统一附件、异步校验、逐行结果、错误文件、动态权限和功能开关闭环；不写入任何学生、任务、积分或考勤业务数据。

**架构：**沿用 Spring Boot 应用服务、MyBatis XML、Flyway、动态 RBAC 和组织数据范围模式。模板模块负责字段映射和版本锁，新增 `importjob` 模块负责作业快照、竞争领取、POI 校验及结果落库；文件内容始终经统一附件服务保存和读取。Web 独立提供批量校验页面，uni-app 不增加相关入口、路由或 API。

**技术栈：**Spring Boot 3、JDK 17、Maven、MyBatis XML、Flyway、MySQL 8/H2、Apache POI、React、TypeScript、Ant Design Pro 风格组件、Vitest、uni-app。

---

## 统一约束

- 所有新增表主键均为应用层生成的 19 位雪花 `BIGINT`，禁止自增、UUID 主键和数据库序列。
- 只新增 `V56__add_import_validation_job.sql`，不得修改 V1 至 V55 历史迁移。
- 所有 API 中的 19 位标识按字符串序列化，禁止 JavaScript 数值精度损失。
- 后端同时校验功能开关、动态权限和当前组织数据范围，前端隐藏不能替代后端拦截。
- 错误结果不保存原始单元格敏感值，日志不输出文件内容、存储键、手机号或姓名。
- 功能关闭时不领取新作业，历史事实不删除；已领取作业完成当前处理并进入确定终态。
- 导入模板创建必须把模板元数据、文件关系和字段映射放在同一事务；物理文件失败补偿保留原始异常。
- 本专项只支持 `.xlsx` 和第一个工作表，不支持 `.xls`、CSV、业务写入、导出或小程序管理。

---

## 任务 1：数据库迁移与 POI 基础依赖

**文件：**

- 修改：`server/pom.xml`
- 新增：`server/src/main/resources/db/migration/V56__add_import_validation_job.sql`
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

- [x] 1.1 先在 `FlywayMigrationTest` 增加失败测试，断言 V56 已应用并存在以下结构：
  - `sys_import_export_template_field`
  - `sys_import_job`
  - `sys_import_job_row_result`
  - 功能开关 `DATA_IMPORT_VALIDATION`
  - 权限 `IMPORT_JOB_READ`、`IMPORT_JOB_CREATE`
  - 系统管理员两条 `ALLOW` 授权
  - 三张表主键为 `BIGINT`、非自增，新增种子标识为 19 位数字
- [x] 1.2 运行单测并确认因 V56 缺失而失败：

```powershell
cd server
mvn -Dtest=FlywayMigrationTest test
```

- [x] 1.3 新增 V56 迁移。字段映射表保存模板字段；作业表保存模板/字典快照、文件、范围、状态和计数；行结果表只保存行号、状态和错误摘要。为模板字段唯一性、作业编码、状态扫描、创建人查询及作业行号建立约束或索引。
- [x] 1.4 为三张表增加必要外键。删除策略使用受限删除，不允许级联删除附件、用户、组织或模板事实。
- [x] 1.5 在 `pom.xml` 增加明确版本的 `poi-ooxml` 依赖，不引入重复 Excel 解析库。
- [x] 1.6 重跑迁移测试，确认空库连续执行 56 个迁移且断言通过。

## 任务 2：模板字段映射领域与持久化

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/templateconfig/domain/ImportTemplateFieldDataType.java`
- 新增：`server/src/main/java/com/lingdong/learning/templateconfig/domain/ImportExportTemplateFieldRecord.java`
- 新增：`server/src/main/java/com/lingdong/learning/templateconfig/application/ImportTemplateFieldInput.java`
- 新增：`server/src/main/java/com/lingdong/learning/templateconfig/application/ImportTemplateFieldView.java`
- 新增：`server/src/main/java/com/lingdong/learning/templateconfig/application/ReplaceImportTemplateFieldsCommand.java`
- 新增：`server/src/main/java/com/lingdong/learning/templateconfig/application/ImportTemplateUsageQuery.java`
- 新增：`server/src/main/java/com/lingdong/learning/templateconfig/application/ImportTemplateFieldApplicationService.java`
- 新增：`server/src/main/java/com/lingdong/learning/templateconfig/infrastructure/persistence/ImportExportTemplateFieldMapper.java`
- 新增：`server/src/main/resources/mapper/templateconfig/ImportExportTemplateFieldMapper.xml`
- 修改：`server/src/main/java/com/lingdong/learning/templateconfig/application/CreateImportExportTemplateUploadCommand.java`
- 修改：`server/src/main/java/com/lingdong/learning/templateconfig/application/ImportExportTemplateApplicationService.java`
- 修改：`server/src/main/java/com/lingdong/learning/templateconfig/infrastructure/persistence/ImportExportTemplateMapper.java`
- 修改：`server/src/main/resources/mapper/templateconfig/ImportExportTemplateMapper.xml`
- 新增测试：`server/src/test/java/com/lingdong/learning/templateconfig/application/ImportTemplateFieldApplicationServiceTest.java`
- 修改测试：`server/src/test/java/com/lingdong/learning/templateconfig/application/ImportExportTemplateApplicationServiceTest.java`

- [x] 2.1 先写字段规则测试：至少一个字段、字段编码格式、表头唯一、编码唯一、顺序唯一、六种类型、文本长度、字典与布尔互斥。
- [x] 2.2 写状态与引用测试：仅 `IMPORT` 模板可配置；仅停用且未被作业引用的模板可整体替换；版本冲突拒绝；无字段映射的历史导入模板不得启用。
- [x] 2.3 运行模板应用服务测试并确认新增场景失败。
- [x] 2.4 实现字段枚举、记录、Mapper XML 和应用服务。整体替换先校验全部输入，再一次删除和批量插入，禁止出现部分字段更新。
- [x] 2.5 扩展上传创建命令，使 `IMPORT` 创建必须携带字段，`EXPORT` 创建必须不携带字段。模板、文件关系、字段映射在同一事务提交。
- [x] 2.6 保留已有内部调用的兼容边界，但所有可启用导入模板都必须满足字段映射不为空；不得为测试绕过生产规则。
- [x] 2.7 重跑模板和统一附件测试，验证数据库回滚与物理内容补偿的原始异常语义。

## 任务 3：纯 XLSX 校验内核

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/importjob/application/validation/ImportFieldMappingSnapshot.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/application/validation/ImportCellError.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/application/validation/ImportRowValidationResult.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/application/validation/WorkbookValidationResult.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/application/validation/WorkbookValidationService.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/application/validation/ImportValidationErrorWorkbookWriter.java`
- 新增测试：`server/src/test/java/com/lingdong/learning/importjob/application/validation/WorkbookValidationServiceTest.java`
- 新增测试：`server/src/test/java/com/lingdong/learning/importjob/application/validation/ImportValidationErrorWorkbookWriterTest.java`

- [x] 3.1 使用 POI 在测试内存中生成工作簿，先覆盖正确表头、空白行忽略、有效行计数和五列错误文件结构。
- [x] 3.2 增加失败测试：空文件、损坏文件、无工作表、缺失/重复/未知表头、行数超限。
- [x] 3.3 增加字段失败测试：必填、长度、整数、小数、日期、日期时间、布尔、字典快照和同一行多错误累计。
- [x] 3.4 增加公式安全测试：不执行公式，只读取缓存结果；缓存不可用时返回格式错误。
- [x] 3.5 实现纯校验服务。它只接收字节、映射快照和行数上限，不依赖当前用户、数据库 Mapper 或 HTTP 会话。
- [x] 3.6 配置 POI 压缩包安全阈值和工作簿保护；异常统一转为受限错误编码与中文消息。
- [x] 3.7 实现错误工作簿生成器，严格只输出行号、列名、字段编码、错误编码和中文说明。
- [x] 3.8 运行两个校验内核测试类并确认通过。

## 任务 4：导入作业持久化、权限和创建流程

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/importjob/domain/ImportJobStatus.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/domain/ImportJobRowStatus.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/domain/ImportJobRecord.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/domain/ImportJobRowResultRecord.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/application/CreateImportJobCommand.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/application/ImportJobQuery.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/application/ImportJobView.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/application/ImportJobDetailView.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/application/ImportJobOptionView.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/application/ImportJobApplicationService.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/application/ImportJobAccessService.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/infrastructure/persistence/ImportJobMapper.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/infrastructure/persistence/ImportJobRowResultMapper.java`
- 新增：`server/src/main/resources/mapper/importjob/ImportJobMapper.xml`
- 新增：`server/src/main/resources/mapper/importjob/ImportJobRowResultMapper.xml`
- 新增测试：`server/src/test/java/com/lingdong/learning/importjob/application/ImportJobApplicationServiceTest.java`
- 新增测试：`server/src/test/java/com/lingdong/learning/importjob/application/ImportJobPersistenceTest.java`

- [x] 4.1 先写创建测试：三功能开关、`IMPORT_JOB_CREATE`、启用导入模板、字段映射、`.xlsx` 文件、可选组织范围和 19 位作业标识。
- [x] 4.2 写读取边界测试：系统管理员全量；普通用户只能读取本人且仍处于当前组织范围的作业；无组织作业仅创建人和系统管理员可读；当前 `DENY`、角色撤回、账号停用立即生效。
- [x] 4.3 写附件补偿测试：数据库失败时清理源文件；清理失败作为 suppressed 异常，原始异常不被替换。
- [x] 4.4 实现 Mapper、快照序列化、列表/详情/错误分页和选项查询。查询条件使用参数绑定，不拼接用户输入 SQL。
- [x] 4.5 作业创建时固化模板版本、模板名称、有序字段映射及启用字典项编码快照；模板后续变化不能改变已排队作业。
- [x] 4.6 通过统一附件服务建立 `IMPORT_JOB_SOURCE/IMPORT_VALIDATION` 关系，API 视图不得暴露 `storageKey`、内容摘要或永久 URL。
- [x] 4.7 实现 `ImportTemplateUsageQuery` 的作业引用查询，锁定已被任何作业引用的模板字段。
- [x] 4.8 运行导入作业应用和持久化测试。

## 任务 5：异步领取、批量处理和终态

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/importjob/application/ImportJobClaimService.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/application/ImportJobResultService.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/application/ImportJobBatchService.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/infrastructure/scheduling/ImportJobSchedulingConfiguration.java`
- 修改：`server/src/main/resources/application.yml`
- 新增测试：`server/src/test/java/com/lingdong/learning/importjob/application/ImportJobClaimServiceTest.java`
- 新增测试：`server/src/test/java/com/lingdong/learning/importjob/application/ImportJobBatchServiceTest.java`
- 新增测试：`server/src/test/java/com/lingdong/learning/importjob/application/ImportJobResultServiceTest.java`

- [x] 5.1 先写竞争领取测试：仅 `QUEUED + versionNo` 条件更新成功者进入 `VALIDATING`，第二实例领取失败。
- [x] 5.2 写批处理测试：功能关闭不领取；单作业异常不阻塞后续；已进入 `VALIDATING` 的作业可完成当前处理。
- [x] 5.3 写终态测试：全有效为 `VALIDATED`；表头或任一行错误为 `VALIDATION_FAILED`；读取或内部异常为 `SYSTEM_FAILED`；终态重复调用不改写事实。
- [x] 5.4 实现领取服务与 Mapper 条件更新，禁止仅靠 JVM 锁保证多实例互斥。
- [x] 5.5 实现批量处理：读取受控源文件、解析快照、调用纯校验内核、批量写行结果、更新计数。
- [x] 5.6 无效结果生成错误 XLSX，经统一附件服务保存并建立 `IMPORT_JOB_ERROR/IMPORT_VALIDATION` 关系；关系或终态失败时补偿新错误文件。
- [x] 5.7 在 `application.yml` 增加调度启停、周期、批量数和默认 10000 行上限配置，并提供可关闭的调度配置类。
- [x] 5.8 运行领取、批处理和结果服务测试。

## 任务 6：REST、OpenAPI 与公共能力

**文件：**

- 新增：`server/src/main/java/com/lingdong/learning/templateconfig/web/ImportTemplateFieldRequest.java`
- 新增：`server/src/main/java/com/lingdong/learning/templateconfig/web/ReplaceImportTemplateFieldsRequest.java`
- 新增：`server/src/main/java/com/lingdong/learning/templateconfig/web/ImportTemplateFieldResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateManagementController.java`
- 修改：`server/src/main/java/com/lingdong/learning/templateconfig/web/CreateImportExportTemplateRequest.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/web/ImportJobController.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/web/CreateImportJobRequest.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/web/ImportJobResponse.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/web/ImportJobDetailResponse.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/web/ImportJobOptionsResponse.java`
- 新增：`server/src/main/java/com/lingdong/learning/importjob/web/ImportJobErrorResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityController.java`
- 修改测试：`server/src/test/java/com/lingdong/learning/templateconfig/web/ImportExportTemplateManagementControllerTest.java`
- 新增测试：`server/src/test/java/com/lingdong/learning/importjob/web/ImportJobControllerTest.java`
- 修改测试：`server/src/test/java/com/lingdong/learning/feature/web/PublicCapabilityControllerTest.java`

- [x] 6.1 先写 MockMvc/OpenAPI 失败测试，覆盖模板字段 GET/PUT、作业 options/create/list/detail/errors/error-file。
- [x] 6.2 扩展模板 multipart 创建的 `fields` JSON 部件，返回中文校验错误；导出模板拒绝非空字段。
- [x] 6.3 实现作业控制器。创建限制 multipart 大小并只接受 `.xlsx`；列表默认 20、最大 100；错误文件不存在返回 404。
- [x] 6.4 所有接口复用应用服务的当前权限与范围检查，关闭任一必要开关统一拒绝，不留下可直接访问的旁路。
- [x] 6.5 公共能力新增 `dataImportValidationEnabled`，只在 Web 客户端且 `DATA_IMPORT_VALIDATION`、`ATTACHMENT_SERVICE`、`IMPORT_EXPORT_TEMPLATE_MANAGEMENT` 同时启用时为真。
- [x] 6.6 确认响应标识为字符串，错误文件响应含安全文件名和正确 MIME，不返回存储实现信息。
- [x] 6.7 运行三个控制器测试类与 OpenAPI 生成测试。

## 任务 7：Web 模板字段映射界面

**文件：**

- 修改：`web/src/api/import-export-templates.ts`
- 修改：`web/src/features/import-export-templates/ImportExportTemplateManagementPage.tsx`
- 修改：`web/src/features/import-export-templates/ImportExportTemplateManagementPage.test.tsx`

- [x] 7.1 先写失败测试：创建导入模板显示字段表格；导出模板隐藏字段区；至少一行；增删、排序、类型联动、必填、长度和字典选择。
- [x] 7.2 扩展 API 类型并把字段数组序列化为 multipart JSON；所有标识继续使用字符串。
- [x] 7.3 实现紧凑可编辑字段表格，不嵌套卡片；删除和排序使用图标按钮与中文提示。
- [x] 7.4 为停用且未被引用的历史导入模板提供字段抽屉；版本冲突后刷新当前模板，不覆盖服务端新版本。
- [x] 7.5 校验长表头、长字段编码及 390px 弹窗布局，表格只在自身区域横向滚动。
- [x] 7.6 运行模板页面测试、Web 类型检查和构建。

## 任务 8：Web 导入校验作业页面

**文件：**

- 新增：`web/src/api/import-jobs.ts`
- 新增：`web/src/features/import-jobs/ImportJobManagementPage.tsx`
- 新增：`web/src/features/import-jobs/ImportJobManagementPage.test.tsx`
- 修改：`web/src/App.tsx`
- 修改：`web/src/App.test.tsx`
- 修改：`web/src/api/auth.ts`

- [x] 8.1 先写失败测试：能力/读取权限隐藏入口，直达路由拒绝；创建权限控制按钮；列表筛选、状态、进度、详情和错误分页可用。
- [x] 8.2 实现作业 API，上传使用 multipart，下载使用受控 Blob，不拼接永久文件地址。
- [x] 8.3 新增 `/import-jobs` 页面和菜单。筛选包含编码、模板、组织、状态和时间；详情只显示快照、计数、失败摘要和错误，不显示原始单元格内容。
- [x] 8.4 错误文件下载使用下载图标和工具提示；无文件时按钮不可用并保持稳定尺寸。
- [x] 8.5 390px 下筛选和弹窗单列，宽表内部滚动；1280px 下信息密度与现有管理页面一致。
- [x] 8.6 运行页面、路由、能力测试及 Web 全量检查。

## 任务 9：全量验收、隔离扫描与中文文档

**文件：**

- 修改：`README.md`
- 修改：`docs/design/00-文档导航.md`
- 修改：`docs/design/01-业务需求说明书-BRD.md`
- 修改：`docs/design/03-功能详细设计-FSD.md`
- 修改：`docs/design/04-系统架构设计-HLD.md`
- 修改：`docs/design/05-数据库设计.md`
- 修改：`docs/design/06-Flyway迁移规范.md`
- 修改：`docs/design/07-API接口设计.md`
- 修改：`docs/design/08-权限与安全设计.md`
- 修改：`docs/design/10-测试方案与验收用例.md`
- 修改：`docs/design/11-部署运维与发布方案.md`
- 修改：`docs/design/12-开发任务总计划.md`
- 修改：本计划

- [x] 9.1 运行后端全量测试并记录测试总数与 56 个 Flyway 迁移结果：

```powershell
cd server
mvn test
```

- [x] 9.2 运行 Web 全量测试、类型检查和生产构建：

```powershell
cd web
npm test -- --run
npm run typecheck
npm run build
```

- [x] 9.3 运行 uni-app 类型检查和 H5、微信小程序双目标构建，证明 V56 未破坏独立小程序应用：

```powershell
cd miniapp
npm run type-check
npm run build:h5
npm run build:mp-weixin
```

- [x] 9.4 启动本地 Web，完成 1280px、390px、只读角色、功能关闭、创建弹窗和详情抽屉视觉验收；检查控制台无错误、文本不重叠。
- [x] 9.5 扫描小程序，确认没有导入作业页面、路由、API、权限码或菜单；扫描已知微信、数据库、Redis 密钥，确认没有新增明文泄漏。
- [x] 9.6 执行 `git diff --check`，仅接受工作区既有换行告警，不接受新增空白错误。
- [x] 9.7 用中文更新设计文档、README 和总计划，明确“校验完成不等于业务导入成功”，并记录未执行远程数据库、Redis、共享测试、预生产、生产及业务 UAT。
- [x] 9.8 对照设计逐项自审，确认所有勾选项均有测试、构建或视觉证据后再声明 V56 完成。

---

## 完成定义

只有同时满足以下条件，V56 才能标记完成：

1. V56 空库连续迁移、三表约束、19 位主键、开关和权限种子通过自动化测试。
2. 导入模板字段映射、版本锁、历史模板补齐和引用锁定闭环。
3. XLSX 表头、逐行规则、行上限、公式安全、错误文件和异常路径均有自动化测试。
4. 作业竞争领取、幂等终态、附件补偿、动态权限、当前组织范围和三开关闭环。
5. REST/OpenAPI 与 Web 页面完整，1280px 和 390px 视觉验收无阻断问题。
6. uni-app 不包含该管理能力且双目标构建通过。
7. 后端、Web、miniapp 全量验证通过，中文文档与总计划同步更新。
8. 未把任何业务数据写入、导出能力或敏感审批误计入本专项完成范围。
