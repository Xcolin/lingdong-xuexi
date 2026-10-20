package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.attachment.application.TaskAttachmentView;

import java.util.List;

/** 家长任务详情打卡记录（含附件视图）。 */
public record FamilyTaskCheckInRecord(
        FamilyTaskCheckInRow row,
        List<TaskAttachmentView> attachments
) {
    public FamilyTaskCheckInRecord {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
