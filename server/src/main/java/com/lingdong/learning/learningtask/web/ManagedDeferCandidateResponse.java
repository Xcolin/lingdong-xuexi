package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.ManagedDeferCandidateView;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;
import com.lingdong.learning.learningtask.domain.TaskDeferType;

import java.time.LocalDate;

/** 管理端可顺延任务摘要。 */
public record ManagedDeferCandidateResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long assignmentId,
        String title,
        @JsonSerialize(using = ToStringSerializer.class) Long studentId,
        String studentName,
        LearningTaskSourceType sourceType,
        String sourceOrganizationName,
        LocalDate scheduledDate,
        TaskAssignmentStatus currentStatus,
        TaskDeferType lastDeferType,
        boolean overnightMigrated
) {
    static ManagedDeferCandidateResponse from(ManagedDeferCandidateView view) {
        return new ManagedDeferCandidateResponse(
                view.assignmentId(), view.title(), view.studentId(), view.studentName(),
                view.sourceType(), view.sourceOrganizationName(), view.scheduledDate(),
                view.currentStatus(), view.lastDeferType(), view.overnightMigrated());
    }
}
