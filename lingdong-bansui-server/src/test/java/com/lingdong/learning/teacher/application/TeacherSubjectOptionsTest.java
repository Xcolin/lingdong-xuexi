package com.lingdong.learning.teacher.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class TeacherSubjectOptionsTest {
 @Test void optionsAreRealAndPermissionIsCheckedBeforeDictionaryAccess() throws Exception {
  var fixture=new TeacherSubjectRulesTest();
  var method=TeacherSubjectService.class.getMethod("options",com.lingdong.learning.auth.application.AuthenticatedUser.class);
  var options=java.util.List.of(new TeacherSubjectDictionary.Option("MATH","数学"));when(fixture.dictionary.options()).thenReturn(options);
  assertThat(method.invoke(fixture.service,fixture.actor)).isEqualTo(options);
  reset(fixture.dictionary);when(fixture.permissions.isAllowed(1L,com.lingdong.learning.permission.domain.PermissionClient.MINIAPP,"TEACHER_READ")).thenReturn(false);
  assertThatThrownBy(()->method.invoke(fixture.service,fixture.actor)).hasCauseInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
  verifyNoInteractions(fixture.dictionary);
 }
}
