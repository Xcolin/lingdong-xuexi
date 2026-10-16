package com.lingdong.learning.iam.application;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.*;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.user.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainingInstitutionMemberScopeTest {
 @Test void membersMustAlreadyBelongToCurrentInstitutionButApprovedMultiInstitutionStudentCanBeSelected() {
  var organizations=mock(OrganizationMapper.class); var memberships=mock(UserOrganizationMapper.class);
  var scopes=mock(OrganizationDataScopeService.class);
  var service=new MemberScopeService(organizations,memberships,mock(UserRoleMapper.class),scopes);
  var training=Organization.create(10L,null,"ROOT","TRAINING","培训机构","TRAINING_INSTITUTION","/TRAINING/",0,null);
  var school=Organization.create(20L,null,"ROOT","SCHOOL","学校","SCHOOL","/SCHOOL/",0,null);
  when(organizations.findById(10L)).thenReturn(training); when(organizations.findById(20L)).thenReturn(school);
  when(scopes.resolve(1L)).thenReturn(new OrganizationDataScope(false,false,List.of(training.path())));
  when(memberships.findOrganizationIds(2L)).thenReturn(List.of(20L));
  assertThatThrownBy(() -> service.requireUser(1L,2L,training)).isInstanceOf(SystemOperationAccessDeniedException.class);
  when(memberships.findOrganizationIds(2L)).thenReturn(List.of(20L,10L));
  assertThatCode(() -> service.requireUser(1L,2L,training)).doesNotThrowAnyException();
  assertThat(service.school(training)).isEqualTo(training);
 }
}
