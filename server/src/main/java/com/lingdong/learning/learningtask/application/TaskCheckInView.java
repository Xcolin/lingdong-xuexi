package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.attachment.application.TaskAttachmentView;

import java.time.LocalDateTime;
import java.util.List;

/** 学生任务最近一次打卡摘要。 */
public record TaskCheckInView(
        Long id,
        Integer submissionNo,
        String content,
        String status,
        LocalDateTime submittedAt,
        String reviewComment,
        List<TaskAttachmentView> attachments
) {
    public TaskCheckInView {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public TaskCheckInView(
            Long id, Integer submissionNo, String content, String status,
            LocalDateTime submittedAt, String reviewComment
    ) {
        this(id, submissionNo, content, status, submittedAt, reviewComment, List.of());
    }

    public TaskCheckInView withAttachments(List<TaskAttachmentView> values) {
        return new TaskCheckInView(
                id, submissionNo, content, status, submittedAt, reviewComment, values);
    }
}
