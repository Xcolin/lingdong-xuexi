package com.lingdong.learning.studentimport.application;

import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationEffectiveStatus;
import com.lingdong.learning.organization.domain.OrganizationStatus;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.studentimport.domain.StudentImportCredentialStatus;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionRecord;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionStatus;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentImportAccessServiceTest {
    private static final long USER_ID = 1874244142494647001L;
    private static final long SCHOOL_ID = 1874244142494647002L;
    private static final long CLASS_ID = 1874244142494647003L;

    private FeatureAccessService featureAccessService;
    private PermissionDecisionService permissionDecisionService;
    private OrganizationDataScopeService dataScopeService;
    private UserRoleMapper userRoleMapper;
    private UserMapper userMapper;
    private OrganizationMapper organizationMapper;
    private StudentImportAccessService service;

    @BeforeEach
    void setUp() {
        featureAccessService = mock(FeatureAccessService.class);
        permissionDecisionService = mock(PermissionDecisionService.class);
        dataScopeService = mock(OrganizationDataScopeService.class);
        userRoleMapper = mock(UserRoleMapper.class);
        userMapper = mock(UserMapper.class);
        organizationMapper = mock(OrganizationMapper.class);
        service = new StudentImportAccessService(
                featureAccessService, permissionDecisionService, dataScopeService,
                userRoleMapper, userMapper, organizationMapper);
        when(userMapper.findById(USER_ID)).thenReturn(enabledUser());
        when(userRoleMapper.hasRoleCode(USER_ID, "ORG_ADMIN")).thenReturn(true);
        when(dataScopeService.canAccess(USER_ID, SCHOOL_ID)).thenReturn(true);
        when(dataScopeService.canAccess(USER_ID, CLASS_ID)).thenReturn(true);
        when(organizationMapper.findById(SCHOOL_ID)).thenReturn(organization(SCHOOL_ID, "SCHOOL"));
        when(organizationMapper.findById(CLASS_ID)).thenReturn(organization(CLASS_ID, "CLASS"));
    }

    @Test
    void allowsOrganizationAdministratorWithCurrentPermissionAndScope() {
        allow("STUDENT_IMPORT_EXECUTE");
        allow("STUDENT_IMPORT_RESULT_READ");
        allow("STUDENT_IMPORT_CREDENTIAL_DOWNLOAD");

        assertThatCode(() -> service.requireExecute(USER_ID, SCHOOL_ID, CLASS_ID))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.requireOwnerRead(USER_ID, execution(USER_ID)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.requireCredentialDownload(USER_ID, execution(USER_ID)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDisabledUserMissingPermissionOrOrganizationScopeAndAllowsCustomRole() {
        allow("STUDENT_IMPORT_EXECUTE");
        when(userMapper.findById(USER_ID)).thenReturn(new User(
                USER_ID, "disabled", "停用账号", null, null,
                UserType.ORGANIZATION, UserStatus.DISABLED, null, null));
        assertThatThrownBy(() -> service.requireExecute(USER_ID, SCHOOL_ID, null))
                .isInstanceOf(SystemOperationAccessDeniedException.class);

        when(userMapper.findById(USER_ID)).thenReturn(enabledUser());
        when(userRoleMapper.hasRoleCode(USER_ID, "ORG_ADMIN")).thenReturn(false);
        assertThatCode(() -> service.requireExecute(USER_ID, SCHOOL_ID, null))
                .doesNotThrowAnyException();

        when(userRoleMapper.hasRoleCode(USER_ID, "ORG_ADMIN")).thenReturn(true);
        when(permissionDecisionService.isAllowed(
                USER_ID, PermissionClient.WEB, "STUDENT_IMPORT_EXECUTE")).thenReturn(false);
        assertThatThrownBy(() -> service.requireExecute(USER_ID, SCHOOL_ID, null))
                .isInstanceOf(SystemOperationAccessDeniedException.class);

        allow("STUDENT_IMPORT_EXECUTE");
        when(dataScopeService.canAccess(USER_ID, SCHOOL_ID)).thenReturn(false);
        assertThatThrownBy(() -> service.requireExecute(USER_ID, SCHOOL_ID, null))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
    }

    @Test
    void rejectsWrongOrganizationTypesAndDifferentOwner() {
        allow("STUDENT_IMPORT_EXECUTE");
        when(organizationMapper.findById(SCHOOL_ID)).thenReturn(organization(SCHOOL_ID, "REGION"));
        assertThatThrownBy(() -> service.requireExecute(USER_ID, SCHOOL_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("学校");

        when(organizationMapper.findById(SCHOOL_ID)).thenReturn(organization(SCHOOL_ID, "SCHOOL"));
        when(organizationMapper.findById(CLASS_ID)).thenReturn(organization(CLASS_ID, "GRADE"));
        assertThatThrownBy(() -> service.requireExecute(USER_ID, SCHOOL_ID, CLASS_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("班级");

        allow("STUDENT_IMPORT_RESULT_READ");
        assertThatThrownBy(() -> service.requireOwnerRead(USER_ID, execution(USER_ID + 1)))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
    }

    private void allow(String permissionCode) {
        when(permissionDecisionService.isAllowed(USER_ID, PermissionClient.WEB, permissionCode))
                .thenReturn(true);
    }

    private User enabledUser() {
        return new User(USER_ID, "org-admin", "机构管理员", null, null,
                UserType.ORGANIZATION, UserStatus.ENABLED, null, null);
    }

    private Organization organization(long id, String typeCode) {
        String path = "CLASS".equals(typeCode) ? "/school/class/" : "/school/";
        return new Organization(id, null, "ROOT", "ORG-" + id, "测试组织", typeCode,
                path, 1, OrganizationStatus.ENABLED,
                OrganizationEffectiveStatus.ENABLED, 1, null, null, null);
    }

    private StudentImportExecutionRecord execution(long requesterId) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 16, 0);
        return new StudentImportExecutionRecord(
                1874244142494647004L, "SIM-1874244142494647004",
                1874244142494647005L, requesterId, SCHOOL_ID, CLASS_ID,
                StudentImportExecutionStatus.SUCCEEDED, 0L, 1, 1, 1, 0,
                null, null, 1874244142494647006L,
                StudentImportCredentialStatus.AVAILABLE, now.plusHours(24), null,
                now, now, now, now, now);
    }
}
