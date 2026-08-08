package com.lingdong.learning.growthpoint.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 主家长驳回兑换请求。 */
public record RejectRewardExchangeRequest(
        @NotBlank @Size(max = 500) String rejectReason
) {
}
