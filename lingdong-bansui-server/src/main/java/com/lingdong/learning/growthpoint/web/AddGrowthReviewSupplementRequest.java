package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.growthpoint.application.AddGrowthReviewSupplementCommand;
import com.lingdong.learning.growthpoint.domain.GrowthReviewSupplementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 成长复盘补录请求。 */
public record AddGrowthReviewSupplementRequest(
        @NotNull GrowthReviewSupplementType supplementType,
        @NotBlank @Size(max = 1000) String content
) {
    public AddGrowthReviewSupplementCommand toCommand() {
        return new AddGrowthReviewSupplementCommand(supplementType, content);
    }
}
