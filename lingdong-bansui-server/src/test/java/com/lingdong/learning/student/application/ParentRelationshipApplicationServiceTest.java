package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.application.ParentPhoneAuthenticationService;
import com.lingdong.learning.auth.application.AuthenticationFailedException;
import com.lingdong.learning.auth.application.ParentRelationshipMobileVerificationCommand;
import com.lingdong.learning.auth.application.ParentSmsPurpose;
import com.lingdong.learning.auth.application.VerifiedParentAccount;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.student.domain.ParentRelationship;
import com.lingdong.learning.student.domain.ParentRelationshipInvitation;
import com.lingdong.learning.student.domain.ParentRelationshipInvitationStatus;
import com.lingdong.learning.student.domain.ParentRelationshipInvitationType;
import com.lingdong.learning.student.domain.ParentRelationshipRole;
import com.lingdong.learning.student.infrastructure.persistence.ParentRelationshipChangeMapper;
import com.lingdong.learning.student.infrastructure.persistence.ParentRelationshipInvitationMapper;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class ParentRelationshipApplicationServiceTest {
    private static final long PRIMARY_USER_ID = 8910000000000000101L;
    private static final long SECONDARY_USER_ID = 8910000000000000102L;
    private static final long STUDENT_ID = 8910000000000000103L;
    private static final long PRIMARY_RELATIONSHIP_ID = 8910000000000000104L;
    private static final String MOBILE = "13800138000";
    private static final String CODE = "384291";

    private final ParentStudentMapper relationshipMapper = mock(ParentStudentMapper.class);
    private final ParentRelationshipInvitationMapper invitationMapper =
            mock(ParentRelationshipInvitationMapper.class);
    private final ParentRelationshipChangeMapper changeMapper = mock(ParentRelationshipChangeMapper.class);
    private final ParentPhoneAuthenticationService phoneService = mock(ParentPhoneAuthenticationService.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
    private final ParentRelationshipTaskTransferService taskTransferService =
            mock(ParentRelationshipTaskTransferService.class);
    private final AtomicLong ids = new AtomicLong(8910000000000000200L);
    private final IdGenerator idGenerator = ids::incrementAndGet;
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-09T05:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private ParentRelationshipApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ParentRelationshipApplicationService(
                relationshipMapper, invitationMapper, changeMapper, phoneService,
                userMapper, featureAccessService, taskTransferService, idGenerator, clock);
    }

    @Test
    void createsFiveMinuteSecondaryInvitationForActivePrimaryParent() {
        when(relationshipMapper.findActiveByStudentIdForUpdate(STUDENT_ID))
                .thenReturn(List.of(primaryRelationship()));
        when(invitationMapper.insert(any(ParentRelationshipInvitation.class))).thenReturn(1);

        ParentRelationshipInvitationView result = service.createInvitation(
                new CreateParentRelationshipInvitationCommand(
                        PRIMARY_USER_ID, STUDENT_ID, MOBILE,
                        ParentRelationshipInvitationType.SECONDARY_BIND,
                        AuthClientType.WEB, "source-address-hash"));

        assertThat(result.invitationId()).isNotNull();
        assertThat(result.maskedMobile()).isEqualTo("138****8000");
        assertThat(result.expiresAt()).isEqualTo(LocalDateTime.now(clock).plusMinutes(5));
        verify(invitationMapper).expirePendingByStudentAndType(
                STUDENT_ID, ParentRelationshipInvitationType.SECONDARY_BIND, LocalDateTime.now(clock));
        verify(phoneService).issueSmsCode(
                MOBILE, ParentSmsPurpose.SECONDARY_PARENT_BIND,
                AuthClientType.WEB, "source-address-hash");
    }

    @Test
    void returnsOnlyMaskedCurrentRelationshipMembers() {
        when(relationshipMapper.findActiveByStudentId(STUDENT_ID))
                .thenReturn(List.of(primaryRelationship(), secondaryRelationship()));
        when(userMapper.findById(PRIMARY_USER_ID)).thenReturn(User.create(
                PRIMARY_USER_ID, "primary", "主家长", "13800138000", UserType.FAMILY));
        when(userMapper.findById(SECONDARY_USER_ID)).thenReturn(User.create(
                SECONDARY_USER_ID, "secondary", "副家长", "13900139000", UserType.FAMILY));

        ParentRelationshipView result = service.getRelationships(PRIMARY_USER_ID, STUDENT_ID);

        assertThat(result.primaryParent().displayName()).isEqualTo("主家长");
        assertThat(result.primaryParent().mobileMasked()).isEqualTo("138****8000");
        assertThat(result.primaryParent().relationshipRole())
                .isEqualTo(ParentRelationshipRole.PRIMARY_GUARDIAN);
        assertThat(result.secondaryParent().displayName()).isEqualTo("副家长");
        assertThat(result.secondaryParent().mobileMasked()).isEqualTo("139****9000");
    }

    @Test
    void listsOnlyStudentsWithCurrentParentRelationship() {
        when(relationshipMapper.findActiveStudentsByParent(PRIMARY_USER_ID)).thenReturn(List.of(
                new ParentRelationshipStudentView(
                        STUDENT_ID, "小灵", ParentRelationshipRole.PRIMARY_GUARDIAN)));

        List<ParentRelationshipStudentView> result = service.getRelationshipStudents(PRIMARY_USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).studentId()).isEqualTo(STUDENT_ID);
        assertThat(result.get(0).relationshipRole())
                .isEqualTo(ParentRelationshipRole.PRIMARY_GUARDIAN);
    }

    @Test
    void rejectsInvitationWhenOperatorIsNotPrimaryOrStudentAlreadyHasSecondary() {
        when(relationshipMapper.findActiveByStudentIdForUpdate(STUDENT_ID)).thenReturn(List.of());
        assertThatThrownBy(() -> service.createInvitation(new CreateParentRelationshipInvitationCommand(
                PRIMARY_USER_ID, STUDENT_ID, MOBILE,
                ParentRelationshipInvitationType.SECONDARY_BIND,
                AuthClientType.WEB, "source-address-hash")))
                .isInstanceOf(RuntimeException.class);

        when(relationshipMapper.findActiveByStudentIdForUpdate(STUDENT_ID)).thenReturn(List.of(
                primaryRelationship(), secondaryRelationship()));
        assertThatThrownBy(() -> service.createInvitation(new CreateParentRelationshipInvitationCommand(
                PRIMARY_USER_ID, STUDENT_ID, MOBILE,
                ParentRelationshipInvitationType.SECONDARY_BIND,
                AuthClientType.WEB, "source-address-hash")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void acceptsInvitationAndCreatesAuditedSecondaryRelationship() {
        ParentRelationshipInvitation invitation = pendingInvitation();
        when(invitationMapper.findByIdForUpdate(invitation.id())).thenReturn(invitation);
        when(phoneService.verifyRelationshipInvitationMobile(any(ParentRelationshipMobileVerificationCommand.class)))
                .thenReturn(new VerifiedParentAccount(SECONDARY_USER_ID, true, "1"));
        when(relationshipMapper.findActiveByStudentIdForUpdate(STUDENT_ID))
                .thenReturn(List.of(primaryRelationship()));
        when(relationshipMapper.countActiveStudentsByParent(SECONDARY_USER_ID)).thenReturn(9L);
        when(relationshipMapper.findByParentAndStudent(SECONDARY_USER_ID, STUDENT_ID)).thenReturn(null);
        when(relationshipMapper.insertSecondary(anyLong(), eq(SECONDARY_USER_ID), eq(STUDENT_ID), any()))
                .thenReturn(1);
        when(invitationMapper.respondIfPending(
                eq(invitation.id()), eq(ParentRelationshipInvitationStatus.ACCEPTED),
                any(), eq(SECONDARY_USER_ID), any())).thenReturn(1);
        when(changeMapper.insert(any())).thenReturn(1);

        VerifiedParentAccount result = service.acceptInvitation(new RespondParentRelationshipInvitationCommand(
                invitation.id(), MOBILE, CODE, AuthClientType.MINIAPP,
                true, "1", "source-address-hash"));

        assertThat(result.userId()).isEqualTo(SECONDARY_USER_ID);
        verify(phoneService).verifyRelationshipInvitationMobile(
                new ParentRelationshipMobileVerificationCommand(
                        MOBILE, CODE, ParentSmsPurpose.SECONDARY_PARENT_BIND,
                        AuthClientType.MINIAPP, true, "1", "source-address-hash"));
        verify(relationshipMapper).insertSecondary(
                anyLong(), eq(SECONDARY_USER_ID), eq(STUDENT_ID), eq(LocalDateTime.now(clock)));
        verify(changeMapper).insert(any());
    }

    @Test
    void rejectsAcceptanceAtTenActiveStudents() {
        ParentRelationshipInvitation invitation = pendingInvitation();
        when(invitationMapper.findByIdForUpdate(invitation.id())).thenReturn(invitation);
        when(phoneService.verifyRelationshipInvitationMobile(any()))
                .thenReturn(new VerifiedParentAccount(SECONDARY_USER_ID, false, "1"));
        when(relationshipMapper.findActiveByStudentIdForUpdate(STUDENT_ID))
                .thenReturn(List.of(primaryRelationship()));
        when(relationshipMapper.countActiveStudentsByParent(SECONDARY_USER_ID)).thenReturn(10L);

        assertThatThrownBy(() -> service.acceptInvitation(new RespondParentRelationshipInvitationCommand(
                invitation.id(), MOBILE, CODE, AuthClientType.WEB,
                true, "1", "source-address-hash")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void primaryParentUnbindsSecondaryAndAppendsAudit() {
        ParentRelationship secondary = secondaryRelationship();
        when(relationshipMapper.findActiveByStudentIdForUpdate(STUDENT_ID))
                .thenReturn(List.of(primaryRelationship(), secondary));
        when(relationshipMapper.unbind(eq(secondary.id()), any(), any())).thenReturn(1);
        when(changeMapper.insert(any())).thenReturn(1);

        service.unbindSecondary(PRIMARY_USER_ID, STUDENT_ID, SECONDARY_USER_ID);

        verify(relationshipMapper).unbind(
                secondary.id(), "CLOSED:" + secondary.id(), LocalDateTime.now(clock));
        verify(changeMapper).insert(any());
    }

    @Test
    void rejectsInvitationAfterCodeVerificationWithoutCreatingParentAccount() {
        ParentRelationshipInvitation invitation = pendingInvitation();
        when(invitationMapper.findByIdForUpdate(invitation.id())).thenReturn(invitation);
        when(invitationMapper.respondIfPending(
                eq(invitation.id()), eq(ParentRelationshipInvitationStatus.REJECTED),
                any(), eq(null), any())).thenReturn(1);

        service.rejectInvitation(new RespondParentRelationshipInvitationCommand(
                invitation.id(), MOBILE, CODE, AuthClientType.WEB,
                false, null, "source-address-hash"));

        verify(phoneService).verifyRelationshipInvitationCodeOnly(
                MOBILE, CODE, ParentSmsPurpose.SECONDARY_PARENT_BIND, AuthClientType.WEB);
        verify(phoneService, never()).verifyRelationshipInvitationMobile(any());
        verify(invitationMapper).respondIfPending(
                invitation.id(), ParentRelationshipInvitationStatus.REJECTED,
                "CLOSED:" + invitation.id(), null, LocalDateTime.now(clock));
    }

    @Test
    void expiresInvitationWithoutConsumingVerificationCode() {
        LocalDateTime now = LocalDateTime.now(clock);
        ParentRelationshipInvitation expired = new ParentRelationshipInvitation(
                8910000000000000109L, STUDENT_ID, PRIMARY_USER_ID, null, MOBILE,
                ParentRelationshipInvitationType.SECONDARY_BIND,
                ParentRelationshipInvitationStatus.PENDING, "PENDING",
                now, null, null, now.minusMinutes(5), now.minusMinutes(5));
        when(invitationMapper.findByIdForUpdate(expired.id())).thenReturn(expired);

        assertThatThrownBy(() -> service.acceptInvitation(
                new RespondParentRelationshipInvitationCommand(
                        expired.id(), MOBILE, CODE, AuthClientType.WEB,
                        true, "1", "source-address-hash")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("家长关系邀请已过期");

        verify(invitationMapper).respondIfPending(
                expired.id(), ParentRelationshipInvitationStatus.EXPIRED,
                "CLOSED:" + expired.id(), null, now);
        verify(phoneService, never()).verifyRelationshipInvitationMobile(any());
    }

    @Test
    void rejectsRepeatedResponseBeforeConsumingVerificationCode() {
        LocalDateTime now = LocalDateTime.now(clock);
        ParentRelationshipInvitation responded = new ParentRelationshipInvitation(
                8910000000000000110L, STUDENT_ID, PRIMARY_USER_ID, SECONDARY_USER_ID, MOBILE,
                ParentRelationshipInvitationType.SECONDARY_BIND,
                ParentRelationshipInvitationStatus.ACCEPTED, "CLOSED:8910000000000000110",
                now.plusMinutes(5), now, SECONDARY_USER_ID, now.minusMinutes(1), now);
        when(invitationMapper.findByIdForUpdate(responded.id())).thenReturn(responded);

        assertThatThrownBy(() -> service.rejectInvitation(
                new RespondParentRelationshipInvitationCommand(
                        responded.id(), MOBILE, CODE, AuthClientType.WEB,
                        false, null, "source-address-hash")))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(phoneService, never()).verifyRelationshipInvitationCodeOnly(
                any(), any(), any(), any());
    }

    @Test
    void rollsBackAcceptanceWhenConditionalResponseLosesConcurrencyRace() {
        ParentRelationshipInvitation invitation = pendingInvitation();
        when(invitationMapper.findByIdForUpdate(invitation.id())).thenReturn(invitation);
        when(phoneService.verifyRelationshipInvitationMobile(any()))
                .thenReturn(new VerifiedParentAccount(SECONDARY_USER_ID, false, "1"));
        when(relationshipMapper.findActiveByStudentIdForUpdate(STUDENT_ID))
                .thenReturn(List.of(primaryRelationship()));
        when(relationshipMapper.findByParentAndStudent(SECONDARY_USER_ID, STUDENT_ID))
                .thenReturn(null);
        when(relationshipMapper.insertSecondary(
                anyLong(), eq(SECONDARY_USER_ID), eq(STUDENT_ID), any())).thenReturn(1);
        when(invitationMapper.respondIfPending(
                eq(invitation.id()), eq(ParentRelationshipInvitationStatus.ACCEPTED),
                any(), eq(SECONDARY_USER_ID), any())).thenReturn(0);

        assertThatThrownBy(() -> service.acceptInvitation(
                new RespondParentRelationshipInvitationCommand(
                        invitation.id(), MOBILE, CODE, AuthClientType.WEB,
                        true, "1", "source-address-hash")))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(changeMapper, never()).insert(any());
    }

    @Test
    void primarySelfUnbindPromotesEarliestSecondary() {
        ParentRelationship secondary = secondaryRelationship();
        when(relationshipMapper.findActiveByStudentIdForUpdate(STUDENT_ID))
                .thenReturn(List.of(primaryRelationship(), secondary));
        when(relationshipMapper.unbind(eq(PRIMARY_RELATIONSHIP_ID), any(), any())).thenReturn(1);
        when(relationshipMapper.promoteToPrimary(secondary.id(), LocalDateTime.now(clock))).thenReturn(1);
        when(changeMapper.insert(any())).thenReturn(1);

        ParentRelationshipView result = service.unbindPrimary(PRIMARY_USER_ID, STUDENT_ID);

        assertThat(result.primaryParentUserId()).isEqualTo(SECONDARY_USER_ID);
        verify(relationshipMapper).promoteToPrimary(secondary.id(), LocalDateTime.now(clock));
        verify(taskTransferService).transferPendingFamilyReviews(
                STUDENT_ID, PRIMARY_USER_ID, SECONDARY_USER_ID, PRIMARY_USER_ID);
    }

    @Test
    void primarySelfUnbindWithoutSecondaryLeavesStudentWithoutPrimary() {
        when(relationshipMapper.findActiveByStudentIdForUpdate(STUDENT_ID))
                .thenReturn(List.of(primaryRelationship()));
        when(relationshipMapper.unbind(eq(PRIMARY_RELATIONSHIP_ID), any(), any())).thenReturn(1);
        when(changeMapper.insert(any())).thenReturn(1);

        ParentRelationshipView result = service.unbindPrimary(PRIMARY_USER_ID, STUDENT_ID);

        assertThat(result.primaryParentUserId()).isNull();
        assertThat(result.secondaryParentUserId()).isNull();
        verify(taskTransferService, never()).transferPendingFamilyReviews(
                anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void acceptsPrimaryTransferBySafelySwappingCurrentPrimaryAndSecondary() {
        LocalDateTime now = LocalDateTime.now(clock);
        ParentRelationshipInvitation invitation = new ParentRelationshipInvitation(
                8910000000000000107L, STUDENT_ID, PRIMARY_USER_ID, SECONDARY_USER_ID, MOBILE,
                ParentRelationshipInvitationType.PRIMARY_TRANSFER,
                ParentRelationshipInvitationStatus.PENDING, "PENDING",
                now.plusMinutes(5), null, null, now, now);
        ParentRelationship secondary = secondaryRelationship();
        when(invitationMapper.findByIdForUpdate(invitation.id())).thenReturn(invitation);
        when(phoneService.verifyRelationshipInvitationMobile(any()))
                .thenReturn(new VerifiedParentAccount(SECONDARY_USER_ID, false, "1"));
        when(relationshipMapper.findActiveByStudentIdForUpdate(STUDENT_ID))
                .thenReturn(List.of(primaryRelationship(), secondary));
        when(relationshipMapper.moveToTransitionScope(
                secondary.id(), "TRANSITION:" + invitation.id(), now)).thenReturn(1);
        when(relationshipMapper.demoteToSecondary(PRIMARY_RELATIONSHIP_ID, now)).thenReturn(1);
        when(relationshipMapper.promoteTransitionToPrimary(secondary.id(), now)).thenReturn(1);
        when(invitationMapper.respondIfPending(
                eq(invitation.id()), eq(ParentRelationshipInvitationStatus.ACCEPTED),
                any(), eq(SECONDARY_USER_ID), any())).thenReturn(1);
        when(changeMapper.insert(any())).thenReturn(1);

        VerifiedParentAccount result = service.acceptInvitation(new RespondParentRelationshipInvitationCommand(
                invitation.id(), MOBILE, CODE, AuthClientType.WEB,
                true, "1", "source-address-hash"));

        assertThat(result.userId()).isEqualTo(SECONDARY_USER_ID);
        var order = inOrder(relationshipMapper);
        order.verify(relationshipMapper).moveToTransitionScope(
                secondary.id(), "TRANSITION:" + invitation.id(), now);
        order.verify(relationshipMapper).demoteToSecondary(PRIMARY_RELATIONSHIP_ID, now);
        order.verify(relationshipMapper).promoteTransitionToPrimary(secondary.id(), now);
        verify(changeMapper).insert(any());
        verify(taskTransferService).transferPendingFamilyReviews(
                STUDENT_ID, PRIMARY_USER_ID, SECONDARY_USER_ID, SECONDARY_USER_ID);
    }

    @Test
    void acceptsPrimaryTransferToNewParentWhenStudentHasNoSecondary() {
        LocalDateTime now = LocalDateTime.now(clock);
        ParentRelationshipInvitation invitation = new ParentRelationshipInvitation(
                8910000000000000108L, STUDENT_ID, PRIMARY_USER_ID, null, MOBILE,
                ParentRelationshipInvitationType.PRIMARY_TRANSFER,
                ParentRelationshipInvitationStatus.PENDING, "PENDING",
                now.plusMinutes(5), null, null, now, now);
        when(invitationMapper.findByIdForUpdate(invitation.id())).thenReturn(invitation);
        when(phoneService.verifyRelationshipInvitationMobile(any()))
                .thenReturn(new VerifiedParentAccount(SECONDARY_USER_ID, true, "1"));
        when(relationshipMapper.findActiveByStudentIdForUpdate(STUDENT_ID))
                .thenReturn(List.of(primaryRelationship()));
        when(relationshipMapper.countActiveStudentsByParent(SECONDARY_USER_ID)).thenReturn(9L);
        when(relationshipMapper.findByParentAndStudent(SECONDARY_USER_ID, STUDENT_ID)).thenReturn(null);
        when(relationshipMapper.demoteToSecondary(PRIMARY_RELATIONSHIP_ID, now)).thenReturn(1);
        when(relationshipMapper.insertPrimaryAt(
                anyLong(), eq(SECONDARY_USER_ID), eq(STUDENT_ID), eq(now))).thenReturn(1);
        when(invitationMapper.respondIfPending(
                eq(invitation.id()), eq(ParentRelationshipInvitationStatus.ACCEPTED),
                any(), eq(SECONDARY_USER_ID), any())).thenReturn(1);
        when(changeMapper.insert(any())).thenReturn(1);

        service.acceptInvitation(new RespondParentRelationshipInvitationCommand(
                invitation.id(), MOBILE, CODE, AuthClientType.MINIAPP,
                true, "1", "source-address-hash"));

        var order = inOrder(relationshipMapper);
        order.verify(relationshipMapper).demoteToSecondary(PRIMARY_RELATIONSHIP_ID, now);
        order.verify(relationshipMapper).insertPrimaryAt(
                anyLong(), eq(SECONDARY_USER_ID), eq(STUDENT_ID), eq(now));
        verify(taskTransferService).transferPendingFamilyReviews(
                STUDENT_ID, PRIMARY_USER_ID, SECONDARY_USER_ID, SECONDARY_USER_ID);
    }

    private ParentRelationship primaryRelationship() {
        return new ParentRelationship(
                PRIMARY_RELATIONSHIP_ID, PRIMARY_USER_ID, STUDENT_ID,
                ParentRelationshipRole.PRIMARY_GUARDIAN, "ACTIVE", "PRIMARY",
                LocalDateTime.now(clock).minusDays(10), null);
    }

    private ParentRelationship secondaryRelationship() {
        return new ParentRelationship(
                8910000000000000105L, SECONDARY_USER_ID, STUDENT_ID,
                ParentRelationshipRole.SECONDARY_GUARDIAN, "ACTIVE", "SECONDARY",
                LocalDateTime.now(clock).minusDays(5), null);
    }

    private ParentRelationshipInvitation pendingInvitation() {
        LocalDateTime now = LocalDateTime.now(clock);
        return new ParentRelationshipInvitation(
                8910000000000000106L, STUDENT_ID, PRIMARY_USER_ID, null, MOBILE,
                ParentRelationshipInvitationType.SECONDARY_BIND,
                ParentRelationshipInvitationStatus.PENDING, "PENDING",
                now.plusMinutes(5), null, null, now, now);
    }
}
