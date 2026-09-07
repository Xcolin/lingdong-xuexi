package com.lingdong.learning.importjob.application.validation;

import com.lingdong.learning.templateconfig.domain.ImportTemplateFieldDataType;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkbookValidationServiceTest {
    private final WorkbookValidationService service = new WorkbookValidationService();

    @Test
    void validatesSixTypesDictionaryAndFormulaCacheWhileIgnoringBlankRows() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("导入数据");
            var header = sheet.createRow(0);
            String[] names = {"姓名", "年龄", "分数", "生日", "提交时间", "是否启用", "状态"};
            for (int index = 0; index < names.length; index++) {
                header.createCell(index).setCellValue(names[index]);
            }
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("张同学");
            row.createCell(1).setCellValue(12);
            row.createCell(2).setCellValue(98.5);
            var dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat()
                    .getFormat("yyyy-mm-dd"));
            var dateTimeStyle = workbook.createCellStyle();
            dateTimeStyle.setDataFormat(workbook.getCreationHelper().createDataFormat()
                    .getFormat("yyyy-mm-dd hh:mm:ss"));
            row.createCell(3).setCellValue(LocalDate.of(2014, 5, 6));
            row.getCell(3).setCellStyle(dateStyle);
            row.createCell(4).setCellValue(LocalDateTime.of(2026, 9, 1, 8, 30));
            row.getCell(4).setCellStyle(dateTimeStyle);
            row.createCell(5).setCellValue("是");
            row.createCell(6).setCellValue("ACTIVE");
            var formula = sheet.createRow(2);
            formula.createCell(0).setCellValue("李同学");
            formula.createCell(1).setCellFormula("6+7");
            workbook.getCreationHelper().createFormulaEvaluator()
                    .evaluateFormulaCell(formula.getCell(1));
            assertThat(formula.getCell(1).getCellType()).isEqualTo(CellType.FORMULA);
            formula.createCell(2).setCellValue("88.25");
            formula.createCell(3).setCellValue("2013-04-05");
            formula.createCell(4).setCellValue("2026-09-01 09:30:00");
            formula.createCell(5).setCellValue("false");
            formula.createCell(6).setCellValue("INACTIVE");
            sheet.createRow(3).createCell(0, CellType.BLANK);

            WorkbookValidationResult result = service.validate(
                    bytes(workbook), mappings(), 100);

            assertThat(result.totalRows()).isEqualTo(2);
            assertThat(result.validRows()).isEqualTo(2);
            assertThat(result.invalidRows()).isZero();
            assertThat(result.errors()).isEmpty();
        }
    }

    @Test
    void reportsHeaderAndCellErrorsWithoutReturningOriginalValues() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("错误数据");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("姓名");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("未知列");

            WorkbookValidationResult headerResult = service.validate(
                    bytes(workbook), mappings(), 100);
            assertThat(headerResult.errors()).extracting(ImportCellError::errorCode)
                    .contains("HEADER_DUPLICATED", "HEADER_UNKNOWN", "HEADER_MISSING");
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("字段错误");
            var header = sheet.createRow(0);
            List<ImportFieldMappingSnapshot> mappings = mappings();
            for (int index = 0; index < mappings.size(); index++) {
                header.createCell(index).setCellValue(mappings.get(index).columnName());
            }
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("");
            row.createCell(1).setCellValue("12.5");
            row.createCell(2).setCellValue("1,000.00");
            row.createCell(3).setCellValue("2026-02-30");
            row.createCell(4).setCellValue("错误时间");
            row.createCell(5).setCellValue("未知");
            row.createCell(6).setCellValue("SECRET_VALUE");

            WorkbookValidationResult result = service.validate(bytes(workbook), mappings, 100);

            assertThat(result.invalidRows()).isEqualTo(1);
            assertThat(result.errors()).extracting(ImportCellError::errorCode)
                    .contains("REQUIRED", "TYPE_INVALID", "DICTIONARY_INVALID");
            assertThat(result.errors()).allSatisfy(error ->
                    assertThat(error.message()).doesNotContain("SECRET_VALUE"));
        }
    }

    @Test
    void rejectsBrokenWorkbookAndReturnsRowLimitValidationError() throws Exception {
        assertThatThrownBy(() -> service.validate(new byte[]{1, 2, 3}, mappings(), 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("XLSX");

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("超限数据");
            var header = sheet.createRow(0);
            List<ImportFieldMappingSnapshot> mappings = mappings();
            for (int index = 0; index < mappings.size(); index++) {
                header.createCell(index).setCellValue(mappings.get(index).columnName());
            }
            sheet.createRow(1).createCell(0).setCellValue("第一行");
            sheet.createRow(2).createCell(0).setCellValue("第二行");

            WorkbookValidationResult result = service.validate(bytes(workbook), mappings, 1);

            assertThat(result.errors()).extracting(ImportCellError::errorCode)
                    .containsExactly("ROW_LIMIT_EXCEEDED");
            assertThat(result.validRows()).isZero();
        }
    }

    private List<ImportFieldMappingSnapshot> mappings() {
        return List.of(
                field("NAME", "姓名", ImportTemplateFieldDataType.TEXT, true, 20, Set.of(), 10),
                field("AGE", "年龄", ImportTemplateFieldDataType.INTEGER, true, null, Set.of(), 20),
                field("SCORE", "分数", ImportTemplateFieldDataType.DECIMAL, true, null, Set.of(), 30),
                field("BIRTH_DATE", "生日", ImportTemplateFieldDataType.DATE, true, null, Set.of(), 40),
                field("SUBMITTED_AT", "提交时间", ImportTemplateFieldDataType.DATETIME, true, null, Set.of(), 50),
                field("ACTIVE", "是否启用", ImportTemplateFieldDataType.BOOLEAN, true, null, Set.of(), 60),
                field("STATUS", "状态", ImportTemplateFieldDataType.TEXT, true, 20,
                        Set.of("ACTIVE", "INACTIVE"), 70)
        );
    }

    private ImportFieldMappingSnapshot field(
            String code,
            String name,
            ImportTemplateFieldDataType type,
            boolean required,
            Integer maxLength,
            Set<String> dictionaryValues,
            int sortOrder
    ) {
        return new ImportFieldMappingSnapshot(
                code, name, type, required, maxLength, dictionaryValues, sortOrder);
    }

    private byte[] bytes(XSSFWorkbook workbook) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
