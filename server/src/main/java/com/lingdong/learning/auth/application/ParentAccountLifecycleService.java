package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.persistence.DeviceSessionMapper;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAccountLifecycleMapper;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper;
import com.lingdong.learning.auth.infrastructure.security.ParentSmsCodeHasher;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Duration;

/** 家长手机号换绑与账号注销前置流程的统一事务边界。 */
@Service
public class ParentAccountLifecycleService {
    private static final String FEATURE_CODE = "PARENT_ACCOUNT_LIFECYCLE";
    private static final String PARENT_ROLE_CODE = "PARENT";
    private static final String ACTIVE_SCOPE_KEY = "ACTIVE";
    private static final String CANCELLATION_CONFIRMATION = "确认注销";
    private static final Duration CANCELLATION_COOLING_PERIOD = Duration.ofDays(7);

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final ParentAuthenticationMapper parentAuthenticationMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final ParentSmsVerificationService smsService;
    private final ParentMobileChangeTicketService ticketService;
    private final ParentSmsCodeHasher hasher;
    private final ParentAccountLifecycleMapper lifecycleMapper;
    private final DeviceSessionMapper sessionMapper;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public ParentAccountLifecycleService(
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            ParentAuthenticationMapper parentAuthenticationMapper,
            ParentStudentMapper parentStudentMapper,
            ParentSmsVerificationService smsService,
            ParentMobileChangeTicketService ticketService,
            ParentSmsCodeHasher hasher,
            ParentAccountLifecycleMapper lifecycleMapper,
            DeviceSessionMapper sessionMapper,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.parentAuthenticationMapper = parentAuthenticationMapper;
        this.parentStudentMapper = parentStudentMapper;
        this.smsService = smsService;
        this.ticketService = ticketService;
        this.hasher = hasher;
        this.lifecycleMapper = lifecycleMapper;
        this.sessionMapper = sessionMapper;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public IssuedParentSmsCode issueCurrentMobileCode(
            Long userId,
            AuthClientType clientType,
            String sourceDigest
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        User parent = requireParent(userMapper.findById(userId));
        return smsService.issue(
                parent.mobile(), ParentSmsPurpose.CHANGE_MOBILE_CURRENT, clientType, sourceDigest);
    }

    public String verifyCurrentMobile(Long userId, AuthClientType clientType, String code) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        User parent = requireParent(userMapper.findById(userId));
        smsService.verifyAndConsume(
                parent.mobile(), ParentSmsPurpose.CHANGE_MOBILE_CURRENT, clientType, code);
        return ticketService.issue(userId, clientType, hasher.mobileDigest(parent.mobile()));
    }

    public IssuedParentSmsCode issueCancellationCode(
            Long userId,
            AuthClientType clientType,
            String sourceDigest
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        User parent = requireParent(userMapper.findById(userId));
        return smsService.issue(
                parent.mobile(), ParentSmsPurpose.ACCOUNT_CANCELLATION, clientType, sourceDigest);
    }

    @Transactional
    public IssuedParentSmsCode issueNewMobileCode(
            Long userId,
            AuthClientType clientType,
            String ticket,
            String newMobile,
            String sourceDigest
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        User parent = requireParent(userMapper.findByIdForUpdate(userId));
        String currentDigest = hasher.mobileDigest(parent.mobile());
        ticketService.requireValid(ticket, userId, clientType, currentDigest);
        requireAvailableNewMobile(parent, newMobile);
        return smsService.issue(newMobile, ParentSmsPurpose.CHANGE_MOBILE_NEW, clientType, sourceDigest);
    }

