package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScope;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentClassQueryServiceTest {
    private final OrganizationMapper organizations = mock(OrganizationMapper.class);
    private final StudentOrganizationMapper students = mock(StudentOrganizationMapper.class);
    private final StudentManagementAccessService access = mock(StudentManagementAccessService.class);
    private final FeatureAccessService features = mock(FeatureAccessService.class);
    private final AuthenticatedUser user = mock(AuthenticatedUser.class);

    @Test void usesAssignmentPermissionAndTargetClassWithoutTaskCreationPermission() {
        when(user.clientType()).thenReturn(AuthClientType.WEB);
        when(organizations.findById(3L)).thenReturn(Organization.create(3L, 2L, "PARENT:2", "CLS_ONE", "一班", "CLASS", "/SCHOOL/GRADE/CLS_ONE/", 1, null));
        when(access.organizationAllowed(user, 3L)).thenReturn(true);
        var scope = OrganizationDataScope.all(false);
        when(access.scope(user)).thenReturn(scope);
        when(students.findClassStudentOptions(3L, scope, false)).thenReturn(List.of());
        new StudentClassQueryService(organizations, students, access, features).options(user, 3L, false);
        verify(access).require(user, "STUDENT_CLASS_ASSIGN");
        verify(students).findClassStudentOptions(3L, scope, false);
    }

    @Test void refusesOutsideClassBeforeReturningCandidates() {
        when(user.clientType()).thenReturn(AuthClientType.WEB);
        when(organizations.findById(3L)).thenReturn(Organization.create(3L, 2L, "PARENT:2", "CLS_ONE", "一班", "CLASS", "/SCHOOL/GRADE/CLS_ONE/", 1, null));
        assertThatThrownBy(() -> new StudentClassQueryService(organizations, students, access, features).options(user, 3L, true))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(access).require(user, "STUDENT_READ");
        verifyNoInteractions(students);
    }
}
