# 灵动学习 V57 通用异步导出作业实施计划

> **供代理执行：** 必须使用 `superpowers:executing-plans` 或 `superpowers:subagent-driven-development`，严格按复选框顺序实施并逐项更新状态。

**目标：** 建设 Web 端通用异步 Excel 导出框架，完成家长积分明细普通导出和系统管理员权限变更日志敏感导出，并由系统审核员审批敏感申请。

**架构：** 新建 `exportjob` 独立领域，使用“作业状态机 + 数据集适配器注册表 + 流式工作簿生成器 + 统一附件服务”。普通和敏感导出共用生成链路，敏感导出通过既有 `SENSITIVE_DATA_EXPORT` 系统任务桥接审批；所有创建、执行、查询和下载均实时复核功能开关、动态 RBAC、固定角色和对象关系。

**技术栈：** Spring Boot 3.4、JDK 17、MyBatis XML、Flyway、MySQL 8/H2、Apache POI SXSSF、React、TypeScript、Ant Design、Vitest。

**执行约束：** 当前是包含历史改动的共享工作区，不自动创建 Git 提交；只修改本计划列出的 V57 文件和必要的现有集成文件。不得连接或执行远程 MySQL、Redis、共享测试、预生产、生产、微信或对象存储环境。

---

### 任务 1：V57 数据库迁移与结构验收

**文件：**
- 新建：`server/src/main/resources/db/migration/V57__add_async_export_job.sql`
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

- [x] **步骤 1.1：先增加迁移失败测试**

在 `FlywayMigrationTest` 增加 `addsAsyncExportJobFoundationThroughV57()`，断言迁移版本为 57、存在 `sys_export_job` 和 `sys_export_job_event`、两张表主键为非自增 `BIGINT`、总主键表数量为 82，并校验开关、四项权限、角色最小授权、附件规则、外键、唯一约束和索引。

```java
assertThat(jdbcTemplate.queryForObject(
        "select count(*) from flyway_schema_history where version='57' and success=true",
        Integer.class)).isEqualTo(1);
assertThat(jdbcTemplate.queryForObject("""
        select count(*) from information_schema.tables
        where table_name in ('sys_export_job', 'sys_export_job_event')
        """, Integer.class)).isEqualTo(2);
assertThat(jdbcTemplate.queryForObject("""
        select count(*) from information_schema.columns
        where table_name in ('sys_export_job', 'sys_export_job_event')
          and column_name='id' and upper(data_type)='BIGINT' and is_identity='NO'
        """, Integer.class)).isEqualTo(2);
```

- [x] **步骤 1.2：运行测试并确认因 V57 不存在而失败**

