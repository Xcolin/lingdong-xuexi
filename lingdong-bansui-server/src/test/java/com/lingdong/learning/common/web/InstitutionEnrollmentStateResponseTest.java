package com.lingdong.learning.common.web;
import com.lingdong.learning.common.alert.ErrorAlertService;
import com.lingdong.learning.common.security.SecurityErrorResponseFactory;
import com.lingdong.learning.student.application.InstitutionEnrollmentStateException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
class InstitutionEnrollmentStateResponseTest {
 @Test void enrollmentConflictExplainsTransferWhileUnknownConflictsStayGeneric() {
  var handler=new ApiExceptionHandler(new SecurityErrorResponseFactory(),mock(ErrorAlertService.class));
  var request=new MockHttpServletRequest("POST","/api/v1/institution-enrollment/requests");
  var response=handler.handleInstitutionEnrollmentState(new InstitutionEnrollmentStateException("学生在该学校已有行政班级，请使用校内转班"),request);
  assertThat(response.getStatusCode().value()).isEqualTo(409);
  assertThat(response.getBody().message()).isEqualTo("学生在该学校已有行政班级，请使用校内转班");
  assertThat(handler.handleStateConflict(new IllegalStateException("internal detail"),request).getBody().message()).isEqualTo("当前状态不允许执行此操作");
 }
}
