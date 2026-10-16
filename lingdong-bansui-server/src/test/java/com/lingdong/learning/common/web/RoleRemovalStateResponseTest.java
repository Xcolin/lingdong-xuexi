package com.lingdong.learning.common.web;
import com.lingdong.learning.common.alert.ErrorAlertService;
import com.lingdong.learning.common.security.SecurityErrorResponseFactory;
import com.lingdong.learning.user.application.RoleRemovalStateException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
class RoleRemovalStateResponseTest {
 @Test void administratorIdentityConflictExplainsHowToRemoveRole() {
  var handler=new ApiExceptionHandler(new SecurityErrorResponseFactory(),mock(ErrorAlertService.class));
  var request=new MockHttpServletRequest("POST","/api/v1/roles/1/users:revoke");
  var response=handler.handleRoleRemovalState(new RoleRemovalStateException("请先取消对应组织的管理员身份，再移除该角色"),request);
  assertThat(response.getStatusCode().value()).isEqualTo(409);
  assertThat(response.getBody().message()).isEqualTo("请先取消对应组织的管理员身份，再移除该角色");
  assertThat(handler.handleStateConflict(new IllegalStateException("internal"),request).getBody().message()).isEqualTo("当前状态不允许执行此操作");
 }
}
