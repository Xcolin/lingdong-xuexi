package com.lingdong.learning.permission.application;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.menu.infrastructure.persistence.MenuMapper;
import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.permission.domain.*;
import com.lingdong.learning.user.domain.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
class PermissionAvailabilityServiceTest {
 private final FeatureAccessService features=mock(FeatureAccessService.class);
 private final MenuMapper menus=mock(MenuMapper.class);
 private final UserMapper users=mock(UserMapper.class);
 private final ParentAuthenticationMapper parents=mock(ParentAuthenticationMapper.class);
 private final UserRoleMapper roles=mock(UserRoleMapper.class);
 private final RoleMapper roleCatalog=mock(RoleMapper.class);
 private PermissionAvailabilityService service() throws Exception {
  when(menus.findAll()).thenReturn(List.of(new com.lingdong.learning.menu.domain.MenuNode(1L,"REWARD_CREATE","创建奖励",com.lingdong.learning.menu.domain.MenuType.BUTTON,null,null,null,"REWARD_CREATE",true,0,com.lingdong.learning.menu.domain.MenuStatus.ENABLED,0L)));
  return new PermissionAvailabilityService(new ObjectMapper(),features,menus,users,parents,roles,roleCatalog);
 }
 private Permission reward(){return new Permission(1L,"REWARD_CREATE","创建奖励",PermissionResourceType.BUTTON,PermissionClient.WEB,null,PermissionStatus.ENABLED,null);}
 @Test void closedFeatureIsUnavailableEvenWhenGranted() throws Exception {
  assertThat(service().generalReason(reward())).contains("REWARD_EXCHANGE");
 }
 @Test void personalActionsRejectOtherAccountIdentities() throws Exception {
  when(features.isEnabled("REWARD_EXCHANGE",null)).thenReturn(true);
  when(users.findById(8L)).thenReturn(User.create(8L,"teacher","teacher",null,UserType.ORGANIZATION));
  assertThat(service().userReason(reward(),8L)).contains("账号身份不适用");
 }
 @Test void familyRoleNameDoesNotSubstituteForActualParentProfile() throws Exception {
  when(features.isEnabled("REWARD_EXCHANGE",null)).thenReturn(true);
  when(users.findById(8L)).thenReturn(User.create(8L,"parent","parent",null,UserType.FAMILY));
  when(roles.hasRoleCode(8L,"PARENT")).thenReturn(true);
  assertThat(service().userReason(reward(),8L)).contains("缺少家长档案");
 }
 @Test void unknownWebCodeCannotBeGranted() throws Exception {
  Permission custom=new Permission(7L,"UNIMPLEMENTED_FEATURE","未实现",PermissionResourceType.BUTTON,PermissionClient.WEB,null,PermissionStatus.ENABLED,null);
  assertThat(service().generalReason(custom)).contains("尚未接入");
 }
 @Test void necessaryPageFeatureMustBeAvailableBeforeGrantingClassWrite() throws Exception {
  var decisions=service();var permissionCatalog=mock(com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper.class);
  org.springframework.test.util.ReflectionTestUtils.setField(decisions,"permissions",permissionCatalog);
  var page=new Permission(10L,"ORG_NODE_READ","组织架构",PermissionResourceType.PAGE,PermissionClient.WEB,null,PermissionStatus.ENABLED,null);
  var create=new Permission(11L,"CLASS_CREATE","创建班级",PermissionResourceType.BUTTON,PermissionClient.WEB,10L,PermissionStatus.ENABLED,null);
  when(permissionCatalog.findByCode("ORG_NODE_READ")).thenReturn(page);
  when(permissionCatalog.findByCode("CLASS_READ")).thenReturn(new Permission(12L,"CLASS_READ","读取班级",PermissionResourceType.BUTTON,PermissionClient.WEB,10L,PermissionStatus.ENABLED,null));
  when(features.isEnabled("CLASS_MANAGEMENT",null)).thenReturn(true);
  when(menus.findAll()).thenReturn(List.of(
   new com.lingdong.learning.menu.domain.MenuNode(10L,"ORG_NODE_READ","组织架构",com.lingdong.learning.menu.domain.MenuType.PAGE,null,"/organizations",null,"ORG_NODE_READ",true,0,com.lingdong.learning.menu.domain.MenuStatus.ENABLED,0L),
   new com.lingdong.learning.menu.domain.MenuNode(11L,"CLASS_CREATE","创建班级",com.lingdong.learning.menu.domain.MenuType.BUTTON,10L,null,null,"CLASS_CREATE",true,0,com.lingdong.learning.menu.domain.MenuStatus.ENABLED,0L),
   new com.lingdong.learning.menu.domain.MenuNode(12L,"CLASS_READ","读取班级",com.lingdong.learning.menu.domain.MenuType.BUTTON,10L,null,null,"CLASS_READ",true,0,com.lingdong.learning.menu.domain.MenuStatus.ENABLED,0L)));
  assertThat(decisions.generalReason(create)).contains("必要读取").contains("ORG_NODE_READ");
 }
 @Test void currentSystemRoleIsCheckedWithoutNullCandidateFailure() throws Exception {
  when(users.findById(8L)).thenReturn(User.create(8L,"admin","admin",null,UserType.PLATFORM));
  when(roles.hasRoleCode(8L,"SYS_ADMIN")).thenReturn(true);
  var permission=new Permission(9L,"ORG_TYPE_READ","组织类型",PermissionResourceType.BUTTON,PermissionClient.WEB,null,PermissionStatus.ENABLED,null);
  assertThat(service().identityReason(permission,8L)).isNull();
  when(roles.hasRoleCode(8L,"SYS_ADMIN")).thenReturn(false);
  assertThat(service().identityReason(permission,8L,"SYS_ADMIN")).isNull();
 }
 @Test void webOnlyCatalogMarksStudentAccountAsInappOnly() throws Exception {
  when(users.findById(8L)).thenReturn(User.create(8L,"student","student",null,UserType.STUDENT));
  var dashboard=new Permission(9L,"DASHBOARD_READ","工作台",PermissionResourceType.PAGE,PermissionClient.WEB,null,PermissionStatus.ENABLED,null);
  assertThat(service().userReason(dashboard,8L)).contains("小程序");
 }
}
