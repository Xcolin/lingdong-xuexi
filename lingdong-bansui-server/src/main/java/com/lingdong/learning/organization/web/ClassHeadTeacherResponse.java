package com.lingdong.learning.organization.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** 单个班级的班主任展示信息，未设置班主任时 headTeacherName 为 null。 */
public record ClassHeadTeacherResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long organizationId,
        String headTeacherName
) {
}
