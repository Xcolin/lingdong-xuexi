package com.lingdong.learning.menu.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;
import com.lingdong.learning.iam.audit.application.IamChangeAuditService;
import com.lingdong.learning.iam.audit.application.IamChangeTargetType;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.menu.domain.MenuNode;
import com.lingdong.learning.menu.domain.MenuType;
import com.lingdong.learning.menu.infrastructure.persistence.MenuMapper;
import com.lingdong.learning.permission.application.PermissionAssignment;
import com.lingdong.learning.permission.domain.Permission;
import com.lingdong.learning.permission.domain.PermissionEffect;
import com.lingdong.learning.permission.domain.PermissionStatus;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.permission.infrastructure.persistence.RolePermissionMapper;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** 菜单树勾选式授权：视图仅含页面与权限按钮，保存按差量增删 sys_role_permission（只动菜单来源授权）。 */
@Service
public class MenuGrantApplicationService {
    private final com.lingdong.learning.permission.application.PermissionDecisionService decisions;
    private final MenuMapper menus;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissions;
    private final RolePermissionMapper rolePermissions;
    private final UserMapper users;
    private final UserRoleMapper userRoles;
    private final IdGenerator ids;
    private final IamChangeAuditService audit;
    public MenuGrantApplicationService(MenuMapper menus,RoleMapper roleMapper,PermissionMapper permissions,RolePermissionMapper rolePermissions,
                                       UserMapper users,UserRoleMapper userRoles,IdGenerator ids,IamChangeAuditService audit, com.lingdong.learning.permission.application.PermissionDecisionService decisions) {
        this.decisions = decisions;
        this.menus=menus; this.roleMapper=roleMapper; this.permissions=permissions; this.rolePermissions=rolePermissions;
        this.users=users; this.userRoles=userRoles; this.ids=ids; this.audit=audit;
    }

    /** 授权视图：页面与权限按钮（纯 UI 按钮与目录不出现），granted=该角色已有 ALLOW 授权。 */
    public List<MenuGrantViewNode> grantView(AuthenticatedUser user,Long roleId) {
        requireOperator(user);
        Map<Long,PermissionEffect> current=currentEffects(roleId);
        return grantableNodes().stream()
                .map(n->{
                    Permission p=permissions.findByCode(n.permissionCode());
                    boolean granted=p!=null && current.get(p.id())==PermissionEffect.ALLOW;
                    return new MenuGrantViewNode(n.id(),n.code(),n.name(),n.type(),n.parentId(),granted);
                }).toList();
    }

    /** 差量保存：仅对菜单来源（页面与权限按钮派生）的权限做增删，其余授权不受影响；汇总一条审计。 */
    @Transactional
    public List<MenuGrantViewNode> saveGrants(AuthenticatedUser user,Long roleId,List<String> codes) {
        requireOperator(user);
        if(rolePermissions.lockRole(roleId)==null) throw new ResourceNotFoundException("角色不存在："+roleId);
        Map<String,MenuNode> grantable=new LinkedHashMap<>();
        grantableNodes().forEach(n->grantable.put(n.code(),n));
        Set<String> submitted=new LinkedHashSet<>(codes==null?List.of():codes);
        for(String code:submitted) if(!grantable.containsKey(code)) throw new IllegalArgumentException("编码不参与菜单树授权："+code);

        Map<Long,PermissionEffect> current=new HashMap<>();
        rolePermissions.lockByRoleId(roleId).forEach(a->current.put(a.permissionId(),a.effect()));
        Map<Long,String> permissionCodeByPid=new HashMap<>();
        grantable.values().forEach(n->{
            Permission p=permissions.findByCode(n.permissionCode());
            if(p!=null) permissionCodeByPid.put(p.id(),n.code());
        });
        List<String> added=new ArrayList<>(),removed=new ArrayList<>();
        for(String code:submitted) {
            Permission p=permissions.findByCode(code);
            if(p==null) throw new IllegalStateException("权限不存在："+code);
            if(p.status()!=PermissionStatus.ENABLED) throw new IllegalStateException("权限已停用，不能配置授权效果："+code);
            if(current.get(p.id())!=PermissionEffect.ALLOW) {
                rolePermissions.insert(ids.nextId(),roleId,p.id(),PermissionEffect.ALLOW);
                added.add(code);
            }
        }
        for(Map.Entry<Long,PermissionEffect> entry:current.entrySet()) {
            if(entry.getValue()!=PermissionEffect.ALLOW) continue;
            String code=permissionCodeByPid.get(entry.getKey());
            if(code==null||submitted.contains(code)) continue;
            rolePermissions.delete(roleId,entry.getKey());
            removed.add(code);
        }
        if(!added.isEmpty()||!removed.isEmpty()) {
            // 审计列上限 128 字符：明细放得下则记编码明细，否则退化为数量汇总，仍保持汇总单条审计
            String detail="add:"+String.join(",",added)+";remove:"+String.join(",",removed);
            if(detail.length()>128) detail="add:"+added.size()+";remove:"+removed.size();
            audit.record(IamChangeAuditEventType.ROLE_PERMISSION_CONFIGURE,user.userId(),
                    IamChangeTargetType.ROLE_PERMISSION,roleId,null,null,null,detail);
        }
        return grantView(user,roleId);
    }

    private List<MenuNode> grantableNodes() {
        return menus.findAll().stream()
                .filter(n->n.type()==MenuType.PAGE||(n.type()==MenuType.BUTTON&&n.grantable()))
                .filter(n->n.permissionCode()!=null)
                .toList();
    }

    private Map<Long,PermissionEffect> currentEffects(Long roleId) {
        Map<Long,PermissionEffect> effects=new HashMap<>();
        for(PermissionAssignment assignment:rolePermissions.findByRoleId(roleId)) effects.put(assignment.permissionId(),assignment.effect());
        return effects;
    }

    private void requireOperator(AuthenticatedUser user) {
        var operator=user==null?null:users.findById(user.userId());
        if(operator==null||operator.status()!=UserStatus.ENABLED
                ||user.clientType()!=com.lingdong.learning.auth.domain.AuthClientType.WEB
                ||!decisions.isAllowed(user.userId(), com.lingdong.learning.permission.domain.PermissionClient.WEB, "IAM_ROLE_PERMISSION_GRANT")) {
            throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("当前账号无权限授权操作资格");
        }
    }
}
