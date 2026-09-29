package com.lingdong.learning.growthpoint.domain;

/** 家庭奖励兑换状态。 */
public enum GrowthRewardExchangeStatus {
    PENDING_APPROVAL,
    PENDING_VERIFICATION,
    REJECTED,
    AUTO_REJECTED,
    EXPIRED,
    VERIFIED
}
