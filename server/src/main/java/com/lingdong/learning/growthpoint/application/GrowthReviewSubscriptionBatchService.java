package com.lingdong.learning.growthpoint.application;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewDeliveryMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewSubscriptionMapper;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 按主键分页扫描，隔离单条失败，排程时间每条重新取上海时区当前时间。 */
@Service
public class GrowthReviewSubscriptionBatchService {
    private static final Logger LOG = LoggerFactory.getLogger(GrowthReviewSubscriptionBatchService.class);
    private final GrowthReviewSubscriptionMapper subscriptions;
    private final GrowthReviewDeliveryMapper deliveries;
    private final GrowthReviewSubscriptionQueueService queue;
    private final Clock clock;
    public GrowthReviewSubscriptionBatchService(GrowthReviewSubscriptionMapper subscriptions, GrowthReviewDeliveryMapper deliveries,
            GrowthReviewSubscriptionQueueService queue, Clock clock) {
        this.subscriptions=subscriptions; this.deliveries=deliveries; this.queue=queue; this.clock=clock;
    }
    public int process() {
        deliveries.cancelExpired(now());
        long after = 0; int queued = 0;
        while (true) {
            var rows = subscriptions.findAfter(after, 100);
            for (var row : rows) {
                after = row.id();
                try { queued += queue.prepare(row.parentUserId(), row.studentId(), now()); }
                catch (RuntimeException exception) { LOG.warn("周报订阅排程失败，保留后续扫描重试。subscriptionId={}", row.id(), exception); }
            }
            if (rows.size() < 100) return queued;
        }
    }
    private LocalDateTime now() { return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Shanghai"))); }
}
