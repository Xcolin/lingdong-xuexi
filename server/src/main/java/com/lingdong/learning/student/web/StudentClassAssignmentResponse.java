package com.lingdong.learning.student.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.student.application.StudentClassAssignment;

/** 学生当前班级响应，所有雪花标识按字符串序列化。 */
public record StudentClassAssignmentResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long studentId,
        @JsonSerialize(using = ToStringSerializer.class) Long classOrganizationId,
        String status
) {
    static StudentClassAssignmentResponse from(StudentClassAssignment assignment) {
        return new StudentClassAssignmentResponse(
                assignment.studentId(), assignment.classOrganizationId(), assignment.status());
    }
}
