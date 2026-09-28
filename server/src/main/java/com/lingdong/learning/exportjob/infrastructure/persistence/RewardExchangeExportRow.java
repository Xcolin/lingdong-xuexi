package com.lingdong.learning.exportjob.infrastructure.persistence;

import java.time.LocalDateTime;
import com.lingdong.learning.growthpoint.domain.GrowthRewardExchangeStatus;

/** Only exchange-time facts needed by the five report columns. */
public record RewardExchangeExportRow(Long id, String rewardName, Long requiredPoints,
        LocalDateTime requestedAt, GrowthRewardExchangeStatus status, LocalDateTime reviewedAt) {}
