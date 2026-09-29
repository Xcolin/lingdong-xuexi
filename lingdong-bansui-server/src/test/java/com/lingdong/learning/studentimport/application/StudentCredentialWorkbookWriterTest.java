package com.lingdong.learning.studentimport.application;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudentCredentialWorkbookWriterTest {
    @Test
    void writesOnlySourceRowAccountAndInitialCode() throws Exception {
        StudentCredentialWorkbookWriter writer = new StudentCredentialWorkbookWriter();

        byte[] content = writer.write(List.of(
                new StudentCredentialLine(2, "12345678", "2468"),
                new StudentCredentialLine(3, "87654321", "1357")));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("源文件行号");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("学员账号");
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("初始登录码");
            assertThat(sheet.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("12345678");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("2468");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
        }
    }
}
