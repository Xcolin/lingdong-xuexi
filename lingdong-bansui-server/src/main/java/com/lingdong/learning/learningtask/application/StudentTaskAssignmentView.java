package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.attachment.application.TaskAttachmentView;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.TaskDeferType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 学生本人可见的任务实例视图。 */
public record StudentTaskAssignmentView(
        Long id,
        Long taskId,
        LearningTaskSourceType sourceType,
        Long sourceOrganizationId,
        String sourceOrganizationName,
        String title,
        Integer difficultyLevel,
        Integer basePoints,
        Integer durationMinutes,
        LocalDate scheduledDate,
        LocalDateTime dueAt,
        String categoryCode,
        String remark,
        String currentStatus,
        String effectiveStatus,
        Long currentReviewerId,
        String reviewerDisplayName,
        TaskDeferType lastDeferType,
        boolean overnightMigrated,
        ActiveTaskPauseView activePause,
        TaskCheckInView latestCheckIn,
        List<String> tagCodes,
        String description,
        List<String> requirementItems,
        List<Long> exampleFileIds,
        LocalDate lastDeferredToDate,
        List<TaskTimelineNodeView> timeline,
        List<TaskAttachmentView> exampleFiles
) {
    public StudentTaskAssignmentView {
        tagCodes = tagCodes == null ? List.of() : List.copyOf(tagCodes);
        requirementItems = requirementItems == null ? List.of() : List.copyOf(requirementItems);
        exampleFileIds = exampleFileIds == null ? List.of() : List.copyOf(exampleFileIds);
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
        exampleFiles = exampleFiles == null ? List.of() : List.copyOf(exampleFiles);
    }

    public StudentTaskAssignmentView withTagCodes(List<String> codes) {
        return new StudentTaskAssignmentView(
                id, taskId, sourceType, sourceOrganizationId, sourceOrganizationName, title,
                difficultyLevel, basePoints, durationMinutes, scheduledDate, dueAt, categoryCode, remark,
                currentStatus, effectiveStatus, currentReviewerId, reviewerDisplayName,
                lastDeferType, overnightMigrated, activePause, latestCheckIn, codes,
                description, requirementItems, exampleFileIds, lastDeferredToDate, timeline, exampleFiles);
    }

    public StudentTaskAssignmentView withLatestCheckIn(TaskCheckInView checkIn) {
        return new StudentTaskAssignmentView(
                id, taskId, sourceType, sourceOrganizationId, sourceOrganizationName, title,
                difficultyLevel, basePoints, durationMinutes, scheduledDate, dueAt, categoryCode, remark,
                currentStatus, effectiveStatus, currentReviewerId, reviewerDisplayName,
                lastDeferType, overnightMigrated, activePause, checkIn, tagCodes,
                description, requirementItems, exampleFileIds, lastDeferredToDate, timeline, exampleFiles);
    }

    public StudentTaskAssignmentView withTimeline(List<TaskTimelineNodeView> nodes) {
        return new StudentTaskAssignmentView(
                id, taskId, sourceType, sourceOrganizationId, sourceOrganizationName, title,
                difficultyLevel, basePoints, durationMinutes, scheduledDate, dueAt, categoryCode, remark,
                currentStatus, effectiveStatus, currentReviewerId, reviewerDisplayName,
                lastDeferType, overnightMigrated, activePause, latestCheckIn, tagCodes,
                description, requirementItems, exampleFileIds, lastDeferredToDate, nodes, exampleFiles);
    }

    public StudentTaskAssignmentView withExampleFiles(List<TaskAttachmentView> files) {
        return new StudentTaskAssignmentView(
                id, taskId, sourceType, sourceOrganizationId, sourceOrganizationName, title,
                difficultyLevel, basePoints, durationMinutes, scheduledDate, dueAt, categoryCode, remark,
                currentStatus, effectiveStatus, currentReviewerId, reviewerDisplayName,
                lastDeferType, overnightMigrated, activePause, latestCheckIn, tagCodes,
                description, requirementItems, exampleFileIds, lastDeferredToDate, timeline, files);
    }
}
