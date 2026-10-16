package com.lingdong.learning.user.application;
import com.lingdong.learning.permission.application.PermissionAvailabilityService;
import com.lingdong.learning.permission.domain.PermissionEffect;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.permission.infrastructure.persistence.RolePermissionMapper;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** Identity compatibility is independent of temporary menu/feature/permission enablement. */
@Service
public class RoleIdentityCompatibilityService {
 private final RoleMapper roles;private final RolePermissionMapper grants;private final PermissionMapper permissions;private final PermissionAvailabilityService availability;
 public RoleIdentityCompatibilityService(RoleMapper roles,RolePermissionMapper grants,PermissionMapper permissions,PermissionAvailabilityService availability){this.roles=roles;this.grants=grants;this.permissions=permissions;this.availability=availability;}
 @Transactional
 public void requireCompatible(Long userId,Long roleId){
  if(grants.lockRole(roleId)==null)throw new ResourceNotFoundException("角色不存在");
  var role=roles.findById(roleId);if(role==null)throw new ResourceNotFoundException("角色不存在");
  for(var grant:grants.lockByRoleId(roleId)){
   if(grant.effect()!=PermissionEffect.ALLOW)continue;
   var permission=permissions.findById(grant.permissionId());
   if(permission==null)throw new IllegalStateException("角色动作目录不完整");
   String reason=availability.identityReason(permission,userId,role.code());
   if(reason!=null)throw new SystemOperationAccessDeniedException("角色动作不适用于目标账号："+permission.code()+"（"+reason+"）");
  }
 }
}
