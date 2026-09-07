package com.lingdong.learning.studentimport.application;

/** 从已通过校验的源工作簿读取的最小业务值。 */
public record StudentImportWorkbookRow(int rowNumber, String studentName, String gradeCode) { }
