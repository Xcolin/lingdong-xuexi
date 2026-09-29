package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.growthpoint.application.GrowthReviewDetailView;
import com.lingdong.learning.growthpoint.application.GrowthReviewExportPreparationService.Prepared;
import com.lingdong.learning.growthpoint.infrastructure.pdf.GrowthReviewPdfRenderer;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipInputStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 固化报告到受控文件结果的编排测试；PDF 字体与分页由真实渲染专项验证。 */
class GrowthReviewPdfArtifactServiceTest {
    private final GrowthReviewExportPayloadStore payloads = mock(GrowthReviewExportPayloadStore.class);
    private final GrowthReviewExportAccessService access = mock(GrowthReviewExportAccessService.class);
    private final GrowthReviewPdfRenderer renderer = mock(GrowthReviewPdfRenderer.class);
    private final ExportJobRecord job = mock(ExportJobRecord.class);
    private final GrowthReviewPdfArtifactService service = new GrowthReviewPdfArtifactService(payloads, access, renderer, 1048576);

    @Test void rendersSingleFrozenReportAsPdf() throws Exception {
        var report = report("灵动伴随-测试学生-日报-2026-09-01至2026-09-01.pdf", "report-one");
        var prepared = configure(List.of(report));
        var result = service.generate(job);
        assertThat(result.fileName()).endsWith(".pdf");
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.content()).isEqualTo("report-one".getBytes(StandardCharsets.UTF_8));
        assertThat(result.reportCount()).isEqualTo(1);
        verify(access, atLeastOnce()).requireExecution(prepared);
    }

    @Test void packagesMultiplePdfFilesInStableOrder() throws Exception {
        configure(List.of(report("日报一.pdf", "one"), report("日报二.pdf", "two")));
        var result = service.generate(job);
        assertThat(result.contentType()).isEqualTo("application/zip");
        assertThat(result.reportCount()).isEqualTo(2);
        try (var zip = new ZipInputStream(new ByteArrayInputStream(result.content()), StandardCharsets.UTF_8)) {
            assertThat(zip.getNextEntry().getName()).isEqualTo("日报一.pdf");
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("one");
            assertThat(zip.getNextEntry().getName()).isEqualTo("日报二.pdf");
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("two");
            assertThat(zip.getNextEntry()).isNull();
        }
    }

    @Test void refusesRevocationBetweenReportsWithoutReturningPartialArchive() throws Exception {
        var first = report("一.pdf", "one");
        var second = report("二.pdf", "two");
        var prepared = configure(List.of(first, second));
        doNothing().doThrow(new IllegalStateException("权限已撤销")).when(access).requireExecution(prepared);
        assertThatThrownBy(() -> service.generate(job)).isInstanceOf(IllegalStateException.class);
        verify(renderer, never()).render(eq(second), any());
    }

    @Test void refusesNonExportingJobBeforeLoadingPayload() {
        when(job.status()).thenReturn(ExportJobStatus.QUEUED);
        assertThatThrownBy(() -> service.generate(job)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(payloads);
    }

    @Test void rechecksStoredScopeAndRejectsOtherDatasetTypes() throws Exception {
        var prepared = configure(List.of(report("一.pdf", "one")));
        service.requireExecution(job);
        verify(access).requireExecution(prepared);
        when(job.exportType()).thenReturn(com.lingdong.learning.exportjob.domain.ExportJobType.GROWTH_POINT_LEDGER);
        assertThatThrownBy(() -> service.requireExecution(job)).isInstanceOf(IllegalStateException.class);
    }

    @Test void refusesUnsafeOrDuplicateArchiveNames() throws Exception {
        configure(List.of(report("../一.pdf", "one"), report("二.pdf", "two")));
        assertThatThrownBy(() -> service.generate(job)).isInstanceOf(IllegalStateException.class);
        configure(List.of(report("一.pdf", "one"), report("一.pdf", "two")));
        assertThatThrownBy(() -> service.generate(job)).isInstanceOf(IllegalStateException.class);
    }

    @Test void refusesOversizedSingleAndArchiveOutputs() throws Exception {
        var tiny = new GrowthReviewPdfArtifactService(payloads, access, renderer, 3);
        configure(List.of(report("一.pdf", "long-report")));
        assertThatThrownBy(() -> tiny.generate(job)).isInstanceOf(IllegalStateException.class);
        configure(List.of(report("一.pdf", "1"), report("二.pdf", "2")));
        assertThatThrownBy(() -> tiny.generate(job)).isInstanceOf(IllegalStateException.class);
    }

    @Test void propagatesRendererFailureWithoutReturningPartialResult() throws Exception {
        var report = report("一.pdf", "one");
        configure(List.of(report));
        when(renderer.render(eq(report), any())).thenThrow(new IOException("字体资源不可用"));
        assertThatThrownBy(() -> service.generate(job)).isInstanceOf(IOException.class);
    }

    @Test void rejectsMultipleControlCharactersInFileName() throws Exception {
        configure(List.of(report("一\n二\n三.pdf", "one")));
        assertThatThrownBy(() -> service.generate(job)).isInstanceOf(IllegalStateException.class);
    }

    @Test void packagesRealChinesePdfReportsReadableByPdfBox() throws Exception {
        var day = java.time.LocalDate.of(2026, 9, 1);
        var reports = java.util.stream.IntStream.range(0, 2).mapToObj(index -> new GrowthReviewDetailView(
                1874244142494650501L + index, 1874244142494650403L, "合成测试学生",
                com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType.DAY,
                day.plusDays(index), day.plusDays(index), 1874244142494650601L + index, 1,
                1, 1, 0, 0, 0, java.math.BigDecimal.ONE, 10, 0, day.atStartOfDay(), day.atStartOfDay(),
                List.of(), List.of(), List.of())).toList();
        configure(reports);
        var real = new GrowthReviewPdfArtifactService(payloads, access, new GrowthReviewPdfRenderer(), 1048576);
        var result = real.generate(job);
        try (var zip = new ZipInputStream(new ByteArrayInputStream(result.content()), StandardCharsets.UTF_8)) {
            for (int index = 0; index < 2; index++) {
                assertThat(zip.getNextEntry().getName()).contains("合成测试学生", day.plusDays(index).toString()).endsWith(".pdf");
                try (var pdf = org.apache.pdfbox.Loader.loadPDF(zip.readAllBytes())) {
                    assertThat(pdf.getNumberOfPages()).isPositive();
                    assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf)).contains("合成测试学生", day.plusDays(index).toString());
                }
            }
            assertThat(zip.getNextEntry()).isNull();
        }
    }

    private Prepared configure(List<GrowthReviewDetailView> reports) {
        when(job.status()).thenReturn(ExportJobStatus.EXPORTING);
        when(job.exportType()).thenReturn(com.lingdong.learning.exportjob.domain.ExportJobType.GROWTH_REVIEW_PDF);
        when(job.sensitive()).thenReturn(false);
        when(job.systemTaskId()).thenReturn(null);
        when(job.studentId()).thenReturn(1874244142494650403L);
        when(job.id()).thenReturn(1874244142494650401L);
        var prepared = new Prepared(1874244142494650402L, 1874244142494650403L, reports);
        when(payloads.load(job)).thenReturn(new GrowthReviewExportPayloadStore.Frozen(1, 1874244142494650401L, 1874244142494650404L,
                "V1", GrowthReviewPdfRenderer.Template.DETAILED, prepared));
        return prepared;
    }

    private GrowthReviewDetailView report(String name, String bytes) throws Exception {
        var report = mock(GrowthReviewDetailView.class);
        when(renderer.fileName(report)).thenReturn(name);
        when(renderer.render(eq(report), any())).thenReturn(bytes.getBytes(StandardCharsets.UTF_8));
        return report;
    }
}
