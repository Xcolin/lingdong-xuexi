package com.lingdong.learning.student.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentStatus;

import java.time.LocalDateTime;

/** 范围内的学生档案与账号标识；账号标识用于入学申请，不包含登录凭证。 */
public record StudentResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String studentName,
        String gradeCode,
        StudentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        @JsonSerialize(using = ToStringSerializer.class) Long studentUserId
) {
    static StudentResponse from(Student student) {
        return new StudentResponse(student.id(), student.studentName(), student.gradeCode(), student.status(),
                student.createdAt(), student.updatedAt(), student.studentUserId());
    }
}
