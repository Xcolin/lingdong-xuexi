package com.lingdong.learning.importjob.application.validation;

import java.util.List;

/** 单个非空数据行的校验结果。 */
public record ImportRowValidationResult(
        int rowNumber,
        boolean valid,
        List<ImportCellError> errors
) {
    public ImportRowValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public String errorSummary() {
        return errors.stream().map(ImportCellError::message)
                .distinct().collect(java.util.stream.Collectors.joining("；"));
    }
}
