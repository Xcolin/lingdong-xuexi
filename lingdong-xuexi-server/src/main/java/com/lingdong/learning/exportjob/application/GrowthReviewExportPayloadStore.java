package com.lingdong.learning.exportjob.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobMapper;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobPayloadMapper;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobPayloadMapper.Payload;
import com.lingdong.learning.growthpoint.application.GrowthReviewExportPreparationService.Prepared;
import com.lingdong.learning.growthpoint.infrastructure.pdf.GrowthReviewPdfRenderer.Template;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Objects;

/** 内部内容存储，不授予创建或下载权限；调用方必须先完成作业权限和开关检查。 */
@Service
public class GrowthReviewExportPayloadStore {
    private static final String TYPE = "GROWTH_REVIEW_PDF_V1";
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private final ExportJobPayloadMapper payloads;
    private final ExportJobMapper jobs;
    private final ObjectMapper json;
    private final IdGenerator ids;
    private final GrowthReviewExportAccessService access;
    private final GrowthReviewPdfTemplateService templates;

    public GrowthReviewExportPayloadStore(ExportJobPayloadMapper payloads, ExportJobMapper jobs,
                                         ObjectMapper json, IdGenerator ids, GrowthReviewExportAccessService access,
                                         GrowthReviewPdfTemplateService templates) {
        this.payloads = payloads;
        this.jobs = jobs;
        this.json = json;
        this.ids = ids;
        this.access = access;
        this.templates = templates;
    }

    /** 格式版本独立于业务报告版本；升级时显式兼容，不用当前报告重新生成旧内容。 */
    public record Frozen(int schemaVersion, Long jobId, Long templateId, String templateVersion,
                         Template template, Prepared prepared) { }

    @Transactional(propagation = Propagation.MANDATORY)
    public void freeze(Long jobId, Prepared prepared, Template template) {
        validate(prepared, template);
        ExportJobRecord job = jobs.findById(jobId);
        requireScope(job, prepared);
        // 固化入口同样拒绝停用或撤权，防止内部调用跳过外层创建授权。
        access.requireExecution(prepared);
        templates.requireForCreation(job.templateId(), job.templateName(), job.templateVersion(), template);
        var frozen = new Frozen(1, jobId, job.templateId(), job.templateVersion(), template, prepared);
        try {
            String content = json.writeValueAsString(frozen);
            requireSize(content);
            if (payloads.insertForQueuedJob(new Payload(ids.nextId(), jobId, TYPE, content, hash(content)),
                    prepared.requesterId(), prepared.studentId()) != 1) {
                throw new IllegalStateException("只有尚未领取的初始作业可以固化报告内容");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("复盘导出内容序列化失败", exception);
        }
    }

    @Transactional(readOnly = true)
    public Frozen load(ExportJobRecord job) {
        if (job == null) throw new IllegalStateException("导出作业不存在");
        Payload payload = payloads.findByJobId(job.id());
        if (payload == null || !TYPE.equals(payload.payloadType())) {
            throw new IllegalStateException("复盘导出内容不存在或格式不支持");
        }
        requireSize(payload.payloadJson());
        // 摘要用于发现存储损坏，不替代权限校验，也不是防数据库管理员篡改的签名。
        if (!hash(payload.payloadJson()).equals(payload.contentSha256())) {
            throw new IllegalStateException("复盘导出内容完整性校验失败");
        }
        try {
            Frozen frozen = json.readValue(payload.payloadJson(), Frozen.class);
            if (frozen == null || frozen.schemaVersion() != 1 || !Objects.equals(frozen.jobId(), job.id())
                    || !Objects.equals(frozen.templateId(), job.templateId())
                    || !Objects.equals(frozen.templateVersion(), job.templateVersion())) {
                throw new IllegalStateException("复盘导出内容版本或作业引用不匹配");
            }
            validate(frozen.prepared(), frozen.template());
            requireScope(job, frozen.prepared());
            return frozen;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("复盘导出内容无法解析", exception);
        }
    }

    private static void requireScope(ExportJobRecord job, Prepared prepared) {
        if (job == null || job.exportType() != com.lingdong.learning.exportjob.domain.ExportJobType.GROWTH_REVIEW_PDF
                || !Boolean.FALSE.equals(job.sensitive()) || job.systemTaskId() != null
                || !Objects.equals(job.requesterId(), prepared.requesterId())
                || !Objects.equals(job.studentId(), prepared.studentId())) {
            throw new IllegalStateException("复盘导出内容与作业归属不匹配");
        }
    }

    private static void validate(Prepared prepared, Template template) {
        if (prepared == null || template == null || prepared.requesterId() == null || prepared.studentId() == null
                || prepared.reports().isEmpty() || prepared.reports().size() > 1000) {
            throw new IllegalArgumentException("复盘导出内容、模板或报告数量不合法");
        }
        var reviewIds = new HashSet<Long>();
        for (var report : prepared.reports()) {
            if (!Objects.equals(report.studentId(), prepared.studentId()) || report.reviewId() == null
                    || report.snapshotId() == null || report.contentVersion() < 1 || !reviewIds.add(report.reviewId())) {
                throw new IllegalArgumentException("复盘导出报告对象、版本或唯一性不合法");
            }
        }
    }

    private static void requireSize(String content) {
        if (content == null || content.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("复盘导出内容超过8MB保护上限，请缩小日期区间");
        }
    }

    private static String hash(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少SHA-256支持", exception);
        }
    }
}
