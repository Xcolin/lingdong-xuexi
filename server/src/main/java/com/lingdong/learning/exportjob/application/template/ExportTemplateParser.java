package com.lingdong.learning.exportjob.application.template;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 校验 XLSX 模板动态内容，并将占位符限制在适配器白名单内。 */
public class ExportTemplateParser {
    private static final Pattern PLACEHOLDER =
            Pattern.compile("^\\$\\{([A-Z][A-Z0-9_]{0,63})}$");

    public ExportTemplateDefinition parse(
            byte[] content,
            List<ExportColumnDefinition> allowedColumns,
            List<String> selectedCodes
    ) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("导出模板文件不能为空");
        }
        Map<String, ExportColumnDefinition> allowed = allowedColumns(allowedColumns);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            rejectDynamicContent(workbook);
            Map<String, Integer> indexes = placeholderIndexes(workbook, allowed);
            requireDefaultColumns(allowed, indexes);
            List<ExportColumnDefinition> selected = selectedColumns(allowed, indexes, selectedCodes);
            return new ExportTemplateDefinition(selected, indexes);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("导出模板不是有效的 .xlsx 文件", exception);
        }
    }

    private Map<String, ExportColumnDefinition> allowedColumns(
            List<ExportColumnDefinition> allowedColumns
    ) {
        if (allowedColumns == null || allowedColumns.isEmpty()) {
            throw new IllegalArgumentException("导出字段白名单不能为空");
        }
        Map<String, ExportColumnDefinition> allowed = new LinkedHashMap<>();
        for (ExportColumnDefinition column : allowedColumns) {
            if (column == null || column.code() == null || column.header() == null
                    || column.header().isBlank()) {
                throw new IllegalArgumentException("导出字段定义不完整");
            }
            String code = normalizeCode(column.code());
            if (!PLACEHOLDER.matcher("${" + code + "}").matches()
                    || allowed.put(code, new ExportColumnDefinition(
                    code, column.header().trim(), column.defaultSelected())) != null) {
                throw new IllegalArgumentException("导出字段白名单包含重复或非法编码");
            }
        }
        return allowed;
    }

    private void rejectDynamicContent(XSSFWorkbook workbook) {
        if (workbook.isMacroEnabled() || !workbook.getExternalLinksTable().isEmpty()) {
            throw new IllegalArgumentException("导出模板包含不允许的动态内容");
        }
        for (Sheet sheet : workbook) {
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.FORMULA) {
                        throw new IllegalArgumentException("导出模板包含不允许的动态内容");
                    }
                }
            }
        }
    }

    private Map<String, Integer> placeholderIndexes(
            XSSFWorkbook workbook,
            Map<String, ExportColumnDefinition> allowed
    ) {
        if (workbook.getNumberOfSheets() == 0) {
            throw new IllegalArgumentException("导出模板必须包含工作表");
        }
        Row header = workbook.getSheetAt(0).getRow(0);
        if (header == null || header.getLastCellNum() <= 0) {
            throw new IllegalArgumentException("导出模板首行必须配置字段占位符");
        }
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < header.getLastCellNum(); index++) {
            Cell cell = header.getCell(index);
            if (cell == null || cell.getCellType() == CellType.BLANK) {
                continue;
            }
            if (cell.getCellType() != CellType.STRING) {
                throw new IllegalArgumentException("导出模板首行只能配置字段占位符");
            }
            Matcher matcher = PLACEHOLDER.matcher(cell.getStringCellValue().trim());
            if (!matcher.matches()) {
                throw new IllegalArgumentException("导出模板首行包含非法占位符");
            }
            String code = matcher.group(1);
            if (!allowed.containsKey(code)) {
                throw new IllegalArgumentException("导出模板包含适配器不允许的字段：" + code);
            }
            if (indexes.put(code, index) != null) {
                throw new IllegalArgumentException("导出模板包含重复字段占位符：" + code);
            }
        }
        if (indexes.isEmpty()) {
            throw new IllegalArgumentException("导出模板首行必须配置字段占位符");
        }
        return indexes;
    }

    private void requireDefaultColumns(
            Map<String, ExportColumnDefinition> allowed,
            Map<String, Integer> indexes
    ) {
        List<String> missing = allowed.values().stream()
                .filter(ExportColumnDefinition::defaultSelected)
                .map(ExportColumnDefinition::code)
                .filter(code -> !indexes.containsKey(code))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("导出模板缺少默认字段：" + String.join(",", missing));
        }
    }

    private List<ExportColumnDefinition> selectedColumns(
            Map<String, ExportColumnDefinition> allowed,
            Map<String, Integer> indexes,
            List<String> requestedCodes
    ) {
        List<String> codes = requestedCodes == null || requestedCodes.isEmpty()
                ? allowed.values().stream().filter(ExportColumnDefinition::defaultSelected)
                .map(ExportColumnDefinition::code).toList()
                : requestedCodes.stream().map(this::normalizeCode).toList();
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("至少选择一个导出字段");
        }
        Set<String> unique = new HashSet<>();
        List<ExportColumnDefinition> selected = new ArrayList<>();
        for (String code : codes) {
            if (!unique.add(code)) {
                throw new IllegalArgumentException("导出字段不能重复选择：" + code);
            }
            ExportColumnDefinition column = allowed.get(code);
            if (column == null || !indexes.containsKey(code)) {
                throw new IllegalArgumentException("导出字段不允许或模板未配置：" + code);
            }
            selected.add(column);
        }
        return List.copyOf(selected);
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("导出字段编码不能为空");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
