package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.application.AuthenticationFailedException;
import com.lingdong.learning.auth.application.ParentPhoneAuthenticationService;
import com.lingdong.learning.auth.application.ParentRelationshipMobileVerificationCommand;
import com.lingdong.learning.auth.application.ParentPhoneAuthenticatedSession;
import com.lingdong.learning.auth.application.ParentSmsPurpose;
import com.lingdong.learning.auth.application.VerifiedParentAccount;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.student.domain.ParentRelationship;
import com.lingdong.learning.student.domain.ParentRelationshipChange;
import com.lingdong.learning.student.domain.ParentRelationshipChangeType;
import com.lingdong.learning.student.domain.ParentRelationshipInvitation;
import com.lingdong.learning.student.domain.ParentRelationshipInvitationStatus;
import com.lingdong.learning.student.domain.ParentRelationshipInvitationType;
import com.lingdong.learning.student.domain.ParentRelationshipRole;
import com.lingdong.learning.student.infrastructure.persistence.ParentRelationshipChangeMapper;
import com.lingdong.learning.student.infrastructure.persistence.ParentRelationshipInvitationMapper;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 统一编排家长关系邀请、关系唯一约束和不可变审计。 */
@Service
public class ParentRelationshipApplicationService {
    private static final String FEATURE_CODE = "PARENT_RELATIONSHIP_MANAGEMENT";
    private static final int MAX_ACTIVE_STUDENTS = 10;

