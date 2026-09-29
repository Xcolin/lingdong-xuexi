package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.domain.TeacherClassRelation;
import com.lingdong.learning.learningtask.domain.TeacherClassStatus;

import java.time.LocalDateTime;

/** 教师班级关系响应，雪花标识按字符串输出。 */
public record TeacherClassResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long teacherUserId,
        @JsonSerialize(using = ToStringSerializer.class) Long classOrganizationId,
        TeacherClassStatus status,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo
) {
    static TeacherClassResponse from(TeacherClassRelation relation) {
        return new TeacherClassResponse(
                relation.id(), relation.teacherUserId(), relation.classOrganizationId(), relation.status(),
                relation.effectiveFrom(), relation.effectiveTo());
    }
}
