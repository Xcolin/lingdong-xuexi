package com.lingdong.learning.importjob.application.validation;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** 生成不包含原始业务值的导入校验错误工作簿。 */
@Component
public class ImportValidationErrorWorkbookWriter {
    private static final String[] HEADERS = {
            "行号", "列名", "字段编码", "错误编码", "错误说明"
    };

    public byte[] write(List<ImportCellError> errors) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("错误工作簿至少需要一条校验错误");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("校验错误");
            var header = sheet.createRow(0);
            for (int column = 0; column < HEADERS.length; column++) {
                header.createCell(column).setCellValue(HEADERS[column]);
            }
            for (int index = 0; index < errors.size(); index++) {
                ImportCellError error = errors.get(index);
                var row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(error.rowNumber());
                row.createCell(1).setCellValue(safe(error.columnName()));
                row.createCell(2).setCellValue(safe(error.fieldCode()));
                row.createCell(3).setCellValue(safe(error.errorCode()));
                row.createCell(4).setCellValue(safe(error.message()));
            }
            sheet.setColumnWidth(0, 12 * 256);
            sheet.setColumnWidth(1, 24 * 256);
            sheet.setColumnWidth(2, 24 * 256);
            sheet.setColumnWidth(3, 28 * 256);
            sheet.setColumnWidth(4, 48 * 256);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("校验错误文件生成失败", exception);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
