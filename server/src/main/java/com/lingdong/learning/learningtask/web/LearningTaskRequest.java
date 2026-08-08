package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.CreateLearningTaskCommand;
import com.lingdong.learning.learningtask.application.LearningTaskDraftInput;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Web 端创建和更新任务草稿的统一请求。 */
public record LearningTaskRequest(
        @NotNull LearningTaskSourceType sourceType,
        Long sourceOrganizationId,
        @NotBlank @Size(max = 50) String title,
        @NotNull @Min(1) @Max(3) Integer difficultyLevel,
        @NotNull @Min(1) @Max(1440) Integer durationMinutes,
        @NotNull LocalDate scheduledDate,
        @Size(max = 64) String categoryCode,
        @Size(max = 20) List<@Size(max = 64) String> tagCodes,
        @Size(max = 200) String remark,
        Long reviewerUserId,
        @NotEmpty List<@Valid LearningTaskTargetRequest> targets,
        Boolean recurrenceEnabled,
        LocalDate recurrenceEndDate
) {
    CreateLearningTaskCommand toCommand() {
        return new CreateLearningTaskCommand(
                sourceType, sourceOrganizationId, reviewerUserId,
                new LearningTaskDraftInput(
                        title, difficultyLevel, durationMinutes, scheduledDate, categoryCode,
                        tagCodes, remark, targets.stream().map(LearningTaskTargetRequest::toInput).toList(),
                        Boolean.TRUE.equals(recurrenceEnabled), recurrenceEndDate));
    }
}
