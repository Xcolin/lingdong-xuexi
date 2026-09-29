package com.lingdong.learning.exportjob.application.template;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** 仅消费已经鉴权的白名单行；纯文本表格不执行模板中的链接或脚本。 */
public class ExportTablePdfWriter {
    public Path write(ExportTemplateDefinition definition, Iterator<Map<String, Object>> rows,
            Path tempDirectory) throws IOException {
        return write(definition, rows, tempDirectory, "灵动伴随 · 学生任务报表");
    }

    public Path write(ExportTemplateDefinition definition, Iterator<Map<String, Object>> rows,
            Path tempDirectory, String title) throws IOException {
        if (definition == null || definition.columns().isEmpty() || rows == null || tempDirectory == null)
            throw new IllegalArgumentException("PDF 表格参数不能为空");
        Files.createDirectories(tempDirectory);
        Path output = Files.createTempFile(tempDirectory, "export-", ".pdf");
        try (var document = new PDDocument();
             var fontData = getClass().getResourceAsStream("/fonts/NotoSansSC-VF.ttf")) {
            if (fontData == null) throw new IOException("PDF 中文字体资源缺失");
            var font = PDType0Font.load(document, fontData, true);
            document.getDocumentInformation().setTitle(title.replace(" · ", ""));
            document.getDocumentInformation().setProducer("灵动伴随");
            try (var layout = new Layout(document, font, definition.columns(), title)) {
                layout.newPage();
                boolean empty = true;
                while (rows.hasNext()) {
                    empty = false;
                    Map<String, Object> row = rows.next();
                    var values = definition.columns().stream().map(column -> {
                        Object value = row.get(column.code());
                        return value == null ? "" : value.toString();
                    }).toList();
                    layout.row(values);
                }
                if (empty) layout.text("暂无符合条件的数据", 40, layout.y - 24, 11);
            }
            for (int index = 0; index < document.getNumberOfPages(); index++) {
                try (var stream = new PDPageContentStream(document, document.getPage(index),
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    stream.beginText(); stream.setFont(font, 9); stream.newLineAtOffset(40, 24);
                    stream.showText("第 " + (index + 1) + " 页 / 共 " + document.getNumberOfPages() + " 页");
                    stream.endText();
                }
            }
            document.save(output.toFile());
            return output;
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(output);
            throw exception;
        }
    }

    private static final class Layout implements AutoCloseable {
        private static final float MARGIN = 40, FONT_SIZE = 9, LINE_HEIGHT = 14, PADDING = 6;
        private final PDDocument document;
        private final PDType0Font font;
        private final List<ExportColumnDefinition> columns;
        private final String title;
        private final float[] widths;
        private PDPageContentStream stream;
        private float y;

        Layout(PDDocument document, PDType0Font font, List<ExportColumnDefinition> columns, String title) {
            this.document = document; this.font = font; this.columns = columns; this.title = title;
            widths = new float[columns.size()];
            float totalWeight = columns.size() + columns.stream().filter(c -> c.code().equals("TASK_TITLE")).count();
            for (int i = 0; i < widths.length; i++) widths[i] = (PDRectangle.A4.getHeight() - 2 * MARGIN)
                    * (columns.get(i).code().equals("TASK_TITLE") ? 2 : 1) / totalWeight;
        }

        void newPage() throws IOException {
            close();
            var page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            document.addPage(page); stream = new PDPageContentStream(document, page);
            text(title, MARGIN, page.getMediaBox().getHeight() - 36, 16);
            y = page.getMediaBox().getHeight() - 58;
            var headers = wrapCells(columns.stream().map(ExportColumnDefinition::header).toList());
            int lines = headers.stream().mapToInt(List::size).max().orElse(1);
            draw(headers, 0, lines, true);
        }

        void row(List<String> values) throws IOException {
            var cells = wrapCells(values);
            int lines = cells.stream().mapToInt(List::size).max().orElse(1);
            int offset = 0;
            // 超长单元格按行跨页；每页重画表头，不截断正文或复制其他列的值。
            while (offset < lines) {
                int available = (int) ((y - MARGIN - 2 * PADDING) / LINE_HEIGHT);
                if (available < 1) { newPage(); continue; }
                int count = Math.min(lines - offset, available);
                draw(cells, offset, count, false);
                offset += count;
                if (offset < lines) newPage();
            }
        }

        private List<List<String>> wrapCells(List<String> values) throws IOException {
            var cells = new ArrayList<List<String>>();
            for (int i = 0; i < values.size(); i++) cells.add(wrap(values.get(i), widths[i] - 2 * PADDING));
            return cells;
        }

        private List<String> wrap(String value, float width) throws IOException {
            var lines = new ArrayList<String>();
            var line = new StringBuilder();
            float used = 0;
            String normalized = value.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ');
            for (int offset = 0; offset < normalized.length();) {
                int codePoint = normalized.codePointAt(offset); offset += Character.charCount(codePoint);
                if (codePoint == '\n') { lines.add(line.toString()); line.setLength(0); used = 0; continue; }
                String character = new String(Character.toChars(codePoint));
                float characterWidth;
                try { characterWidth = font.getStringWidth(character) * FONT_SIZE / 1000; }
                catch (IllegalArgumentException exception) { throw new IOException("PDF 字体不支持报表中的字符", exception); }
                if (used + characterWidth > width && !line.isEmpty()) {
                    lines.add(line.toString()); line.setLength(0); used = 0;
                }
                line.append(character); used += characterWidth;
            }
            lines.add(line.toString());
            return lines;
        }

        private void draw(List<List<String>> cells, int offset, int count, boolean header) throws IOException {
            float height = count * LINE_HEIGHT + 2 * PADDING;
            float x = MARGIN;
            for (int column = 0; column < cells.size(); column++) {
                if (header) {
                    stream.setNonStrokingColor(0.91f, 0.94f, 0.97f);
                    stream.addRect(x, y - height, widths[column], height); stream.fill();
                }
                stream.setStrokingColor(0.7f, 0.75f, 0.8f); stream.setLineWidth(0.4f);
                stream.addRect(x, y - height, widths[column], height); stream.stroke();
                for (int line = 0; line < count && offset + line < cells.get(column).size(); line++)
                    text(cells.get(column).get(offset + line), x + PADDING,
                            y - PADDING - FONT_SIZE - line * LINE_HEIGHT, FONT_SIZE);
                x += widths[column];
            }
            y -= height;
        }

        void text(String value, float x, float baseline, float size) throws IOException {
            stream.setNonStrokingColor(0.12f, 0.16f, 0.22f);
            stream.beginText(); stream.setFont(font, size); stream.newLineAtOffset(x, baseline);
            try { stream.showText(value); }
            catch (IllegalArgumentException exception) { throw new IOException("PDF 字体不支持报表中的字符", exception); }
            finally { stream.endText(); }
        }
        @Override public void close() throws IOException {
            if (stream != null) { stream.close(); stream = null; }
        }
    }
}
