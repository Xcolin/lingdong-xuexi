package com.lingdong.learning.exportjob.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.audit.application.CreateSystemTaskCommand;
import com.lingdong.learning.audit.application.ImpactScope;
import com.lingdong.learning.audit.application.SystemTask;
import com.lingdong.learning.audit.application.SystemTaskApplicationService;
import com.lingdong.learning.audit.application.SystemTaskType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.exportjob.application.adapter.ExportAdapterRegistry;
import com.lingdong.learning.exportjob.application.adapter.ExportDatasetAdapter;
import com.lingdong.learning.exportjob.application.adapter.ExportRequestDefinition;
import com.lingdong.learning.exportjob.application.template.ExportTemplateDefinition;
import com.lingdong.learning.exportjob.application.template.ExportTemplateParser;
import com.lingdong.learning.exportjob.domain.ExportJobEventType;
import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobMapper;
import com.lingdong.learning.exportjob.infrastructure.security.ExportSourceHasher;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateRecord;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateStatus;
import com.lingdong.learning.templateconfig.domain.TemplateType;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 创建普通或敏感导出作业，并固化模板、筛选、范围与脱敏快照。 */
@Service
public class ExportJobApplicationService {
    private final ExportJobAccessService accessService;
    private final ExportAdapterRegistry adapterRegistry;
    private final ImportExportTemplateMapper templateMapper;
    private final ManagedAttachmentContentService contentService;
    private final ExportTemplateParser templateParser;
    private final ExportJobMapper jobMapper;
    private final ExportJobEventService eventService;
    private final SystemTaskApplicationService systemTaskService;
    private final IdGenerator idGenerator;
    private final ExportSourceHasher sourceHasher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ExportJobApplicationService(
            ExportJobAccessService accessService,
            ExportAdapterRegistry adapterRegistry,
            ImportExportTemplateMapper templateMapper,
            ManagedAttachmentContentService contentService,
            ExportTemplateParser templateParser,
            ExportJobMapper jobMapper,
            ExportJobEventService eventService,
            SystemTaskApplicationService systemTaskService,
            IdGenerator idGenerator,
            ExportSourceHasher sourceHasher,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.accessService = accessService;
        this.adapterRegistry = adapterRegistry;
        this.templateMapper = templateMapper;
        this.contentService = contentService;
        this.templateParser = templateParser;
        this.jobMapper = jobMapper;
        this.eventService = eventService;
        this.systemTaskService = systemTaskService;
        this.idGenerator = idGenerator;
        this.sourceHasher = sourceHasher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public ExportJobRecord create(CreateExportJobCommand command) {
        Objects.requireNonNull(command, "导出请求不能为空");
        if (command.requesterId() == null || command.exportType() == null) {
            throw new IllegalArgumentException("申请人和导出类型不能为空");
        }
        if (command.startedAt() != null && command.endedAt() != null
                && command.startedAt().isAfter(command.endedAt())) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        String reason = required(command.reason(), "导出原因", 500);
        ExportDatasetAdapter adapter = adapterRegistry.require(command.exportType());
        requireKnownSensitivity(command.exportType(), adapter);
        requireCreateAccess(command, adapter);

        String studentTaskSource = optionalCode(command.studentTaskSource(), 32);
        String studentTaskStatus = optionalCode(command.studentTaskStatus(), 32);
        String outputFormat = command.outputFormat() == null ? "XLSX" : optionalCode(command.outputFormat(), 8);
        if (!List.of("XLSX", "PDF").contains(outputFormat)) throw new IllegalArgumentException("导出格式必须为 XLSX 或 PDF");
        if (studentTaskSource != null) com.lingdong.learning.learningtask.domain.LearningTaskSourceType.valueOf(studentTaskSource);
        if (studentTaskStatus != null) com.lingdong.learning.learningtask.domain.TaskAssignmentStatus.valueOf(studentTaskStatus);
        var studentTaskScope = command.exportType() == ExportJobType.STUDENT_TASK_REPORT ? accessService.studentTasks().freeze(command) : null;
        String orgStatClassId = command.orgStatClassId();
        if (orgStatClassId != null) {
            if (!orgStatClassId.matches("[1-9][0-9]{18}")) throw new IllegalArgumentException("机构统计班级必须为19位字符串雪花标识");
            try { Long.parseLong(orgStatClassId); }
            catch (NumberFormatException exception) { throw new IllegalArgumentException("机构统计班级雪花标识超出范围"); }
        }
        var orgStatScope = command.exportType() == ExportJobType.ORGANIZATION_TASK_STATISTICS
                ? accessService.orgTaskStats().freeze(command) : null;
        String attClassId = command.attClassId();
        if (attClassId != null) {
            if (!attClassId.matches("[1-9][0-9]{18}")) throw new IllegalArgumentException("考勤班级必须为19位字符串雪花标识");
            try { Long.parseLong(attClassId); }
            catch (NumberFormatException exception) { throw new IllegalArgumentException("考勤班级雪花标识超出范围"); }
        }
        var attScope = command.exportType() == ExportJobType.ATTENDANCE_LEDGER
                ? accessService.attendanceLedger().freeze(command) : null;
        String dictionaryType = optionalCode(command.dictionaryTypeCode(), 64);
        String dictionaryStatus = optionalCode(command.dictionaryStatus(), 16);
        if (dictionaryStatus != null && !List.of("ENABLED", "DISABLED").contains(dictionaryStatus)) {
            throw new IllegalArgumentException("字典项状态必须为 ENABLED 或 DISABLED");
        }
        String templateType = optionalCode(command.templateType(), 16);
        String templateModule = optionalCode(command.templateModuleCode(), 64);
        String templateStatus = optionalCode(command.templateStatus(), 16);
        if (templateType != null && !List.of("IMPORT", "EXPORT").contains(templateType)) {
            throw new IllegalArgumentException("模板类型必须为 IMPORT 或 EXPORT");
        }
        if (templateStatus != null && !List.of("ENABLED", "DISABLED").contains(templateStatus)) {
            throw new IllegalArgumentException("模板状态必须为 ENABLED 或 DISABLED");
        }
        String interfaceCaller = command.interfaceCallerName() == null || command.interfaceCallerName().isBlank()
                ? null : required(command.interfaceCallerName(), "调用方", 100);
        String interfaceStatus = optionalCode(command.interfaceStatus(), 16);
        if (interfaceStatus != null && !List.of("ENABLED", "DISABLED").contains(interfaceStatus)) {
            throw new IllegalArgumentException("接口服务状态必须为 ENABLED 或 DISABLED");
        }
        String interfaceOwner = command.interfaceOwnerId();
        if (interfaceOwner != null) {
            if (!interfaceOwner.matches("[1-9][0-9]{18}")) {
                throw new IllegalArgumentException("接口责任人必须为19位字符串雪花标识");
            }
            try { Long.parseLong(interfaceOwner); }
            catch (NumberFormatException exception) {
                throw new IllegalArgumentException("接口责任人雪花标识超出范围");
            }
        }
        String cacheDomain = optionalCode(command.cacheDomain(), 32);
        String cacheStatus = optionalCode(command.cacheStatus(), 16);
        if (cacheDomain != null) com.lingdong.learning.cache.domain.CacheDomain.valueOf(cacheDomain);
        if (cacheStatus != null) com.lingdong.learning.cache.domain.CacheOperationStatus.valueOf(cacheStatus);
        String systemTaskType = optionalCode(command.systemTaskType(), 64);
        String systemTaskStatus = optionalCode(command.systemTaskStatus(), 32);
        if (systemTaskType != null) SystemTaskType.valueOf(systemTaskType);
        if (systemTaskStatus != null) com.lingdong.learning.audit.application.SystemTaskStatus.valueOf(systemTaskStatus);
        String rewardExchangeStatus = optionalCode(command.rewardExchangeStatus(), 32);
        if (rewardExchangeStatus != null) com.lingdong.learning.growthpoint.domain.GrowthRewardExchangeStatus.valueOf(rewardExchangeStatus);
        String attachmentModule = optionalCode(command.attachmentModuleCode(), 64);
        String attachmentCategory = optionalCode(command.attachmentFileCategory(), 64);
        String attachmentUploader = command.attachmentUploaderId();
        if (attachmentUploader != null && attachmentUploader.isBlank()) attachmentUploader = null;
        if (attachmentUploader != null) {
            if (!attachmentUploader.matches("[1-9][0-9]{18}")) throw new IllegalArgumentException("上传人必须为19位字符串雪花标识");
            try { Long.parseLong(attachmentUploader); }
            catch (NumberFormatException exception) { throw new IllegalArgumentException("上传人雪花标识超出范围"); }
        }
        String exceptionType = optionalCode(command.exceptionType(), 32);
        String exceptionStatus = optionalCode(command.exceptionStatus(), 32);
        if (exceptionType != null) com.lingdong.learning.exceptionreport.domain.ExceptionReportType.valueOf(exceptionType);
        if (exceptionStatus != null) com.lingdong.learning.exceptionreport.domain.ExceptionReportStatus.valueOf(exceptionStatus);
        String exceptionClassId = command.exceptionClassId();
        Long selectedClassId = null;
        if (exceptionClassId != null) {
            if (!exceptionClassId.matches("[1-9][0-9]{18}")) throw new IllegalArgumentException("异常班级必须为19位字符串雪花标识");
            try { selectedClassId = Long.valueOf(exceptionClassId); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("异常班级雪花标识超出范围"); }
        }
        var exceptionScope = command.exportType() == ExportJobType.EXCEPTION_REPORT_LEDGER
                ? accessService.requireExceptionReportExport(command.requesterId()) : null;
        List<Long> exceptionClassIds = exceptionScope == null ? null : exceptionScope.classIds();
        if (selectedClassId != null) {
            if (exceptionClassIds == null || !exceptionClassIds.contains(selectedClassId))
                throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("当前账号无权导出该班级异常报备");
            exceptionClassIds = List.of(selectedClassId);
        }
        var taskScope = command.exportType() == ExportJobType.SYSTEM_TASK_LEDGER
                ? accessService.requireSystemTaskExport(command.requesterId()) : null;
        ImportExportTemplateRecord template = requireDefaultTemplate(command.exportType());
        AttachmentContentView templateContent = contentService.read(template.fileId());
        ExportTemplateDefinition definition = templateParser.parse(
                templateContent.content(), adapter.columns(), command.selectedColumns());
        ExportRequestDefinition request = new ExportRequestDefinition(
                command.requesterId(), command.studentId(), command.startedAt(),
                command.endedAt(), command.eventType(), dictionaryType, dictionaryStatus,
                templateType, templateModule, templateStatus, interfaceCaller, interfaceStatus,
                interfaceOwner == null ? null : Long.valueOf(interfaceOwner), cacheDomain, cacheStatus,
                systemTaskType, systemTaskStatus, taskScope == null ? null : taskScope.auditor(),
                taskScope == null ? null : taskScope.types(), rewardExchangeStatus, exceptionType, exceptionStatus,
                exceptionScope == null ? null : exceptionScope.teacherOnly(), exceptionClassIds, attachmentModule, attachmentUploader, attachmentCategory, studentTaskSource, studentTaskStatus, studentTaskScope == null ? null : studentTaskScope.ids(),
                orgStatScope == null ? null : orgStatScope.ids(), attScope == null ? null : attScope.ids());
        long upperBound = adapter.captureUpperBound(request);
        LocalDateTime now = LocalDateTime.now(clock);
        long jobId = idGenerator.nextId();
        Long systemTaskId = adapter.sensitive() ? createAndSubmitSystemTask(command.requesterId(), reason) : null;
        ExportJobStatus status = adapter.sensitive()
                ? ExportJobStatus.PENDING_REVIEW : ExportJobStatus.QUEUED;
        ExportJobRecord job = new ExportJobRecord(
                jobId, "EXP-" + jobId, command.exportType(), template.id(),
                template.templateName(), template.version(), command.requesterId(),
                command.studentId(), systemTaskId,
                json(new ExportFilterSnapshot(command.startedAt(), command.endedAt(), command.eventType(),
                        dictionaryType, dictionaryStatus, templateType, templateModule, templateStatus,
                        interfaceCaller, interfaceStatus, interfaceOwner, cacheDomain, cacheStatus, systemTaskType, systemTaskStatus, rewardExchangeStatus, exceptionClassId, exceptionType, exceptionStatus, attachmentModule, attachmentUploader, attachmentCategory, studentTaskSource, studentTaskStatus, outputFormat, orgStatClassId, attClassId)),
                json(definition.columns().stream()
                        .map(column -> new ExportColumnSnapshot(column.code(), column.header())).toList()),
                json(new ExportScopeSnapshot(command.studentId(), upperBound, taskScope == null ? null : taskScope.auditor(),
                        taskScope == null ? null : taskScope.types(), exceptionScope == null ? null : exceptionScope.teacherOnly(), exceptionClassIds, studentTaskScope == null ? null : studentTaskScope.role(), studentTaskScope == null ? null : studentTaskScope.ids(),
                        orgStatScope == null ? null : orgStatScope.role(), orgStatScope == null ? null : orgStatScope.ids(),
                        attScope == null ? null : attScope.role(), attScope == null ? null : attScope.ids())),
                json(new ExportMaskPolicySnapshot("FAMILY_NAME_STAR", 1)),
                reason, adapter.sensitive(), status, 0L, null, 0L, 0L, null, null,
                sourceHasher.hash(command.requestSource()), now, null,
                adapter.sensitive() ? null : now, null, null, now, now);
        if (jobMapper.insert(job) != 1) {
            throw new IllegalStateException("导出作业保存失败");
        }
        eventService.record(jobId, ExportJobEventType.REQUESTED, command.requesterId(),
                adapter.sensitive() ? "已申请敏感导出" : "已申请普通导出");
        if (adapter.sensitive()) {
            eventService.record(jobId, ExportJobEventType.REVIEW_SUBMITTED,
                    command.requesterId(), "已提交系统审核");
        }
        return job;
    }

    private void requireCreateAccess(CreateExportJobCommand command, ExportDatasetAdapter adapter) {
        if (command.exportType() != ExportJobType.STUDENT_TASK_REPORT
                && (command.studentTaskSource() != null || command.studentTaskStatus() != null)) {
            throw new IllegalArgumentException("当前导出类型不支持学生任务筛选");
        }
        if (command.exportType() != ExportJobType.STUDENT_TASK_REPORT
                && command.exportType() != ExportJobType.ORGANIZATION_TASK_STATISTICS
                && command.exportType() != ExportJobType.ATTENDANCE_LEDGER
                && command.outputFormat() != null && !"XLSX".equals(command.outputFormat())) {
            throw new IllegalArgumentException("当前导出类型不支持 PDF 格式");
        }
        if (command.orgStatClassId() != null && command.exportType() != ExportJobType.ORGANIZATION_TASK_STATISTICS) {
            throw new IllegalArgumentException("当前导出类型不支持机构统计筛选");
        }
        if (command.attClassId() != null && command.exportType() != ExportJobType.ATTENDANCE_LEDGER) {
            throw new IllegalArgumentException("当前导出类型不支持考勤班级筛选");
        }
        if (command.exportType() != ExportJobType.ATTACHMENT_LEDGER
                && (command.attachmentModuleCode() != null || command.attachmentUploaderId() != null || command.attachmentFileCategory() != null)) {
            throw new IllegalArgumentException("当前导出类型不支持附件筛选");
        }

        if (command.exportType() != ExportJobType.EXCEPTION_REPORT_LEDGER
                && (command.exceptionClassId() != null || command.exceptionType() != null || command.exceptionStatus() != null)) {
            throw new IllegalArgumentException("当前导出类型不支持异常报备筛选");
        }
        if (command.exportType() != ExportJobType.REWARD_EXCHANGE_LEDGER && command.rewardExchangeStatus() != null) {
            throw new IllegalArgumentException("当前导出类型不支持奖励兑换筛选");
        }
        if (command.exportType() != ExportJobType.SYSTEM_TASK_LEDGER
                && (command.systemTaskType() != null || command.systemTaskStatus() != null)) {
            throw new IllegalArgumentException("当前导出类型不支持系统任务筛选");
        }
        if (command.exportType() != ExportJobType.CACHE_OPERATION_LOG && (command.cacheDomain() != null || command.cacheStatus() != null)) {
            throw new IllegalArgumentException("当前导出类型不支持缓存筛选");
        }
        if (command.exportType() != ExportJobType.INTERFACE_SERVICE_LEDGER
                && (command.interfaceCallerName() != null || command.interfaceStatus() != null || command.interfaceOwnerId() != null)) {
            throw new IllegalArgumentException("当前导出类型不支持接口服务筛选");
        }
        if (command.exportType() != ExportJobType.TEMPLATE_LEDGER
                && (command.templateType() != null || command.templateModuleCode() != null || command.templateStatus() != null)) {
            throw new IllegalArgumentException("当前导出类型不支持模板筛选");
        }
        if (command.exportType() == ExportJobType.DICTIONARY_LEDGER) {
            if (command.studentId() != null || command.eventType() != null) {
                throw new IllegalArgumentException("字典台账不能指定学生或权限事件类型");
            }
            accessService.requireDictionaryExport(command.requesterId());
            return;
        }
        if (command.dictionaryTypeCode() != null || command.dictionaryStatus() != null) {
            throw new IllegalArgumentException("当前导出类型不支持字典筛选");
        }
        if (command.exportType() == ExportJobType.STUDENT_TASK_REPORT) {
            if (command.eventType() != null) throw new IllegalArgumentException("任务报表不能指定权限事件");
            accessService.studentTasks().require(command.requesterId());
            return;
        }
        if (command.exportType() == ExportJobType.ORGANIZATION_TASK_STATISTICS) {
            if (command.studentId() != null || command.eventType() != null) throw new IllegalArgumentException("机构任务统计不能指定学生或权限事件");
            accessService.orgTaskStats().require(command.requesterId());
            return;
        }
        if (command.exportType() == ExportJobType.ATTENDANCE_LEDGER) {
            if (command.attClassId() != null && command.studentId() != null) {
                throw new IllegalArgumentException("考勤台账不能同时指定班级和学生");
            }
            if (command.eventType() != null) throw new IllegalArgumentException("考勤台账不能指定权限事件");
            var visibility = accessService.attendanceLedger().require(command.requesterId());
            boolean staff = "ORGANIZATION".equals(visibility.mode()) || "TEACHER".equals(visibility.mode());
            if (command.attClassId() != null) {
                if (!staff) throw new IllegalArgumentException("家庭身份不能指定考勤班级");
            } else if (command.studentId() != null) {
                if (staff) throw new IllegalArgumentException("教师或机构身份不能指定学生");
                accessService.attendanceLedger().requireFamily(command.requesterId(), command.studentId());
            } else if (!staff) {
                throw new IllegalArgumentException("家庭身份必须指定导出学生");
            }
            return;
        }
        if (command.exportType() == ExportJobType.ATTACHMENT_LEDGER) {
            if (command.studentId() != null || command.eventType() != null) throw new IllegalArgumentException("附件台账不能指定学生或权限事件");
            accessService.requireAttachmentLedgerExport(command.requesterId());
            return;
        }
        if (command.exportType() == ExportJobType.EXCEPTION_REPORT_LEDGER) {
            if (command.studentId() != null || command.eventType() != null) throw new IllegalArgumentException("异常报备台账不能指定学生或权限事件");
            accessService.requireExceptionReportExport(command.requesterId());
            return;
        }
        if (command.exportType() == ExportJobType.SYSTEM_TASK_LEDGER) {
            if (command.studentId() != null || command.eventType() != null) throw new IllegalArgumentException("系统任务台账不能指定学生或权限事件");
            accessService.requireSystemTaskExport(command.requesterId());
            return;
        }
        if (command.exportType() == ExportJobType.CACHE_OPERATION_LOG) {
            if (command.studentId() != null || command.eventType() != null) throw new IllegalArgumentException("缓存日志不能指定学生或权限事件");
            accessService.requireCacheExport(command.requesterId());
            return;
        }
        if (command.exportType() == ExportJobType.INTERFACE_SERVICE_LEDGER) {
            if (command.studentId() != null || command.eventType() != null) {
                throw new IllegalArgumentException("接口台账不能指定学生或权限事件类型");
            }
            accessService.requireInterfaceExport(command.requesterId());
            return;
        }
        if (command.exportType() == ExportJobType.TEMPLATE_LEDGER) {
            if (command.studentId() != null || command.eventType() != null) {
                throw new IllegalArgumentException("模板台账不能指定学生或权限事件类型");
            }
            accessService.requireTemplateExport(command.requesterId());
            return;
        }
        if (command.exportType() == ExportJobType.REWARD_EXCHANGE_LEDGER) {
            if (command.studentId() == null || command.eventType() != null) throw new IllegalArgumentException("奖励兑换导出必须指定学生且不能指定权限事件");
            accessService.requireRewardExchangeExport(command.requesterId(), command.studentId());
            return;
        }
        if (adapter.sensitive()) {
            if (command.studentId() != null) {
                throw new IllegalArgumentException("权限日志导出不能指定学生");
            }
            accessService.requireSensitiveSubmit(command.requesterId());
        } else {
            if (command.studentId() == null) {
                throw new IllegalArgumentException("积分明细导出必须指定学生");
            }
            if (command.eventType() != null) {
                throw new IllegalArgumentException("积分明细导出不能指定权限事件类型");
            }
            accessService.requireOrdinaryCreate(command.requesterId(), command.studentId());
        }
    }

    private void requireKnownSensitivity(ExportJobType type, ExportDatasetAdapter adapter) {
        boolean expected = type == ExportJobType.IAM_CHANGE_AUDIT;
        if (adapter.type() != type || adapter.sensitive() != expected) {
            throw new IllegalStateException("导出适配器类型或安全级别配置不一致");
        }
    }

    private ImportExportTemplateRecord requireDefaultTemplate(ExportJobType type) {
        ImportExportTemplateRecord template = templateMapper.findCurrentDefault(type.templateModule(), TemplateType.EXPORT);
        if (template == null) {
            throw new IllegalStateException("导出模板未配置");
        }
        if (template.status() != ImportExportTemplateStatus.ENABLED
                || !Boolean.TRUE.equals(template.defaultTemplate())
                || template.templateType() != TemplateType.EXPORT
                || !type.templateModule().equals(template.moduleCode())) {
            throw new IllegalStateException("默认导出模板不可用");
        }
        return template;
    }

    private Long createAndSubmitSystemTask(Long requesterId, String reason) {
        SystemTask task = systemTaskService.createDraft(new CreateSystemTaskCommand(
                requesterId, SystemTaskType.SENSITIVE_DATA_EXPORT,
                "权限变更日志导出", reason, ImpactScope.GLOBAL));
        systemTaskService.submit(task.id(), requesterId);
        return task.id();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("导出作业快照序列化失败", exception);
        }
    }

    private String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + "长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private String optionalCode(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.length() > maxLength || !normalized.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("字典筛选编码格式不合法");
        }
        return normalized;
    }
}
