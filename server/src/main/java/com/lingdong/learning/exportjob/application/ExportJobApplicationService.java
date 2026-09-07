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

        ImportExportTemplateRecord template = requireDefaultTemplate();
        AttachmentContentView templateContent = contentService.read(template.fileId());
        ExportTemplateDefinition definition = templateParser.parse(
                templateContent.content(), adapter.columns(), command.selectedColumns());
        ExportRequestDefinition request = new ExportRequestDefinition(
                command.requesterId(), command.studentId(), command.startedAt(),
                command.endedAt(), command.eventType());
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
                json(new ExportFilterSnapshot(command.startedAt(), command.endedAt(), command.eventType())),
                json(definition.columns().stream()
                        .map(column -> new ExportColumnSnapshot(column.code(), column.header())).toList()),
                json(new ExportScopeSnapshot(command.studentId(), upperBound)),
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
        if (adapter.sensitive()) {
            if (command.studentId() != null) {
                throw new IllegalArgumentException("权限日志导出不能指定学生");
            }
            accessService.requireSensitiveSubmit(command.requesterId());
        } else {
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

    private ImportExportTemplateRecord requireDefaultTemplate() {
        ImportExportTemplateRecord template = templateMapper.findCurrentDefault("REPORT", TemplateType.EXPORT);
        if (template == null) {
            throw new IllegalStateException("导出模板未配置");
        }
        if (template.status() != ImportExportTemplateStatus.ENABLED
                || !Boolean.TRUE.equals(template.defaultTemplate())
                || template.templateType() != TemplateType.EXPORT
                || !"REPORT".equals(template.moduleCode())) {
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
}
