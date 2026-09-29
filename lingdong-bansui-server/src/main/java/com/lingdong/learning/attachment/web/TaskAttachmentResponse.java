package com.lingdong.learning.attachment.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.attachment.application.TaskAttachmentView;

/** 统一附件元数据响应。 */
public record TaskAttachmentResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String originalName,
        String contentType,
        long sizeBytes,
        String contentUrl
) {
    public static TaskAttachmentResponse from(TaskAttachmentView view) {
        return new TaskAttachmentResponse(
                view.id(), view.originalName(), view.contentType(), view.sizeBytes(), view.contentUrl());
    }
}
