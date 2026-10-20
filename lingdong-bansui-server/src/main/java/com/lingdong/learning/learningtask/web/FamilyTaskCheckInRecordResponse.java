package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.attachment.web.TaskAttachmentResponse;
import com.lingdong.learning.learningtask.application.FamilyTaskCheckInRecord;

import java.time.LocalDateTime;
import java.util.List;

/** 家长任务详情打卡记录响应（02-06），所有标识以字符串返回避免前端精度丢失。 */
public record FamilyTaskCheckInRecordResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long checkInId,
        @JsonSerialize(using = ToStringSerializer.class) Long assignmentId,
        @JsonSerialize(using = ToStringSerializer.class) Long studentId,
        String studentName,
        String content,
        String status,
        LocalDateTime submittedAt,
        String completion,
        List<TaskAttachmentResponse> attachments
) {
    public static FamilyTaskCheckInRecordResponse from(FamilyTaskCheckInRecord record) {
        return new FamilyTaskCheckInRecordResponse(
                record.row().checkInId(), record.row().assignmentId(), record.row().studentId(),
                record.row().studentName(), record.row().content(), record.row().status(),
                record.row().submittedAt(), record.row().completion(),
                record.attachments().stream().map(TaskAttachmentResponse::from).toList());
    }
}
