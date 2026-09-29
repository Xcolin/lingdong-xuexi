package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.TeacherOption;

import java.util.List;

/** 任务教师候选响应，只返回显示名和授权班级标识。 */
public record TeacherOptionResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long userId,
        String displayName,
        @JsonSerialize(contentUsing = ToStringSerializer.class) List<Long> classOrganizationIds
) {
    static TeacherOptionResponse from(TeacherOption option) {
        return new TeacherOptionResponse(
                option.userId(), option.displayName(), option.classOrganizationIds());
    }
}
