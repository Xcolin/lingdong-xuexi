package com.lingdong.learning.exportjob.application.template;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

/** 验证实际 PDF 的白名单列、中文、跨页和失败清理。 */
class ExportTablePdfWriterTest {
    @TempDir Path temporary;
    private final ExportTablePdfWriter writer = new ExportTablePdfWriter();
    private final ExportTemplateDefinition definition = new ExportTemplateDefinition(List.of(
            new ExportColumnDefinition("STUDENT_NAME", "学生", true),
            new ExportColumnDefinition("TASK_TITLE", "任务", true),
            new ExportColumnDefinition("SOURCE_TYPE", "来源", true),
            new ExportColumnDefinition("STATUS", "状态", true),
            new ExportColumnDefinition("POINTS", "积分", true),
            new ExportColumnDefinition("REVIEW_STATUS", "审核状态", true)), Map.of());

    @Test void rendersSelectedColumnsAndLiteralTextWithoutActiveContent() throws Exception {
        var path = writer.write(definition, List.<Map<String, Object>>of(Map.of(
                "STUDENT_NAME", "张*", "TASK_TITLE", "<b>阅读</b> https://example.invalid", "POINTS", -5,
                "SECRET", "不可导出字段")).iterator(), temporary);
        try (var document = Loader.loadPDF(path.toFile())) {
            assertThat(new PDFTextStripper().getText(document)).contains("学生", "张*", "<b>阅读</b>", "-5")
                    .doesNotContain("不可导出字段");
            assertThat(document.getDocumentCatalog().getOpenAction()).isNull();
            for (var page : document.getPages()) {
                assertThat(page.getAnnotations()).isEmpty();
                for (var name : page.getResources().getFontNames()) assertThat(page.getResources().getFont(name).isEmbedded()).isTrue();
            }
        }
    }

    @Test void paginatesLongCellsAndRepeatsHeadersWithoutLosingText() throws Exception {
        var data = new java.util.ArrayList<Map<String, Object>>();
        data.add(Map.of("STUDENT_NAME", "王*", "TASK_TITLE", "完整长标题".repeat(900), "POINTS", 1));
        IntStream.range(0, 205).forEach(i -> data.add(Map.of("STUDENT_NAME", "测试学生" + i,
                "TASK_TITLE", "完成阅读记录" + i, "POINTS", i, "SOURCE_TYPE", "机构任务",
                "STATUS", "待审核", "REVIEW_STATUS", "待审核")));
        var path = writer.write(definition, data.iterator(), temporary);
        try (var document = Loader.loadPDF(path.toFile())) {
            assertThat(document.getNumberOfPages()).isGreaterThan(3).isLessThan(40);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("测试学生204", "完成阅读记录204");
            // 换行及续页表头会穿插正文；逐字计数确认长单元格未被截断。
            assertThat(text.chars().filter(character -> character == '长').count()).isEqualTo(900);
            var stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page); stripper.setEndPage(page);
                assertThat(stripper.getText(document)).contains("学生", "任务", "积分", "第 " + page + " 页");
            }
            var preview = Path.of("target", "student-task-pdf-preview");
            Files.createDirectories(preview);
            Files.copy(path, preview.resolve("学生任务表格.pdf"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            var renderer = new PDFRenderer(document);
            for (int page : new int[]{0, 1, document.getNumberOfPages() - 1})
                ImageIO.write(renderer.renderImageWithDPI(page, 90), "png", preview.resolve("page-" + (page + 1) + ".png").toFile());
        }
    }

    @Test void rendersEmptyResultAndCleansFailedOutput() throws Exception {
        var empty = writer.write(definition, List.<Map<String, Object>>of().iterator(), temporary);
        try (var document = Loader.loadPDF(empty.toFile())) {
            assertThat(new PDFTextStripper().getText(document)).contains("暂无符合条件的数据", "学生", "任务");
        }
        Files.delete(empty);
        assertThatThrownBy(() -> writer.write(definition, List.<Map<String, Object>>of(
                Map.of("TASK_TITLE", "\uD83D\uDE80")).iterator(), temporary)).isInstanceOf(java.io.IOException.class);
        try (var files = Files.list(temporary)) { assertThat(files.toList()).isEmpty(); }
        var broken = new java.util.Iterator<Map<String, Object>>() {
            public boolean hasNext() { throw new IllegalStateException("模拟分页撤权"); }
            public Map<String, Object> next() { throw new AssertionError(); }
        };
        assertThatThrownBy(() -> writer.write(definition, broken, temporary)).isInstanceOf(IllegalStateException.class);
        try (var files = Files.list(temporary)) { assertThat(files.toList()).isEmpty(); }
    }
}
