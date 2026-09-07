package com.lingdong.learning.importjob.application.validation;

/** 不携带原始单元格值的导入校验错误。 */
public record ImportCellError(
        int rowNumber,
        String columnName,
        String fieldCode,
        String errorCode,
        String message
) { }
