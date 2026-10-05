package com.lingdong.learning.menu.application;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.iam.audit.application.IamChangeAuditService;
import com.lingdong.learning.menu.domain.*;
import com.lingdong.learning.menu.infrastructure.persistence.MenuMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.*;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.user.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class MenuWriteContractTest {
 private final MenuMapper menus=mock(MenuMapper.class);
 private final PermissionMapper permissions=mock(PermissionMapper.class);
 private final MenuApplicationService service=new MenuApplicationService(menus,mock(IdGenerator.class),mock(PermissionDecisionService.class),permissions,mock(UserMapper.class),mock(UserRoleMapper.class),mock(IamChangeAuditService.class));
 private MenuNode node(long id,String code,MenuType type,Long parent,String route) { return new MenuNode(id,code,code,type,parent,route,null,type==MenuType.DIRECTORY?null:code,type!=MenuType.DIRECTORY,0,MenuStatus.ENABLED,0L); }
 private void validate(MenuNode candidate,MenuNode before,List<MenuNode> all) throws Exception {
  var method=MenuApplicationService.class.getDeclaredMethod("validate",MenuNode.class,MenuNode.class,List.class); method.setAccessible(true);
  try { method.invoke(service,candidate,before,all); } catch(java.lang.reflect.InvocationTargetException e) { throw (Exception)e.getCause(); }
 }
 @Test void rejectsCodeMutationAndUnregisteredOrExternalPageUrl() throws Exception {
  var before=node(1,"PAGE_READ",MenuType.PAGE,null,"/users");
  assertThatThrownBy(()->validate(node(1,"NEW_READ",MenuType.PAGE,null,"/users"),before,List.of(before))).isInstanceOf(IllegalArgumentException.class);
  for(String route:List.of("/missing","https://example.com","/users?admin=true")) assertThatThrownBy(()->validate(node(2,"PAGE_READ",MenuType.PAGE,null,route),null,List.of())).isInstanceOf(IllegalArgumentException.class);
  validate(before,null,List.of());
 }
 @Test void syncsButtonResourceTypeAndPermissionParent() throws Exception {
  var page=node(1,"PAGE_READ",MenuType.PAGE,null,"/users"); var button=node(2,"ACTION",MenuType.BUTTON,1L,null);
  when(menus.findAll()).thenReturn(List.of(page,button));
  when(permissions.findByCode("ACTION")).thenReturn(new Permission(20L,"ACTION","old",PermissionResourceType.PAGE,PermissionClient.BOTH,null,PermissionStatus.ENABLED,null));
  when(permissions.findByCode("PAGE_READ")).thenReturn(new Permission(10L,"PAGE_READ","page",PermissionResourceType.PAGE,PermissionClient.WEB,null,PermissionStatus.ENABLED,null));
  var method=MenuApplicationService.class.getDeclaredMethod("syncPermission",MenuNode.class,MenuNode.class); method.setAccessible(true); method.invoke(service,button,button);
  verify(permissions).updateMenuMetadata(20L,"ACTION",PermissionStatus.ENABLED,PermissionResourceType.BUTTON,10L);
  verify(permissions,never()).updateCode(anyLong(),anyString());
 }
 @Test void refusesMiniappPermissionCollisionWithoutChangingPermission() throws Exception {
  var button=node(2,"MINIAPP_ACTION",MenuType.BUTTON,1L,null);
  when(permissions.findByCode(button.code())).thenReturn(new Permission(20L,button.code(),"miniapp",PermissionResourceType.BUTTON,PermissionClient.MINIAPP,null,PermissionStatus.ENABLED,null));
  var method=MenuApplicationService.class.getDeclaredMethod("syncPermission",MenuNode.class,MenuNode.class); method.setAccessible(true);
  assertThatThrownBy(()->method.invoke(service,button,null)).isInstanceOf(java.lang.reflect.InvocationTargetException.class).hasCauseInstanceOf(IllegalArgumentException.class);
  verify(permissions,never()).updateMenuMetadata(anyLong(),anyString(),any(),any(),any());
 }
 @Test void publicPermissionControllerRejectsStandaloneCreation() {
  var controller=new com.lingdong.learning.iam.web.PermissionManagementController(null,null);
  assertThatThrownBy(controller::rejectStandalonePermissionCreation).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
 }
 @Test void protectedPageCannotBeHiddenByDisablingItsAncestor() {
  var directory=node(1,"core-directory",MenuType.DIRECTORY,null,null);
  var core=node(2,"MENU_READ",MenuType.PAGE,1L,"/menu-management");
  var disabled=new MenuNode(directory.id(),directory.code(),directory.name(),directory.type(),null,null,null,null,false,0,MenuStatus.DISABLED,0L);
  assertThatThrownBy(()->validate(disabled,directory,List.of(directory,core))).isInstanceOf(IllegalArgumentException.class);
 }
}
