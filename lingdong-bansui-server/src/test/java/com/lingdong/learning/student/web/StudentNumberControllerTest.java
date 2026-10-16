package com.lingdong.learning.student.web;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;import com.lingdong.learning.common.security.RequirePermission;import com.lingdong.learning.auth.application.AuthenticatedUser;import com.lingdong.learning.student.infrastructure.persistence.AcademicMetadataStore;
class StudentNumberControllerTest {
 @Test void endpointsRequireExistingPermissionAndKeepIdsAsStrings() throws Exception {
  var type=Class.forName("com.lingdong.learning.student.web.StudentNumberController");var update=Class.forName(type.getName()+"$Update");
  assertThat(type.getMethod("read",AuthenticatedUser.class,long.class,long.class).getAnnotation(RequirePermission.class).value()).isEqualTo("STUDENT_ORGANIZATION_MANAGE");assertThat(type.getMethod("update",AuthenticatedUser.class,long.class,long.class,update).getAnnotation(RequirePermission.class).value()).isEqualTo("STUDENT_ORGANIZATION_MANAGE");
  var response=Class.forName(type.getName()+"$Response");var value=response.getMethod("from",AcademicMetadataStore.EnrollmentMetadata.class).invoke(null,new AcademicMetadataStore.EnrollmentMetadata(9007199254740993L,9007199254740995L,"A001",2));assertThat(response.getMethod("studentId").invoke(value)).isEqualTo("9007199254740993");assertThat(response.getMethod("institutionId").invoke(value)).isEqualTo("9007199254740995");
 }
 @Test void versionIsRequiredAndNumberLengthIsBounded() throws Exception {
  var type=Class.forName("com.lingdong.learning.student.web.StudentNumberController$Update");var constructor=type.getConstructor(String.class,Integer.class);try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()){var validator=factory.getValidator();assertThat(validator.validate(constructor.newInstance(null,0))).isEmpty();assertThat(validator.validate(constructor.newInstance("A001",null))).isNotEmpty();assertThat(validator.validate(constructor.newInstance("A001",-1))).isNotEmpty();assertThat(validator.validate(constructor.newInstance("x".repeat(65),1))).isNotEmpty();}
 }
}
