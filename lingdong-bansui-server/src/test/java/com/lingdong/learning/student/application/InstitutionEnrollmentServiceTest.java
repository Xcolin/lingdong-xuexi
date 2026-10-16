package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.infrastructure.persistence.*;
import com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InstitutionEnrollmentServiceTest {
    final InstitutionEnrollmentMapper requests=mock(InstitutionEnrollmentMapper.class);
    final StudentMapper students=mock(StudentMapper.class);
    final StudentOrganizationMapper relations=mock(StudentOrganizationMapper.class);
    final StudentOrganizationChangeMapper changes=mock(StudentOrganizationChangeMapper.class);
    final ParentStudentMapper parents=mock(ParentStudentMapper.class);
    final OrganizationMapper organizations=mock(OrganizationMapper.class);
    final OrganizationAdminMapper admins=mock(OrganizationAdminMapper.class);
    final UserOrganizationMapper members=mock(UserOrganizationMapper.class);
    final PermissionDecisionService permissions=mock(PermissionDecisionService.class);
    final OrganizationDataScopeService scopes=mock(OrganizationDataScopeService.class);
    final IdGenerator ids=mock(IdGenerator.class);
    final Clock clock=Clock.fixed(Instant.parse("2026-10-09T00:00:00Z"),ZoneOffset.UTC);
    final InstitutionEnrollmentService service=new InstitutionEnrollmentService(requests,students,relations,changes,parents,organizations,admins,members,permissions,scopes,ids,clock);
    final AuthenticatedUser actor=new AuthenticatedUser(1L,null,"parent","家长",AuthClientType.WEB,List.of("PARENT"));
    final Student student=Student.create(20L,"学生",null,21L);
    @BeforeEach void setup(){
        when(organizations.findById(10L)).thenReturn(org(10L,null,"TRAINING_INSTITUTION","/10/"));
        when(organizations.findById(11L)).thenReturn(org(11L,10L,"CLASS","/10/11/"));
        when(organizations.findById(30L)).thenReturn(org(30L,null,"FAMILY","/30/"));
        when(students.findByStudentUserId(21L)).thenReturn(student);
        when(students.findByIdForUpdate(20L)).thenReturn(student);
        when(requests.familyMember(21L,30L)).thenReturn(true);
        when(ids.nextId()).thenReturn(100L,101L,102L,103L);
    }
    Organization org(Long id,Long parent,String type,String path){return Organization.create(id,parent,"PARENT","ORG"+id,type,type,path,0,null);}
    InstitutionEnrollmentMapper.Request request(){return new InstitutionEnrollmentMapper.Request(80L,20L,21L,"学生",10L,"培训",11L,"课程",30L,1L,"PENDING",LocalDateTime.now(clock),null,null);}
    @Test void sharedFamilyMembershipDoesNotCreateParentAuthority(){
        when(permissions.isAllowed(1L,PermissionClient.WEB,"STUDENT_ENROLLMENT_REQUEST_CREATE")).thenReturn(true);
        when(requests.familyMember(1L,30L)).thenReturn(true);
        assertThatThrownBy(()->service.request(actor,21L,"A".repeat(43),30L)).isInstanceOf(SystemOperationAccessDeniedException.class);
        verify(requests,never()).lockInvitation(anyString());
    }
    @Test void actualParentCanSubmitWithoutGlobalStudentCreatePermission(){
        when(permissions.isAllowed(1L,PermissionClient.WEB,"STUDENT_ENROLLMENT_REQUEST_CREATE")).thenReturn(true);
        when(parents.existsActiveByParentAndStudent(1L,20L)).thenReturn(true);
        when(requests.lockInvitation(anyString())).thenReturn(new InstitutionEnrollmentMapper.Invitation(50L,"hash",10L,11L,2L,LocalDateTime.now(clock).plusDays(1),"ACTIVE",100,2));
        when(requests.consume(50L)).thenReturn(1);
        when(requests.lockRequest(100L)).thenReturn(request());
        assertThat(service.request(actor,21L,"A".repeat(43),30L)).isNotNull();
        verify(requests).consume(50L);
        verifyNoInteractions(members);
    }
    @Test void actualFamilyAdminCanManageStudentWithoutInferringKinship(){
        when(admins.exists(1L,30L)).thenReturn(true);
        assertThat(service.canFamilyStudent(actor,student,30L,"STUDENT_ENROLLMENT_REQUEST_CREATE")).isTrue();
        verify(parents).existsActiveByParentAndStudent(1L,20L);
        verify(parents,never()).insertPrimary(anyLong(),anyLong(),anyLong());
    }
    @Test void approvalAddsTrainingCourseAndPreservesOtherInstitutionClasses(){
        when(permissions.isAllowed(1L,PermissionClient.WEB,"STUDENT_ENROLLMENT_REVIEW")).thenReturn(true);
        when(scopes.canAccess(1L,11L)).thenReturn(true);
        when(requests.lockRequest(80L)).thenReturn(request());
        when(relations.findActiveEnrollmentOrganizationIds(20L)).thenReturn(List.of(10L,99L));
        when(requests.review(eq(80L),eq("APPROVED"),eq(1L),any(),isNull())).thenReturn(1);
        service.review(actor,80L,true,null);
        verify(relations).insertClass(anyLong(),eq(20L),eq(11L));
        verify(relations,never()).deactivateActiveClasses(anyLong());
        verify(relations,never()).deactivateActiveClassesInEnrollment(anyLong(),anyLong());
        verify(members).insert(anyLong(),eq(21L),eq(10L));
        verify(members).insert(anyLong(),eq(21L),eq(11L));
    }
    @Test void schoolExistingAdministrativeClassRequiresTransfer(){
        when(organizations.findById(10L)).thenReturn(org(10L,null,"SCHOOL","/10/"));
        when(permissions.isAllowed(1L,PermissionClient.WEB,"STUDENT_ENROLLMENT_REVIEW")).thenReturn(true);
        when(scopes.canAccess(1L,11L)).thenReturn(true);
        when(requests.lockRequest(80L)).thenReturn(request());
        when(relations.findActiveClassOrganizationIdsInEnrollment(20L,10L)).thenReturn(List.of(12L));
        assertThatThrownBy(()->service.review(actor,80L,true,null)).hasMessageContaining("校内转班");
        verify(relations,never()).insertClass(anyLong(),anyLong(),anyLong());
    }
    @Test void resolveOnlyReturnsFiniteNamesAndRejectsExpiredCode(){
        when(requests.lockInvitation(anyString())).thenReturn(new InstitutionEnrollmentMapper.Invitation(50L,"hash",10L,11L,2L,LocalDateTime.now(clock).minusSeconds(1),"ACTIVE",100,0));
        assertThatThrownBy(()->service.resolve(actor,"A".repeat(43))).hasMessageContaining("失效");
    }
    @Test void invitationsStoreDigestAndReturnCodeOnlyAtCreation(){
        when(permissions.isAllowed(1L,PermissionClient.WEB,"STUDENT_ENROLLMENT_INVITE_CREATE")).thenReturn(true);
        when(scopes.canAccess(1L,11L)).thenReturn(true);
        var invitation=service.createInvitation(actor,11L,7);
        assertThat(invitation.inviteCode()).hasSize(43);
        assertThat(invitation.toString()).doesNotContain(invitation.inviteCode());
        verify(requests).insertInvitation(argThat(i->i.codeHash().length()==64&&!i.codeHash().equals(invitation.inviteCode())&&i.maxUses()==100));
    }
}


