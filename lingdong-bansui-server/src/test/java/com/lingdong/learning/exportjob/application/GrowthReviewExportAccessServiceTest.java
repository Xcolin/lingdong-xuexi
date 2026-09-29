package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.feature.application.FeatureDisabledException;
import com.lingdong.learning.growthpoint.application.GrowthReviewExportPreparationService.Prepared;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 新导出和历史访问分别核验，不能用历史规则绕过新导出开关。 */
class GrowthReviewExportAccessServiceTest {
    private static final long PARENT = 1874244142494650201L;
    private static final long STUDENT = 1874244142494650202L;
    private final FeatureAccessService features = mock(FeatureAccessService.class);
    private final PermissionDecisionService permissions = mock(PermissionDecisionService.class);
    private final UserMapper users = mock(UserMapper.class);
    private final UserRoleMapper roles = mock(UserRoleMapper.class);
    private final ParentStudentMapper relations = mock(ParentStudentMapper.class);
    private GrowthReviewExportAccessService access;

    @BeforeEach void setUp() {
        access = new GrowthReviewExportAccessService(features, permissions, users, roles, relations);
        when(users.findById(PARENT)).thenReturn(new User(PARENT, "parent", "测试家长", null, null,
                UserType.FAMILY, UserStatus.ENABLED, null, null));
        when(roles.hasRoleCode(PARENT, "PARENT")).thenReturn(true);
        when(relations.existsActiveByParentAndStudent(PARENT, STUDENT)).thenReturn(true);
        for (var code : List.of("EXPORT_JOB_CREATE", "EXPORT_JOB_READ", "GROWTH_REVIEW_READ_CHILD")) {
            when(permissions.isAllowed(PARENT, PermissionClient.WEB, code)).thenReturn(true);
        }
    }

    @Test void allowsActiveSecondaryParentWithoutRequiringPrimaryRelationship() {
        access.requireCreate(user(AuthClientType.WEB), STUDENT);
        access.requireExecution(prepared());
        verify(relations, never()).existsActivePrimaryByParentAndStudent(any(), any());
        verify(features, times(2)).requireEnabled("GROWTH_REVIEW_PDF_EXPORT", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DATA_EXPORT", "GROWTH_REVIEW_PDF_EXPORT", "IMPORT_EXPORT_TEMPLATE_MANAGEMENT", "ATTACHMENT_SERVICE"})
    void refusesCreationAndExecutionWhenRequiredFeatureIsOff(String feature) {
        doThrow(new FeatureDisabledException(feature)).when(features).requireEnabled(feature, null);
        assertThatThrownBy(() -> access.requireCreate(user(AuthClientType.WEB), STUDENT)).isInstanceOf(FeatureDisabledException.class);
        assertThatThrownBy(() -> access.requireExecution(prepared())).isInstanceOf(FeatureDisabledException.class);
    }

    @Test void historicalReadDoesNotRequireGenerationFeaturesOrCreatePermission() {
        when(permissions.isAllowed(PARENT, PermissionClient.WEB, "EXPORT_JOB_CREATE")).thenReturn(false);
        access.requireHistoricalRead(user(AuthClientType.WEB), prepared());
        verify(features).requireEnabled("ATTACHMENT_SERVICE", null);
        verify(features, never()).requireEnabled("DATA_EXPORT", null);
        verify(features, never()).requireEnabled("GROWTH_REVIEW_PDF_EXPORT", null);
        verify(features, never()).requireEnabled("IMPORT_EXPORT_TEMPLATE_MANAGEMENT", null);
        verify(permissions, never()).isAllowed(PARENT, PermissionClient.WEB, "EXPORT_JOB_CREATE");
    }

    @Test void historicalReadStillRefusesDisabledAttachmentService() {
        doThrow(new FeatureDisabledException("ATTACHMENT_SERVICE")).when(features).requireEnabled("ATTACHMENT_SERVICE", null);
        assertThatThrownBy(() -> access.requireHistoricalRead(user(AuthClientType.WEB), prepared())).isInstanceOf(FeatureDisabledException.class);
    }

    @Test void refusesRevokedRelationshipAtExecutionAndHistoricalRead() {
        when(relations.existsActiveByParentAndStudent(PARENT, STUDENT)).thenReturn(false);
        assertThatThrownBy(() -> access.requireExecution(prepared())).isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> access.requireHistoricalRead(user(AuthClientType.WEB), prepared())).isInstanceOf(SystemOperationAccessDeniedException.class);
    }

    @Test void refusesAuditorEvenWhenAlsoParentAndRejectsDisabledAccount() {
        when(roles.hasRoleCode(PARENT, "SYS_AUDITOR")).thenReturn(true);
        assertThatThrownBy(() -> access.requireCreate(user(AuthClientType.WEB), STUDENT)).isInstanceOf(SystemOperationAccessDeniedException.class);
        when(roles.hasRoleCode(PARENT, "SYS_AUDITOR")).thenReturn(false);
        when(users.findById(PARENT)).thenReturn(null);
        assertThatThrownBy(() -> access.requireExecution(prepared())).isInstanceOf(SystemOperationAccessDeniedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXPORT_JOB_READ", "GROWTH_REVIEW_READ_CHILD"})
    void historicalReadRechecksDynamicPermission(String permission) {
        when(permissions.isAllowed(PARENT, PermissionClient.WEB, permission)).thenReturn(false);
        assertThatThrownBy(() -> access.requireHistoricalRead(user(AuthClientType.WEB), prepared())).isInstanceOf(SystemOperationAccessDeniedException.class);
    }

    @Test void refusesDifferentOwnerMiniappAndMissingScope() {
        assertThatThrownBy(() -> access.requireHistoricalRead(user(AuthClientType.WEB), new Prepared(PARENT + 1, STUDENT, List.of())))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> access.requireCreate(user(AuthClientType.MINIAPP), STUDENT)).isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> access.requireExecution(null)).isInstanceOf(SystemOperationAccessDeniedException.class);
    }

    private Prepared prepared() { return new Prepared(PARENT, STUDENT, List.of()); }
    private AuthenticatedUser user(AuthClientType client) {
        return new AuthenticatedUser(PARENT, PARENT + 3, "parent", "测试家长", client, List.of("PARENT"));
    }
}
