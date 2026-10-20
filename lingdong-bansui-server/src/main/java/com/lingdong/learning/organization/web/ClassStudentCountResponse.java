package com.lingdong.learning.organization.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** 班级有效学员计数响应，组织标识按字符串输出。 */
public record ClassStudentCountResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long organizationId,
        long memberCount
) {
}
