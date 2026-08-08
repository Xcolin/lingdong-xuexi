package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.domain.GrowthPointAccount;
import com.lingdong.learning.growthpoint.domain.GrowthPointLedger;
import com.lingdong.learning.growthpoint.domain.GrowthReward;
import com.lingdong.learning.growthpoint.domain.GrowthRewardExchange;
import com.lingdong.learning.growthpoint.domain.GrowthRewardExchangeStatus;
import com.lingdong.learning.growthpoint.domain.GrowthRewardStatus;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointAccountMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointLedgerMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthRewardExchangeMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthRewardMapper;
import com.lingdong.learning.learningtask.application.CurrentStudentAccessService;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** 家庭奖励兑换申请、审批扣分、驳回和核销状态机。 */
@Service
public class GrowthRewardExchangeService {
    private static final String FEATURE_CODE = "REWARD_EXCHANGE";
    private static final long APPROVAL_WINDOW_HOURS = 72L;

    private final GrowthRewardExchangeMapper exchangeMapper;
    private final GrowthRewardMapper rewardMapper;
    private final GrowthPointAccountMapper accountMapper;
    private final GrowthPointLedgerMapper ledgerMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final CurrentStudentAccessService currentStudentAccessService;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public GrowthRewardExchangeService(
            GrowthRewardExchangeMapper exchangeMapper,
            GrowthRewardMapper rewardMapper,
            GrowthPointAccountMapper accountMapper,
            GrowthPointLedgerMapper ledgerMapper,
            ParentStudentMapper parentStudentMapper,
            CurrentStudentAccessService currentStudentAccessService,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.exchangeMapper = exchangeMapper;
        this.rewardMapper = rewardMapper;
        this.accountMapper = accountMapper;
        this.ledgerMapper = ledgerMapper;
        this.parentStudentMapper = parentStudentMapper;
        this.currentStudentAccessService = currentStudentAccessService;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public GrowthRewardExchange apply(AuthenticatedUser currentUser, Long rewardId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        Student student = requireMiniappStudent(currentUser);
        Long validatedRewardId = requireId(rewardId, "奖励标识不合法");
        GrowthReward reward = rewardMapper.findByIdForUpdate(validatedRewardId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (reward == null || !student.id().equals(reward.studentId())
                || reward.status() != GrowthRewardStatus.ONLINE
                || (reward.expiresAt() != null && !reward.expiresAt().isAfter(now))) {
            throw notFound();
        }
        GrowthPointAccount account = requireAccount(student.id());
        if (account.availablePoints() < reward.requiredPoints()) {
            throw new IllegalStateException("可用积分不足");
        }
        if (exchangeMapper.existsActive(student.id(), reward.id())) {
            throw new IllegalStateException("该奖励已有进行中的兑换");
        }
        GrowthRewardExchange exchange = new GrowthRewardExchange(
                idGenerator.nextId(), reward.id(), student.id(), currentUser.userId(),
                reward.rewardName(), reward.requiredPoints(), reward.description(), now,
                now.plusHours(APPROVAL_WINDOW_HOURS), GrowthRewardExchangeStatus.PENDING_APPROVAL,
                null, null, null, null, null, 0, now, now);
        requireSingleWrite(exchangeMapper.insert(exchange));
        return exchange;
    }

    @Transactional(readOnly = true)
    public GrowthRewardExchangePage findMine(
            AuthenticatedUser currentUser, int page, int pageSize
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        Student student = requireMiniappStudent(currentUser);
        PageRequest pagination = requirePage(page, pageSize);
        return exchangePage(student.id(), pagination);
    }

    @Transactional(readOnly = true)
    public GrowthRewardExchangePage findManaged(
            AuthenticatedUser currentUser, Long studentId, int page, int pageSize
    ) {
        requireAccessibleChild(currentUser, studentId);
        return exchangePage(studentId, requirePage(page, pageSize));
    }

    @Transactional
    public GrowthRewardExchange approve(AuthenticatedUser currentUser, Long exchangeId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        GrowthRewardExchange exchange = requireManagedExchange(currentUser, exchangeId);
        requireStatus(exchange, GrowthRewardExchangeStatus.PENDING_APPROVAL);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(exchange.approvalDeadline())) {
            throw new IllegalStateException("兑换申请已超过审批时限");
        }
        GrowthReward reward = rewardMapper.findByIdForUpdate(exchange.rewardId());
        if (reward == null || (reward.expiresAt() != null && !reward.expiresAt().isAfter(now))) {
            throw new IllegalStateException("兑换奖励已过期");
        }
        GrowthPointAccount account = requireAccount(exchange.studentId());
        if (account.availablePoints() < exchange.requiredPointsSnapshot()) {
            throw new IllegalStateException("可用积分不足");
        }

        requireSingleWrite(exchangeMapper.approve(
                exchange.id(), exchange.versionNo(), currentUser.userId(), now));
        requireSingleWrite(accountMapper.redeem(
                account.id(), exchange.requiredPointsSnapshot(), account.versionNo(), now));
        requireSingleWrite(ledgerMapper.insert(GrowthPointLedger.redemption(
                idGenerator.nextId(), account.id(), exchange.studentId(), exchange.id(),
                exchange.requiredPointsSnapshot(), currentUser.userId(), now,
                exchange.rewardNameSnapshot())));
        return approved(exchange, currentUser.userId(), now);
    }

    @Transactional
    public GrowthRewardExchange reject(
            AuthenticatedUser currentUser, Long exchangeId, String rejectReason
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        String normalizedReason = normalizeReason(rejectReason);
        GrowthRewardExchange exchange = requireManagedExchange(currentUser, exchangeId);
        requireStatus(exchange, GrowthRewardExchangeStatus.PENDING_APPROVAL);
        LocalDateTime now = LocalDateTime.now(clock);
        requireSingleWrite(exchangeMapper.reject(
                exchange.id(), exchange.versionNo(), currentUser.userId(), now, normalizedReason));
        return rejected(exchange, currentUser.userId(), now, normalizedReason);
    }

    @Transactional
    public GrowthRewardExchange verify(AuthenticatedUser currentUser, Long exchangeId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        GrowthRewardExchange exchange = requireManagedExchange(currentUser, exchangeId);
        requireStatus(exchange, GrowthRewardExchangeStatus.PENDING_VERIFICATION);
        LocalDateTime now = LocalDateTime.now(clock);
        requireSingleWrite(exchangeMapper.verify(
                exchange.id(), exchange.versionNo(), currentUser.userId(), now));
        return verified(exchange, currentUser.userId(), now);
    }

    private GrowthRewardExchange requireManagedExchange(
            AuthenticatedUser currentUser, Long exchangeId
    ) {
        requireWebParent(currentUser);
        Long validatedExchangeId = requireId(exchangeId, "兑换标识不合法");
        GrowthRewardExchange exchange = exchangeMapper.findByIdForUpdate(validatedExchangeId);
        if (exchange == null || !parentStudentMapper.existsActivePrimaryByParentAndStudent(
                currentUser.userId(), exchange.studentId())) {
            throw notFound();
        }
        return exchange;
    }

    private void requireAccessibleChild(AuthenticatedUser currentUser, Long studentId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireWebParent(currentUser);
        if (studentId == null || !parentStudentMapper.existsActivePrimaryByParentAndStudent(
                currentUser.userId(), studentId)) {
            throw notFound();
        }
    }

    private GrowthPointAccount requireAccount(Long studentId) {
        GrowthPointAccount account = accountMapper.findByStudentIdForUpdate(studentId);
        if (account == null) {
            throw new IllegalStateException("学生积分账户不可用");
        }
        return account;
    }

    private Student requireMiniappStudent(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.clientType() != AuthClientType.MINIAPP) {
            throw new SystemOperationAccessDeniedException("仅小程序学生可申请本人奖励兑换");
        }
        return currentStudentAccessService.require(currentUser);
    }

