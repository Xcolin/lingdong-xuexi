package com.lingdong.learning.student.web;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.student.infrastructure.persistence.AcademicMetadataStore;
class AcademicClassMetadataControllerTest {
 @Test void controllerUsesExistingPermissionsAndStringIds() throws Exception {
  var type=Class.forName("com.lingdong.learning.student.web.AcademicClassMetadataController");var update=Class.forName(type.getName()+"$Update");
  assertThat(type.getMethod("read",AuthenticatedUser.class,long.class).getAnnotation(RequirePermission.class).value()).isEqualTo("CLASS_READ");
  assertThat(type.getMethod("update",AuthenticatedUser.class,long.class,update).getAnnotation(RequirePermission.class).value()).isEqualTo("CLASS_UPDATE");
  assertThat(type.getMethod("headTeacherOptions",AuthenticatedUser.class,long.class).getAnnotation(RequirePermission.class).value()).isEqualTo("CLASS_UPDATE");
  var response=Class.forName(type.getName()+"$Response");var value=response.getMethod("from",AcademicMetadataStore.ClassMetadata.class).invoke(null,new AcademicMetadataStore.ClassMetadata(9007199254740993L,9007199254740995L,2));
  assertThat(response.getMethod("classId").invoke(value)).isEqualTo("9007199254740993");assertThat(response.getMethod("headTeacherUserId").invoke(value)).isEqualTo("9007199254740995");
 }
 @Test void requestRequiresVersionAndRejectsInvalidTeacher() throws Exception {
  var type=Class.forName("com.lingdong.learning.student.web.AcademicClassMetadataController$Update");var constructor=type.getConstructor(Long.class,Integer.class);
  try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()){var validator=factory.getValidator();assertThat(validator.validate(constructor.newInstance(null,0))).isEmpty();assertThat(validator.validate(constructor.newInstance(0L,1))).isNotEmpty();assertThat(validator.validate(constructor.newInstance(1L,-1))).isNotEmpty();assertThat(validator.validate(constructor.newInstance(1L,null))).isNotEmpty();}
 }
 @Test void assignedTeacherNameIsExplicitAndLegacyResponseStaysEmpty() throws Exception {
  var viewType=Class.forName("com.lingdong.learning.student.application.AcademicMetadataService$ClassView");var metadata=new AcademicMetadataStore.ClassMetadata(3L,4L,2);
  var view=viewType.getConstructor(AcademicMetadataStore.ClassMetadata.class,String.class).newInstance(metadata,"李老师");
  var responseType=Class.forName("com.lingdong.learning.student.web.AcademicClassMetadataController$Response");var response=responseType.getMethod("from",viewType).invoke(null,view);
  assertThat(responseType.getMethod("headTeacherName").invoke(response)).isEqualTo("李老师");
  var legacy=responseType.getMethod("from",AcademicMetadataStore.ClassMetadata.class).invoke(null,metadata);assertThat(responseType.getMethod("headTeacherName").invoke(legacy)).isNull();
 }
}
