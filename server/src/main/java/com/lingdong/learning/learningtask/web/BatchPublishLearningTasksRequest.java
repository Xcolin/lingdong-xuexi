package com.lingdong.learning.learningtask.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Web 端批量发布请求。 */
public record BatchPublishLearningTasksRequest(
        @NotEmpty @Size(max = 100) List<@NotNull @Positive Long> taskIds
) {
}
