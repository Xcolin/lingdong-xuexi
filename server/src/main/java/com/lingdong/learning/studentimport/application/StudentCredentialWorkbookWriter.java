package com.lingdong.learning.studentimport.application;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** 生成一次性交付的最小学员初始凭证 XLSX。 */
@Component
public class StudentCredentialWorkbookWriter {
    public byte[] write(List<StudentCredentialLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("学员初始凭证内容不能为空");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("初始凭证");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("源文件行号");
            header.createCell(1).setCellValue("学员账号");
            header.createCell(2).setCellValue("初始登录码");
            for (int index = 0; index < lines.size(); index++) {
                StudentCredentialLine line = lines.get(index);
                var row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(line.sourceRowNumber());
                row.createCell(1).setCellValue(line.studentAccount());
                row.createCell(2).setCellValue(line.initialLoginCode());
            }
            sheet.setColumnWidth(0, 14 * 256);
            sheet.setColumnWidth(1, 16 * 256);
            sheet.setColumnWidth(2, 16 * 256);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("学员初始凭证文件生成失败", exception);
        }
    }
}