运行：

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -Dtest=FlywayMigrationTest test
```

预期：`addsAsyncExportJobFoundationThroughV57` 失败，原因是版本 57 或新增表不存在。

- [x] **步骤 1.3：创建迁移脚本**

迁移必须包含：

```sql
CREATE TABLE sys_export_job (
    id BIGINT NOT NULL PRIMARY KEY,
    job_code VARCHAR(32) NOT NULL,
    export_type VARCHAR(32) NOT NULL,
    template_id BIGINT NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    template_version VARCHAR(32) NOT NULL,
    requester_id BIGINT NOT NULL,
    student_id BIGINT,
    system_task_id BIGINT,
    filter_snapshot TEXT NOT NULL,
    column_snapshot TEXT NOT NULL,
    scope_snapshot TEXT NOT NULL,
    mask_policy_snapshot TEXT NOT NULL,
    request_reason VARCHAR(500) NOT NULL,
    sensitive_flag TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    version_no BIGINT NOT NULL DEFAULT 0,
    result_file_id BIGINT,
    total_rows BIGINT NOT NULL DEFAULT 0,
    processed_rows BIGINT NOT NULL DEFAULT 0,
    failure_code VARCHAR(64),
    failure_message VARCHAR(500),
    request_source_hash VARCHAR(64) NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP,
    queued_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_export_job_code UNIQUE (job_code),
    CONSTRAINT uk_sys_export_job_system_task UNIQUE (system_task_id),
    CONSTRAINT uk_sys_export_job_result_file UNIQUE (result_file_id),
    CONSTRAINT fk_sys_export_job_template FOREIGN KEY (template_id) REFERENCES sys_import_export_template(id),
    CONSTRAINT fk_sys_export_job_requester FOREIGN KEY (requester_id) REFERENCES sys_user(id),
    CONSTRAINT fk_sys_export_job_student FOREIGN KEY (student_id) REFERENCES edu_student(id),
    CONSTRAINT fk_sys_export_job_system_task FOREIGN KEY (system_task_id) REFERENCES sys_system_task(id),
    CONSTRAINT fk_sys_export_job_result_file FOREIGN KEY (result_file_id) REFERENCES sys_file(id)
);
```

`sys_export_job_event` 只保存申请、提交审核、批准、驳回、领取、成功和失败事件。新增 `DATA_EXPORT`，新增 `EXPORT_JOB_READ`、`EXPORT_JOB_CREATE`、`EXPORT_SENSITIVE_SUBMIT`、`EXPORT_SENSITIVE_REVIEW`；`PARENT` 获得读和普通创建，`SYS_ADMIN` 获得读和敏感提交，`SYS_AUDITOR` 只获得敏感审核。新增 `EXPORT_JOB/REPORT_EXPORT` 的 `.xlsx` 附件规则。全部种子使用 `1874244142494646632` 起的连续 19 位数字。

- [x] **步骤 1.4：运行迁移测试并确认通过**

运行：`mvn -Dtest=FlywayMigrationTest test`

预期：全部迁移测试通过，Flyway 从 V1 连续执行至 V57。

### 任务 2：作业领域模型和 MyBatis 持久化

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/domain/ExportJobType.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/domain/ExportJobStatus.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/domain/ExportJobEventType.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/domain/ExportJobRecord.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/domain/ExportJobEventRecord.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/infrastructure/persistence/ExportJobMapper.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/infrastructure/persistence/ExportJobEventMapper.java`
- 新建：`server/src/main/resources/mapper/exportjob/ExportJobMapper.xml`
- 新建：`server/src/main/resources/mapper/exportjob/ExportJobEventMapper.xml`
- 新建：`server/src/test/java/com/lingdong/learning/exportjob/infrastructure/persistence/ExportJobPersistenceTest.java`

- [x] **步骤 2.1：先写状态与条件更新持久化测试**

测试插入普通与敏感作业、按申请人分页、按系统任务查询、`QUEUED + version_no` 单次领取、进度单调更新、终态条件更新和事件按时间正序读取。

```java
assertThat(jobMapper.claim(job.id(), 0L)).isEqualTo(1);
assertThat(jobMapper.claim(job.id(), 0L)).isZero();
assertThat(jobMapper.updateProgress(job.id(), 1L, 50L)).isEqualTo(1);
assertThat(eventMapper.findByJobId(job.id()))
        .extracting(ExportJobEventRecord::eventType)
        .containsExactly(ExportJobEventType.REQUESTED, ExportJobEventType.CLAIMED);
```

- [x] **步骤 2.2：实现枚举、记录和映射器**

状态严格为：

```java
public enum ExportJobStatus {
    PENDING_REVIEW, QUEUED, EXPORTING, SUCCEEDED, FAILED, REJECTED
}
```

`ExportJobMapper` 暴露 `insert`、`findById`、`findBySystemTaskId`、`findQueued`、`findPageByRequester`、`countByRequester`、`claim`、`updateProgress`、`queueAfterReview`、`rejectAfterReview`、`succeed`、`fail`。所有变更方法必须带当前状态和期望版本条件。

- [x] **步骤 2.3：运行持久化测试**

运行：`mvn -Dtest=ExportJobPersistenceTest test`

