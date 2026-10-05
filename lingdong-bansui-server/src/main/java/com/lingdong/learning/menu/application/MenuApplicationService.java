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
import com.lingdong.learning.permission.domain.Permission;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.permission.domain.PermissionResourceType;
import com.lingdong.learning.permission.domain.PermissionStatus;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import com.lingdong.learning.user.domain.UserStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 菜单树管理：编码即权限编码（自动同步权限目录）、按钮批量维护与拖拽层级调整。 */
@Service
public class MenuApplicationService {
    /** 页面使用业务权限编码；全部按钮保留 actionKey 并以编码作为权限编码。 */
    private static final String PERMISSION_CODE_PATTERN="[A-Z][A-Z0-9_]{2,127}";
    private static final String SLUG_CODE_PATTERN="[a-zA-Z][a-zA-Z0-9_.-]{0,127}";
    private static final String CORE_PAGE_ROUTE="/dashboard";
    private static final String MENU_MANAGE_ROUTE="/menu-management";
    private static final int BATCH_LIMIT=100;
    private static final Set<String> PAGE_ROUTES=Set.of("/dashboard","/system-tasks","/feature-management","/attendance-records","/learning-tasks","/exception-reports","/growth-points","/rewards","/growth-reviews","/anonymous-ranks","/rank-preferences","/student-login","/parent-relationships","/teachers","/users","/iam","/dictionaries","/cache-management","/interface-services","/attachment-management","/import-export-templates","/import-jobs","/export-jobs","/organizations","/menu-management");
    private final MenuMapper mapper;
    private final IdGenerator ids;
    private final PermissionDecisionService decisions;
    private final PermissionMapper permissions;
    private final UserMapper users;
    private final UserRoleMapper roles;
    private final IamChangeAuditService audit;
    public MenuApplicationService(MenuMapper mapper,IdGenerator ids,PermissionDecisionService decisions,PermissionMapper permissions,UserMapper users,UserRoleMapper roles,IamChangeAuditService audit) {
        this.mapper=mapper; this.ids=ids; this.decisions=decisions; this.permissions=permissions; this.users=users; this.roles=roles; this.audit=audit;
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
        syncPermission(node,null);
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
        syncPermission(node,before);
        audit.record(IamChangeAuditEventType.MENU_UPDATE,user.userId(),IamChangeTargetType.MENU,node.id(),node.parentId(),null,"v"+before.version(),"v"+(node.version()+1));
        return new MenuNode(node.id(),node.code(),node.name(),node.type(),node.parentId(),node.route(),node.icon(),node.permissionCode(),node.grantable(),node.sortOrder(),node.status(),node.version()+1);
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
    /** 批量新增按钮：整批原子，冲突条目整批拒绝并报告明细。 */
    @Transactional
    public List<MenuNode> createButtons(AuthenticatedUser user,Long pageId,List<MenuButtonBatchCommand> commands) {
        requireManager(user,true);
        if(commands==null || commands.isEmpty() || commands.size()>BATCH_LIMIT) throw new IllegalArgumentException("批量按钮参数无效");
        List<MenuNode> all=mapper.lockAll();
        MenuNode parent=all.stream().filter(n->n.id().equals(pageId)).findFirst().orElseThrow(()->new ResourceNotFoundException("菜单不存在"));
        if(parent.type()!=MenuType.PAGE) throw new IllegalArgumentException("按钮必须属于页面");
        List<String> conflicts=new ArrayList<>();
        Set<String> batchCodes=new HashSet<>();
        List<MenuNode> nodes=new ArrayList<>();
        for(MenuButtonBatchCommand c:commands) {
            try {
                MenuNode node=normalize(ids.nextId(),new MenuWriteCommand(c.code(),c.name(),MenuType.BUTTON,pageId,null,c.icon(),c.grantable(),c.sortOrder(),c.status()==null?MenuStatus.ENABLED:c.status(),0L),0L);
                if(!batchCodes.add(node.code())) { conflicts.add(node.code()+"（批内重复）"); continue; }
                if(all.stream().anyMatch(x->x.code().equals(node.code()))) { conflicts.add(node.code()+"（编码已存在）"); continue; }
                nodes.add(node);
            } catch(IllegalArgumentException e) { conflicts.add(c.code()+"（"+e.getMessage()+"）"); }
        }
        if(!conflicts.isEmpty()) throw new MenuBatchConflictException(conflicts);
        List<MenuNode> created=new ArrayList<>();
        for(MenuNode node:nodes) {
            mapper.insert(node);
            syncPermission(node,null);
            audit.record(IamChangeAuditEventType.MENU_CREATE,user.userId(),IamChangeTargetType.MENU,node.id(),node.parentId(),null,null,node.type()+":"+node.status()+":v0");
            created.add(node);
        }
        return created;
    }
    /** 批量修改按钮：整批原子，编码不可修改并联动权限同步，冲突条目整批拒绝并报告明细。 */
    @Transactional
    public List<MenuNode> updateButtons(AuthenticatedUser user,List<MenuButtonBatchUpdateCommand> commands) {
        requireManager(user,true);
        if(commands==null || commands.isEmpty() || commands.size()>BATCH_LIMIT) throw new IllegalArgumentException("批量按钮参数无效");
        List<MenuNode> all=mapper.lockAll();
        Map<Long,MenuNode> map=new HashMap<>(); all.forEach(n->map.put(n.id(),n));
        List<String> conflicts=new ArrayList<>();
        Set<String> batchCodes=new HashSet<>();
        List<MenuNode[]> pairs=new ArrayList<>();
        for(MenuButtonBatchUpdateCommand c:commands) {
            MenuNode before=map.get(c.id());
            if(before==null) { conflicts.add(String.valueOf(c.id())+"（菜单不存在）"); continue; }
            if(before.type()!=MenuType.BUTTON) { conflicts.add(before.code()+"（非按钮节点）"); continue; }
            if(c.version()==null || !before.version().equals(c.version())) { conflicts.add(before.code()+"（版本已变更）"); continue; }
            try {
                MenuNode node=normalize(before.id(),new MenuWriteCommand(c.code(),c.name(),MenuType.BUTTON,before.parentId(),null,c.icon(),c.grantable(),c.sortOrder(),c.status()==null?before.status():c.status(),c.version()),c.version());
                if(all.stream().anyMatch(x->x.code().equals(node.code()) && !x.id().equals(node.id())) || !batchCodes.add(node.code())) { conflicts.add(node.code()+"（编码已存在）"); continue; }
                validate(node,before,all);
                pairs.add(new MenuNode[]{before,node});
            } catch(IllegalArgumentException|IllegalStateException e) { conflicts.add(before.code()+"（"+e.getMessage()+"）"); }
        }
        if(!conflicts.isEmpty()) throw new MenuBatchConflictException(conflicts);
        List<MenuNode> updated=new ArrayList<>();
        for(MenuNode[] pair:pairs) {
            MenuNode before=pair[0],node=pair[1];
            if(mapper.update(node)!=1) throw new IllegalStateException("菜单已变更，请刷新");
            syncPermission(node,before);
            audit.record(IamChangeAuditEventType.MENU_UPDATE,user.userId(),IamChangeTargetType.MENU,node.id(),node.parentId(),null,"v"+before.version(),"v"+(node.version()+1));
            updated.add(new MenuNode(node.id(),node.code(),node.name(),node.type(),node.parentId(),node.route(),node.icon(),node.permissionCode(),node.grantable(),node.sortOrder(),node.status(),node.version()+1));
        }
        return updated;
    }
    /** 拖拽层级调整：移动到目标父级下指定位置，阻止移入自身子孙。 */
    @Transactional
    public MenuNode move(AuthenticatedUser user,Long id,MenuPositionCommand c) {
        requireManager(user,true);
        if(c==null || c.version()==null || c.index()==null || c.index()<0) throw new IllegalArgumentException("位置参数无效");
        List<MenuNode> all=mapper.lockAll();
        Map<Long,MenuNode> map=new HashMap<>(); all.forEach(n->map.put(n.id(),n));
        MenuNode node=map.get(id);
        if(node==null) throw new ResourceNotFoundException("菜单不存在");
        if(!node.version().equals(c.version())) throw new IllegalStateException("菜单已变更，请刷新");
        if(Objects.equals(node.parentId(),c.parentId())) throw new IllegalArgumentException("同级顺序请使用排序接口");
        MenuNode newParent=c.parentId()==null?null:map.get(c.parentId());
        if(c.parentId()!=null && newParent==null) throw new IllegalArgumentException("目标父级不存在");
        if(newParent!=null) {
            if(newParent.type()==MenuType.BUTTON) throw new IllegalArgumentException("目标父级类型无效");
            if(node.type()==MenuType.BUTTON && newParent.type()!=MenuType.PAGE) throw new IllegalArgumentException("按钮必须属于页面");
            if(node.type()!=MenuType.BUTTON && newParent.type()!=MenuType.DIRECTORY) throw new IllegalArgumentException("目标父级类型无效");
            Long cursor=newParent.id();
            while(cursor!=null) {
                if(cursor.equals(node.id())) throw new IllegalArgumentException("菜单不可移动到自身或其子孙之下");
                var next=map.get(cursor); cursor=next==null?null:next.parentId();
            }
        } else if(node.type()==MenuType.BUTTON) throw new IllegalArgumentException("按钮必须属于页面");
        int index=Math.min(c.index(),all.stream().filter(n->Objects.equals(n.parentId(),c.parentId())).toList().size());
        MenuNode moved=new MenuNode(node.id(),node.code(),node.name(),node.type(),c.parentId(),node.route(),node.icon(),node.permissionCode(),node.grantable(),index*10,node.status(),node.version());
        validate(moved,node,all);
        List<MenuNode> oldRemaining=all.stream()
                .filter(n->Objects.equals(n.parentId(),node.parentId()) && !n.id().equals(node.id()))
                .sorted(Comparator.comparingInt(MenuNode::sortOrder).thenComparing(MenuNode::code)).toList();
        List<MenuNode> newSiblings=new ArrayList<>(all.stream()
                .filter(n->Objects.equals(n.parentId(),c.parentId()) && !n.id().equals(node.id()))
                .sorted(Comparator.comparingInt(MenuNode::sortOrder).thenComparing(MenuNode::code)).toList());
        newSiblings.add(index,node);
        for(int i=0;i<oldRemaining.size();i++) {
            MenuNode s=oldRemaining.get(i);
            if(mapper.reorder(s.id(),s.version(),i*10)!=1) throw new IllegalStateException("菜单已变更，请刷新");
        }
        int order=0;
        for(MenuNode s:newSiblings) {
            if(!s.id().equals(node.id())) {
                if(mapper.reorder(s.id(),s.version(),order*10)!=1) throw new IllegalStateException("菜单已变更，请刷新");
            }
            order++;
        }
        if(mapper.update(moved)!=1) throw new IllegalStateException("菜单已变更，请刷新");
        syncPermission(moved,node);
        audit.record(IamChangeAuditEventType.MENU_REORDER,user.userId(),IamChangeTargetType.MENU,node.id(),c.parentId(),null,"parent:"+node.parentId(),"parent:"+c.parentId()+":index:"+index);
        return new MenuNode(moved.id(),moved.code(),moved.name(),moved.type(),moved.parentId(),moved.route(),moved.icon(),moved.permissionCode(),moved.grantable(),moved.sortOrder(),moved.status(),moved.version()+1);
    }
    private MenuNode normalize(Long id,MenuWriteCommand c,Long version) {
        String name=text(c.name(),128,true), code=text(c.code(),128,true);
        if(c.type()==null || c.status()==null || c.sortOrder()==null || c.sortOrder()<0 || c.sortOrder()>1000000) throw new IllegalArgumentException("菜单字段无效");
        boolean grantable=c.type()==MenuType.PAGE || c.type()==MenuType.BUTTON;
        String permissionCode=grantable?code:null;
        if(c.type()==MenuType.PAGE) {
            if(!code.matches(PERMISSION_CODE_PATTERN)) throw new IllegalArgumentException("页面编码必须为权限编码");
            if(text(c.route(),128,false)==null) throw new IllegalArgumentException("页面路由必填");
        } else if(c.type()==MenuType.DIRECTORY) {
            if(!code.matches(SLUG_CODE_PATTERN)) throw new IllegalArgumentException("目录编码无效");

        } else if(!code.matches(SLUG_CODE_PATTERN)) {
            throw new IllegalArgumentException("按钮编码无效");
        }
        String route=c.type()==MenuType.PAGE?text(c.route(),128,true):null;
        if(c.type()!=MenuType.PAGE && c.route()!=null) throw new IllegalArgumentException("目录和按钮不允许配置路由");
        String icon=text(c.icon(),64,false);
        if(icon!=null && !icon.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("图标编码无效");
        return new MenuNode(id,code,name,c.type(),c.parentId(),route,icon,permissionCode,grantable,c.sortOrder(),c.status(),version);
    }
    private void validate(MenuNode n,MenuNode before,List<MenuNode> all) {
        Map<Long,MenuNode> map=new HashMap<>(); all.forEach(x->map.put(x.id(),x));
        if(all.stream().anyMatch(x->x.code().equals(n.code()) && !x.id().equals(n.id()))) throw new IllegalStateException("菜单编码已存在");
        if(before!=null && !Objects.equals(before.code(),n.code())) throw new IllegalArgumentException("菜单编码创建后不可修改");
        if(n.type()==MenuType.PAGE && !PAGE_ROUTES.contains(n.route())) throw new IllegalArgumentException("页面访问URL必须为已注册页面路由");
        if(n.type()!=MenuType.PAGE && n.route()!=null) throw new IllegalArgumentException("目录和按钮不允许配置路由");
        if(n.type()==MenuType.PAGE && all.stream().anyMatch(x->x.type()==MenuType.PAGE && !x.id().equals(n.id()) && Objects.equals(x.route(),n.route()))) throw new IllegalStateException("页面路由已配置");
        MenuNode parent=n.parentId()==null?null:map.get(n.parentId());
        if(n.parentId()!=null && parent==null) throw new IllegalArgumentException("父级菜单不存在");
        if(parent!=null && (parent.type()==MenuType.BUTTON || (n.type()!=MenuType.BUTTON && parent.type()!=MenuType.DIRECTORY))) throw new IllegalArgumentException("父级类型无效");
        if(n.type()==MenuType.BUTTON && (parent==null || parent.type()!=MenuType.PAGE)) throw new IllegalArgumentException("按钮必须属于页面");
        if(all.stream().anyMatch(x->Objects.equals(x.parentId(),n.id()) && (n.type()==MenuType.BUTTON || (n.type()==MenuType.PAGE && x.type()!=MenuType.BUTTON) || (n.type()==MenuType.DIRECTORY && x.type()==MenuType.BUTTON)))) throw new IllegalArgumentException("现有子级与节点类型不兼容");
        Set<Long> visited=new HashSet<>(); visited.add(n.id());
        while(parent!=null) { if(!visited.add(parent.id())) throw new IllegalArgumentException("菜单不可循环引用"); parent=parent.parentId()==null?null:map.get(parent.parentId()); }
        if(before!=null && before.permissionCode()!=null && n.permissionCode()!=null && !before.permissionCode().equals(n.permissionCode())) {
            Permission existing=permissions.findByCode(n.permissionCode());
            Permission old=permissions.findByCode(before.permissionCode());
            if(existing!=null && (old==null || !existing.id().equals(old.id()))) throw new IllegalStateException("权限编码已被占用");
        }
        if(before!=null && (Objects.equals(before.route(),CORE_PAGE_ROUTE) || Objects.equals(before.route(),MENU_MANAGE_ROUTE))
                && (n.status()!=MenuStatus.ENABLED || n.type()!=MenuType.PAGE || !Objects.equals(n.route(),before.route()) || !Objects.equals(n.parentId(),before.parentId()))) throw new IllegalArgumentException("核心入口不能停用或移动");
        map.put(n.id(),n);
        for(MenuNode core:map.values()) {
            if((Objects.equals(core.route(),CORE_PAGE_ROUTE) || Objects.equals(core.route(),MENU_MANAGE_ROUTE)) && !statusEnabled(core,map))
                throw new IllegalArgumentException("核心入口及其上级目录必须保持启用");
        }
        if(before!=null && before.type()==MenuType.BUTTON && Objects.equals(routeOf(map,before.parentId()),MENU_MANAGE_ROUTE)
                && (n.status()!=MenuStatus.ENABLED || n.type()!=MenuType.BUTTON || !Objects.equals(n.parentId(),before.parentId()))) throw new IllegalArgumentException("菜单配置操作不能停用或移动");
    }
    private String routeOf(Map<Long,MenuNode> map,Long id) {
        MenuNode n=id==null?null:map.get(id);
        return n==null?null:n.route();
    }
    /** 编码-权限同步：页面与所有按钮按编码 upsert 权限（client=WEB），停用联动置 DISABLED。 */
    private void syncPermission(MenuNode node,MenuNode before) {
        String target=node.permissionCode(), old=before==null?null:before.permissionCode();
        if(target==null) {
            if(old!=null) disablePermission(old);
            return;
        }
        Permission existing=permissions.findByCode(target);
        if(existing!=null && existing.client()==PermissionClient.MINIAPP) throw new IllegalArgumentException("菜单编码不能占用小程序专属权限");
        PermissionStatus status=node.status()==MenuStatus.ENABLED?PermissionStatus.ENABLED:PermissionStatus.DISABLED;
        PermissionResourceType type=node.type()==MenuType.PAGE?PermissionResourceType.PAGE:PermissionResourceType.BUTTON;
        MenuNode parent=node.parentId()==null?null:mapper.findAll().stream().filter(n->n.id().equals(node.parentId())).findFirst().orElse(null);
        Permission parentPermission=parent==null || parent.permissionCode()==null?null:permissions.findByCode(parent.permissionCode());
        Long parentId=parentPermission==null?null:parentPermission.id();
        if(existing==null) {
            permissions.insert(new Permission(ids.nextId(),target,node.name(),type,PermissionClient.WEB,parentId,status,null));
        } else {
            permissions.updateMenuMetadata(existing.id(),node.name(),status,type,parentId);
        }
    }
    private void disablePermission(String code) {
        Permission p=permissions.findByCode(code);
        if(p!=null && p.status()!=PermissionStatus.DISABLED) permissions.updateNameAndStatus(p.id(),p.name(),PermissionStatus.DISABLED);
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
        if(account==null || account.status()!=UserStatus.ENABLED) throw new SystemOperationAccessDeniedException("当前角色不可管理菜单");
        if(!decisions.isAllowed(user.userId(),PermissionClient.WEB,write?"MENU_MANAGE":"MENU_READ")) throw new SystemOperationAccessDeniedException("当前账号无菜单权限");
    }
    public static List<MenuNode> filterVisible(List<MenuNode> all,Set<String> allowed) {
        Map<Long,MenuNode> map=new HashMap<>(); all.forEach(n->map.put(n.id(),n));
        Map<Long,List<MenuNode>> children=new HashMap<>();
        for(MenuNode n:all) if(n.parentId()!=null) children.computeIfAbsent(n.parentId(),k->new ArrayList<>()).add(n);
        return all.stream().filter(n->{
            if(!statusEnabled(n,map)) return false;
            return switch(n.type()) {
                case DIRECTORY -> true;
                case PAGE -> pagePermissionOk(n,children,allowed,new HashSet<>());
                case BUTTON -> n.permissionCode()!=null && allowed.contains(n.permissionCode());
            };
        }).toList();
    }
    /** 自身及全部祖先均为启用、父级可解析且无环。 */
    private static boolean statusEnabled(MenuNode n,Map<Long,MenuNode> map) {
        Set<Long> seen=new HashSet<>(); MenuNode cursor=n;
        while(cursor!=null) {
            if(!seen.add(cursor.id()) || cursor.status()!=MenuStatus.ENABLED) return false;
            if(cursor.parentId()==null) return true;
            cursor=map.get(cursor.parentId()); if(cursor==null) return false;
        }
        return false;
    }
    /** 页面权限：无权限码、自身被授权、或其下任意可授权按钮被授权即视为可进入。 */
    private static boolean pagePermissionOk(MenuNode page,Map<Long,List<MenuNode>> children,Set<String> allowed,Set<Long> seen) {
        if(page.permissionCode()==null || allowed.contains(page.permissionCode())) return true;
        return hasAllowedActionButton(page,children,allowed,seen);
    }
    private static boolean hasAllowedActionButton(MenuNode node,Map<Long,List<MenuNode>> children,Set<String> allowed,Set<Long> seen) {
        if(!seen.add(node.id())) return false;
        for(MenuNode child:children.getOrDefault(node.id(),List.of())) {
            if(child.permissionCode()!=null && allowed.contains(child.permissionCode())) return true;
            if(hasAllowedActionButton(child,children,allowed,seen)) return true;
        }
        return false;
    }
}
