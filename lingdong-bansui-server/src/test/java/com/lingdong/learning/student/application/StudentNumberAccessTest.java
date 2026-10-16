package com.lingdong.learning.student.application;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import com.lingdong.learning.student.infrastructure.persistence.*;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import java.util.List;
class StudentNumberAccessTest {
 @Test void deniedPermissionStopsAllMetadataReadsAndWrites() throws Exception {
  var store=mock(AcademicMetadataStore.class);var access=mock(StudentManagementAccessService.class);var orgs=mock(OrganizationMapper.class);var students=mock(StudentMapper.class);var relations=mock(StudentOrganizationMapper.class);var features=mock(FeatureAccessService.class);
  var actor=new AuthenticatedUser(1L,2L,"actor","actor",AuthClientType.MINIAPP,List.of());doThrow(new SystemOperationAccessDeniedException("denied")).when(access).require(actor,"STUDENT_ORGANIZATION_MANAGE");
  var type=Class.forName("com.lingdong.learning.student.application.StudentNumberService");var service=type.getConstructor(AcademicMetadataStore.class,StudentManagementAccessService.class,OrganizationMapper.class,StudentMapper.class,StudentOrganizationMapper.class,FeatureAccessService.class).newInstance(store,access,orgs,students,relations,features);
  assertThatThrownBy(()->type.getMethod("read",AuthenticatedUser.class,long.class,long.class).invoke(service,actor,10L,2L)).hasCauseInstanceOf(SystemOperationAccessDeniedException.class);
  assertThatThrownBy(()->type.getMethod("update",AuthenticatedUser.class,long.class,long.class,String.class,int.class).invoke(service,actor,10L,2L,"A001",0)).hasCauseInstanceOf(SystemOperationAccessDeniedException.class);
  verifyNoInteractions(store,orgs,students,relations);
 }
}
