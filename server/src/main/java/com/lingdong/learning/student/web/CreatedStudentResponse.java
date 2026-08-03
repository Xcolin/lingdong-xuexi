package com.lingdong.learning.student.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.student.application.CreatedStudent;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentStatus;

import java.time.LocalDateTime;

/** 学生创建成功专用响应，初始登录码仅在当前响应中出现。 */
public record CreatedStudentResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String studentName,
        String gradeCode,
        StudentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String studentAccount,
        String initialLoginCode
) {
    static CreatedStudentResponse from(CreatedStudent createdStudent) {
        Student student = createdStudent.student();
        return new CreatedStudentResponse(
                student.id(), student.studentName(), student.gradeCode(), student.status(),
                student.createdAt(), student.updatedAt(),
                createdStudent.studentAccount(), createdStudent.initialLoginCode()
        );
    }
}
