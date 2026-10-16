package com.lingdong.learning.student.web;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
class ClassAccountAssociationControllerTest {
 @Test void endpointUsesExistingAssignmentPermissionAndRedactsProof() throws Exception {
  var controller=Class.forName("com.lingdong.learning.student.web.ClassAccountAssociationController");var request=Class.forName(controller.getName()+"$Request");
  assertThat(controller.getMethod("associate",AuthenticatedUser.class,long.class,request,jakarta.servlet.http.HttpServletRequest.class).getAnnotation(RequirePermission.class).value()).isEqualTo("STUDENT_CLASS_ASSIGN");
  var value=request.getConstructors()[0].newInstance("child","1234","device","preview",null,null,true);
  assertThat(value.toString()).doesNotContain("1234","child");
  try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()){var validator=factory.getValidator();assertThat(validator.validate(value)).isEmpty();assertThat(validator.validate(request.getConstructors()[0].newInstance("child","1234","device","preview",null,null,false))).isNotEmpty();assertThat(validator.validate(request.getConstructors()[0].newInstance("child","12345","device","preview",null,null,true))).isNotEmpty();}
 }
}