    private final ParentStudentMapper relationshipMapper;
    private final ParentRelationshipInvitationMapper invitationMapper;
    private final ParentRelationshipChangeMapper changeMapper;
    private final ParentPhoneAuthenticationService phoneService;
    private final UserMapper userMapper;
    private final FeatureAccessService featureAccessService;
    private final ParentRelationshipTaskTransferService taskTransferService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public ParentRelationshipApplicationService(
            ParentStudentMapper relationshipMapper,
            ParentRelationshipInvitationMapper invitationMapper,
            ParentRelationshipChangeMapper changeMapper,
            ParentPhoneAuthenticationService phoneService,
            UserMapper userMapper,
            FeatureAccessService featureAccessService,
            ParentRelationshipTaskTransferService taskTransferService,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.relationshipMapper = relationshipMapper;
        this.invitationMapper = invitationMapper;
        this.changeMapper = changeMapper;
        this.phoneService = phoneService;
        this.userMapper = userMapper;
        this.featureAccessService = featureAccessService;
        this.taskTransferService = taskTransferService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public ParentRelationshipInvitationView createInvitation(
            CreateParentRelationshipInvitationCommand command
    ) {
        Objects.requireNonNull(command, "家长关系邀请命令不能为空");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        LocalDateTime now = LocalDateTime.now(clock);
        List<ParentRelationship> relationships =
                relationshipMapper.findActiveByStudentIdForUpdate(command.studentId());
        requireActivePrimary(relationships, command.operatorUserId());
        if (command.invitationType() == ParentRelationshipInvitationType.SECONDARY_BIND
                && activeSecondary(relationships) != null) {
            throw new IllegalStateException("该学生已存在活动副家长");
        }

        User invitee = userMapper.findByMobileForUpdate(command.inviteeMobile());
        if (invitee != null && invitee.id().equals(command.operatorUserId())) {
            throw new IllegalArgumentException("不能邀请本人成为其他家长角色");
        }
        invitationMapper.expirePendingByStudentAndType(
                command.studentId(), command.invitationType(), now);
        Long invitationId = idGenerator.nextId();
        LocalDateTime expiresAt = now.plusMinutes(5);
        ParentRelationshipInvitation invitation = new ParentRelationshipInvitation(
                invitationId, command.studentId(), command.operatorUserId(),
                invitee == null ? null : invitee.id(), command.inviteeMobile(),
                command.invitationType(), ParentRelationshipInvitationStatus.PENDING,
                "PENDING", expiresAt, null, null, now, now);
        try {
            if (invitationMapper.insert(invitation) != 1) {
                throw new IllegalStateException("家长关系邀请保存失败");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("该学生已有待处理的同类关系邀请", exception);
        }
        phoneService.issueSmsCode(
                command.inviteeMobile(), smsPurpose(command.invitationType()),
                command.clientType(), command.sourceAddressHash());
        return new ParentRelationshipInvitationView(
                invitationId, maskMobile(command.inviteeMobile()), expiresAt);
    }

    /** 主副家长均可读取本人活动关系学生的当前主副关系。 */
    @Transactional(readOnly = true)
    public ParentRelationshipView getRelationships(Long operatorUserId, Long studentId) {
        List<ParentRelationship> relationships = relationshipMapper.findActiveByStudentId(studentId);
        boolean accessible = relationships.stream()
                .anyMatch(relationship -> relationship.parentUserId().equals(operatorUserId));
        if (!accessible) {
            throw new ResourceNotFoundException("家长关系不存在或不可访问");
        }
        Long primaryUserId = relationships.stream()
                .filter(relationship -> relationship.role() == ParentRelationshipRole.PRIMARY_GUARDIAN)
                .map(ParentRelationship::parentUserId)
                .findFirst().orElse(null);
        Long secondaryUserId = relationships.stream()
                .filter(relationship -> relationship.role() == ParentRelationshipRole.SECONDARY_GUARDIAN)
                .map(ParentRelationship::parentUserId)
                .findFirst().orElse(null);
        ParentRelationship primary = relationships.stream()
                .filter(relationship -> relationship.role() == ParentRelationshipRole.PRIMARY_GUARDIAN)
                .findFirst().orElse(null);
        ParentRelationship secondary = relationships.stream()
                .filter(relationship -> relationship.role() == ParentRelationshipRole.SECONDARY_GUARDIAN)
                .findFirst().orElse(null);
        return new ParentRelationshipView(
                studentId, primaryUserId, secondaryUserId,
                memberView(primary), memberView(secondary));
    }

    /** 查询当前家长全部活动关系学生，供独立关系管理功能选择。 */
    @Transactional(readOnly = true)
    public List<ParentRelationshipStudentView> getRelationshipStudents(Long operatorUserId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        return List.copyOf(relationshipMapper.findActiveStudentsByParent(operatorUserId));
    }

    @Transactional
    public VerifiedParentAccount acceptInvitation(RespondParentRelationshipInvitationCommand command) {
        Objects.requireNonNull(command, "家长关系邀请响应命令不能为空");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        LocalDateTime now = LocalDateTime.now(clock);
        ParentRelationshipInvitation invitation = requirePendingInvitation(command, now);
        ParentSmsPurpose purpose = smsPurpose(invitation.invitationType());
        VerifiedParentAccount account = phoneService.verifyRelationshipInvitationMobile(
                new ParentRelationshipMobileVerificationCommand(
                        command.mobile(), command.smsCode(), purpose, command.clientType(),
                        command.agreementAccepted(), command.agreementVersion(),
                        command.sourceAddressHash()));
        if (invitation.inviteeUserId() != null
                && !invitation.inviteeUserId().equals(account.userId())) {
            throw new AuthenticationFailedException();
        }

        List<ParentRelationship> relationships =
                relationshipMapper.findActiveByStudentIdForUpdate(invitation.studentId());
        ParentRelationship primary = requireActivePrimary(relationships, invitation.inviterUserId());
        if (invitation.invitationType() == ParentRelationshipInvitationType.PRIMARY_TRANSFER) {
            return acceptPrimaryTransfer(invitation, account, relationships, primary, now);
        }
        if (activeSecondary(relationships) != null || invitation.inviterUserId().equals(account.userId())) {
            throw new IllegalStateException("副家长关系已存在或目标账号无效");
        }
        if (relationshipMapper.countActiveStudentsByParent(account.userId()) >= MAX_ACTIVE_STUDENTS) {
            throw new IllegalStateException("该家长已达到最多可关联学生数量");
        }

        ParentRelationship historical =
                relationshipMapper.findByParentAndStudent(account.userId(), invitation.studentId());
        Long relationshipId;
        ParentRelationshipChangeType changeType;
        if (historical == null) {
            relationshipId = idGenerator.nextId();
            if (relationshipMapper.insertSecondary(
                    relationshipId, account.userId(), invitation.studentId(), now) != 1) {
                throw new IllegalStateException("副家长关系保存失败");
            }
            changeType = ParentRelationshipChangeType.BIND_SECONDARY;
        } else {
            relationshipId = historical.id();
            if (!"UNBOUND".equals(historical.status())
                    || relationshipMapper.reactivateAsSecondary(relationshipId, now) != 1) {
                throw new IllegalStateException("历史家长关系重新绑定失败");
            }
            changeType = ParentRelationshipChangeType.BIND_SECONDARY;
        }
        if (invitationMapper.respondIfPending(
                invitation.id(), ParentRelationshipInvitationStatus.ACCEPTED,
                closedScope(invitation.id()), account.userId(), now) != 1) {
            throw new AuthenticationFailedException();
        }
        appendChange(new ParentRelationshipChange(
                idGenerator.nextId(), invitation.studentId(), relationshipId, invitation.id(),
                changeType, account.userId(), null, account.userId(), null,
                ParentRelationshipRole.SECONDARY_GUARDIAN, now, now));
        return account;
    }

    /** 在同一数据库事务中完成邀请接受、关系变更、任务转交与独立会话创建。 */
    @Transactional
    public ParentPhoneAuthenticatedSession acceptInvitationAndCreateSession(
            RespondParentRelationshipInvitationCommand command,
            String deviceId,
            String deviceName
    ) {
        VerifiedParentAccount account = acceptInvitation(command);
        return phoneService.createRelationshipSession(
                account, command.clientType(), deviceId, deviceName);
    }

    private VerifiedParentAccount acceptPrimaryTransfer(
            ParentRelationshipInvitation invitation,
            VerifiedParentAccount account,
            List<ParentRelationship> relationships,
            ParentRelationship primary,
            LocalDateTime now
    ) {
        if (primary.parentUserId().equals(account.userId())) {
            throw new IllegalStateException("监护权不能转移给当前主家长本人");
        }
        ParentRelationship activeSecondary = relationships.stream()
                .filter(relationship -> relationship.parentUserId().equals(account.userId()))
                .filter(relationship -> relationship.role() == ParentRelationshipRole.SECONDARY_GUARDIAN)
                .findFirst()
                .orElse(null);
        ParentRelationship currentSecondary = activeSecondary(relationships);
        if (currentSecondary != null && activeSecondary == null) {
            throw new IllegalStateException("学生已有其他活动副家长，不能转移给第三位家长");
        }

        Long targetRelationshipId;
        if (activeSecondary != null) {
            targetRelationshipId = activeSecondary.id();
            String transitionScope = "TRANSITION:" + invitation.id();
            if (relationshipMapper.moveToTransitionScope(
                    activeSecondary.id(), transitionScope, now) != 1
                    || relationshipMapper.demoteToSecondary(primary.id(), now) != 1
                    || relationshipMapper.promoteTransitionToPrimary(activeSecondary.id(), now) != 1) {
                throw new IllegalStateException("监护权转移关系更新失败");
            }
        } else {
            if (relationshipMapper.countActiveStudentsByParent(account.userId()) >= MAX_ACTIVE_STUDENTS) {
                throw new IllegalStateException("该家长已达到最多可关联学生数量");
            }
            ParentRelationship historical = relationshipMapper.findByParentAndStudent(
                    account.userId(), invitation.studentId());
            if (relationshipMapper.demoteToSecondary(primary.id(), now) != 1) {
                throw new IllegalStateException("原主家长角色降级失败");
            }
            if (historical == null) {
                targetRelationshipId = idGenerator.nextId();
                if (relationshipMapper.insertPrimaryAt(
                        targetRelationshipId, account.userId(), invitation.studentId(), now) != 1) {
                    throw new IllegalStateException("新主家长关系保存失败");
                }
            } else {
                targetRelationshipId = historical.id();
                if (!"UNBOUND".equals(historical.status())
                        || relationshipMapper.reactivateAsPrimary(targetRelationshipId, now) != 1) {
                    throw new IllegalStateException("历史主家长关系重新激活失败");
                }
            }
        }
        taskTransferService.transferPendingFamilyReviews(
                invitation.studentId(), primary.parentUserId(), account.userId(), account.userId());
        if (invitationMapper.respondIfPending(
                invitation.id(), ParentRelationshipInvitationStatus.ACCEPTED,
                closedScope(invitation.id()), account.userId(), now) != 1) {
            throw new AuthenticationFailedException();
        }
        appendChange(new ParentRelationshipChange(
                idGenerator.nextId(), invitation.studentId(), targetRelationshipId, invitation.id(),
                ParentRelationshipChangeType.TRANSFER_PRIMARY, account.userId(),
                primary.parentUserId(), account.userId(),
                activeSecondary == null ? null : ParentRelationshipRole.SECONDARY_GUARDIAN,
                ParentRelationshipRole.PRIMARY_GUARDIAN, now, now));
        return account;
    }

    /** 拒绝邀请只消费验证码并关闭邀请，不创建账号或家长关系。 */
    @Transactional
    public void rejectInvitation(RespondParentRelationshipInvitationCommand command) {
        Objects.requireNonNull(command, "家长关系邀请响应命令不能为空");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        LocalDateTime now = LocalDateTime.now(clock);
        ParentRelationshipInvitation invitation = requirePendingInvitation(command, now);
        phoneService.verifyRelationshipInvitationCodeOnly(
                command.mobile(), command.smsCode(), smsPurpose(invitation.invitationType()),
                command.clientType());
        if (invitationMapper.respondIfPending(
                invitation.id(), ParentRelationshipInvitationStatus.REJECTED,
                closedScope(invitation.id()), null, now) != 1) {
            throw new AuthenticationFailedException();
        }
    }

    /** 活动主家长解除指定副家长关系，副家长不能通过该入口解除自己。 */
    @Transactional
    public void unbindSecondary(Long operatorUserId, Long studentId, Long secondaryParentUserId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        LocalDateTime now = LocalDateTime.now(clock);
        List<ParentRelationship> relationships =
                relationshipMapper.findActiveByStudentIdForUpdate(studentId);
        requireActivePrimary(relationships, operatorUserId);
        ParentRelationship secondary = relationships.stream()
                .filter(relationship -> relationship.parentUserId().equals(secondaryParentUserId))
                .filter(relationship -> relationship.role() == ParentRelationshipRole.SECONDARY_GUARDIAN)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("家长关系不存在或不可管理"));
        if (relationshipMapper.unbind(secondary.id(), closedScope(secondary.id()), now) != 1) {
            throw new IllegalStateException("副家长关系解除失败");
        }
        appendChange(new ParentRelationshipChange(
                idGenerator.nextId(), studentId, secondary.id(), null,
                ParentRelationshipChangeType.UNBIND_SECONDARY, operatorUserId,
                secondary.parentUserId(), null, ParentRelationshipRole.SECONDARY_GUARDIAN,
                null, now, now));
    }

    /** 主家长解除自己；存在副家长时按锁定结果顺序晋升最早绑定者。 */
    @Transactional
    public ParentRelationshipView unbindPrimary(Long operatorUserId, Long studentId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        LocalDateTime now = LocalDateTime.now(clock);
        List<ParentRelationship> relationships =
                relationshipMapper.findActiveByStudentIdForUpdate(studentId);
        ParentRelationship primary = requireActivePrimary(relationships, operatorUserId);
        ParentRelationship secondary = activeSecondary(relationships);
        if (relationshipMapper.unbind(primary.id(), closedScope(primary.id()), now) != 1) {
            throw new IllegalStateException("主家长关系解除失败");
        }

        ParentRelationshipChangeType operationType;
        Long newPrimaryUserId = null;
        if (secondary == null) {
            operationType = ParentRelationshipChangeType.UNBIND_PRIMARY_ORPHAN;
        } else {
            if (relationshipMapper.promoteToPrimary(secondary.id(), now) != 1) {
                throw new IllegalStateException("副家长自动晋升失败");
            }
            operationType = ParentRelationshipChangeType.UNBIND_PRIMARY_PROMOTE;
            newPrimaryUserId = secondary.parentUserId();
            taskTransferService.transferPendingFamilyReviews(
                    studentId, primary.parentUserId(), secondary.parentUserId(), operatorUserId);
        }
        appendChange(new ParentRelationshipChange(
                idGenerator.nextId(), studentId, primary.id(), null, operationType,
                operatorUserId, primary.parentUserId(), newPrimaryUserId,
                ParentRelationshipRole.PRIMARY_GUARDIAN,
                secondary == null ? null : ParentRelationshipRole.PRIMARY_GUARDIAN,
                now, now));
        return new ParentRelationshipView(studentId, newPrimaryUserId, null);
    }

    private ParentRelationshipInvitation requirePendingInvitation(
            RespondParentRelationshipInvitationCommand command,
            LocalDateTime now
    ) {
        ParentRelationshipInvitation invitation = invitationMapper.findByIdForUpdate(command.invitationId());
        if (invitation == null
                || invitation.status() != ParentRelationshipInvitationStatus.PENDING
                || !Objects.equals(invitation.inviteeMobile(), command.mobile())) {
            throw new AuthenticationFailedException();
        }
        if (!invitation.expiresAt().isAfter(now)) {
            invitationMapper.respondIfPending(
                    invitation.id(), ParentRelationshipInvitationStatus.EXPIRED,
                    closedScope(invitation.id()), null, now);
            throw new IllegalStateException("家长关系邀请已过期");
        }
        return invitation;
    }

    private ParentRelationship requireActivePrimary(
            List<ParentRelationship> relationships,
            Long parentUserId
    ) {
        return relationships.stream()
                .filter(relationship -> relationship.parentUserId().equals(parentUserId))
                .filter(relationship -> relationship.role() == ParentRelationshipRole.PRIMARY_GUARDIAN)
                .filter(relationship -> "ACTIVE".equals(relationship.status()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("家长关系不存在或不可管理"));
    }

    private ParentRelationship activeSecondary(List<ParentRelationship> relationships) {
        return relationships.stream()
                .filter(relationship -> relationship.role() == ParentRelationshipRole.SECONDARY_GUARDIAN)
                .filter(relationship -> "ACTIVE".equals(relationship.status()))
                .findFirst()
                .orElse(null);
    }

    private ParentSmsPurpose smsPurpose(ParentRelationshipInvitationType invitationType) {
        if (invitationType == ParentRelationshipInvitationType.SECONDARY_BIND) {
            return ParentSmsPurpose.SECONDARY_PARENT_BIND;
        }
        if (invitationType == ParentRelationshipInvitationType.PRIMARY_TRANSFER) {
            return ParentSmsPurpose.PRIMARY_PARENT_TRANSFER;
        }
        throw new IllegalArgumentException("家长关系邀请类型不能为空");
    }

    private void appendChange(ParentRelationshipChange change) {
        if (changeMapper.insert(change) != 1) {
            throw new IllegalStateException("家长关系变更审计保存失败");
        }
    }

    private String maskMobile(String mobile) {
        if (mobile == null || !mobile.matches("1[3-9]\\d{9}")) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }

    private ParentRelationshipMemberView memberView(ParentRelationship relationship) {
        if (relationship == null) {
            return null;
        }
        User user = userMapper.findById(relationship.parentUserId());
        return new ParentRelationshipMemberView(
                relationship.parentUserId(), user == null ? null : user.displayName(),
                user == null || user.mobile() == null ? null : maskMobile(user.mobile()),
                relationship.role());
    }

    private String closedScope(Long id) {
        return "CLOSED:" + id;
    }
}
