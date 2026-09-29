package com.lingdong.learning.student.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.application.AuthenticatedSession;
import com.lingdong.learning.auth.application.ParentPhoneAuthenticatedSession;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.security.SessionTokenService;
import com.lingdong.learning.student.application.CreateParentRelationshipInvitationCommand;
import com.lingdong.learning.student.application.ParentRelationshipApplicationService;
import com.lingdong.learning.student.application.ParentRelationshipInvitationView;
import com.lingdong.learning.student.application.ParentRelationshipMemberView;
import com.lingdong.learning.student.application.ParentRelationshipView;
import com.lingdong.learning.student.application.ParentRelationshipStudentView;
import com.lingdong.learning.student.domain.ParentRelationshipRole;
import com.lingdong.learning.student.domain.ParentRelationshipInvitationType;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParentRelationshipControllerTest {
    private static final long PARENT_ID = 8910000000000000601L;
    private static final long STUDENT_ID = 8910000000000000602L;
    private static final long SECONDARY_ID = 8910000000000000603L;
    private static final long INVITATION_ID = 8910000000000000604L;

    private final ParentRelationshipApplicationService service =
            mock(ParentRelationshipApplicationService.class);
    private final SessionTokenService tokenService = mock(SessionTokenService.class);
    private final ParentRelationshipController controller =
            new ParentRelationshipController(service, tokenService);
    private final AuthenticatedUser currentUser = new AuthenticatedUser(
            PARENT_ID, 1L, "parent", "家长", AuthClientType.WEB, List.of("PARENT"));

    @Test
    void mapsAuthenticatedOperationsAndStringSnowflakeIdentifiers() {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(tokenService.hash("127.0.0.1")).thenReturn("source-address-hash");
        when(service.getRelationships(PARENT_ID, STUDENT_ID)).thenReturn(
                new ParentRelationshipView(
                        STUDENT_ID, PARENT_ID, SECONDARY_ID,
                        new ParentRelationshipMemberView(
                                PARENT_ID, "主家长", "138****8000",
                                ParentRelationshipRole.PRIMARY_GUARDIAN),
                        new ParentRelationshipMemberView(
                                SECONDARY_ID, "副家长", "139****9000",
                                ParentRelationshipRole.SECONDARY_GUARDIAN)));
        when(service.getRelationshipStudents(PARENT_ID)).thenReturn(List.of(
                new ParentRelationshipStudentView(
                        STUDENT_ID, "小灵", ParentRelationshipRole.PRIMARY_GUARDIAN)));
        when(service.createInvitation(any(CreateParentRelationshipInvitationCommand.class)))
                .thenReturn(new ParentRelationshipInvitationView(
                        INVITATION_ID, "138****8000", LocalDateTime.of(2026, 8, 9, 13, 30)));
        when(service.unbindPrimary(PARENT_ID, STUDENT_ID)).thenReturn(
                new ParentRelationshipView(STUDENT_ID, null, null));

        List<ParentRelationshipStudentResponse> students = controller.getRelationshipStudents(currentUser);
        ParentRelationshipResponse relationships = controller.getRelationships(currentUser, STUDENT_ID);
        ParentRelationshipInvitationResponse secondaryInvitation = controller.createSecondaryInvitation(
                currentUser, STUDENT_ID,
                new ParentRelationshipInvitationRequest("13800138000", AuthClientType.WEB), servletRequest);
        controller.unbindSecondary(currentUser, STUDENT_ID, SECONDARY_ID);
        ParentRelationshipResponse afterPrimaryUnbind = controller.unbindPrimary(currentUser, STUDENT_ID);
        ParentRelationshipInvitationResponse transferInvitation = controller.createPrimaryTransferInvitation(
                currentUser, STUDENT_ID,
                new ParentRelationshipInvitationRequest("13800138000", AuthClientType.WEB), servletRequest);

        assertThat(relationships.studentId()).isEqualTo(Long.toString(STUDENT_ID));
        assertThat(students.get(0).studentId()).isEqualTo(Long.toString(STUDENT_ID));
        assertThat(students.get(0).relationshipRole())
                .isEqualTo(ParentRelationshipRole.PRIMARY_GUARDIAN);
        assertThat(relationships.primaryParentUserId()).isEqualTo(Long.toString(PARENT_ID));
        assertThat(relationships.secondaryParentUserId()).isEqualTo(Long.toString(SECONDARY_ID));
        assertThat(relationships.primaryParent().userId()).isEqualTo(Long.toString(PARENT_ID));
        assertThat(relationships.primaryParent().displayName()).isEqualTo("主家长");
        assertThat(relationships.primaryParent().mobileMasked()).isEqualTo("138****8000");
        assertThat(relationships.primaryParent().mobileMasked()).doesNotContain("13800138000");
        assertThat(relationships.secondaryParent().relationshipRole())
                .isEqualTo(ParentRelationshipRole.SECONDARY_GUARDIAN);
        assertThat(secondaryInvitation.invitationId()).isEqualTo(Long.toString(INVITATION_ID));
        assertThat(transferInvitation.invitationId()).isEqualTo(Long.toString(INVITATION_ID));
        assertThat(afterPrimaryUnbind.studentId()).isEqualTo(Long.toString(STUDENT_ID));
        verify(service).unbindSecondary(PARENT_ID, STUDENT_ID, SECONDARY_ID);
        verify(service).createInvitation(new CreateParentRelationshipInvitationCommand(
                PARENT_ID, STUDENT_ID, "13800138000",
                ParentRelationshipInvitationType.PRIMARY_TRANSFER,
                AuthClientType.WEB, "source-address-hash"));
    }

    @Test
    void acceptsOrRejectsInvitationThroughPublicControlledResponses() {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(tokenService.hash("127.0.0.1")).thenReturn("source-address-hash");
        LocalDateTime now = LocalDateTime.of(2026, 8, 9, 14, 0);
        when(service.acceptInvitationAndCreateSession(any(), any(), any())).thenReturn(
                new ParentPhoneAuthenticatedSession(
                        new AuthenticatedSession(1L, "access", "refresh",
                                now.plusMinutes(30), now.plusDays(7)),
                        true, false, "1"));
        RespondParentRelationshipInvitationRequest request =
                new RespondParentRelationshipInvitationRequest(
                        "13800138000", "384291", AuthClientType.MINIAPP,
                        "device", "家长小程序", true, "1");

        var accepted = controller.acceptInvitation(INVITATION_ID, request, servletRequest);
        controller.rejectInvitation(INVITATION_ID, request, servletRequest);

        assertThat(accepted.session().accessToken()).isEqualTo("access");
        verify(service).rejectInvitation(any());
    }
}
