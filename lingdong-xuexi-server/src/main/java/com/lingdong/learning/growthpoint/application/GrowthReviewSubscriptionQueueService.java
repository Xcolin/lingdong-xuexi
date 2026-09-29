package com.lingdong.learning.growthpoint.application;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewDeliveryMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewSubscriptionMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.DayOfWeek;
import java.time.LocalDateTime;

/** 每条订阅独立事务；这里只排队，不执行任何微信发送。 */
@Service
public class GrowthReviewSubscriptionQueueService {
    private final UserMapper users;
    private final GrowthReviewSubscriptionMapper subscriptions;
    private final GrowthReviewSubscriptionService access;
    private final GrowthReviewDeliveryMapper deliveries;
    private final IdGenerator ids;
    public GrowthReviewSubscriptionQueueService(UserMapper users, GrowthReviewSubscriptionMapper subscriptions,
            GrowthReviewSubscriptionService access, GrowthReviewDeliveryMapper deliveries, IdGenerator ids) {
        this.users=users; this.subscriptions=subscriptions; this.access=access; this.deliveries=deliveries; this.ids=ids;
    }
    @Transactional
    public int prepare(Long parentId, Long studentId, LocalDateTime now) {
        if (now == null) throw new IllegalArgumentException("排程时间不能为空");
        // 与启停偏好共用用户行锁，使排队和取消顺序确定，唯一周键兜底多实例重复扫描。
        if (users.findByIdForUpdate(parentId) == null) return 0;
        var subscription = subscriptions.find(parentId, studentId);
        if (subscription == null) return 0;
        if (!subscription.enabled() || !access.canSchedule(parentId, studentId)) {
            deliveries.cancelPending(subscription.id());
            return 0;
        }
        if (now.getDayOfWeek() != DayOfWeek.MONDAY || now.getHour() < 7 || now.getHour() >= 22) return 0;
        var start = now.toLocalDate().minusWeeks(1);
        var report = deliveries.findWeeklyReport(studentId, start, start.plusDays(6));
        if (report == null) {
            deliveries.cancelPending(subscription.id());
            return 0;
        }
        Long existing = deliveries.findId(subscription.id(), start);
        if (existing != null) return deliveries.reactivate(existing, subscription.version(), report, now);
        return deliveries.insert(ids.nextId(), subscription.id(), subscription.version(), start, report,
                now.toLocalDate().atTime(22, 0), now);
    }
}
