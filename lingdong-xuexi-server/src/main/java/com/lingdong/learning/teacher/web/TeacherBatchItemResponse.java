package com.lingdong.learning.teacher.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.teacher.application.TeacherBatchItemResult;

/** 教师批量操作单项响应。 */
public record TeacherBatchItemResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long teacherUserId,
        boolean success,
        String errorCode,
        String message
) {
    static TeacherBatchItemResponse from(TeacherBatchItemResult item) {
        return new TeacherBatchItemResponse(
                item.teacherUserId(), item.success(), item.errorCode(), item.message());
    }
}
