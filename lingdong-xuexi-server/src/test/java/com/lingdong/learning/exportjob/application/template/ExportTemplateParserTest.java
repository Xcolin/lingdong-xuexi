package com.lingdong.learning.exportjob.application.template;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbookType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportTemplateParserTest {
    private final ExportTemplateParser parser = new ExportTemplateParser();
    private final List<ExportColumnDefinition> allowed = List.of(
            new ExportColumnDefinition("OCCURRED_AT", "发生时间", true),
            new ExportColumnDefinition("STUDENT_NAME", "学生姓名", true),
            new ExportColumnDefinition("REMARK", "备注", false)
    );

    @Test
    void parsesSelectedColumnsInRequestedOrder() throws IOException {
        byte[] content = workbook("${OCCURRED_AT}", "${STUDENT_NAME}", "${REMARK}");

        ExportTemplateDefinition definition = parser.parse(
                content, allowed, List.of("STUDENT_NAME", "OCCURRED_AT"));

        assertThat(definition.columns()).extracting(ExportColumnDefinition::code)
                .containsExactly("STUDENT_NAME", "OCCURRED_AT");
        assertThat(definition.templateColumnIndexes())
                .containsEntry("OCCURRED_AT", 0)
                .containsEntry("STUDENT_NAME", 1);
    }

    @Test
    void rejectsDuplicateUnknownAndMissingDefaultPlaceholders() throws IOException {
        assertThatThrownBy(() -> parser.parse(
                workbook("${OCCURRED_AT}", "${OCCURRED_AT}", "${STUDENT_NAME}"),
                allowed, List.of("OCCURRED_AT")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");

        assertThatThrownBy(() -> parser.parse(
                workbook("${OCCURRED_AT}", "${STUDENT_NAME}", "${SECRET}"),
                allowed, List.of("OCCURRED_AT")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不允许");

        assertThatThrownBy(() -> parser.parse(
                workbook("${OCCURRED_AT}", "${REMARK}"), allowed, List.of("OCCURRED_AT")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("默认字段");
    }

    @Test
    void rejectsFormulaCellsWithoutEvaluatingThem() throws IOException {
        byte[] content;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("模板");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("${OCCURRED_AT}");
            header.createCell(1).setCellValue("${STUDENT_NAME}");
            var formula = sheet.createRow(1).createCell(0, CellType.FORMULA);
            formula.setCellFormula("1+1");
            workbook.write(output);
            content = output.toByteArray();
        }

        assertThatThrownBy(() -> parser.parse(content, allowed, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("导出模板包含不允许的动态内容");
    }

    @Test
    void rejectsExternalLinksAndMacroWorkbookTypes() throws IOException {
        byte[] externalLinkContent;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             XSSFWorkbook external = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var row = workbook.createSheet("模板").createRow(0);
            row.createCell(0).setCellValue("${OCCURRED_AT}");
            row.createCell(1).setCellValue("${STUDENT_NAME}");
            external.createSheet("外部数据").createRow(0).createCell(0).setCellValue("受限内容");
            workbook.linkExternalWorkbook("external.xlsx", external);
            workbook.write(output);
            externalLinkContent = output.toByteArray();
        }

        assertThatThrownBy(() -> parser.parse(externalLinkContent, allowed, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("导出模板包含不允许的动态内容");

        byte[] macroContent;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.setWorkbookType(XSSFWorkbookType.XLSM);
            var row = workbook.createSheet("模板").createRow(0);
            row.createCell(0).setCellValue("${OCCURRED_AT}");
            row.createCell(1).setCellValue("${STUDENT_NAME}");
            workbook.write(output);
            macroContent = output.toByteArray();
        }

        assertThatThrownBy(() -> parser.parse(macroContent, allowed, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("导出模板包含不允许的动态内容");
    }

    private byte[] workbook(String... headers) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var row = workbook.createSheet("模板").createRow(0);
            for (int index = 0; index < headers.length; index++) {
                row.createCell(index).setCellValue(headers[index]);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
