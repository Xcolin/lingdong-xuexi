package com.lingdong.learning.growthpoint.infrastructure.pdf;

import com.lingdong.learning.growthpoint.application.GrowthReviewDetailView;
import com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.RoundingMode;

/** 只渲染已授权快照；不查询数据库、不访问网络、不负责文件下载授权。 */
@Component
public class GrowthReviewPdfRenderer {
    public enum Template { SIMPLE, DETAILED }

    public byte[] render(GrowthReviewDetailView report, Template template) throws IOException {
        if (report == null || template == null) throw new IllegalArgumentException("复盘及模板不能为空");
        try (var document = new PDDocument();
             var fontData = getClass().getResourceAsStream("/fonts/NotoSansSC-VF.ttf");
             var output = new ByteArrayOutputStream()) {
            if (fontData == null) throw new IOException("复盘中文字体资源缺失");
            var font = PDType0Font.load(document, fontData, true);
            document.getDocumentInformation().setTitle("灵动学习成长复盘");
            document.getDocumentInformation().setProducer("灵动学习");
            try (var layout = new Layout(document, font)) {
                layout.text("灵动学习 · 成长复盘", 20);
                layout.text(report.studentName() + " · " + periodLabel(report.periodType()), 15);
                layout.text(report.periodStart() + " 至 " + report.periodEnd(), 11);
                layout.text("快照版本：" + report.contentVersion() + "    模板：" + (template == Template.SIMPLE ? "简洁版" : "详细版"), 11);
                layout.text("完成率：" + report.completionRate().movePointRight(2).setScale(2, RoundingMode.HALF_UP) + "%", 13);
                layout.text("获取积分：" + report.earnedPoints() + "    进行中：" + report.inProgressCount()
                        + "    待优化：" + report.pendingOptimizationCount() + "    暂停次数：" + report.pauseCount(), 11);
                if (template == Template.DETAILED) {
                    layout.text("成长分析", 15);
                    layout.text("本周期共 " + report.taskTotalCount() + " 项任务，已完成 " + report.completedCount()
                            + " 项，进行中 " + report.inProgressCount() + " 项，待优化 " + report.pendingOptimizationCount() + " 项。", 11);
                    layout.text("任务分类", 15);
                    if (report.categories().isEmpty()) layout.text("暂无分类统计", 11);
                    for (var category : report.categories()) layout.text(category.categoryCode() + "：完成 "
                            + category.completedCount() + " / " + category.taskCount(), 11);
                    layout.text("每日趋势", 15);
                    if (report.dailyTrends().isEmpty()) layout.text("暂无每日趋势", 11);
                    for (var trend : report.dailyTrends()) layout.text(trend.trendDate() + "：完成 "
                            + trend.completedCount() + " / " + trend.taskTotalCount() + "，获取积分 " + trend.earnedPoints(), 11);
                    layout.text("补录记录", 15);
                    if (report.supplements().isEmpty()) layout.text("暂无补录", 11);
                    for (var supplement : report.supplements()) {
                        String label = switch (supplement.supplementType()) {
                            case INSIGHT -> "成长观察";
                            case STRENGTH_WEAKNESS -> "优势与待提升";
                            case NEXT_PLAN -> "下一步计划";
                        };
                        layout.text(label + "（补录） " + supplement.supplementedAt(), 11);
                        layout.text(supplement.content(), 11);
                    }
                }
            }
            // 页脚在分页完成后追加，保持页码与总页数一致。
            for (int index = 0; index < document.getNumberOfPages(); index++) {
                try (var stream = new PDPageContentStream(document, document.getPage(index),
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    stream.beginText();
                    stream.setFont(font, 9);
                    stream.newLineAtOffset(48, 28);
                    stream.showText("第 " + (index + 1) + " 页 / 共 " + document.getNumberOfPages() + " 页");
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    public String fileName(GrowthReviewDetailView report) {
        if (report == null) throw new IllegalArgumentException("复盘不能为空");
        String name = report.studentName() == null ? "学生" : report.studentName();
        name = name.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_");
        if (name.codePointCount(0, name.length()) > 80) name = name.substring(0, name.offsetByCodePoints(0, 80));
        return "灵动学习-" + name + "-" + periodLabel(report.periodType()) + "-"
                + report.periodStart() + "至" + report.periodEnd() + ".pdf";
    }

    private String periodLabel(GrowthReviewPeriodType period) {
        return switch (period) { case DAY -> "日报"; case WEEK -> "周报"; case MONTH -> "月报"; };
    }

    /** 按 Unicode 码点及嵌入字体实际宽度排版，不拆分代理对，不解释标记语言。 */
    private static final class Layout implements AutoCloseable {
        private final PDDocument document;
        private final PDType0Font font;
        private PDPageContentStream stream;
        private float y;
        private int characters;

        Layout(PDDocument document, PDType0Font font) { this.document = document; this.font = font; }

        void text(String value, float size) throws IOException {
            String text = value == null ? "" : value;
            characters = Math.addExact(characters, text.length());
            if (characters > 200_000) throw new IOException("报告内容超过单份渲染上限");
            var line = new StringBuilder();
            float width = 0;
            for (int offset = 0; offset < text.length();) {
                int point = text.codePointAt(offset);
                offset += Character.charCount(point);
                if (point == '\r') continue;
                if (point == '\n') { line(line.toString(), size); line.setLength(0); width = 0; continue; }
                String glyph = Character.isISOControl(point) ? " " : new String(Character.toChars(point));
                float advance;
                try { advance = font.getStringWidth(glyph) / 1000 * size; }
                catch (IllegalArgumentException exception) { throw new IOException("报告包含当前字体不支持的字符", exception); }
                if (width + advance > PDRectangle.A4.getWidth() - 96 && !line.isEmpty()) {
                    line(line.toString(), size); line.setLength(0); width = 0;
                }
                line.append(glyph); width += advance;
            }
            if (!line.isEmpty()) line(line.toString(), size);
            y -= 8;
        }

        private void line(String value, float size) throws IOException {
            if (stream == null || y < 60 + size) {
                if (stream != null) stream.close();
                if (document.getNumberOfPages() >= 500) throw new IOException("报告页数超过单份渲染上限");
                var page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                stream = new PDPageContentStream(document, page);
                y = PDRectangle.A4.getHeight() - 52;
            }
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(48, y);
            stream.showText(value);
            stream.endText();
            y -= Math.max(18, size * 1.5f);
        }

        @Override public void close() throws IOException { if (stream != null) stream.close(); }
    }
}