预期：插入、查询、领取、进度和终态幂等测试全部通过。

### 任务 3：模板占位符解析和流式 Excel 生成

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/template/ExportColumnDefinition.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/template/ExportTemplateDefinition.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/template/ExportTemplateParser.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/template/ExportWorkbookWriter.java`
- 新建：`server/src/test/java/com/lingdong/learning/exportjob/application/template/ExportTemplateParserTest.java`
- 新建：`server/src/test/java/com/lingdong/learning/exportjob/application/template/ExportWorkbookWriterTest.java`

- [x] **步骤 3.1：先写模板安全失败测试**

覆盖：首行 `${FIELD_CODE}`、重复占位符、未知列、缺少默认列、公式单元格、外部链接、空工作簿和非 `.xlsx` 内容。公式或外链必须抛出 `IllegalArgumentException("导出模板包含不允许的动态内容")`。

- [x] **步骤 3.2：实现只读解析器**

解析器使用 `WorkbookFactory.create`，不调用公式求值器；遍历所有工作表拒绝 `CellType.FORMULA`，检查外部链接集合为空，第一张工作表首行只接受完全匹配 `\$\{[A-Z][A-Z0-9_]{0,63}}` 的占位符。

```java
private static final Pattern PLACEHOLDER =
        Pattern.compile("^\\$\\{([A-Z][A-Z0-9_]{0,63})}$");
```

- [x] **步骤 3.3：先写流式生成测试**

用 5 行测试数据和每表 2 行配置验证生成 3 个数据工作表；验证选择列顺序、静态标题、样式复制、姓名脱敏、Excel 公式注入字符以单引号前缀写入、临时文件关闭后可删除。

- [x] **步骤 3.4：实现 `ExportWorkbookWriter`**

使用 `SXSSFWorkbook` 和临时文件输出，表头占一行，数据行按选定列写入。文本以 `= + - @` 开头时写入前增加 `'`；单元格最大 32767 字符，超限截断并记录受限警告，不创建公式单元格。

- [x] **步骤 3.5：运行模板与工作簿测试**

运行：`mvn -Dtest=ExportTemplateParserTest,ExportWorkbookWriterTest test`

预期：模板拒绝规则、动态列、分表和文件清理全部通过。

### 任务 4：导出适配器和实时访问边界

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/adapter/ExportDatasetAdapter.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/adapter/ExportAdapterRegistry.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/adapter/ExportRequestDefinition.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/adapter/ExportDataPage.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/adapter/GrowthPointLedgerExportAdapter.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/adapter/IamChangeAuditExportAdapter.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/infrastructure/persistence/GrowthPointExportMapper.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/infrastructure/persistence/IamAuditExportMapper.java`
- 新建：`server/src/main/resources/mapper/exportjob/GrowthPointExportMapper.xml`
- 新建：`server/src/main/resources/mapper/exportjob/IamAuditExportMapper.xml`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/ExportJobAccessService.java`
- 新建：`server/src/test/java/com/lingdong/learning/exportjob/application/ExportJobAccessServiceTest.java`
- 新建：`server/src/test/java/com/lingdong/learning/exportjob/application/adapter/GrowthPointLedgerExportAdapterTest.java`
- 新建：`server/src/test/java/com/lingdong/learning/exportjob/application/adapter/IamChangeAuditExportAdapterTest.java`

- [x] **步骤 4.1：先写访问边界测试**

覆盖三个开关、活动用户、`DENY` 优先、普通家长角色和主监护关系、系统管理员固定角色和 `IAM_AUDIT_READ`、系统审核员只审核元数据、跨申请人查询与下载拒绝。

- [x] **步骤 4.2：实现统一访问服务**

`ExportJobAccessService` 提供以下明确方法：

```java
void requireOrdinaryCreate(long userId, long studentId);
void requireSensitiveSubmit(long userId);
void requireSensitiveReview(long userId);
void requireExecution(ExportJobRecord job);
void requireOwnerRead(long userId, ExportJobRecord job);
void requireOwnerDownload(long userId, ExportJobRecord job);
void requireFeatures();
```

