package com.lingdong.learning.datascope.application;
import com.lingdong.learning.datascope.infrastructure.persistence.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.teacher.infrastructure.persistence.TeacherProfileMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TeacherClassMapper;
import com.lingdong.learning.learningtask.domain.TeacherClassRelation;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class TeacherSchoolIdentityClassScopeTest {
 final RoleAssignmentScopeMapper grants=mock(RoleAssignmentScopeMapper.class);final RoleDataScopeMapper custom=mock(RoleDataScopeMapper.class);final UserOrganizationMapper memberships=mock(UserOrganizationMapper.class);final OrganizationAdminMapper admins=mock(OrganizationAdminMapper.class);final OrganizationMapper orgs=mock(OrganizationMapper.class);final UserMapper users=mock(UserMapper.class);final TeacherProfileMapper profiles=mock(TeacherProfileMapper.class);final TeacherClassMapper classes=mock(TeacherClassMapper.class);
 final OrganizationDataScopeService service=new OrganizationDataScopeService(grants,custom,memberships,admins,orgs,users);
 final Organization school=Organization.create(10L,null,"ROOT","SCHOOL","School","SCHOOL","/SCHOOL/",0,null);
 final Organization own=Organization.create(11L,10L,"PARENT:10","OWN","Own class","CLASS","/SCHOOL/OWN/",0,null);
 final Organization sibling=Organization.create(12L,10L,"PARENT:10","OTHER","Other class","CLASS","/SCHOOL/OTHER/",0,null);
 final Organization foreignSchool=Organization.create(20L,null,"ROOT","FOREIGN","Foreign school","SCHOOL","/FOREIGN/",0,null);
 final Organization foreign=Organization.create(21L,20L,"PARENT:20","FOREIGNCLASS","Foreign class","CLASS","/FOREIGN/FOREIGNCLASS/",0,null);
 @BeforeEach void fixture(){ReflectionTestUtils.setField(service,"teacherProfiles",profiles);ReflectionTestUtils.setField(service,"teacherClasses",classes);when(users.findById(1L)).thenReturn(User.create(1L,"teacher","Teacher",null,UserType.ORGANIZATION));when(grants.findByUserId(1L)).thenReturn(List.of(new RoleAssignmentScope(5L,RoleDataScope.CLASS,10L)));when(profiles.enabled(1L)).thenReturn(true);when(classes.findActiveByTeacher(1L)).thenReturn(List.of(TeacherClassRelation.active(1L,1L,11L)));when(memberships.findOrganizationIds(1L)).thenReturn(List.of(10L));when(orgs.findByIds(List.of(10L))).thenReturn(List.of(school));when(admins.findOrganizationIds(1L)).thenReturn(List.of());when(orgs.findById(10L)).thenReturn(school);when(orgs.findById(11L)).thenReturn(own);when(orgs.findById(12L)).thenReturn(sibling);when(orgs.findById(20L)).thenReturn(foreignSchool);when(orgs.findById(21L)).thenReturn(foreign);}
 @Test void schoolBasedClassIdentityResolvesOnlyActualTeachingClass(){var scope=service.resolve(1L);assertThat(scope.rootPaths()).containsExactly(own.path());assertThat(scope.allOrganizations()).isFalse();assertThat(scope.allows(school)).isFalse();assertThat(scope.allows(sibling)).isFalse();}
 @Test void revokedClassImmediatelyRemovesDerivedScope(){when(classes.findActiveByTeacher(1L)).thenReturn(List.of());assertThat(service.resolve(1L).rootPaths()).isEmpty();}
 @Test void foreignSchoolTeachingRecordDoesNotEnterTheGrantedInstitution(){when(classes.findActiveByTeacher(1L)).thenReturn(List.of(TeacherClassRelation.active(1L,1L,11L),TeacherClassRelation.active(2L,1L,21L)));assertThat(service.resolve(1L).rootPaths()).containsExactly(own.path());}
 @Test void missingTeacherProfileCannotExpandSchoolBasedClassRole(){when(profiles.enabled(1L)).thenReturn(false);assertThat(service.resolve(1L).rootPaths()).isEmpty();verifyNoInteractions(classes);}
 @Test void normalExplicitClassAssignmentStillUsesItsOwnRootWithoutTeacherProfile(){when(grants.findByUserId(1L)).thenReturn(List.of(new RoleAssignmentScope(5L,RoleDataScope.CLASS,11L)));when(profiles.enabled(1L)).thenReturn(false);assertThat(service.resolve(1L).rootPaths()).containsExactly(own.path());verifyNoInteractions(profiles,classes);}
}
