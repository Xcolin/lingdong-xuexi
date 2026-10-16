package com.lingdong.learning.student.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.infrastructure.persistence.AcademicMetadataStore;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TeacherClassMapper;
import java.util.List;
class AcademicMetadataAccessTest {
 @Test void permissionDenialPrecedesResourceQueriesAndWrites() throws Exception {
  var access=mock(StudentManagementAccessService.class);var store=mock(AcademicMetadataStore.class);var orgs=mock(OrganizationMapper.class);var users=mock(UserMapper.class);var teachers=mock(TeacherClassMapper.class);
  var actor=new AuthenticatedUser(1L,2L,"actor","actor",AuthClientType.MINIAPP,List.of());
  doThrow(new SystemOperationAccessDeniedException("denied")).when(access).require(actor,"CLASS_UPDATE");
  var type=Class.forName("com.lingdong.learning.student.application.AcademicMetadataService");
  var service=type.getConstructor(AcademicMetadataStore.class,StudentManagementAccessService.class,OrganizationMapper.class,UserMapper.class,TeacherClassMapper.class,com.lingdong.learning.feature.application.FeatureAccessService.class).newInstance(store,access,orgs,users,teachers,mock(com.lingdong.learning.feature.application.FeatureAccessService.class));
  assertThatThrownBy(()->type.getMethod("setHeadTeacher",AuthenticatedUser.class,long.class,Long.class,int.class).invoke(service,actor,3L,4L,0)).hasCauseInstanceOf(SystemOperationAccessDeniedException.class);
  assertThatThrownBy(()->type.getMethod("headTeacherOptions",AuthenticatedUser.class,long.class).invoke(service,actor,3L)).hasCauseInstanceOf(SystemOperationAccessDeniedException.class);
  verifyNoInteractions(store,orgs,users,teachers);
 }
}
