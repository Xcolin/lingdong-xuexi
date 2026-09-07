package com.lingdong.learning.exportjob.application.template;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** 使用新工作簿流式写入白名单列，避免复制模板中的隐藏动态内容。 */
public class ExportWorkbookWriter {
    private static final int EXCEL_CELL_TEXT_LIMIT = 32767;
    private static final int EXCEL_MAX_DATA_ROWS = 1_048_575;

    public Path write(
            byte[] templateContent,
            ExportTemplateDefinition definition,
            Iterator<Map<String, Object>> rows,
            int sheetMaxDataRows,
            Path tempDirectory
    ) throws IOException {
        if (templateContent == null || definition == null || rows == null || tempDirectory == null) {
            throw new IllegalArgumentException("导出工作簿参数不能为空");
        }
        if (sheetMaxDataRows < 1 || sheetMaxDataRows > EXCEL_MAX_DATA_ROWS) {
            throw new IllegalArgumentException("单工作表数据行数范围为1至1048575");
        }
        Files.createDirectories(tempDirectory);
        Path output = Files.createTempFile(tempDirectory, "export-", ".xlsx");
        try (XSSFWorkbook source = new XSSFWorkbook(new ByteArrayInputStream(templateContent));
             SXSSFWorkbook target = new SXSSFWorkbook(100)) {
            target.setCompressTempFiles(true);
            Map<String, CellStyle> styles = copyHeaderStyles(source, target, definition);
            Sheet sheet = createSheet(target, source, definition, styles, 1);
            int sheetNumber = 1;
            int rowIndex = 1;
            while (rows.hasNext()) {
                if (rowIndex > sheetMaxDataRows) {
                    sheetNumber++;
                    sheet = createSheet(target, source, definition, styles, sheetNumber);
                    rowIndex = 1;
                }
                writeRow(sheet.createRow(rowIndex++), definition.columns(), rows.next());
            }
            try (OutputStream stream = Files.newOutputStream(output)) {
                target.write(stream);
            }
            target.dispose();
            return output;
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(output);
            throw exception;
        }
    }

    private Map<String, CellStyle> copyHeaderStyles(
            XSSFWorkbook source,
            SXSSFWorkbook target,
            ExportTemplateDefinition definition
    ) {
        Row sourceHeader = source.getSheetAt(0).getRow(0);
        Map<String, CellStyle> styles = new HashMap<>();
        for (ExportColumnDefinition column : definition.columns()) {
            Cell sourceCell = sourceHeader.getCell(definition.templateColumnIndexes().get(column.code()));
            XSSFCellStyle style = target.getXSSFWorkbook().createCellStyle();
            style.cloneStyleFrom(sourceCell.getCellStyle());
            styles.put(column.code(), style);
        }
        return styles;
    }

    private Sheet createSheet(
            SXSSFWorkbook workbook,
            XSSFWorkbook source,
            ExportTemplateDefinition definition,
            Map<String, CellStyle> styles,
            int sheetNumber
    ) {
        String name = sheetNumber == 1 ? "导出数据" : "导出数据" + sheetNumber;
        Sheet sheet = workbook.createSheet(name);
        Row header = sheet.createRow(0);
        List<ExportColumnDefinition> columns = definition.columns();
        for (int index = 0; index < columns.size(); index++) {
            ExportColumnDefinition column = columns.get(index);
            Cell cell = header.createCell(index);
            cell.setCellValue(column.header());
            cell.setCellStyle(styles.get(column.code()));
            int sourceIndex = definition.templateColumnIndexes().get(column.code());
            sheet.setColumnWidth(index, source.getSheetAt(0).getColumnWidth(sourceIndex));
        }
        return sheet;
    }

    private void writeRow(
            Row row,
            List<ExportColumnDefinition> columns,
            Map<String, Object> values
    ) {
        for (int index = 0; index < columns.size(); index++) {
            Object value = values.get(columns.get(index).code());
            Cell cell = row.createCell(index);
            if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else if (value instanceof Boolean booleanValue) {
                cell.setCellValue(booleanValue);
            } else if (value instanceof TemporalAccessor temporal) {
                cell.setCellValue(temporal.toString());
            } else if (value != null) {
                cell.setCellValue(safeText(value.toString()));
            }
        }
    }

    private String safeText(String value) {
        String text = value.length() > EXCEL_CELL_TEXT_LIMIT
                ? value.substring(0, EXCEL_CELL_TEXT_LIMIT) : value;
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            return "'" + text;
        }
        return text;
    }
}
