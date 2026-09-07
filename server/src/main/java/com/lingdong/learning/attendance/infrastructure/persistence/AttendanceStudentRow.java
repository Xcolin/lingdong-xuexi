package com.lingdong.learning.attendance.infrastructure.persistence;

/** 指定日期已建立有效班级关系的学生。 */
public record AttendanceStudentRow(Long studentId, String studentName) { }