全部方法复用 `PermissionDecisionService`，角色使用 `UserRoleMapper`，用户状态使用 `UserMapper`，家长对象关系由 `GrowthPointQueryMapper.findPrimaryStudentsByParentUserId` 验证。

- [x] **步骤 4.3：先写适配器分页测试**

积分适配器测试 `student_id + occurred_at + id<=upperBound + id>cursor`；IAM 适配器测试组合筛选、`id<=upperBound + id>cursor`。两者均以 `id` 升序稳定分页，返回下一游标和受控列值。

- [x] **步骤 4.4：实现适配器和专用 MyBatis 查询**

适配器不得接受 SQL 字段名。`columns()` 返回固定白名单，`captureUpperBound()` 返回当前最大标识，`count()` 和 `fetchAfter()` 使用同一规范化筛选与上界。积分适配器只暴露规格中的十列；IAM 适配器只暴露九列，姓名使用统一姓氏脱敏函数。

- [x] **步骤 4.5：运行访问和适配器测试**

运行：`mvn -Dtest=ExportJobAccessServiceTest,GrowthPointLedgerExportAdapterTest,IamChangeAuditExportAdapterTest test`

预期：对象越权、角色越权、动态拒绝和游标边界全部通过。

### 任务 5：创建作业、敏感审批和不可变事件

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/CreateExportJobCommand.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/ExportJobApplicationService.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/ExportJobReviewService.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/ExportJobEventService.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/infrastructure/security/ExportSourceHasher.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/infrastructure/config/ExportJobProperties.java`
- 修改：`server/src/main/resources/application.yml`
- 新建：`server/src/test/java/com/lingdong/learning/exportjob/application/ExportJobApplicationServiceTest.java`
- 新建：`server/src/test/java/com/lingdong/learning/exportjob/application/ExportJobReviewServiceTest.java`
- 新建：`server/src/test/java/com/lingdong/learning/exportjob/infrastructure/security/ExportSourceHasherTest.java`

- [x] **步骤 5.1：先写普通与敏感创建失败测试**

覆盖默认 `EXPORT/REPORT` 模板缺失、停用、未知或重复列、原因空白、筛选时间倒置、普通类型由系统管理员提交、敏感类型由家长提交，以及快照 JSON 不含手机号、原始地址和存储键。

- [x] **步骤 5.2：实现创建事务**

普通导出创建 `QUEUED` 作业和 `REQUESTED` 事件。敏感导出在同一事务调用：

```java
SystemTask draft = systemTaskService.createDraft(new CreateSystemTaskCommand(
        requesterId,
        SystemTaskType.SENSITIVE_DATA_EXPORT,
        "权限变更日志导出",
        normalizedReason,
        ImpactScope.GLOBAL));
