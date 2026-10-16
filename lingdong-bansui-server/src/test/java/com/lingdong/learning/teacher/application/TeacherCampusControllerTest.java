package com.lingdong.learning.teacher.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lingdong.learning.teacher.web.TeacherManagementController;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.user.domain.UserStatus;
class TeacherCampusControllerTest {
 @Test void campusReachesQueryAndOldJavaSignatureRemains() throws Exception {
  var service=mock(TeacherManagementApplicationService.class);var controller=new TeacherManagementController(service,mock(TeacherBatchApplicationService.class));var actor=mock(AuthenticatedUser.class);when(service.list(eq(actor),any())).thenReturn(new TeacherPage(java.util.List.of(),1,20,0));
  var method=TeacherManagementController.class.getMethod("list",AuthenticatedUser.class,String.class,Long.class,Long.class,UserStatus.class,int.class,int.class,Long.class);
  assertThat(method.getAnnotation(com.lingdong.learning.common.security.RequirePermission.class).value()).isEqualTo("TEACHER_READ");method.invoke(controller,actor,null,2L,null,null,1,20,3L);verify(service).list(eq(actor),argThat(q->Long.valueOf(3).equals(q.campusOrganizationId())));
  controller.list(actor,null,2L,null,null,1,20);verify(service).list(eq(actor),argThat(q->q.campusOrganizationId()==null));
  controller.list(actor,null,2L,null,null,1,20,3L,"MATH");verify(service).list(eq(actor),argThat(q->"MATH".equals(q.subjectCode())&&Long.valueOf(3).equals(q.campusOrganizationId())));
 }
}
