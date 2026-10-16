package com.lingdong.learning.iam.application;
import com.lingdong.learning.datascope.application.OrganizationDataScope;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class MemberScopeSingleActionTest {
 @Test void allScopeAndOneExplicitAssociationActionCanAttachUnboundWithoutDataScopeManagement(){
  var scopes=mock(OrganizationDataScopeService.class);var decisions=mock(PermissionDecisionService.class);var service=new MemberScopeService(null,null,null,scopes);ReflectionTestUtils.setField(service,"decisions",decisions);
  when(scopes.resolve(1L)).thenReturn(OrganizationDataScope.all(false));when(decisions.isAllowed(1L,PermissionClient.WEB,"ORG_ADMIN_ASSIGN")).thenReturn(true);
  assertThat(service.canAttachUnbound(1L)).isTrue();verify(decisions,never()).isAllowed(1L,PermissionClient.WEB,"IAM_DATA_SCOPE_CONFIGURE");
  when(decisions.isAllowed(1L,PermissionClient.WEB,"ORG_ADMIN_ASSIGN")).thenReturn(false);
  when(decisions.isAllowed(1L,PermissionClient.WEB,"IAM_DATA_SCOPE_CONFIGURE")).thenReturn(true);
  assertThat(service.canAttachUnbound(1L)).isFalse();
 }
}
