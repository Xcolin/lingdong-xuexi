package com.lingdong.learning.student.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentStatus;

import java.time.LocalDateTime;

/** 对外返回的学生档案，不返回未来登录账号或关系表内部字段。 */
public record StudentResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String studentName,
        String gradeCode,
        StudentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static StudentResponse from(Student student) {
        return new StudentResponse(student.id(), student.studentName(), student.gradeCode(), student.status(),
                student.createdAt(), student.updatedAt());
    }
}
