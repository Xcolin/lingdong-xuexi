package com.lingdong.learning.exportjob.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.audit.application.ImpactScope;
import com.lingdong.learning.audit.application.SystemTask;
import com.lingdong.learning.audit.application.SystemTaskApplicationService;
import com.lingdong.learning.audit.application.SystemTaskStatus;
import com.lingdong.learning.audit.application.SystemTaskType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.exportjob.application.adapter.ExportAdapterRegistry;
import com.lingdong.learning.exportjob.application.adapter.ExportDatasetAdapter;
import com.lingdong.learning.exportjob.application.template.ExportColumnDefinition;
import com.lingdong.learning.exportjob.application.template.ExportTemplateDefinition;
import com.lingdong.learning.exportjob.application.template.ExportTemplateParser;
import com.lingdong.learning.exportjob.domain.ExportJobEventType;
import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobMapper;
import com.lingdong.learning.exportjob.infrastructure.security.ExportSourceHasher;
import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateRecord;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateStatus;
import com.lingdong.learning.templateconfig.domain.TemplateType;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportJobApplicationServiceTest {
    private static final long REQUESTER_ID = 1874244142494646960L;
    private static final long STUDENT_ID = 1874244142494646961L;
    private static final long TEMPLATE_ID = 1874244142494646962L;
    private static final long FILE_ID = 1874244142494646963L;
    private static final long JOB_ID = 1874244142494646964L;
    private static final long TASK_ID = 1874244142494646965L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 3, 11, 0);

    private ExportJobAccessService accessService;
    private ExportAdapterRegistry adapterRegistry;
    private ExportDatasetAdapter adapter;
    private ImportExportTemplateMapper templateMapper;
    private ManagedAttachmentContentService contentService;
    private ExportTemplateParser templateParser;
    private ExportJobMapper jobMapper;
    private ExportJobEventService eventService;
    private SystemTaskApplicationService systemTaskService;
    private ExportJobApplicationService service;

    @BeforeEach
    void setUp() {
        accessService = mock(ExportJobAccessService.class);
        adapterRegistry = mock(ExportAdapterRegistry.class);
        adapter = mock(ExportDatasetAdapter.class);
        templateMapper = mock(ImportExportTemplateMapper.class);
        contentService = mock(ManagedAttachmentContentService.class);
        templateParser = mock(ExportTemplateParser.class);
        jobMapper = mock(ExportJobMapper.class);
        eventService = mock(ExportJobEventService.class);
        systemTaskService = mock(SystemTaskApplicationService.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn(JOB_ID);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        Clock clock = Clock.fixed(Instant.parse("2026-09-03T03:00:00Z"), ZoneId.of("Asia/Shanghai"));
        service = new ExportJobApplicationService(
                accessService, adapterRegistry, templateMapper, contentService, templateParser,
                jobMapper, eventService, systemTaskService, idGenerator,
                new ExportSourceHasher("0123456789abcdef0123456789abcdef"), objectMapper, clock);

        when(templateMapper.findCurrentDefault("REPORT", TemplateType.EXPORT)).thenReturn(template());
        when(contentService.read(FILE_ID)).thenReturn(new AttachmentContentView(
                "report.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3}));
        when(templateParser.parse(any(), any(), any())).thenReturn(new ExportTemplateDefinition(
                List.of(new ExportColumnDefinition("OCCURRED_AT", "发生时间", true)),
                java.util.Map.of("OCCURRED_AT", 0)));
        when(adapter.captureUpperBound(any())).thenReturn(1874244142494646999L);
        when(jobMapper.insert(any())).thenReturn(1);
    }

    @Test
    void createsOrdinaryQueuedJobAndPersistsOnlyControlledSnapshots() {
        when(adapterRegistry.require(ExportJobType.GROWTH_POINT_LEDGER)).thenReturn(adapter);
        when(adapter.type()).thenReturn(ExportJobType.GROWTH_POINT_LEDGER);
        when(adapter.sensitive()).thenReturn(false);
        when(adapter.columns()).thenReturn(List.of(
                new ExportColumnDefinition("OCCURRED_AT", "发生时间", true)));

        ExportJobRecord created = service.create(new CreateExportJobCommand(
                REQUESTER_ID, ExportJobType.GROWTH_POINT_LEDGER, STUDENT_ID,
                NOW.minusDays(1), NOW, null, List.of("OCCURRED_AT"),
                "  导出本周积分  ", "13800000000@192.168.1.10/storage/private-key"));

        assertThat(created.status()).isEqualTo(ExportJobStatus.QUEUED);
        assertThat(created.id().toString()).hasSize(19);
        assertThat(created.jobCode()).isEqualTo("EXP-" + JOB_ID);
        assertThat(created.queuedAt()).isEqualTo(NOW);
        assertThat(created.requestReason()).isEqualTo("导出本周积分");
        assertThat(created.requestSourceHash()).hasSize(64)
                .doesNotContain("192.168", "13800000000", "private-key");
        assertThat(created.filterSnapshot() + created.columnSnapshot()
                + created.scopeSnapshot() + created.maskPolicySnapshot())
                .doesNotContain("192.168", "13800000000", "private-key")
                .contains("1874244142494646999", "OCCURRED_AT", "FAMILY_NAME_STAR");
        verify(accessService).requireOrdinaryCreate(REQUESTER_ID, STUDENT_ID);
        verify(eventService).record(JOB_ID, ExportJobEventType.REQUESTED, REQUESTER_ID, "已申请普通导出");
        verify(systemTaskService, never()).createDraft(any());
    }

    @Test
    void createsSensitivePendingReviewJobAndSubmitsSystemTask() {
        when(adapterRegistry.require(ExportJobType.IAM_CHANGE_AUDIT)).thenReturn(adapter);
        when(adapter.type()).thenReturn(ExportJobType.IAM_CHANGE_AUDIT);
        when(adapter.sensitive()).thenReturn(true);
        when(adapter.columns()).thenReturn(List.of(
                new ExportColumnDefinition("OCCURRED_AT", "发生时间", true)));
        SystemTask task = systemTask(SystemTaskStatus.DRAFT);
        when(systemTaskService.createDraft(any())).thenReturn(task);
        when(systemTaskService.submit(TASK_ID, REQUESTER_ID))
                .thenReturn(systemTask(SystemTaskStatus.PENDING_REVIEW));

        ExportJobRecord created = service.create(new CreateExportJobCommand(
                REQUESTER_ID, ExportJobType.IAM_CHANGE_AUDIT, null,
                null, null, IamChangeAuditEventType.USER_STATUS_CHANGE,
                List.of("OCCURRED_AT"), "安全审计复核", "10.0.0.8"));

        assertThat(created.status()).isEqualTo(ExportJobStatus.PENDING_REVIEW);
        assertThat(created.systemTaskId()).isEqualTo(TASK_ID);
        assertThat(created.sensitive()).isTrue();
        assertThat(created.queuedAt()).isNull();
        verify(accessService).requireSensitiveSubmit(REQUESTER_ID);
        verify(systemTaskService).submit(TASK_ID, REQUESTER_ID);
        verify(eventService).record(JOB_ID, ExportJobEventType.REQUESTED, REQUESTER_ID, "已申请敏感导出");
        verify(eventService).record(JOB_ID, ExportJobEventType.REVIEW_SUBMITTED, REQUESTER_ID, "已提交系统审核");
    }

    @Test
    void rejectsMissingTemplateInvalidTimeAndParserFailureBeforeInsert() {
        when(adapterRegistry.require(ExportJobType.GROWTH_POINT_LEDGER)).thenReturn(adapter);
        when(adapter.type()).thenReturn(ExportJobType.GROWTH_POINT_LEDGER);
        when(adapter.sensitive()).thenReturn(false);
        when(adapter.columns()).thenReturn(List.of(
                new ExportColumnDefinition("OCCURRED_AT", "发生时间", true)));
        when(templateMapper.findCurrentDefault("REPORT", TemplateType.EXPORT)).thenReturn(null);
        CreateExportJobCommand command = ordinaryCommand();
        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("模板未配置");

        assertThatThrownBy(() -> service.create(new CreateExportJobCommand(
                REQUESTER_ID, ExportJobType.GROWTH_POINT_LEDGER, STUDENT_ID,
                NOW, NOW.minusDays(1), null, List.of("OCCURRED_AT"), "测试", "local")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("开始时间");

        when(templateMapper.findCurrentDefault("REPORT", TemplateType.EXPORT)).thenReturn(template());
        when(templateParser.parse(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("导出字段不能重复选择：OCCURRED_AT"));
        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("重复");
        verify(jobMapper, never()).insert(any());
    }

    private CreateExportJobCommand ordinaryCommand() {
        return new CreateExportJobCommand(
                REQUESTER_ID, ExportJobType.GROWTH_POINT_LEDGER, STUDENT_ID,
                NOW.minusDays(1), NOW, null, List.of("OCCURRED_AT"), "测试", "local");
    }

    private ImportExportTemplateRecord template() {
        return new ImportExportTemplateRecord(
                TEMPLATE_ID, "通用报表模板", TemplateType.EXPORT, "REPORT", "V1", FILE_ID,
                true, "DEFAULT", ImportExportTemplateStatus.ENABLED, 0L,
                NOW, NOW, "report.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 3L);
    }

    private SystemTask systemTask(SystemTaskStatus status) {
        return new SystemTask(
                TASK_ID, "task-code", SystemTaskType.SENSITIVE_DATA_EXPORT,
                "权限变更日志导出", "安全审计复核", ImpactScope.GLOBAL,
                status, REQUESTER_ID, NOW, null, null, null, NOW, NOW);
    }
}
