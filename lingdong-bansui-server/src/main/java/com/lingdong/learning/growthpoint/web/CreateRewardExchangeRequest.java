package com.lingdong.learning.growthpoint.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 学生兑换申请仅接收奖励标识，学生和积分均由服务端确定。 */
public record CreateRewardExchangeRequest(
        @NotNull @Positive Long rewardId
) {
}
