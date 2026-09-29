package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.infrastructure.config.ParentAccountFinalizationProperties;
import com.lingdong.learning.auth.infrastructure.persistence.DeviceSessionMapper;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAccountLifecycleMapper;
import com.lingdong.learning.auth.infrastructure.persistence.ParentWechatBindingMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** 在独立事务内完成单个家长账号的不可逆注销和身份匿名化。 */
@Service
public class ParentAccountFinalizationService {
    private static final String FEATURE_CODE = "PARENT_ACCOUNT_LIFECYCLE";
    private static final String PARENT_ROLE_CODE = "PARENT";
    private static final String ACTIVE_SCOPE_KEY = "ACTIVE";
    private static final String ACTIVE_RELATIONSHIP_ERROR = "ACTIVE_RELATIONSHIP";

    private final ParentAccountLifecycleMapper lifecycleMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final ParentWechatBindingMapper wechatBindingMapper;
    private final DeviceSessionMapper sessionMapper;
    private final FeatureAccessService featureAccessService;
    private final ParentAccountFinalizationProperties properties;
    private final Clock clock;

    public ParentAccountFinalizationService(
            ParentAccountLifecycleMapper lifecycleMapper,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            ParentStudentMapper parentStudentMapper,
            ParentWechatBindingMapper wechatBindingMapper,
            DeviceSessionMapper sessionMapper,
            FeatureAccessService featureAccessService,
            ParentAccountFinalizationProperties properties,
            Clock clock
    ) {
        this.lifecycleMapper = lifecycleMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.parentStudentMapper = parentStudentMapper;
        this.wechatBindingMapper = wechatBindingMapper;
        this.sessionMapper = sessionMapper;
        this.featureAccessService = featureAccessService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ParentAccountFinalizationResult finalizeCancellation(Long cancellationId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        ParentAccountFinalizationCandidate candidate =
                lifecycleMapper.findFinalizationCandidateForUpdate(cancellationId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!isDue(candidate, now)) {
            return ParentAccountFinalizationResult.SKIPPED;
        }

        User parent = userMapper.findByIdForUpdate(candidate.userId());
        if (!isFinalizableParent(parent)) {
            return ParentAccountFinalizationResult.SKIPPED;
        }
        if (parentStudentMapper.countActiveStudentsByParent(parent.id()) > 0) {
            LocalDateTime retryAt = now.plus(properties.getRelationshipRetryDelay());
            if (lifecycleMapper.deferFinalization(
                    candidate.cancellationId(), retryAt, ACTIVE_RELATIONSHIP_ERROR) != 1) {
                throw new IllegalStateException("注销申请延后状态保存失败");
            }
            return ParentAccountFinalizationResult.DEFERRED_ACTIVE_RELATIONSHIP;
        }

        wechatBindingMapper.deleteByUserId(parent.id());
        if (userMapper.anonymizeCancelledParent(parent.id(), "cancelled_" + parent.id()) != 1) {
            throw new IllegalStateException("家长账号匿名化失败");
        }
        sessionMapper.revokeAllActiveByUserId(parent.id(), now);
        if (lifecycleMapper.finalizeCancellation(
                candidate.cancellationId(), "CLOSED:" + candidate.cancellationId(), now) != 1) {
            throw new IllegalStateException("注销申请终结状态保存失败");
        }
        return ParentAccountFinalizationResult.FINALIZED;
    }

    private boolean isDue(ParentAccountFinalizationCandidate candidate, LocalDateTime now) {
        return candidate != null
                && candidate.status() == ParentAccountCancellationStatus.COOLING_OFF
                && ACTIVE_SCOPE_KEY.equals(candidate.activeScopeKey())
                && !now.isBefore(candidate.coolingEndsAt())
                && (candidate.nextFinalizeAt() == null || !now.isBefore(candidate.nextFinalizeAt()));
    }

    private boolean isFinalizableParent(User user) {
        return user != null
                && user.type() == UserType.FAMILY
                && user.status() != UserStatus.CANCELLED
                && userRoleMapper.hasRoleCode(user.id(), PARENT_ROLE_CODE);
    }
}
