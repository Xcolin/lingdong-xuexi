package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.StudentOption;
import com.lingdong.learning.student.domain.ParentRelationshipRole;

/** 任务学生候选响应，不包含手机号或明文账号。 */
public record StudentOptionResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String studentName,
        String studentAccountMasked,
        @JsonSerialize(using = ToStringSerializer.class) Long currentClassId,
        String currentClassName,
        ParentRelationshipRole relationshipRole
) {
    static StudentOptionResponse from(StudentOption option) {
        return new StudentOptionResponse(
                option.id(), option.studentName(), option.studentAccountMasked(),
                option.currentClassId(), option.currentClassName(), option.relationshipRole());
    }
}
