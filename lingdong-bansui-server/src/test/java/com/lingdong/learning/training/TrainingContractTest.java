package com.lingdong.learning.training;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TrainingContractTest {
 @Test void managementCandidateEndpointUsesTrainingManagePermission() throws Exception {
  var method=TrainingController.class.getMethod("programCandidates",com.lingdong.learning.auth.application.AuthenticatedUser.class,Long.class,int.class,int.class);
  assertArrayEquals(new String[]{"/training-program-candidates"},method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class).value());
  assertEquals(TrainingService.MANAGE,method.getAnnotation(com.lingdong.learning.common.security.RequirePermission.class).value());
 }
 @Test void pageRejectsUnboundedOrNegativePaging() throws Exception {
  Class<?> type=Class.forName("com.lingdong.learning.training.TrainingService");
  var method=type.getDeclaredMethod("validatePage",int.class,int.class);
  method.setAccessible(true);
  assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(null,0,20));
  assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(null,1,101));
  assertDoesNotThrow(()->method.invoke(null,1,20));
 }
}
