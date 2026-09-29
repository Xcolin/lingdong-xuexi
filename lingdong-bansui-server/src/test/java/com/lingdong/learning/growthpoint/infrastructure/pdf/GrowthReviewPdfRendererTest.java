package com.lingdong.learning.growthpoint.infrastructure.pdf;

import com.lingdong.learning.growthpoint.application.*;
import com.lingdong.learning.growthpoint.domain.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/** 使用真实 PDF 解析与渲染验证中文、分页及纯文本边界。 */
class GrowthReviewPdfRendererTest {
    private final GrowthReviewPdfRenderer renderer = new GrowthReviewPdfRenderer();

    @Test void rendersChineseWithEmbeddedFontAndTemplateIsolation() throws Exception {
        var report = report("张小明", "今天阅读很专注");
        try (var simple = Loader.loadPDF(renderer.render(report, GrowthReviewPdfRenderer.Template.SIMPLE));
             var detailed = Loader.loadPDF(renderer.render(report, GrowthReviewPdfRenderer.Template.DETAILED))) {
            var text = new PDFTextStripper();
            assertThat(text.getText(simple)).contains("张小明", "75.00%", "36").doesNotContain("今天阅读很专注", "任务分类");
            assertThat(text.getText(detailed)).contains("今天阅读很专注", "任务分类", "下一步计划");
            var page = detailed.getPage(0);
            for (var name : page.getResources().getFontNames()) assertThat(page.getResources().getFont(name).isEmbedded()).isTrue();
            assertThat(detailed.getDocumentCatalog().getOpenAction()).isNull();
            assertThat(page.getAnnotations()).isEmpty();
        }
    }

    @Test void wrapsLongTextAndRendersInspectablePages() throws Exception {
        var bytes = renderer.render(report("张小明", "认真阅读，记录成长。".repeat(600)), GrowthReviewPdfRenderer.Template.DETAILED);
        var output = Path.of("target", "pdf-verification");
        Files.createDirectories(output);
        Files.write(output.resolve("复盘详细版.pdf"), bytes);
        try (var document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1).isLessThan(30);
            // 提取器会把每页页脚插入正文流，先排除我们生成的页码再核对完整正文。
            assertThat(new PDFTextStripper().getText(document)
                    .replaceAll("第\\s*\\d+\\s*页\\s*/\\s*共\\s*\\d+\\s*页", "").replaceAll("\\s", ""))
                    .contains("认真阅读，记录成长。".repeat(600));
            var images = new PDFRenderer(document);
            ImageIO.write(images.renderImageWithDPI(0, 100), "png", output.resolve("首页.png").toFile());
            ImageIO.write(images.renderImageWithDPI(document.getNumberOfPages() - 1, 100), "png", output.resolve("末页.png").toFile());
        }
    }

    @Test void treatsMarkupAsTextAndSanitizesFilename() throws Exception {
        var report = report("张/小\\明\r\n", "<script>alert(1)</script> https://example.invalid");
        assertThat(renderer.fileName(report)).isEqualTo("灵动伴随-张_小_明__-周报-2026-08-01至2026-08-07.pdf");
        try (var document = Loader.loadPDF(renderer.render(report, GrowthReviewPdfRenderer.Template.DETAILED))) {
            assertThat(new PDFTextStripper().getText(document)).contains("<script>alert(1)</script>");
            for (var page : document.getPages()) assertThat(page.getAnnotations()).isEmpty();
        }
    }

    @Test void rejectsMissingTemplateBeforeRendering() {
        assertThatThrownBy(() -> renderer.render(report("测试学生", ""), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsUnsupportedCharactersWithoutSilentlyDroppingThem() {
        assertThatThrownBy(() -> renderer.render(report("测试", "\uD83D\uDE80"), GrowthReviewPdfRenderer.Template.DETAILED))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("不支持");
    }

    private GrowthReviewDetailView report(String name, String content) {
        var day = LocalDate.of(2026, 8, 1);
        var now = LocalDateTime.of(2026, 8, 8, 0, 0);
        return new GrowthReviewDetailView(1874244142494648101L, 1874244142494647101L, name,
                GrowthReviewPeriodType.WEEK, day, day.plusDays(6), 1874244142494648102L, 1,
                10, 6, 2, 2, 0, new BigDecimal("0.75"), 36, 1, now, now,
                List.of(new GrowthReviewCategoryView("阅读", 10, 6)),
                List.of(new GrowthReviewDailyTrendView(day, 10, 6, 2, 2, new BigDecimal("0.75"), 36, 1)),
                List.of(new GrowthReviewSupplementView(1874244142494648103L, 1874244142494647001L,
                        "PARENT", GrowthReviewSupplementType.NEXT_PLAN, content, now)));
    }
}
