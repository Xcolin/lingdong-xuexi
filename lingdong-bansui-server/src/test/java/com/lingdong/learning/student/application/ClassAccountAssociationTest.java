package com.lingdong.learning.student.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import java.util.List;
class ClassAccountAssociationTest {
 final StudentManagementAccessService access=mock(StudentManagementAccessService.class);
 final OrganizationMapper organizations=mock(OrganizationMapper.class);
 final StudentCodeLoginApplicationService proof=mock(StudentCodeLoginApplicationService.class);
 final StudentOrganizationMapper relations=mock(StudentOrganizationMapper.class);
 final StudentOrganizationLifecycleService lifecycle=mock(StudentOrganizationLifecycleService.class);
 final FeatureAccessService features=mock(FeatureAccessService.class);
 final ClassAccountAssociationService service=new ClassAccountAssociationService(access,organizations,proof,relations,lifecycle,features);
 final AuthenticatedUser actor=new AuthenticatedUser(1L,2L,"actor","actor",AuthClientType.MINIAPP,List.of());
 final StudentCodeLoginCommand command=new StudentCodeLoginCommand("child","1234","device","preview",null,null,"127.0.0.1");
 ClassAccountAssociationTest(){var school=Organization.create(2L,null,"ROOT","S","school","SCHOOL","/2/",1,null);var c=Organization.create(3L,2L,"S","C","class","CLASS","/2/3/",1,null);when(organizations.findById(2L)).thenReturn(school);when(organizations.findById(3L)).thenReturn(c);when(access.organizationAllowed(actor,3L)).thenReturn(true);when(proof.verifyForBinding(command)).thenReturn(new VerifiedStudentIdentity(5L,6L,"child"));}
 @Test void permissionsCheckedBeforeAnyAccountProof(){doThrow(new SystemOperationAccessDeniedException("denied")).when(access).require(actor,"STUDENT_CLASS_ASSIGN");assertThatThrownBy(()->service.associate(actor,3L,command)).isInstanceOf(SystemOperationAccessDeniedException.class);verifyNoInteractions(organizations,proof,relations,lifecycle);}
 @Test void outsideClassCannotProbeAccount(){when(access.organizationAllowed(actor,3L)).thenReturn(false);assertThatThrownBy(()->service.associate(actor,3L,command)).isInstanceOf(SystemOperationAccessDeniedException.class);verifyNoInteractions(proof,relations,lifecycle);}
 @Test void failedProofNeverWritesRelationship(){when(proof.verifyForBinding(command)).thenThrow(new StudentAuthenticationFailedException());assertThatThrownBy(()->service.associate(actor,3L,command)).isInstanceOf(StudentAuthenticationFailedException.class);verifyNoInteractions(relations,lifecycle);}
 @Test void validCodeDoesNotBypassCurrentSchoolEnrollment(){when(relations.existsActiveEnrollmentInScope(5L,2L,null)).thenReturn(false);assertThatThrownBy(()->service.associate(actor,3L,command)).isInstanceOf(SystemOperationAccessDeniedException.class);verifyNoInteractions(lifecycle);}
 @Test void proofUsesExistingScopedUnassignedLifecycle(){when(relations.existsActiveEnrollmentInScope(5L,2L,null)).thenReturn(true);service.associate(actor,3L,command);verify(lifecycle).transferClass(eq(actor),eq(5L),argThat(c->c.classOrganizationId().equals(3L)&&c.requireUnassigned()));verify(access).require(actor,"STUDENT_ORGANIZATION_MANAGE");}
 @Test void disabledFeatureFailsBeforeProof(){doThrow(new IllegalStateException("disabled")).when(features).requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);assertThatThrownBy(()->service.associate(actor,3L,command)).isInstanceOf(IllegalStateException.class);verifyNoInteractions(proof,relations,lifecycle);}
 @Test void proofTransactionMustNotKeepPreLockRepeatableReadSnapshot() throws Exception {var tx=ClassAccountAssociationService.class.getMethod("associate",AuthenticatedUser.class,long.class,StudentCodeLoginCommand.class).getAnnotation(org.springframework.transaction.annotation.Transactional.class);assertThat(tx.isolation()).isEqualTo(org.springframework.transaction.annotation.Isolation.READ_COMMITTED);assertThat(tx.noRollbackFor()).contains(StudentAuthenticationFailedException.class,CaptchaRequiredException.class,StudentAccountLockedException.class);}
}
