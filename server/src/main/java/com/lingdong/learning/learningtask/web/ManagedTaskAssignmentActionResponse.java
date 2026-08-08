package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.ManagedTaskAssignmentActionResult;

/** 管理角色任务实例操作响应。 */
public record ManagedTaskAssignmentActionResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long assignmentId,
        String currentStatus
) {
    static ManagedTaskAssignmentActionResponse from(ManagedTaskAssignmentActionResult result) {
        return new ManagedTaskAssignmentActionResponse(result.assignmentId(), result.currentStatus());
    }
}
