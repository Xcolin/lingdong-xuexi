package com.lingdong.learning.exportjob.application;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.OrganizationDataScope;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.exportjob.infrastructure.config.ExportJobProperties;
import com.lingdong.learning.exportjob.infrastructure.persistence.StudentTaskExportMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CustomExportScopeTest {
 private final UserMapper users=mock(UserMapper.class);
 private final UserRoleMapper roles=mock(UserRoleMapper.class);
 private final PermissionDecisionService permissions=mock(PermissionDecisionService.class);
 private final OrganizationDataScopeService scopes=mock(OrganizationDataScopeService.class);
 private final StudentTaskExportMapper mapper=mock(StudentTaskExportMapper.class);
 private final StudentTaskExportAccessService service=new StudentTaskExportAccessService(users,roles,permissions,
  mock(FeatureAccessService.class),scopes,mapper,new ExportJobProperties());
 @BeforeEach void setup(){
  when(users.findById(11L)).thenReturn(User.create(11L,"custom","custom",null,UserType.ORGANIZATION));
  when(roles.findEnabledRoleCodesByUserId(11L)).thenReturn(List.of("ALL_ROLE_TEST"));
  when(permissions.isAllowed(eq(11L),eq(PermissionClient.WEB),anyString())).thenReturn(true);
  when(scopes.resolve(11L)).thenReturn(OrganizationDataScope.all(false));
 }
 @Test void customRoleUsesActualAllOrRestrictedOrganizationScope(){
  assertThat(service.require(11L).allOrganizations()).isTrue();
  when(scopes.resolve(11L)).thenReturn(new OrganizationDataScope(false,false,List.of("/region/school/")));
  var restricted=service.require(11L);
  assertThat(restricted.allOrganizations()).isFalse();
  assertThat(restricted.rootPaths()).containsExactly("/region/school/");
 }
 @Test void missingPermissionAndEmptyScopeFailClosed(){
  when(permissions.isAllowed(11L,PermissionClient.WEB,"STUDENT_TASK_REPORT_EXPORT")).thenReturn(false);
  assertThatThrownBy(()->service.require(11L)).isInstanceOf(SystemOperationAccessDeniedException.class);
  when(permissions.isAllowed(11L,PermissionClient.WEB,"STUDENT_TASK_REPORT_EXPORT")).thenReturn(true);
  when(scopes.resolve(11L)).thenReturn(OrganizationDataScope.empty());
  assertThatThrownBy(()->service.require(11L)).isInstanceOf(SystemOperationAccessDeniedException.class);
 }
 @Test void currentObjectScopeIsRecheckedBeforeFrozenExportDelivery(){
  var frozen=new ExportScopeSnapshot(null,99L,null,null,null,null,"ORG_ADMIN",List.of(42L),null,null);
  when(mapper.countVisibleIds(any(StudentTaskVisibility.class),eq(List.of(42L)))).thenReturn(1L);
  assertThatCode(()->service.requireFrozen(11L,frozen)).doesNotThrowAnyException();
  when(mapper.countVisibleIds(any(StudentTaskVisibility.class),eq(List.of(42L)))).thenReturn(0L);
  assertThatThrownBy(()->service.requireFrozen(11L,frozen)).isInstanceOf(SystemOperationAccessDeniedException.class);
 }
 @Test void parentExportRemainsBoundToParentStudentRelationships(){
  when(roles.findEnabledRoleCodesByUserId(11L)).thenReturn(List.of("PARENT"));
  var visibility=service.require(11L);
  assertThat(visibility.role()).isEqualTo("PARENT");
  assertThat(visibility.allOrganizations()).isFalse();
  assertThat(visibility.rootPaths()).isEmpty();
 }
}
