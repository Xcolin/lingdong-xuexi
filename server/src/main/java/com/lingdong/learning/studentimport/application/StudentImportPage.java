package com.lingdong.learning.studentimport.application;

import java.util.List;

/** 学员导入执行分页。 */
public record StudentImportPage(List<StudentImportView> items, int page, int pageSize, long total) { }
