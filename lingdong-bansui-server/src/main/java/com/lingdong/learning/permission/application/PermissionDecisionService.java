package com.lingdong.learning.permission.application;

import com.lingdong.learning.permission.domain.Permission;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.permission.domain.PermissionEffect;
import com.lingdong.learning.permission.domain.PermissionStatus;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.permission.infrastructure.persistence.RolePermissionMapper;
import com.lingdong.learning.permission.infrastructure.persistence.UserPermissionMapper;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 按账号、客户端和权限目录执行显式拒绝优先的动态权限决策。 */
@Service
public class PermissionDecisionService {
    @org.springframework.beans.factory.annotation.Autowired private PermissionAvailabilityService availability;
    @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.menu.infrastructure.persistence.MenuMapper menus;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserPermissionMapper userPermissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserMapper userMapper;

    public PermissionDecisionService(
            PermissionMapper permissionMapper,
            RolePermissionMapper rolePermissionMapper,
            UserPermissionMapper userPermissionMapper,
            UserRoleMapper userRoleMapper,
            UserMapper userMapper
    ) {
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userPermissionMapper = userPermissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.userMapper = userMapper;
    }

    public boolean isAllowed(Long userId, PermissionClient requestClient, String permissionCode) {
        return permissionCode != null && AuthorizationRequestCache.get("effective:"+userId+":"+requestClient,()->findAllowedCodes(userId,requestClient)).contains(permissionCode);
    }

    /** Both API authorization and login/menu output consume this exact effective set. */
    public List<String> findAllowedCodes(Long userId, PermissionClient requestClient) {
        if(userId==null || requestClient==null) return List.of();
        User user=userMapper.findById(userId);
        if(user==null || user.status()!=UserStatus.ENABLED) return List.of();
        List<com.lingdong.learning.menu.domain.MenuNode> nodes=menus==null?List.of():menus.findAll();
        java.util.Map<String,Permission> catalog=new java.util.HashMap<>();
        permissionMapper.findAll().forEach(p->catalog.put(p.code(),p));
        java.util.Set<Long> denied=new java.util.HashSet<>();
        rolePermissionMapper.findInheritedByUserId(userId).stream().filter(a->a.effect()==PermissionEffect.DENY).forEach(a->denied.add(a.permissionId()));
        userPermissionMapper.findByUserId(userId).stream().filter(a->a.effect()==PermissionEffect.DENY).forEach(a->denied.add(a.permissionId()));
        java.util.Set<String> effective=new java.util.TreeSet<>();
        for(String code:userRoleMapper.hasAnyEnabledRole(userId)?permissionMapper.findAllowedCodes(userId,requestClient):List.<String>of()) {
            if(usable(userId,requestClient,code,nodes,catalog,denied)) effective.add(code);
        }
        // Personal onboarding is an account capability. Catalog, client, menu and explicit DENY still apply.
        if(user.type()!=com.lingdong.learning.user.domain.UserType.STUDENT)
            for(String code:List.of("ORGANIZATION_ONBOARDING_READ","ORGANIZATION_ONBOARDING_SUBMIT"))
                if(usable(userId,requestClient,code,nodes,catalog,denied))effective.add(code);
        if(requestClient==PermissionClient.WEB) {
            java.util.Set<String> reads=PermissionMenuPolicy.necessaryPages(effective,nodes);
            if(availability!=null) for(String action:effective) {reads.addAll(availability.impliedReads(action,userId));reads.addAll(availability.optionalReads(action));}
            for(String page:reads) {
                if(usable(userId,requestClient,page,nodes,catalog,denied)) effective.add(page);
            }
        }
        if(requestClient==PermissionClient.MINIAPP && availability!=null) {
            java.util.Set<String> reads=new java.util.HashSet<>();
            for(String action:effective) reads.addAll(availability.miniappImpliedReads(action));
            for(String read:reads) if(usable(userId,requestClient,read,nodes,catalog,denied))effective.add(read);
        }
        return List.copyOf(effective);
    }

    private boolean usable(Long userId,PermissionClient client,String code,List<com.lingdong.learning.menu.domain.MenuNode> nodes,java.util.Map<String,Permission> catalog,java.util.Set<Long> denied) {
        Permission p=catalog.get(code);
        if(p==null || p.status()!=PermissionStatus.ENABLED || !supportsClient(p.client(),client)) return false;
        if(denied.contains(p.id())) return false;
        if(availability!=null) {
            if(client==PermissionClient.MINIAPP)for(String required:availability.miniappImpliedReads(code)) {
                Permission read=catalog.get(required);
                if(read==null || read.status()!=PermissionStatus.ENABLED || !supportsClient(read.client(),client) || denied.contains(read.id()) || availability.userReason(read,userId,client)!=null)return false;
            }
            if(availability.userReason(p,userId,client)!=null) return false;
            for(String required:client==PermissionClient.WEB?availability.impliedReads(code,userId):java.util.Set.<String>of()) {
                Permission read=catalog.get(required);
                if(read==null || denied.contains(read.id()) || availability.userReason(read,userId,client)!=null) return false;
            }
        }
        if(client==PermissionClient.WEB) {
            for(String page:PermissionMenuPolicy.necessaryPages(java.util.Set.of(code),nodes)) {
                Permission parent=catalog.get(page);
                if(parent!=null && denied.contains(parent.id())) return false;
            }
        }
        return client!=PermissionClient.WEB || PermissionMenuPolicy.enabled(code,nodes);
    }

    private boolean supportsClient(PermissionClient permissionClient, PermissionClient requestClient) {
        return permissionClient == PermissionClient.BOTH || permissionClient == requestClient;
    }
}
