package com.lingdong.learning.exportjob.application.template;

import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExportWorkbookWriterTest {
    @TempDir Path tempDirectory;

    @Test
    void writesSelectedColumnsAcrossSheetsAndNeutralizesFormulaText() throws IOException {
        byte[] template = styledTemplate();
        ExportTemplateDefinition definition = new ExportTemplateParser().parse(
                template,
                List.of(
                        new ExportColumnDefinition("NAME", "姓名", true),
                        new ExportColumnDefinition("VALUE", "数值", true),
                        new ExportColumnDefinition("HIDDEN", "未选择", false)
                ),
                List.of("VALUE", "NAME")
        );
        List<Map<String, Object>> rows = List.of(
                Map.of("NAME", "张三", "VALUE", 1),
                Map.of("NAME", "=1+1", "VALUE", 2),
                Map.of("NAME", "李四", "VALUE", 3),
                Map.of("NAME", "王五", "VALUE", 4),
                Map.of("NAME", "赵六", "VALUE", 5)
        );

        Path result = new ExportWorkbookWriter().write(
                template, definition, rows.iterator(), 2, tempDirectory);

        assertThat(result).exists();
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(result))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("数值");
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(1).getStringCellValue()).isEqualTo("姓名");
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getCellStyle().getFillForegroundColor())
                    .isEqualTo(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(1).getStringCellValue()).isEqualTo("'=1+1");
            assertThat(workbook.getSheetAt(2).getRow(1).getCell(0).getNumericCellValue()).isEqualTo(5D);
        }

        Files.delete(result);
        assertThat(result).doesNotExist();
    }

    private byte[] styledTemplate() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("模板");
            var row = sheet.createRow(0);
            var style = workbook.createCellStyle();
            style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            row.createCell(0).setCellValue("${NAME}");
            row.getCell(0).setCellStyle(style);
            row.createCell(1).setCellValue("${VALUE}");
            row.getCell(1).setCellStyle(style);
            row.createCell(2).setCellValue("${HIDDEN}");
            row.getCell(2).setCellStyle(style);
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
