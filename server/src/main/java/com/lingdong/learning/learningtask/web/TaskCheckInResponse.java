package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.TaskCheckInView;

import java.time.LocalDateTime;
import java.util.List;

import com.lingdong.learning.attachment.web.TaskAttachmentResponse;

/** 最近一次任务打卡响应。 */
public record TaskCheckInResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        Integer submissionNo,
        String content,
        String status,
        LocalDateTime submittedAt,
        String reviewComment,
        List<TaskAttachmentResponse> attachments
) {
    static TaskCheckInResponse from(TaskCheckInView view) {
        return view == null ? null : new TaskCheckInResponse(
                view.id(), view.submissionNo(), view.content(), view.status(),
                view.submittedAt(), view.reviewComment(), view.attachments().stream()
                        .map(TaskAttachmentResponse::from).toList());
    }
}
