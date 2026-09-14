package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.domain.GrowthReviewSubscription;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewSubscriptionMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import com.lingdong.learning.user.domain.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 主动订阅偏好与撤回，读取/取消不依赖推送开关，不执行消息投递。 */
@Service
public class GrowthReviewSubscriptionService {
    private final GrowthReviewSubscriptionMapper subscriptions;
    private final UserMapper users;
    private final UserRoleMapper roles;
    private final ParentStudentMapper relations;
    private final PermissionDecisionService permissions;
    private final FeatureAccessService features;
    private final IdGenerator ids;
    private final Clock clock;
    private final com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewDeliveryMapper deliveries;

    public GrowthReviewSubscriptionService(GrowthReviewSubscriptionMapper subscriptions, UserMapper users,
            UserRoleMapper roles, ParentStudentMapper relations, PermissionDecisionService permissions,
            FeatureAccessService features, IdGenerator ids, Clock clock,
            com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewDeliveryMapper deliveries) {
        this.subscriptions=subscriptions; this.users=users; this.roles=roles; this.relations=relations;
        this.permissions=permissions; this.features=features; this.ids=ids; this.clock=clock;
        this.deliveries=deliveries;
    }
    public View get(AuthenticatedUser user, Long studentId) {
        requireOwner(user, studentId, false);
        return view(studentId, subscriptions.find(user.userId(), studentId));
    }
    @Transactional
    public View set(AuthenticatedUser user, Long studentId, boolean enabled, long expectedVersion) {
        if (expectedVersion < 0) throw new IllegalArgumentException("订阅版本不能为负数");
        // 同一用户行锁覆盖首次插入竞态；唯一约束和更新版本条件作为数据库兜底。
        requireOwner(user, studentId, true);
        if (enabled) {
            features.requireEnabled("GROWTH_REVIEW_WEEKLY_SUBSCRIPTION", null);
            features.requireEnabled("PERIODIC_GROWTH_REPORT", null);
            if (!permissions.isAllowed(user.userId(), PermissionClient.WEB, "GROWTH_REVIEW_SUBSCRIBE_CHILD")
                    || !permissions.isAllowed(user.userId(), PermissionClient.WEB, "GROWTH_REVIEW_READ_CHILD")
                    || !relations.existsActiveByParentAndStudent(user.userId(), studentId)) throw denied();
        }
        var current = subscriptions.find(user.userId(), studentId);
        var before = view(studentId, current);
        if (before.enabled() == enabled) {
            if (!enabled && current != null) deliveries.cancelPending(current.id());
            return before;
        }
        if (before.version() != expectedVersion) throw new IllegalStateException("订阅状态已变更，请刷新后重试");
        var changed = new GrowthReviewSubscription(current == null ? ids.nextId() : current.id(), user.userId(), studentId,
                enabled, Math.addExact(before.version(), 1), LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Shanghai"))));
        int rows = current == null ? subscriptions.insert(changed) : subscriptions.update(changed, expectedVersion);
        if (rows != 1) throw new IllegalStateException("订阅状态已变更，请刷新后重试");
        if (!enabled) deliveries.cancelPending(changed.id());
        return view(studentId, changed);
    }
    /** 后台排程从持久化身份重新鉴权，不使用创建订阅时的会话快照。 */
    public boolean canSchedule(Long parentId, Long studentId) {
        var account = users.findById(parentId);
        return account != null && account.status() == UserStatus.ENABLED
                && roles.hasRoleCode(parentId, "PARENT") && !roles.hasRoleCode(parentId, "SYS_AUDITOR")
                && features.isEnabled("GROWTH_REVIEW_WEEKLY_SUBSCRIPTION", null)
                && features.isEnabled("PERIODIC_GROWTH_REPORT", null)
                && permissions.isAllowed(parentId, PermissionClient.WEB, "GROWTH_REVIEW_SUBSCRIBE_CHILD")
                && permissions.isAllowed(parentId, PermissionClient.WEB, "GROWTH_REVIEW_READ_CHILD")
                && relations.existsActiveByParentAndStudent(parentId, studentId);
    }
    private void requireOwner(AuthenticatedUser user, Long studentId, boolean lock) {
        if (user == null || user.userId() == null || user.clientType() != AuthClientType.WEB) throw denied();
        if (studentId == null || studentId <= 0) throw new IllegalArgumentException("学生标识必须为正整数");
        var account = lock ? users.findByIdForUpdate(user.userId()) : users.findById(user.userId());
        if (account == null || account.status() != UserStatus.ENABLED || !roles.hasRoleCode(user.userId(), "PARENT")
                || roles.hasRoleCode(user.userId(), "SYS_AUDITOR")) throw denied();
    }
    private View view(Long studentId, GrowthReviewSubscription row) {
        return new View(studentId.toString(), row != null && row.enabled(), row == null ? 0 : row.version());
    }
    private static SystemOperationAccessDeniedException denied() { return new SystemOperationAccessDeniedException("当前账号无权操作该周报订阅"); }
    public record View(String studentId, boolean enabled, long version) { }
}
