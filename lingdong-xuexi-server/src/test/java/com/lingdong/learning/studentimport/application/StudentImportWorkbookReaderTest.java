package com.lingdong.learning.studentimport.application;

import com.lingdong.learning.importjob.application.validation.ImportFieldMappingSnapshot;
import com.lingdong.learning.templateconfig.domain.ImportTemplateFieldDataType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentImportWorkbookReaderTest {
    private final StudentImportWorkbookReader reader = new StudentImportWorkbookReader();

    @Test
    void readsStudentNameAndOptionalGradeUsingValidatedFieldSnapshot() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("学员");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("学员姓名");
            header.createCell(1).setCellValue("年级编码");
            var first = sheet.createRow(1);
            first.createCell(0).setCellValue(" 张同学 ");
            first.createCell(1).setCellValue("G3");
            var formula = sheet.createRow(2);
            formula.createCell(0).setCellFormula("\"李同学\"");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(formula.getCell(0));
            sheet.createRow(3);

            List<StudentImportWorkbookRow> rows = reader.read(bytes(workbook), mappings());

            assertThat(rows).containsExactly(
                    new StudentImportWorkbookRow(2, "张同学", "G3"),
                    new StudentImportWorkbookRow(3, "李同学", null));
        }
    }

    @Test
    void rejectsMissingStudentNameMappingAndUnexpectedBlankName() throws Exception {
        assertThatThrownBy(() -> reader.read(new byte[] {1}, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("STUDENT_NAME");

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("学员");
            sheet.createRow(0).createCell(0).setCellValue("学员姓名");
            sheet.createRow(1).createCell(1).setCellValue("存在其他值");

            assertThatThrownBy(() -> reader.read(bytes(workbook), List.of(mappings().get(0))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("第2行");
        }
    }

    private List<ImportFieldMappingSnapshot> mappings() {
        return List.of(
                new ImportFieldMappingSnapshot("STUDENT_NAME", "学员姓名",
                        ImportTemplateFieldDataType.TEXT, true, 64, Set.of(), 10),
                new ImportFieldMappingSnapshot("GRADE_CODE", "年级编码",
                        ImportTemplateFieldDataType.TEXT, false, 64, Set.of(), 20));
    }

    private byte[] bytes(XSSFWorkbook workbook) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