    private void requireWebParent(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.clientType() != AuthClientType.WEB
                || !currentUser.roleCodes().contains("PARENT")) {
            throw new SystemOperationAccessDeniedException("仅 Web 端主家长可处理奖励兑换");
        }
    }

    private void requireStatus(
            GrowthRewardExchange exchange, GrowthRewardExchangeStatus expectedStatus
    ) {
        if (exchange.status() != expectedStatus) {
            throw new IllegalStateException("兑换状态不允许执行此操作");
        }
    }

    private String normalizeReason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new IllegalArgumentException("驳回原因不合法");
        }
        return normalized;
    }

    private Long requireId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private void requireSingleWrite(int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException("兑换状态已变化");
        }
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("奖励兑换不存在或不可访问");
    }

    private GrowthRewardExchangePage exchangePage(Long studentId, PageRequest pagination) {
        return new GrowthRewardExchangePage(
                exchangeMapper.findByStudentId(
                        studentId, pagination.offset(), pagination.pageSize()),
                pagination.page(), pagination.pageSize(), exchangeMapper.countByStudentId(studentId));
    }

    private PageRequest requirePage(int page, int pageSize) {
        if (page < 1 || page > 1_000_000) {
            throw new IllegalArgumentException("页码不合法");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("每页数量不合法");
        }
        return new PageRequest(page, pageSize, Math.multiplyExact(page - 1, pageSize));
    }

    private record PageRequest(int page, int pageSize, int offset) {
    }

    private GrowthRewardExchange approved(
            GrowthRewardExchange source, Long reviewedBy, LocalDateTime reviewedAt
    ) {
        return new GrowthRewardExchange(
                source.id(), source.rewardId(), source.studentId(), source.requesterUserId(),
                source.rewardNameSnapshot(), source.requiredPointsSnapshot(),
                source.descriptionSnapshot(), source.requestedAt(), source.approvalDeadline(),
                GrowthRewardExchangeStatus.PENDING_VERIFICATION, reviewedBy, reviewedAt, null,
                null, null, source.versionNo() + 1, source.createdAt(), reviewedAt);
    }

    private GrowthRewardExchange rejected(
            GrowthRewardExchange source, Long reviewedBy, LocalDateTime reviewedAt, String reason
    ) {
        return new GrowthRewardExchange(
                source.id(), source.rewardId(), source.studentId(), source.requesterUserId(),
                source.rewardNameSnapshot(), source.requiredPointsSnapshot(),
                source.descriptionSnapshot(), source.requestedAt(), source.approvalDeadline(),
                GrowthRewardExchangeStatus.REJECTED, reviewedBy, reviewedAt, reason,
                null, null, source.versionNo() + 1, source.createdAt(), reviewedAt);
    }

    private GrowthRewardExchange verified(
            GrowthRewardExchange source, Long verifiedBy, LocalDateTime verifiedAt
    ) {
        return new GrowthRewardExchange(
                source.id(), source.rewardId(), source.studentId(), source.requesterUserId(),
                source.rewardNameSnapshot(), source.requiredPointsSnapshot(),
                source.descriptionSnapshot(), source.requestedAt(), source.approvalDeadline(),
                GrowthRewardExchangeStatus.VERIFIED, source.reviewedBy(), source.reviewedAt(),
                source.rejectReason(), verifiedBy, verifiedAt, source.versionNo() + 1,
                source.createdAt(), verifiedAt);
    }
}
