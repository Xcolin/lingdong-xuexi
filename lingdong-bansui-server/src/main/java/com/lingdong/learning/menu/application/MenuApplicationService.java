package com.lingdong.learning.menu.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.menu.domain.*;
import com.lingdong.learning.iam.audit.application.*;
import com.lingdong.learning.menu.infrastructure.persistence.MenuMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import com.lingdong.learning.user.domain.UserStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.ClassPathResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;

@Service
public class MenuApplicationService {
    private static final Set<String> ROUTES = Set.of("/dashboard","/system-tasks","/feature-management","/attendance-records","/learning-tasks","/exception-reports","/growth-points","/rewards","/growth-reviews","/anonymous-ranks","/rank-preferences","/student-login","/parent-relationships","/teachers","/users","/iam","/dictionaries","/cache-management","/interface-services","/attachment-management","/import-export-templates","/import-jobs","/export-jobs","/organizations","/menu-management");
    private final MenuMapper mapper;
    private final IdGenerator ids;
    private final PermissionDecisionService decisions;
    private final PermissionMapper permissions;
    private final UserMapper users;
    private final UserRoleMapper roles;
    private final IamChangeAuditService audit;
    private final Map<String,String> actionRoutes;
    public MenuApplicationService(MenuMapper mapper,IdGenerator ids,PermissionDecisionService decisions,PermissionMapper permissions,UserMapper users,UserRoleMapper roles,IamChangeAuditService audit,ObjectMapper json) {
        this.mapper=mapper; this.ids=ids; this.decisions=decisions; this.permissions=permissions; this.users=users; this.roles=roles; this.audit=audit;
        this.actionRoutes=loadActionRoutes(json);
    }
    private Map<String,String> loadActionRoutes(ObjectMapper json) {
        try(var input=new ClassPathResource("web-menu-actions.json").getInputStream()) {
            var catalog=json.readTree(input);
            if(!catalog.isArray()) throw new IllegalStateException("管理端动作目录格式无效");
            Map<String,String> routes=new HashMap<>();
            for(var action:catalog) {
                if(!action.path("code").isTextual() || !action.path("route").isTextual()
                        || !ROUTES.contains(action.path("route").asText())
                        || routes.putIfAbsent(action.path("code").asText(),action.path("route").asText())!=null) throw new IllegalStateException("管理端动作目录存在无效绑定");
            }
            return Map.copyOf(routes);
        } catch(IOException e) { throw new IllegalStateException("无法加载管理端动作目录",e); }
    }
    public List<MenuNode> list(AuthenticatedUser user) { requireManager(user,false); return mapper.findAll(); }
    public List<MenuNode> current(AuthenticatedUser user) {
        requireWeb(user);
        return filterVisible(mapper.findAll(),new HashSet<>(decisions.findAllowedCodes(user.userId(),PermissionClient.WEB)));
    }
    @Transactional
    public MenuNode create(AuthenticatedUser user,MenuWriteCommand c) {
        requireManager(user,true);
        List<MenuNode> all=mapper.lockAll();
        MenuNode node=normalize(ids.nextId(),c,0L);
        validate(node,null,all);
        try { mapper.insert(node); } catch(DuplicateKeyException e) { throw new IllegalStateException("菜单编码已存在"); }
        audit.record(IamChangeAuditEventType.MENU_CREATE,user.userId(),IamChangeTargetType.MENU,node.id(),node.parentId(),null,null,node.type()+":"+node.status()+":v0");
        return node;
    }
    @Transactional
    public MenuNode update(AuthenticatedUser user,Long id,MenuWriteCommand c) {
        requireManager(user,true);
        List<MenuNode> all=mapper.lockAll();
        MenuNode before=all.stream().filter(n->n.id().equals(id)).findFirst().orElseThrow(()->new ResourceNotFoundException("菜单不存在"));
        if(c.version()==null || !before.version().equals(c.version())) throw new IllegalStateException("菜单已变更，请刷新");
        MenuNode node=normalize(id,c,c.version());
        validate(node,before,all);
        if(mapper.update(node)!=1) throw new IllegalStateException("菜单已变更，请刷新");
        audit.record(IamChangeAuditEventType.MENU_UPDATE,user.userId(),IamChangeTargetType.MENU,node.id(),node.parentId(),null,"v"+before.version(),"v"+(node.version()+1));
        return new MenuNode(node.id(),node.code(),node.name(),node.type(),node.parentId(),node.route(),node.icon(),node.permissionCode(),node.sortOrder(),node.status(),node.version()+1);
    }
    @Transactional
    public List<MenuNode> order(AuthenticatedUser user,MenuOrderCommand c) {
        requireManager(user,true);
        if(c.ids()==null || c.versions()==null || new HashSet<>(c.ids()).size()!=c.ids().size() || c.ids().stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("排序参数无效");
        List<MenuNode> siblings=mapper.lockAll().stream().filter(n->Objects.equals(c.parentId(),n.parentId())).toList();
        Set<Long> siblingIds=new HashSet<>(siblings.stream().map(MenuNode::id).toList());
        if(!siblingIds.equals(new HashSet<>(c.ids())) || !siblingIds.equals(c.versions().keySet())) throw new IllegalStateException("同级目录已变更，请刷新");
        for(MenuNode n:siblings) if(!n.version().equals(c.versions().get(n.id()))) throw new IllegalStateException("菜单已变更，请刷新");
        for(int i=0;i<c.ids().size();i++) {
            Long id=c.ids().get(i);
            if(mapper.reorder(id,c.versions().get(id),i*10)!=1) throw new IllegalStateException("菜单已变更，请刷新");
            audit.record(IamChangeAuditEventType.MENU_REORDER,user.userId(),IamChangeTargetType.MENU,id,c.parentId(),null,"v"+c.versions().get(id),"order:"+(i*10));
        }
        return mapper.findAll();
    }
    private MenuNode normalize(Long id,MenuWriteCommand c,Long version) {
        String code=text(c.code(),128,true), name=text(c.name(),128,true);
        if(!code.matches("[a-zA-Z][a-zA-Z0-9_.-]{0,127}") || c.type()==null || c.status()==null || c.sortOrder()==null || c.sortOrder()<0 || c.sortOrder()>1000000) throw new IllegalArgumentException("菜单字段无效");
        String icon=text(c.icon(),64,false);
        if(icon!=null && !icon.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("图标编码无效");
        return new MenuNode(id,code,name,c.type(),c.parentId(),text(c.route(),128,false),text(c.icon(),64,false),text(c.permissionCode(),128,false),c.sortOrder(),c.status(),version);
    }
    private void validate(MenuNode n,MenuNode before,List<MenuNode> all) {
        Map<Long,MenuNode> map=new HashMap<>(); all.forEach(x->map.put(x.id(),x));
        if(all.stream().anyMatch(x->x.code().equals(n.code()) && !x.id().equals(n.id()))) throw new IllegalStateException("菜单编码已存在");
        if(before!=null && !before.code().equals(n.code())) throw new IllegalArgumentException("菜单编码不可修改");
        if(n.type()==MenuType.PAGE && !ROUTES.contains(n.route()==null?"":n.route())) throw new IllegalArgumentException("页面路由未注册");
        if(n.type()!=MenuType.PAGE && n.route()!=null) throw new IllegalArgumentException("目录和按钮不允许配置路由");
        if(n.type()==MenuType.PAGE && all.stream().anyMatch(x->x.type()==MenuType.PAGE && !x.id().equals(n.id()) && Objects.equals(x.route(),n.route()))) throw new IllegalStateException("页面路由已配置");
        MenuNode parent=n.parentId()==null?null:map.get(n.parentId());
        if(n.parentId()!=null && parent==null) throw new IllegalArgumentException("父级菜单不存在");
        if(parent!=null && (parent.type()==MenuType.BUTTON || (n.type()!=MenuType.BUTTON && parent.type()!=MenuType.DIRECTORY))) throw new IllegalArgumentException("父级类型无效");
        if(n.type()==MenuType.BUTTON && (parent==null || parent.type()!=MenuType.PAGE)) throw new IllegalArgumentException("按钮必须属于页面");
        if(n.type()==MenuType.BUTTON && !Objects.equals(actionRoutes.get(n.code()),parent.route())) throw new IllegalArgumentException("按钮动作未注册或与父页面不匹配");
        if(n.type()==MenuType.PAGE && all.stream().anyMatch(x->Objects.equals(x.parentId(),n.id()) && x.type()==MenuType.BUTTON && !Objects.equals(actionRoutes.get(x.code()),n.route()))) throw new IllegalArgumentException("页面路由与现有按钮动作不匹配");
        if(all.stream().anyMatch(x->Objects.equals(x.parentId(),n.id()) && (n.type()==MenuType.BUTTON || (n.type()==MenuType.PAGE && x.type()!=MenuType.BUTTON) || (n.type()==MenuType.DIRECTORY && x.type()==MenuType.BUTTON)))) throw new IllegalArgumentException("现有子级与节点类型不兼容");
        Set<Long> visited=new HashSet<>(); visited.add(n.id());
        while(parent!=null) { if(!visited.add(parent.id())) throw new IllegalArgumentException("菜单不可循环引用"); parent=parent.parentId()==null?null:map.get(parent.parentId()); }
        if(n.permissionCode()!=null && permissions.findByCode(n.permissionCode())==null) throw new IllegalArgumentException("绑定权限不存在");
        if(before!=null && Set.of("dashboard","menu-management").contains(before.code()) && (n.status()!=MenuStatus.ENABLED || n.type()!=MenuType.PAGE || !Objects.equals(n.route(),before.route()) || !Objects.equals(n.parentId(),before.parentId()) || !Objects.equals(n.permissionCode(),before.permissionCode()))) throw new IllegalArgumentException("核心入口不能停用或更改绑定");
        MenuNode oldParent=before==null || before.parentId()==null?null:map.get(before.parentId());
        if(before!=null && oldParent!=null && oldParent.code().equals("menu-management") && before.type()==MenuType.BUTTON
                && (n.status()!=MenuStatus.ENABLED || n.type()!=MenuType.BUTTON || !Objects.equals(n.parentId(),before.parentId()) || !Objects.equals(n.permissionCode(),before.permissionCode()))) throw new IllegalArgumentException("菜单配置操作不能停用或更改绑定");
    }
    private String text(String value,int max,boolean required) {
        String s=value==null?null:value.trim(); if(s!=null && s.isEmpty()) s=null;
        if((required && s==null) || (s!=null && s.length()>max)) throw new IllegalArgumentException("字段长度无效"); return s;
    }
    private void requireWeb(AuthenticatedUser user) {
        if(user==null || user.clientType()!=AuthClientType.WEB) throw new SystemOperationAccessDeniedException("仅允许管理端会话");
    }
    private void requireManager(AuthenticatedUser user,boolean write) {
        requireWeb(user);
        var account=users.findById(user.userId());
        if(write && roles.hasRoleCode(user.userId(),"SYS_AUDITOR")) throw new SystemOperationAccessDeniedException("审核员只可读取菜单");
        if(account==null || account.status()!=UserStatus.ENABLED || !(roles.hasRoleCode(user.userId(),"SYS_ADMIN") || (!write && roles.hasRoleCode(user.userId(),"SYS_AUDITOR")))) throw new SystemOperationAccessDeniedException("当前角色不可管理菜单");
        if(!decisions.isAllowed(user.userId(),PermissionClient.WEB,write?"MENU_MANAGE":"MENU_READ")) throw new SystemOperationAccessDeniedException("当前账号无菜单权限");
    }
    public static List<MenuNode> filterVisible(List<MenuNode> all,Set<String> allowed) {
        Map<Long,MenuNode> map=new HashMap<>(); all.forEach(n->map.put(n.id(),n));
        return all.stream().filter(n->{
            Set<Long> seen=new HashSet<>(); MenuNode cursor=n;
            while(cursor!=null) {
                if(!seen.add(cursor.id()) || cursor.status()!=MenuStatus.ENABLED || (cursor.permissionCode()!=null && !allowed.contains(cursor.permissionCode()))) return false;
                if(cursor.parentId()==null) return true;
                cursor=map.get(cursor.parentId()); if(cursor==null) return false;
            }
            return false;
        }).toList();
    }
}
