package com.lingdong.learning.importjob.application.validation;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImportValidationErrorWorkbookWriterTest {
    @Test
    void writesOnlyFiveSafeDiagnosticColumns() throws Exception {
        ImportValidationErrorWorkbookWriter writer = new ImportValidationErrorWorkbookWriter();
        byte[] content = writer.write(List.of(new ImportCellError(
                2, "学生账号", "STUDENT_CODE", "REQUIRED", "学生账号不能为空"
        )));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getPhysicalNumberOfCells()).isEqualTo(5);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("行号");
            assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("错误说明");
            assertThat(sheet.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("学生账号不能为空");
            for (var cell : sheet.getRow(0)) {
                assertThat(cell.getStringCellValue()).doesNotContain("原始值");
            }
            for (var cell : sheet.getRow(1)) {
                assertThat(cell.toString()).doesNotContain("原始值");
            }
        }
    }
}
