package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthRewardExchangeMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthRewardMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** 分批、幂等处理到期奖励和超时待审批兑换。 */
@Service
public class GrowthRewardExchangeCleanupService {
    private final GrowthRewardMapper rewardMapper;
    private final GrowthRewardExchangeMapper exchangeMapper;
    private final Clock clock;
    private final int batchSize;

    public GrowthRewardExchangeCleanupService(
            GrowthRewardMapper rewardMapper,
            GrowthRewardExchangeMapper exchangeMapper,
            Clock clock,
            @Value("${lingdong.reward-exchange.cleanup.batch-size:100}") int batchSize
    ) {
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("奖励兑换清理批次大小必须在 1 至 1000 之间");
        }
        this.rewardMapper = rewardMapper;
        this.exchangeMapper = exchangeMapper;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Transactional
    public int processAll() {
        LocalDateTime now = LocalDateTime.now(clock);
        int affectedRows = processExpiredRewards(now);
        affectedRows += processOverdueApprovals(now);
        return affectedRows;
    }

    private int processExpiredRewards(LocalDateTime now) {
        int affectedRows = 0;
        List<Long> rewardIds;
        do {
            rewardIds = rewardMapper.findExpiredOnlineIds(now, batchSize);
            for (Long rewardId : rewardIds) {
                int offlined = rewardMapper.offlineExpired(rewardId, now);
                affectedRows += offlined;
                if (offlined == 1) {
                    affectedRows += exchangeMapper.expirePendingByRewardId(rewardId, now);
                }
            }
        } while (rewardIds.size() == batchSize);
        return affectedRows;
    }

    private int processOverdueApprovals(LocalDateTime now) {
        int affectedRows = 0;
        List<Long> exchangeIds;
        do {
            exchangeIds = exchangeMapper.findOverduePendingApprovalIds(now, batchSize);
            for (Long exchangeId : exchangeIds) {
                affectedRows += exchangeMapper.autoRejectOverdue(exchangeId, now);
            }
        } while (exchangeIds.size() == batchSize);
        return affectedRows;
    }
}
