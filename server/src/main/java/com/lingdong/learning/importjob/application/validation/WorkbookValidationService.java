package com.lingdong.learning.importjob.application.validation;

import com.lingdong.learning.templateconfig.domain.ImportTemplateFieldDataType;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 安全读取首个 XLSX 工作表并执行无业务副作用的字段校验。 */
@Service
public class WorkbookValidationService {
    private static final long MAX_WORKBOOK_BYTES = 50L * 1024 * 1024;
    private static final Pattern INTEGER_PATTERN = Pattern.compile("[+-]?\\d+");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("[+-]?\\d+(\\.\\d+)?");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    static {
        ZipSecureFile.setMinInflateRatio(0.01d);
        ZipSecureFile.setMaxEntrySize(100L * 1024 * 1024);
        ZipSecureFile.setMaxTextSize(10L * 1024 * 1024);
    }

    public WorkbookValidationResult validate(
            byte[] content,
            List<ImportFieldMappingSnapshot> mappings,
            int maxRows
    ) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("XLSX 文件不能为空");
        }
        if (content.length > MAX_WORKBOOK_BYTES) {
            throw new IllegalArgumentException("XLSX 文件大小超过安全上限");
        }
        if (mappings == null || mappings.isEmpty()) {
            throw new IllegalArgumentException("导入字段映射不能为空");
        }
        if (maxRows < 1) {
            throw new IllegalArgumentException("导入最大行数必须为正整数");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("XLSX 文件至少需要一个工作表");
            }
            return validateSheet(workbook.getSheetAt(0), orderedMappings(mappings), maxRows);
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("XLSX")) {
                throw exception;
            }
            throw new IllegalArgumentException("XLSX 文件损坏或格式不受支持", exception);
        } catch (Exception exception) {
            throw new IllegalArgumentException("XLSX 文件损坏或格式不受支持", exception);
        }
    }

    private WorkbookValidationResult validateSheet(
            Sheet sheet,
            List<ImportFieldMappingSnapshot> mappings,
            int maxRows
    ) {
        HeaderResult header = validateHeader(sheet.getRow(0), mappings);
        if (!header.errors().isEmpty()) {
            return new WorkbookValidationResult(0, 0, 0, List.of(), header.errors());
        }
        List<Row> dataRows = nonBlankRows(sheet, header.columnIndexes().keySet());
        if (dataRows.size() > maxRows) {
            ImportCellError error = new ImportCellError(
                    0, "", "", "ROW_LIMIT_EXCEEDED",
                    "数据行数超过允许上限 " + maxRows + " 行"
            );
            return new WorkbookValidationResult(dataRows.size(), 0, 0, List.of(), List.of(error));
        }

        List<ImportRowValidationResult> rowResults = new ArrayList<>(dataRows.size());
        List<ImportCellError> allErrors = new ArrayList<>();
        int validRows = 0;
        for (Row row : dataRows) {
            List<ImportCellError> errors = validateRow(row, mappings, header.columnIndexes());
            boolean valid = errors.isEmpty();
            if (valid) {
                validRows++;
            } else {
                allErrors.addAll(errors);
            }
            rowResults.add(new ImportRowValidationResult(row.getRowNum() + 1, valid, errors));
        }
        return new WorkbookValidationResult(
                dataRows.size(), validRows, dataRows.size() - validRows, rowResults, allErrors);
    }

    private HeaderResult validateHeader(Row header, List<ImportFieldMappingSnapshot> mappings) {
        List<ImportCellError> errors = new ArrayList<>();
        Map<String, Integer> columnIndexes = new LinkedHashMap<>();
        Set<String> duplicated = new HashSet<>();
        int lastCell = header == null ? 0 : Math.max(header.getLastCellNum(), 0);
        for (int column = 0; column < lastCell; column++) {
            String name = cellText(header.getCell(column)).trim();
            if (name.isEmpty()) {
                continue;
            }
            if (columnIndexes.putIfAbsent(name, column) != null) {
                duplicated.add(name);
            }
        }
        for (String name : duplicated) {
            errors.add(new ImportCellError(
                    1, name, "", "HEADER_DUPLICATED", "表头名称重复：" + name));
        }

        Map<String, ImportFieldMappingSnapshot> byName = new HashMap<>();
        for (ImportFieldMappingSnapshot mapping : mappings) {
            byName.put(mapping.columnName(), mapping);
            if (!columnIndexes.containsKey(mapping.columnName())) {
                errors.add(new ImportCellError(
                        1, mapping.columnName(), mapping.fieldCode(), "HEADER_MISSING",
                        "缺少表头：" + mapping.columnName()));
            }
        }
        for (String name : columnIndexes.keySet()) {
            if (!byName.containsKey(name)) {
                errors.add(new ImportCellError(
                        1, name, "", "HEADER_UNKNOWN", "存在未知表头：" + name));
            }
        }
        return new HeaderResult(Map.copyOf(columnIndexes), List.copyOf(errors));
    }

    private List<Row> nonBlankRows(Sheet sheet, Set<String> headerNames) {
        List<Row> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            int lastCell = Math.max(row.getLastCellNum(), 0);
            boolean hasValue = false;
            for (int column = 0; column < lastCell; column++) {
                if (!cellText(row.getCell(column)).trim().isEmpty()) {
                    hasValue = true;
                    break;
                }
            }
            if (hasValue) {
                rows.add(row);
            }
        }
        return rows;
    }

    private List<ImportCellError> validateRow(
            Row row,
            List<ImportFieldMappingSnapshot> mappings,
            Map<String, Integer> columnIndexes
    ) {
        List<ImportCellError> errors = new ArrayList<>();
        for (ImportFieldMappingSnapshot mapping : mappings) {
            Cell cell = row.getCell(columnIndexes.get(mapping.columnName()));
            String value = cellText(cell).trim();
            if (value.isEmpty()) {
                if (mapping.required()) {
                    errors.add(error(row, mapping, "REQUIRED",
                            mapping.columnName() + "不能为空"));
                }
                continue;
            }
            if (mapping.dataType() == ImportTemplateFieldDataType.TEXT
                    && mapping.maxLength() != null
                    && value.length() > mapping.maxLength()) {
                errors.add(error(row, mapping, "TOO_LONG",
                        mapping.columnName() + "长度不能超过 " + mapping.maxLength()));
            }
            if (!matchesType(cell, value, mapping.dataType())) {
                errors.add(error(row, mapping, "TYPE_INVALID",
                        mapping.columnName() + "格式不正确，应为" + typeName(mapping.dataType())));
            }
            if (!mapping.dictionaryValues().isEmpty()
                    && !mapping.dictionaryValues().contains(value)) {
                errors.add(error(row, mapping, "DICTIONARY_INVALID",
                        mapping.columnName() + "不是允许的字典项编码"));
            }
        }
        return List.copyOf(errors);
    }

    private boolean matchesType(Cell cell, String value, ImportTemplateFieldDataType dataType) {
        return switch (dataType) {
            case TEXT -> true;
            case INTEGER -> INTEGER_PATTERN.matcher(value).matches();
            case DECIMAL -> decimal(value);
            case DATE -> excelDate(cell) || parseDate(value);
            case DATETIME -> excelDate(cell) || parseDateTime(value);
            case BOOLEAN -> Set.of("是", "否", "true", "false").contains(value.toLowerCase());
        };
    }

    private boolean decimal(String value) {
        if (!DECIMAL_PATTERN.matcher(value).matches()) {
            return false;
        }
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean excelDate(Cell cell) {
        if (cell == null || effectiveType(cell) != CellType.NUMERIC) {
            return false;
        }
        return DateUtil.isCellDateFormatted(cell);
    }

    private boolean parseDate(String value) {
        try {
            LocalDate.parse(value, DATE_FORMATTER);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private boolean parseDateTime(String value) {
        try {
            LocalDateTime.parse(value, DATETIME_FORMATTER);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (effectiveType(cell)) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue())
                    .stripTrailingZeros().toPlainString();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case BLANK -> "";
            case ERROR, _NONE -> "";
            case FORMULA -> "";
        };
    }

    private CellType effectiveType(Cell cell) {
        return cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();
    }

    private ImportCellError error(
            Row row,
            ImportFieldMappingSnapshot mapping,
            String code,
            String message
    ) {
        return new ImportCellError(
                row.getRowNum() + 1, mapping.columnName(), mapping.fieldCode(), code, message);
    }

    private String typeName(ImportTemplateFieldDataType type) {
        return switch (type) {
            case TEXT -> "文本";
            case INTEGER -> "整数";
            case DECIMAL -> "小数";
            case DATE -> "日期 yyyy-MM-dd";
            case DATETIME -> "日期时间 yyyy-MM-dd HH:mm:ss";
            case BOOLEAN -> "是、否、true 或 false";
        };
    }

    private List<ImportFieldMappingSnapshot> orderedMappings(
            List<ImportFieldMappingSnapshot> mappings
    ) {
        return mappings.stream()
                .sorted(java.util.Comparator.comparing(ImportFieldMappingSnapshot::sortOrder))
                .toList();
    }

    private record HeaderResult(
            Map<String, Integer> columnIndexes,
            List<ImportCellError> errors
    ) { }
}
