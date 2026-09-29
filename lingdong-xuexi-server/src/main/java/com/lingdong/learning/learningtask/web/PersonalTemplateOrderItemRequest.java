package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lingdong.learning.learningtask.application.LearningTaskTemplateOrderItem;

public record PersonalTemplateOrderItemRequest(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long templateId,
        Long versionNo
) {
    LearningTaskTemplateOrderItem toItem() {
        return new LearningTaskTemplateOrderItem(templateId, versionNo);
    }
}
