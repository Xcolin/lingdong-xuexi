package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.LearningTaskTemplateOrderItem;

import java.util.List;

public record PersonalTemplateOrderRequest(List<PersonalTemplateOrderItemRequest> items) {
    List<LearningTaskTemplateOrderItem> toItems() {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(item -> item == null ? null : item.toItem())
                .toList();
    }
}
