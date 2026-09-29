package com.lingdong.learning.growthpoint.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 主家长积分纠错请求。 */
public record CorrectGrowthPointRequest(
        @NotNull Long originalLedgerId,
        @NotBlank @Size(max = 500) String reason
) {
}
