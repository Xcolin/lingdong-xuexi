package com.lingdong.learning.student.application;

import com.lingdong.learning.student.domain.Student;

import java.util.List;

/** 学生目录分页结果，避免 Web 层参与分页和范围判断。 */
public record StudentDirectoryPage(List<Student> items, int page, int pageSize, long total) {
}