    @Transactional
    public void changeMobile(
            Long userId,
            AuthClientType clientType,
            String ticket,
            String newMobile,
            String code
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        User parent = requireParent(userMapper.findByIdForUpdate(userId));
        requireAvailableNewMobile(parent, newMobile);
        String oldMobileDigest = hasher.mobileDigest(parent.mobile());
        String newMobileDigest = hasher.mobileDigest(newMobile);

        smsService.verifyAndConsume(newMobile, ParentSmsPurpose.CHANGE_MOBILE_NEW, clientType, code);
        ticketService.consume(ticket, userId, clientType, oldMobileDigest);

        LocalDateTime changedAt = LocalDateTime.now(clock);
        try {
            if (userMapper.updateMobileIfExpected(userId, parent.mobile(), newMobile) != 1) {
                throw new ParentMobileChangeConflictException();
            }
            ParentMobileChangeRecord audit = new ParentMobileChangeRecord(
                    idGenerator.nextId(), userId, oldMobileDigest, newMobileDigest, clientType, changedAt);
            if (lifecycleMapper.insertMobileChange(audit) != 1) {
                throw new IllegalStateException("手机号变更审计保存失败");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ParentMobileChangeConflictException(exception);
        }
        sessionMapper.revokeAllActiveByUserId(userId, changedAt);
    }

    public ParentAccountLifecycleState getState(Long userId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        User parent = requireParent(userMapper.findById(userId));
        long relationshipCount = parentStudentMapper.countActiveStudentsByParent(userId);
        ParentAccountCancellationRecord cancellation = lifecycleMapper.findActiveCancellation(userId);
        if (cancellation == null) {
            return new ParentAccountLifecycleState(
                    maskMobile(parent.mobile()), relationshipCount,
                    ParentAccountCancellationViewStatus.NONE, null, null, null);
        }
        ParentAccountCancellationViewStatus status = LocalDateTime.now(clock)
                .isBefore(cancellation.coolingEndsAt())
                ? ParentAccountCancellationViewStatus.COOLING_OFF
                : ParentAccountCancellationViewStatus.READY_FOR_FINALIZATION;
        return new ParentAccountLifecycleState(
                maskMobile(parent.mobile()), relationshipCount, status,
                cancellation.id(), cancellation.requestedAt(), cancellation.coolingEndsAt());
    }

    @Transactional
    public ParentAccountCancellationRecord requestCancellation(
            Long userId,
            AuthClientType clientType,
            String code,
            String confirmation
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        if (!CANCELLATION_CONFIRMATION.equals(confirmation)) {
            throw new IllegalArgumentException("请输入确认注销");
        }
        User parent = requireParent(userMapper.findByIdForUpdate(userId));
        ParentAccountCancellationRecord existing = lifecycleMapper.findActiveCancellationForUpdate(userId);
        if (existing != null) {
            return existing;
        }
        if (parentStudentMapper.countActiveStudentsByParent(userId) > 0) {
            throw new ParentAccountCancellationConflictException("请先解除全部学生关系后再申请注销");
        }
        smsService.verifyAndConsume(
                parent.mobile(), ParentSmsPurpose.ACCOUNT_CANCELLATION, clientType, code);
        LocalDateTime requestedAt = LocalDateTime.now(clock);
        ParentAccountCancellationRecord record = new ParentAccountCancellationRecord(
                idGenerator.nextId(), userId, ParentAccountCancellationStatus.COOLING_OFF,
                ACTIVE_SCOPE_KEY, requestedAt, requestedAt.plus(CANCELLATION_COOLING_PERIOD), null, null);
        try {
            if (lifecycleMapper.insertCancellation(record) != 1) {
                throw new IllegalStateException("账号注销申请保存失败");
            }
        } catch (DataIntegrityViolationException exception) {
            ParentAccountCancellationRecord concurrent = lifecycleMapper.findActiveCancellationForUpdate(userId);
            if (concurrent != null) {
                return concurrent;
            }
            throw exception;
        }
        return record;
    }

    @Transactional
    public void revokeCancellation(Long userId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireParent(userMapper.findByIdForUpdate(userId));
        ParentAccountCancellationRecord record = lifecycleMapper.findActiveCancellationForUpdate(userId);
        LocalDateTime revokedAt = LocalDateTime.now(clock);
        if (record == null
                || record.status() != ParentAccountCancellationStatus.COOLING_OFF
                || !revokedAt.isBefore(record.coolingEndsAt())) {
            throw new ParentAccountCancellationConflictException("当前没有可撤销的冷静期注销申请");
        }
        if (lifecycleMapper.revokeCancellation(
                record.id(), "CLOSED:" + record.id(), revokedAt) != 1) {
            throw new ParentAccountCancellationConflictException("注销申请状态已发生变化");
        }
    }

    private void requireAvailableNewMobile(User parent, String newMobile) {
        if (newMobile == null || newMobile.equals(parent.mobile())) {
            throw new ParentMobileChangeConflictException();
        }
        User occupied = userMapper.findByMobileForUpdate(newMobile);
        boolean loginNameMustFollowMobile = parent.username().equals(parent.mobile());
        if (occupied != null || (loginNameMustFollowMobile && userMapper.existsByUsername(newMobile))) {
            throw new ParentMobileChangeConflictException();
        }
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return "***";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private User requireParent(User user) {
        if (user == null
                || user.type() != UserType.FAMILY
                || user.status() != UserStatus.ENABLED
                || !userRoleMapper.hasRoleCode(user.id(), PARENT_ROLE_CODE)
                || parentAuthenticationMapper.findProfileByUserId(user.id()) == null) {
            throw new AuthenticationFailedException();
        }
        return user;
    }
}
