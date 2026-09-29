package com.lingdong.learning.exportjob.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.exportjob.domain.ExportJobEventType;
import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobMapper;
import com.lingdong.learning.exportjob.infrastructure.security.ExportSourceHasher;
import com.lingdong.learning.growthpoint.application.GrowthReviewExportPreparationService;
import com.lingdong.learning.growthpoint.application.GrowthReviewExportSelection;
import com.lingdong.learning.growthpoint.infrastructure.pdf.GrowthReviewPdfRenderer.Template;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;

/** 在同一可重复读事务中授权、选取报告、创建队列作业并固化全文。 */
@Service
public class GrowthReviewExportApplicationService {
    private final GrowthReviewExportAccessService access;
    private final GrowthReviewExportPreparationService preparation;
    private final GrowthReviewExportPayloadStore payloads;
    private final ImportExportTemplateMapper templates;
    private final ExportJobMapper jobs;
    private final ExportJobEventService events;
    private final IdGenerator ids;
    private final ExportSourceHasher hasher;
    private final ObjectMapper json;
    private final Clock clock;

    public GrowthReviewExportApplicationService(GrowthReviewExportAccessService access,
            GrowthReviewExportPreparationService preparation, GrowthReviewExportPayloadStore payloads,
            ImportExportTemplateMapper templates, ExportJobMapper jobs, ExportJobEventService events,
            IdGenerator ids, ExportSourceHasher hasher, ObjectMapper json, Clock clock) {
        this.access = access; this.preparation = preparation; this.payloads = payloads;
        this.templates = templates; this.jobs = jobs; this.events = events;
        this.ids = ids; this.hasher = hasher; this.json = json; this.clock = clock;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ExportJobRecord create(AuthenticatedUser user, GrowthReviewExportSelection selection,
            Long templateId, Template mode, String reason, String requestSource) {
        if (selection == null || templateId == null || templateId <= 0 || mode == null
                || reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new IllegalArgumentException("请选择报告和模板模式，并填写不超过500字的导出原因");
        }
        access.requireCreate(user, selection.studentId());
        String sourceHash = hasher.hash(requestSource);
        var prepared = preparation.prepare(user, selection);
        var template = templates.findById(templateId);
        if (template == null) throw new IllegalArgumentException("复盘导出模板不存在");
        long id = ids.nextId();
        var now = LocalDateTime.now(clock);
        var job = new ExportJobRecord(id, "EXP-" + id, ExportJobType.GROWTH_REVIEW_PDF,
                template.id(), template.templateName(), template.version(), user.userId(), selection.studentId(), null,
                serialize(selection), "[]", serialize(new ExportScopeSnapshot(selection.studentId(), 0)),
                "{\"policy\":\"AUTHORIZED_CHILD_REVIEW\",\"version\":1}", reason.trim(), false,
                ExportJobStatus.QUEUED, 0L, null, 0L, 0L, null, null, sourceHash,
                now, null, now, null, null, now, now);
        if (jobs.insert(job) != 1) throw new IllegalStateException("复盘导出作业保存失败");
        // 固化服务再次核验权限和模板；失败时连同作业回滚，队列不会看到孤立作业。
        payloads.freeze(id, prepared, mode);
        events.record(id, ExportJobEventType.REQUESTED, user.userId(), "已申请成长复盘导出");
        return job;
    }

    private String serialize(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("复盘导出选择条件序列化失败", exception); }
    }
}