systemTaskService.submit(draft.id(), requesterId);
```

随后保存 `PENDING_REVIEW` 作业以及 `REQUESTED`、`REVIEW_SUBMITTED` 事件。模板通过 `findCurrentDefault("REPORT", TemplateType.EXPORT)` 读取并解析，四类快照使用 `ObjectMapper` 的固定记录类型序列化。

- [x] **步骤 5.3：实现来源地址 HMAC**

`ExportSourceHasher` 使用 `HmacSHA256` 和 `EXPORT_SOURCE_HMAC_SECRET`，输入为 `export-source:` 加规范化来源地址，密钥少于 32 字节时抛出中文配置异常。测试只注入专用测试密钥。

- [x] **步骤 5.4：先写审批状态机测试**

覆盖系统审核员批准、驳回意见必填、禁止自审、非 `SENSITIVE_DATA_EXPORT` 任务拒绝、任务与作业不匹配拒绝、重复审批冲突。批准后系统任务为 `APPROVED`、作业为 `QUEUED`；驳回后两者均为 `REJECTED`。

- [x] **步骤 5.5：实现审批事务和事件**

`ExportJobReviewService.approve` 与 `reject` 必须先查关联作业并校验类型及状态，再调用现有 `SystemTaskApplicationService`，随后用状态条件更新作业并追加单个不可变事件。

- [x] **步骤 5.6：运行创建、哈希和审批测试**

运行：`mvn -Dtest=ExportJobApplicationServiceTest,ExportJobReviewServiceTest,ExportSourceHasherTest test`

预期：普通和敏感状态机、快照和 HMAC 测试全部通过。

### 任务 6：异步领取、流式执行、附件补偿和系统任务生效

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/ExportJobClaimService.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/ExportJobExecutionService.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/ExportJobBatchService.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/infrastructure/scheduling/ExportJobSchedulingConfiguration.java`
- 新建：`server/src/test/java/com/lingdong/learning/exportjob/application/ExportJobClaimServiceTest.java`
- 新建：`server/src/test/java/com/lingdong/learning/exportjob/application/ExportJobExecutionServiceTest.java`
- 新建：`server/src/test/java/com/lingdong/learning/exportjob/application/ExportJobBatchServiceTest.java`

- [x] **步骤 6.1：先写并发领取失败测试**

同一 `QUEUED/version_no=0` 作业连续领取两次，只允许第一次转为 `EXPORTING/version_no=1`，并只写一个 `CLAIMED` 事件。

- [x] **步骤 6.2：实现条件领取**

`ExportJobClaimService.claim(id, expectedVersion)` 使用数据库条件更新，不使用 JVM 锁；领取后重新读取并验证状态。

- [x] **步骤 6.3：先写执行与补偿测试**

覆盖执行前权限失效、每批开关关闭、空结果、跨工作表、进度单调更新、附件保存、`EXPORT_JOB_RESULT/REPORT_EXPORT` 关系、结果文件唯一绑定、敏感任务仅成功后 `EFFECTIVE`、生成失败保留 `APPROVED`、附件保存后数据库失败时清理文件。

- [x] **步骤 6.4：实现执行服务**

执行顺序固定为：实时鉴权、解析快照、统计总数、创建临时文件、游标分页、每批检查开关并更新进度、关闭工作簿、统一附件保存、建立关系、事务写 `SUCCEEDED` 和 `SUCCEEDED` 事件、敏感任务标记 `EFFECTIVE`。异常时清理临时文件及未关联附件，将作业写为 `FAILED`，数据库只保存固定错误码和中性中文消息。

- [x] **步骤 6.5：实现有限批处理和调度器**

配置前缀为 `lingdong.export-job`：

```yaml
export-job:
  scheduling-enabled: ${EXPORT_JOB_SCHEDULING_ENABLED:true}
  batch-size: ${EXPORT_JOB_BATCH_SIZE:20}
  query-page-size: ${EXPORT_JOB_QUERY_PAGE_SIZE:500}
  sheet-max-rows: ${EXPORT_JOB_SHEET_MAX_ROWS:50000}
  cron: ${EXPORT_JOB_CRON:0 */1 * * * *}
  source-hmac-secret: ${EXPORT_SOURCE_HMAC_SECRET:}
```

`ExportJobBatchService` 每轮最多 100 项，单作业异常隔离，只记录作业标识和异常类型。

- [x] **步骤 6.6：运行异步执行测试**

运行：`mvn -Dtest=ExportJobClaimServiceTest,ExportJobExecutionServiceTest,ExportJobBatchServiceTest test`

预期：并发、进度、补偿、敏感生效时点和失败隔离全部通过。

### 任务 7：查询下载、REST 契约和 OpenAPI 安全

