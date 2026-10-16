package com.lingdong.learning.student.application;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import com.lingdong.learning.feature.application.*;
import com.lingdong.learning.student.infrastructure.persistence.AcademicMetadataStore;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TeacherClassMapper;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import java.util.List;
class AcademicMetadataFeatureGuardTest {
 @Test void disabledClassManagementStopsReadAndWriteBeforeResourceAccess() throws Exception {
  var store=mock(AcademicMetadataStore.class);var access=mock(StudentManagementAccessService.class);var orgs=mock(OrganizationMapper.class);var users=mock(UserMapper.class);var teachers=mock(TeacherClassMapper.class);var features=mock(FeatureAccessService.class);
  doThrow(new FeatureDisabledException("CLASS_MANAGEMENT")).when(features).requireEnabled("CLASS_MANAGEMENT",null);
  var actor=new AuthenticatedUser(1L,2L,"actor","actor",AuthClientType.MINIAPP,List.of());
  var type=AcademicMetadataService.class;
  var service=type.getConstructor(AcademicMetadataStore.class,StudentManagementAccessService.class,OrganizationMapper.class,UserMapper.class,TeacherClassMapper.class,FeatureAccessService.class).newInstance(store,access,orgs,users,teachers,features);
  assertThatThrownBy(()->service.classMetadata(actor,3L)).isInstanceOf(FeatureDisabledException.class);
  assertThatThrownBy(()->service.setHeadTeacher(actor,3L,4L,0)).isInstanceOf(FeatureDisabledException.class);
  verifyNoInteractions(store,orgs,users,teachers);
 }
 @Test void localClassSwitchBlocksMetadataEvenWhenGlobalSwitchAllows() {
  var store=mock(AcademicMetadataStore.class);var access=mock(StudentManagementAccessService.class);var orgs=mock(OrganizationMapper.class);var users=mock(UserMapper.class);var teachers=mock(TeacherClassMapper.class);var features=mock(FeatureAccessService.class);
  var actor=new AuthenticatedUser(1L,2L,"actor","actor",AuthClientType.MINIAPP,List.of());
  var node=com.lingdong.learning.organization.domain.Organization.create(3L,2L,"S","C","class","CLASS","/2/3/",1,null);
  when(orgs.findById(3L)).thenReturn(node);when(orgs.findByIdForUpdate(3L)).thenReturn(node);when(orgs.findById(2L)).thenReturn(com.lingdong.learning.organization.domain.Organization.create(2L,null,"ROOT","S","school","SCHOOL","/2/",1,null));when(access.organizationAllowed(actor,3L)).thenReturn(true);
  doThrow(new FeatureDisabledException("CLASS_MANAGEMENT")).when(features).requireEnabled("CLASS_MANAGEMENT",3L);
  var service=new AcademicMetadataService(store,access,orgs,users,teachers,features);
  assertThatThrownBy(()->service.classMetadata(actor,3L)).isInstanceOf(FeatureDisabledException.class);
  assertThatThrownBy(()->service.setHeadTeacher(actor,3L,4L,0)).isInstanceOf(FeatureDisabledException.class);
  verify(features,times(2)).requireEnabled("CLASS_MANAGEMENT",null);verifyNoInteractions(store,users,teachers);
 }
}
