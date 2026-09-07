package com.lingdong.learning.studentimport.application;

import com.lingdong.learning.importjob.application.validation.ImportFieldMappingSnapshot;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 只从已通过 V56 校验的工作簿读取学员开户所需字段，不执行公式。 */
@Component
public class StudentImportWorkbookReader {
    private static final String STUDENT_NAME = "STUDENT_NAME";
    private static final String GRADE_CODE = "GRADE_CODE";

    public List<StudentImportWorkbookRow> read(
            byte[] content,
            List<ImportFieldMappingSnapshot> mappings
    ) {
        ImportFieldMappingSnapshot nameMapping = findRequiredMapping(mappings, STUDENT_NAME);
        ImportFieldMappingSnapshot gradeMapping = findMapping(mappings, GRADE_CODE);
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("学员导入 XLSX 文件不能为空");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("学员导入 XLSX 文件没有工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> columns = headerColumns(sheet.getRow(0));
            Integer nameColumn = columns.get(nameMapping.columnName());
            Integer gradeColumn = gradeMapping == null ? null : columns.get(gradeMapping.columnName());
            if (nameColumn == null) {
                throw new IllegalArgumentException("学员导入表头缺少 STUDENT_NAME 对应列");
            }
            List<StudentImportWorkbookRow> rows = new ArrayList<>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || blankRow(row)) {
                    continue;
                }
                String name = normalized(cellText(row.getCell(nameColumn)));
                if (name == null) {
                    throw new IllegalArgumentException("学员导入第" + (index + 1) + "行姓名为空");
                }
                String gradeCode = gradeColumn == null
                        ? null : normalized(cellText(row.getCell(gradeColumn)));
                rows.add(new StudentImportWorkbookRow(index + 1, name, gradeCode));
            }
            return List.copyOf(rows);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("学员导入 XLSX 文件无法读取", exception);
        }
    }

    private ImportFieldMappingSnapshot findRequiredMapping(
            List<ImportFieldMappingSnapshot> mappings,
            String fieldCode
    ) {
        ImportFieldMappingSnapshot mapping = findMapping(mappings, fieldCode);
        if (mapping == null) {
            throw new IllegalArgumentException("学员导入模板缺少 " + fieldCode + " 字段映射");
        }
        return mapping;
    }

    private ImportFieldMappingSnapshot findMapping(
            List<ImportFieldMappingSnapshot> mappings,
            String fieldCode
    ) {
        if (mappings == null) {
            return null;
        }
        return mappings.stream().filter(mapping -> fieldCode.equals(mapping.fieldCode()))
                .findFirst().orElse(null);
    }

    private Map<String, Integer> headerColumns(Row header) {
        if (header == null) {
            throw new IllegalArgumentException("学员导入表头不能为空");
        }
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < header.getLastCellNum(); index++) {
            String value = normalized(cellText(header.getCell(index)));
            if (value != null) {
                columns.put(value, index);
            }
        }
        return columns;
    }

    private boolean blankRow(Row row) {
        for (int index = row.getFirstCellNum(); index >= 0 && index < row.getLastCellNum(); index++) {
            if (normalized(cellText(row.getCell(index))) != null) {
                return false;
            }
        }
        return true;
    }

    private String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue())
                    .stripTrailingZeros().toPlainString();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case BLANK, ERROR, _NONE, FORMULA -> "";
        };
    }

    private String normalized(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
