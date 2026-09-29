package com.lingdong.learning.exceptionreport.application;

/** 当前教师授权班级内可报备学生选项。 */
public record ExceptionReportStudentOption(Long studentId, String studentName, String studentAccountMasked) {
}
