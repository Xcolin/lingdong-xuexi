package com.lingdong.learning.studentimport.application;

import java.util.List;

/** 学员导入逐行结果分页。 */
public record StudentImportRowPage(List<StudentImportRowView> items, int page, int pageSize, long total) { }
