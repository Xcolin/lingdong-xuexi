package com.lingdong.learning.feature.application;
import com.lingdong.learning.feature.infrastructure.persistence.*;
import com.lingdong.learning.feature.domain.*;
import com.lingdong.learning.audit.application.*;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class FeatureManagementSingleActionTest {
 final FeatureManagementMapper mapper=mock(FeatureManagementMapper.class);
 final FeatureToggleMapper toggles=mock(FeatureToggleMapper.class);
 final FeatureToggleChangeService changes=mock(FeatureToggleChangeService.class);
 final SystemTaskApplicationService tasks=mock(SystemTaskApplicationService.class);
 final PermissionDecisionService permissions=mock(PermissionDecisionService.class);
 final FeatureManagementService service=new FeatureManagementService(mapper,toggles,changes,tasks,permissions,mock(UserRoleMapper.class));
 final AuthenticatedUser user=new AuthenticatedUser(1L,null,"operator","Operator",AuthClientType.WEB,List.of());
 @Test void managementAloneCanSubmitWithoutReadPermission(){
  when(permissions.isAllowed(1L,PermissionClient.WEB,"FEATURE_TOGGLE_MANAGE")).thenReturn(true);
  when(toggles.findGlobalForUpdate("FEATURE")).thenReturn(new FeatureToggle(1L,"FEATURE","Feature",FeatureScope.GLOBAL,null,"GLOBAL",FeatureStatus.ENABLED,true,null,0L));
  when(changes.createDraft(any())).thenReturn(new FeatureToggleChange(1L,2L,"FEATURE",FeatureStatus.DISABLED));
  when(mapper.findByTaskId(2L)).thenReturn(mock(FeatureManagementService.ChangeView.class));
  service.submit(user,"FEATURE",FeatureStatus.DISABLED,0L,"Title","Description",true);
  verify(changes).submit(2L,1L);verify(permissions,never()).isAllowed(1L,PermissionClient.WEB,"FEATURE_TOGGLE_READ");
 }
 @Test void reviewAloneCanLoadQueueAndRejectWithinFeatureDomain(){
  when(permissions.isAllowed(1L,PermissionClient.WEB,"FEATURE_TOGGLE_REVIEW")).thenReturn(true);
  when(mapper.findByTaskId(2L)).thenReturn(mock(FeatureManagementService.ChangeView.class));
  service.listChanges(user,null,1,20,true);service.review(user,2L,"Reason",false);
  verify(mapper).findPage(1L,true,SystemTaskStatus.PENDING_REVIEW,20,0);verify(tasks).reject(2L,1L,"Reason");
  verify(permissions,never()).isAllowed(1L,PermissionClient.WEB,"FEATURE_TOGGLE_READ");
 }
 @Test void anotherGrantedActionCannotBypassDeniedReview(){
  when(permissions.isAllowed(1L,PermissionClient.WEB,"FEATURE_TOGGLE_MANAGE")).thenReturn(true);
  assertThatThrownBy(()->service.review(user,2L,"Reason",false)).isInstanceOf(SystemOperationAccessDeniedException.class);
  verifyNoInteractions(tasks,changes,mapper);
 }
 @Test void lookupUsesExactActionEvenIfReadIsGranted(){
  when(permissions.isAllowed(1L,PermissionClient.WEB,"FEATURE_TOGGLE_READ")).thenReturn(true);
  assertThatThrownBy(()->service.toggleOptions(user,"FEATURE_TOGGLE_MANAGE")).isInstanceOf(SystemOperationAccessDeniedException.class);
  when(permissions.isAllowed(1L,PermissionClient.WEB,"FEATURE_TOGGLE_MANAGE")).thenReturn(true);
  when(mapper.findToggles()).thenReturn(List.of());assertThat(service.toggleOptions(user,"FEATURE_TOGGLE_MANAGE")).isEmpty();
 }
}