**文件：**
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/ExportJobQueryService.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/ExportJobOptionService.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/ExportJobView.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/ExportJobPage.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/ExportJobDetailView.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/application/ExportJobReviewView.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/web/ExportJobController.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/web/ExportJobReviewController.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/web/CreateExportJobRequest.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/web/ExportJobResponse.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/web/ExportJobPageResponse.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/web/ExportJobDetailResponse.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/web/ExportJobOptionsResponse.java`
- 新建：`server/src/main/java/com/lingdong/learning/exportjob/web/ExportJobReviewResponse.java`
- 新建：`server/src/test/java/com/lingdong/learning/exportjob/web/ExportJobControllerTest.java`
- 新建：`server/src/test/java/com/lingdong/learning/exportjob/web/ExportJobApiIntegrationTest.java`

- [x] **步骤 7.1：先写控制器和集成失败测试**

覆盖六个规格 API、分页 1 至 100、Bean Validation、19 位标识字符串序列化、安全下载文件名、非成功作业 404、审核员禁止下载、功能关闭后列表和下载均 409，以及响应不包含 `storageKey`、`requestSourceHash`、物理路径和原始筛选 JSON。

- [x] **步骤 7.2：实现查询、选项和下载服务**

本人列表固定以 `requester_id` 过滤。审核列表只返回 `PENDING_REVIEW` 敏感作业的申请人显示名、类型、范围摘要、原因和申请时间。下载先调用 `requireOwnerDownload`，再从统一附件服务读取；不暴露永久地址。

- [x] **步骤 7.3：实现 REST 控制器**

创建请求只接受 `exportType`、`studentId`、起止时间、事件类型、列编码和原因。控制器从 `HttpServletRequest` 取得来源地址后只传 HMAC 摘要，不把原始地址传入领域或持久层。审批端点使用 `@RequirePermission("EXPORT_SENSITIVE_REVIEW")`。

- [x] **步骤 7.4：运行接口与 OpenAPI 测试**

运行：`mvn -Dtest=ExportJobControllerTest,ExportJobApiIntegrationTest test`

预期：接口状态码、权限、对象范围、敏感字段排除和 OpenAPI 生成全部通过。

### 任务 8：Web 能力字段、独立导出中心和积分入口

**文件：**
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityController.java`
- 修改：`server/src/test/java/com/lingdong/learning/feature/web/PublicCapabilityControllerTest.java`
- 新建：`web/src/api/export-jobs.ts`
- 新建：`web/src/features/export-jobs/ExportJobManagementPage.tsx`
- 新建：`web/src/features/export-jobs/ExportJobManagementPage.test.tsx`
- 新建：`web/src/features/export-jobs/CreateExportJobModal.tsx`
- 新建：`web/src/features/export-jobs/ExportJobReviewPanel.tsx`
- 修改：`web/src/features/growth-points/GrowthPointPage.tsx`
- 修改：`web/src/features/growth-points/GrowthPointPage.test.tsx`
- 修改：`web/src/api/capability.ts`
- 修改：`web/src/app/App.tsx`
- 修改：`web/src/app/App.test.tsx`
- 修改：`web/src/styles/index.css`

- [x] **步骤 8.1：先写能力字段失败测试**

新增 `dataExportEnabled`。只有 `WEB` 且 `DATA_EXPORT`、`IMPORT_EXPORT_TEMPLATE_MANAGEMENT`、`ATTACHMENT_SERVICE` 同时启用时为 `true`；`MINIAPP` 始终为 `false`。

- [x] **步骤 8.2：实现公共能力并运行测试**

运行：`mvn -Dtest=PublicCapabilityControllerTest test`

预期：Web 组合开关和小程序隔离断言通过。

- [x] **步骤 8.3：先写 Web API 与页面失败测试**

覆盖普通台账、敏感提交、审核列表、批准驳回、进度展示、下载、列选择、模板未配置提示、积分页导出按钮、功能关闭、权限裁剪和 `/export-jobs` 直达跳转。

```ts
expect(canAccessExportJobs(parent, capabilities)).toBe(true)
expect(canAccessExportJobs(parent, { ...capabilities, dataExportEnabled: false })).toBe(false)
expect(screen.queryByRole('button', { name: '审核敏感导出' })).not.toBeInTheDocument()
```

