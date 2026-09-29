package com.lingdong.learning.growthpoint.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.growthpoint.application.GrowthPointStudentOption;

/** 家长积分页学生选择项。 */
public record GrowthPointStudentOptionResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long studentId,
        String studentName
) {
    static GrowthPointStudentOptionResponse from(GrowthPointStudentOption option) {
        return new GrowthPointStudentOptionResponse(option.studentId(), option.studentName());
    }
}
