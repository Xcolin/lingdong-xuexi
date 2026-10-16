package com.lingdong.learning.datascope.application;

import com.lingdong.learning.datascope.infrastructure.persistence.*;
import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainingInstitutionDataScopeTest {
 @Test void schoolScopeIncludesTrainingRootButDoesNotExpandBeyondMembershipOrAdministratorBoundary() {
  var roles=mock(RoleAssignmentScopeMapper.class); var custom=mock(RoleDataScopeMapper.class);
  var memberships=mock(UserOrganizationMapper.class); var admins=mock(OrganizationAdminMapper.class);
  var organizations=mock(OrganizationMapper.class); var users=mock(UserMapper.class);
  var service=new OrganizationDataScopeService(roles,custom,memberships,admins,organizations,users);
  var institution=Organization.create(10L,null,"ROOT","TRAINING","培训机构","TRAINING_INSTITUTION","/TRAINING/",0,null);
  var grade=Organization.create(11L,10L,"PARENT:10","COURSE","课程分类","GRADE","/TRAINING/COURSE/",0,null);
  var cls=Organization.create(12L,11L,"PARENT:11","CLASS","课程班","CLASS","/TRAINING/COURSE/CLASS/",0,null);
  when(users.findById(1L)).thenReturn(User.create(1L,"admin","管理员",null,UserType.ORGANIZATION));
  when(roles.findByUserId(1L)).thenReturn(List.of(new RoleAssignmentScope(2L,RoleDataScope.SCHOOL,10L)));
  when(organizations.findById(10L)).thenReturn(institution);
  when(memberships.findOrganizationIds(1L)).thenReturn(List.of(10L));
  when(organizations.findByIds(List.of(10L))).thenReturn(List.of(institution));
  assertThat(service.resolve(1L).rootPaths()).containsExactly(institution.path());
  when(admins.findOrganizationIds(1L)).thenReturn(List.of(11L));
  when(organizations.findByIds(List.of(11L))).thenReturn(List.of(grade));
  assertThat(service.resolve(1L).rootPaths()).containsExactly(grade.path());
  assertThat(service.resolve(1L).allows(cls)).isTrue();
  assertThat(service.resolve(1L).allows(institution)).isFalse();
  when(memberships.findOrganizationIds(1L)).thenReturn(List.of());
  assertThat(service.resolve(1L).rootPaths()).isEmpty();
 }
}