- [x] **步骤 8.4：实现独立 API 和页面**

`ExportJobManagementPage` 使用标签页区分“我的导出”和“敏感导出审核”。图标按钮使用 lucide-react 的 `Download`、`Plus`、`Check`、`X`，并提供中文 Tooltip；表格只在自身容器横向滚动。390px 下筛选与弹窗单列，按钮和进度条尺寸稳定。

- [x] **步骤 8.5：接入路由、菜单与积分入口**

`canAccessExportJobs` 同时要求 `dataExportEnabled` 和 `EXPORT_JOB_READ` 或 `EXPORT_SENSITIVE_REVIEW`。积分页按钮只在家长拥有 `EXPORT_JOB_CREATE` 时显示，并预填当前活动主监护学生。功能关闭后菜单、按钮、页面和直达路由全部不可用。

- [x] **步骤 8.6：运行 Web 测试**

运行：`npm test -- --run`

预期：Web 全量测试通过，无未处理异步更新警告。

### 任务 9：端到端回归、视觉验证、端侧隔离和中文文档

**文件：**
- 修改：`README.md`
- 修改：`docs/design/00-设计文档体系与需求追溯-V1.0.md`
- 修改：`docs/design/01-功能详细设计-FSD-V1.0.md`
- 修改：`docs/design/03-系统架构设计-HLD-V1.0.md`
- 修改：`docs/design/04-数据库设计-V1.0.md`
- 修改：`docs/design/05-Flyway迁移规范-V1.0.md`
- 修改：`docs/design/06-API接口设计-V1.0.md`
- 修改：`docs/design/07-权限与安全设计-V1.0.md`
- 修改：`docs/design/09-统计口径与报表设计-V1.0.md`
- 修改：`docs/design/10-测试方案与验收用例-V1.0.md`
- 修改：`docs/design/11-部署运维与发布方案-V1.0.md`
- 修改：`docs/design/12-当前实现一致性核对-V1.0.md`
- 修改：`docs/superpowers/plans/2026-08-08-lingdong-learning-master-development.md`

- [x] **步骤 9.1：运行后端全量回归**

运行：`mvn test`

预期：全部测试通过，Flyway 连续迁移 57 个版本、82 张显式非自增主键表。

- [x] **步骤 9.2：运行 Web 全量构建**

运行：

```powershell
npm test -- --run
npm run build
```

预期：测试和 TypeScript/Vite 构建通过。

- [x] **步骤 9.3：运行 uni-app 隔离验证**

运行：

```powershell
npm run type-check
npm run build:h5
npm run build:mp-weixin
```

随后扫描 `miniapp/src`、H5 和微信产物，不得出现 `export-jobs`、`EXPORT_SENSITIVE_REVIEW`、`IAM_CHANGE_AUDIT` 或导出后台页面。

- [x] **步骤 9.4：执行 1280px 与 390px 视觉检查**

启动本地 Web，使用本地测试夹具检查普通创建、敏感提交、审核列表、进度和下载状态。确认无文本溢出、无控件遮挡、宽表只在表格区域滚动、移动端弹窗单列；检查浏览器控制台无错误。验证后删除临时夹具并停止服务器。

- [x] **步骤 9.5：更新全部中文设计与进度文档**

记录 V57 两张表、四项权限、三角色最小授权、两类适配器、系统任务生效时点、模板发布前置、功能开关、API、测试证据和未执行远程环境。下一迁移统一写为 V58，不宣称 PDF、十四类报表、真实消息、云存储或生产 UAT 已完成。

- [x] **步骤 9.6：执行最终静态检查**

运行：

```powershell
git diff --check
# 使用本机安全扫描规则检查已知微信、数据库和 Redis 明文凭据，命令及结果不得回写具体密钥。
```

预期：`git diff --check` 无错误，已知微信、数据库和 Redis 明文密钥扫描无结果。
