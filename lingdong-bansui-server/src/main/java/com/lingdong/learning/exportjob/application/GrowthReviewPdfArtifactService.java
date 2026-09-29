package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.growthpoint.infrastructure.pdf.GrowthReviewPdfRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 从持久化报告生成单份 PDF 或批量 ZIP，不重查最新复盘，不自行保存或公开文件。 */
@Service
public class GrowthReviewPdfArtifactService {
    private final GrowthReviewExportPayloadStore payloads;
    private final GrowthReviewExportAccessService access;
    private final GrowthReviewPdfRenderer renderer;
    private final int maxBytes;

    public GrowthReviewPdfArtifactService(GrowthReviewExportPayloadStore payloads,
            GrowthReviewExportAccessService access, GrowthReviewPdfRenderer renderer,
            @Value("${lingdong.growth-review-export.max-output-bytes:67108864}") int maxBytes) {
        if (maxBytes < 1 || maxBytes > 134217728) throw new IllegalArgumentException("复盘导出文件保护上限必须为1至128MB字节数");
        this.payloads = payloads;
        this.access = access;
        this.renderer = renderer;
        this.maxBytes = maxBytes;
    }

    /** 结果只供统一导出作业使用，不能直接作为绕过附件鉴权的 HTTP 响应。 */
    public record Artifact(String fileName, String contentType, byte[] content, int reportCount) { }

    /** 执行开始和文件保存后均可复核，不依赖之前的鉴权结果。 */
    public void requireExecution(ExportJobRecord job) {
        requirePdfJob(job);
        access.requireExecution(payloads.load(job).prepared());
    }

    public Artifact generate(ExportJobRecord job) throws IOException {
        requirePdfJob(job);
        var frozen = payloads.load(job);
        var prepared = frozen.prepared();
        var reports = prepared.reports();
        if (reports.isEmpty()) throw new IllegalStateException("复盘导出内容不能为空");
        if (reports.size() == 1) {
            access.requireExecution(prepared);
            var report = reports.get(0);
            String name = safeFileName(renderer.fileName(report));
            byte[] pdf = renderer.render(report, frozen.template());
            requireSize(pdf.length);
            access.requireExecution(prepared);
            return new Artifact(name, "application/pdf", pdf, 1);
        }

        var names = new HashSet<String>();
        try (var output = new LimitedOutput(maxBytes)) {
            long uncompressedSize = 0;
            try (var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                for (var report : reports) {
                    access.requireExecution(prepared);
                    String name = safeFileName(renderer.fileName(report));
                    if (!names.add(name)) throw new IllegalStateException("批量复盘文件名重复，不能覆盖报告");
                    byte[] pdf = renderer.render(report, frozen.template());
                    uncompressedSize += pdf.length;
                    requireSize(uncompressedSize);
                    var entry = new ZipEntry(name);
                    entry.setTime(0);
                    zip.putNextEntry(entry);
                    zip.write(pdf);
                    zip.closeEntry();
                }
            }
            // 最后一份渲染期间发生撤权时，不返回待发布的结果。
            access.requireExecution(prepared);
            return new Artifact("灵动伴随-复盘批量-" + job.id() + ".zip", "application/zip", output.toByteArray(), reports.size());
        }
    }

    private static void requirePdfJob(ExportJobRecord job) {
        if (job == null || job.id() == null || job.status() != ExportJobStatus.EXPORTING
                || job.exportType() != ExportJobType.GROWTH_REVIEW_PDF || job.studentId() == null
                || !Boolean.FALSE.equals(job.sensitive()) || job.systemTaskId() != null) {
            throw new IllegalStateException("只有范围有效且生成中的复盘导出作业可以生成文件");
        }
    }

    private static String safeFileName(String name) {
        if (name == null || !name.endsWith(".pdf") || name.chars().anyMatch(value ->
                Character.isISOControl(value) || value == '\\' || value == '/' || value == ':')) {
            throw new IllegalStateException("复盘文件名不合法");
        }
        return name;
    }

    private void requireSize(long size) {
        if (size > maxBytes) throw new IllegalStateException("复盘导出结果超过保护上限，请缩小日期区间");
    }

    /** 写入压缩包时限制实际内存增长，不能等完整包生成后才检查大小。 */
    private static final class LimitedOutput extends ByteArrayOutputStream {
        private final int limit;
        private LimitedOutput(int limit) { super(Math.min(limit, 8192)); this.limit = limit; }
        @Override public synchronized void write(int value) { check(1); super.write(value); }
        @Override public synchronized void write(byte[] bytes, int offset, int length) {
            check(length);
            super.write(bytes, offset, length);
        }
        private void check(int added) {
            if ((long) count + added > limit) throw new IllegalStateException("复盘压缩结果超过保护上限，请缩小日期区间");
        }
    }
}
