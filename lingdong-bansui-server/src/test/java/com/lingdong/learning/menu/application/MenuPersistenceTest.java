package com.lingdong.learning.menu.application;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.menu.domain.*;
import com.lingdong.learning.user.application.*;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class MenuPersistenceTest {
    @Autowired MenuApplicationService service;
    @Autowired UserAccessApplicationService accounts;
    @Autowired RoleMapper roles;
    private AuthenticatedUser principal(String role) {
        var u=accounts.createUser(new CreateUserCommand("menu_"+UUID.randomUUID().toString().substring(0,10),"菜单测试",null,UserType.PLATFORM));
        accounts.assignRole(new AssignRoleToUserCommand(u.id(),roles.findByCode(role).id(),null));
        return new AuthenticatedUser(u.id(),1L,u.username(),u.displayName(),AuthClientType.WEB,List.of(role));
    }
    private MenuWriteCommand command(String code,MenuType type,Long parent,Long version) {
        return new MenuWriteCommand(code,"测试菜单",type,parent,null,null,null,0,MenuStatus.ENABLED,version);
    }
    @Test void persistsDirectoryAndRejectsCycleAndStaleUpdate() {
        var admin=principal("SYS_ADMIN");
        var a=service.create(admin,command("test-dir-a",MenuType.DIRECTORY,null,null));
        var b=service.create(admin,command("test-dir-b",MenuType.DIRECTORY,a.id(),null));
        assertThat(service.list(admin)).anyMatch(n->n.id().equals(a.id()));
        assertThatThrownBy(()->service.update(admin,a.id(),command(a.code(),MenuType.DIRECTORY,b.id(),a.version()))).isInstanceOf(IllegalArgumentException.class);
        var updated=service.update(admin,a.id(),command(a.code(),MenuType.DIRECTORY,null,a.version()));
        assertThat(updated.version()).isEqualTo(1L);
        assertThatThrownBy(()->service.update(admin,a.id(),command(a.code(),MenuType.DIRECTORY,null,a.version()))).isInstanceOf(IllegalStateException.class);
    }
    @Test void reorderValidatesEntireSiblingVersionSetBeforeAnyWrite() {
        var admin=principal("SYS_ADMIN");
        var p=service.create(admin,command("order-parent",MenuType.DIRECTORY,null,null));
        var a=service.create(admin,command("order-a",MenuType.DIRECTORY,p.id(),null));
        var b=service.create(admin,command("order-b",MenuType.DIRECTORY,p.id(),null));
        assertThatThrownBy(()->service.order(admin,new MenuOrderCommand(p.id(),List.of(b.id(),a.id()),Map.of(a.id(),0L,b.id(),1L)))).isInstanceOf(IllegalStateException.class);
        assertThat(service.list(admin).stream().filter(n->n.id().equals(a.id())).findFirst().orElseThrow().version()).isZero();
        var result=service.order(admin,new MenuOrderCommand(p.id(),List.of(b.id(),a.id()),Map.of(a.id(),0L,b.id(),0L)));
        assertThat(result.stream().filter(n->Objects.equals(n.parentId(),p.id())).map(MenuNode::id).toList()).containsExactly(b.id(),a.id());
        assertThatThrownBy(()->service.order(admin,new MenuOrderCommand(p.id(),List.of(a.id()),Map.of(a.id(),1L)))).isInstanceOf(IllegalStateException.class);
    }
    @Test void auditorIsReadOnlyAndProtectedEntrypointsStayEnabled() {
        var auditor=principal("SYS_AUDITOR");
        assertThat(service.list(auditor)).isNotEmpty();
        assertThatThrownBy(()->service.create(auditor,command("auditor-dir",MenuType.DIRECTORY,null,null))).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
        var admin=principal("SYS_ADMIN");
        var core=service.list(admin).stream().filter(n->n.code().equals("menu-management")).findFirst().orElseThrow();
        assertThatThrownBy(()->service.update(admin,core.id(),new MenuWriteCommand(core.code(),core.name(),core.type(),core.parentId(),core.route(),core.icon(),null,core.sortOrder(),MenuStatus.ENABLED,core.version()))).isInstanceOf(IllegalArgumentException.class);
        var button=service.list(admin).stream().filter(n->Objects.equals(n.parentId(),core.id()) && n.type()==MenuType.BUTTON).findFirst().orElseThrow();
        assertThatThrownBy(()->service.update(admin,button.id(),new MenuWriteCommand(button.code(),button.name(),button.type(),button.parentId(),null,button.icon(),button.permissionCode(),button.sortOrder(),MenuStatus.DISABLED,button.version()))).isInstanceOf(IllegalArgumentException.class);
        accounts.assignRole(new AssignRoleToUserCommand(admin.userId(),roles.findByCode("SYS_AUDITOR").id(),null));
        assertThatThrownBy(()->service.create(admin,command("mixed-role-dir",MenuType.DIRECTORY,null,null))).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
    }
    @Test void validatesParentTypeRoutePermissionAndImmutableCodes() {
        var admin=principal("SYS_ADMIN");
        var dir=service.create(admin,command("validate-dir",MenuType.DIRECTORY,null,null));
        assertThatThrownBy(()->service.create(admin,command("root-button",MenuType.BUTTON,null,null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.create(admin,command("directory-button",MenuType.BUTTON,dir.id(),null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.create(admin,new MenuWriteCommand("unknown-route","未知页面",MenuType.PAGE,dir.id(),"/nonexistent",null,null,0,MenuStatus.ENABLED,null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.create(admin,new MenuWriteCommand("unknown-permission","未知权限",MenuType.DIRECTORY,null,null,null,"NO_SUCH_PERMISSION",0,MenuStatus.ENABLED,null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.update(admin,dir.id(),command("changed-code",MenuType.DIRECTORY,null,dir.version()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.create(admin,command(dir.code(),MenuType.DIRECTORY,null,null))).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsUnregisteredActionsAndActionsAssignedToAnotherPage() {
        var admin=principal("SYS_ADMIN");
        var nodes=service.list(admin);
        var button=nodes.stream().filter(n->n.type()==MenuType.BUTTON && !n.code().startsWith("menu-management.")).findFirst().orElseThrow();
        var otherPage=nodes.stream().filter(n->n.type()==MenuType.PAGE && !n.id().equals(button.parentId())).findFirst().orElseThrow();
        var originalPage=nodes.stream().filter(n->n.id().equals(button.parentId())).findFirst().orElseThrow();
        assertThatThrownBy(()->service.create(admin,command("unregistered.action",MenuType.BUTTON,originalPage.id(),null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.update(admin,button.id(),new MenuWriteCommand(button.code(),button.name(),button.type(),otherPage.id(),null,button.icon(),button.permissionCode(),button.sortOrder(),button.status(),button.version()))).isInstanceOf(IllegalArgumentException.class);
        var updated=service.update(admin,button.id(),new MenuWriteCommand(button.code(),"动作名称",button.type(),button.parentId(),null,button.icon(),button.permissionCode(),button.sortOrder(),button.status(),button.version()));
        assertThat(updated.name()).isEqualTo("动作名称");
    }
}
