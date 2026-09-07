package com.lingdong.learning.importjob.application.validation;

import java.util.List;

/** 一个工作簿完成表头和逐行校验后的汇总结果。 */
public record WorkbookValidationResult(
        int totalRows,
        int validRows,
        int invalidRows,
        List<ImportRowValidationResult> rows,
        List<ImportCellError> errors
) {
    public WorkbookValidationResult {
        rows = rows == null ? List.of() : List.copyOf(rows);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean valid() {
        return errors.isEmpty() && invalidRows == 0;
    }
}
