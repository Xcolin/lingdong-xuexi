package com.lingdong.learning.student.application;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.domain.*;
import com.lingdong.learning.student.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParentStudentOptionServiceTest {
    final ParentActorAccessService actors=mock(ParentActorAccessService.class);
    final PermissionDecisionService permissions=mock(PermissionDecisionService.class);
    final FeatureAccessService features=mock(FeatureAccessService.class);
    final ParentStudentMapper relations=mock(ParentStudentMapper.class);
    final StudentMapper students=mock(StudentMapper.class);
    final StudentOrganizationMapper enrollments=mock(StudentOrganizationMapper.class);
    final ParentStudentOptionService service=new ParentStudentOptionService(actors,permissions,features,relations,students,enrollments);
    final AuthenticatedUser parent=new AuthenticatedUser(1L,2L,"parent","parent",AuthClientType.WEB,List.of("CUSTOM_FAMILY"));
    void grant() {
        when(permissions.isAllowed(1L,PermissionClient.WEB,"REWARD_CREATE")).thenReturn(true);
        when(features.isEnabled("REWARD_EXCHANGE",null)).thenReturn(true);
    }
    @Test void onlyCreateGrantProvidesOnlyOwnRelatedEnabledChildrenWithoutStudentReadOrRelationshipFeature() {
        grant();
        when(relations.findActiveStudentsByParent(1L)).thenReturn(List.of(
                new ParentRelationshipStudentView(3L,"own child",ParentRelationshipRole.SECONDARY_GUARDIAN)));
        when(students.findById(3L)).thenReturn(Student.create(3L,"own child","G3",4L));
        when(enrollments.findActiveEnrollmentOrganizationIds(3L)).thenReturn(List.of(5L));
        assertThat(service.find(parent)).containsExactly(new ParentStudentOptionService.Option(
                "3","own child","G3","5",StudentStatus.ENABLED,ParentRelationshipRole.SECONDARY_GUARDIAN));
        verify(permissions,never()).isAllowed(1L,PermissionClient.WEB,"STUDENT_READ");
        verify(features,never()).isEnabled("PARENT_RELATIONSHIP_MANAGEMENT",null);
        verify(students,never()).findById(99L);
    }
    @Test void noRelationshipReturnsNoChildren() {
        grant();when(relations.findActiveStudentsByParent(1L)).thenReturn(List.of());
        assertThat(service.find(parent)).isEmpty();verifyNoInteractions(students,enrollments);
    }
    @Test void disabledFeatureOrMissingActionBlocksBeforeRelations() {
        assertThatThrownBy(() -> service.find(parent)).isInstanceOf(SystemOperationAccessDeniedException.class);
        when(permissions.isAllowed(1L,PermissionClient.WEB,"REWARD_CREATE")).thenReturn(true);
        assertThatThrownBy(() -> service.find(parent)).isInstanceOf(SystemOperationAccessDeniedException.class);
        verifyNoInteractions(relations,students,enrollments);
    }
}
