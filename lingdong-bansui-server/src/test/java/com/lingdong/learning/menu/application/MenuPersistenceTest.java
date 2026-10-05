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
        return new MenuWriteCommand(code,"测试菜单",type,parent,null,null,false,0,MenuStatus.ENABLED,version);
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
    @Test void crossParentMoveUsesDisplayedCodeOrderForTiedSortNumbers() {
        var admin=principal("SYS_ADMIN");
        var from=service.create(admin,command("drag-from",MenuType.DIRECTORY,null,null));
        var to=service.create(admin,command("drag-to",MenuType.DIRECTORY,null,null));
        var z=service.create(admin,command("drag-z",MenuType.DIRECTORY,to.id(),null));
        var a=service.create(admin,command("drag-a",MenuType.DIRECTORY,to.id(),null));
        var moving=service.create(admin,command("drag-moving",MenuType.DIRECTORY,from.id(),null));
        service.move(admin,moving.id(),new MenuPositionCommand(to.id(),1,moving.version()));
        assertThat(service.list(admin).stream().filter(n->Objects.equals(n.parentId(),to.id()))
            .sorted(Comparator.comparingInt(MenuNode::sortOrder)).map(MenuNode::id).toList())
            .containsExactly(a.id(),moving.id(),z.id());
    }
    @Test void positionApiCannotBypassCoreEntryAndConfigurationButtonProtection() {
        var admin=principal("SYS_ADMIN");
        var destination=service.create(admin,command("core-destination",MenuType.DIRECTORY,null,null));
        var menu=service.list(admin).stream().filter(n->Objects.equals(n.route(),"/menu-management")).findFirst().orElseThrow();
        var dashboard=service.list(admin).stream().filter(n->Objects.equals(n.route(),"/dashboard")).findFirst().orElseThrow();
        assertThatThrownBy(()->service.move(admin,menu.id(),new MenuPositionCommand(destination.id(),0,menu.version()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.move(admin,dashboard.id(),new MenuPositionCommand(destination.id(),0,dashboard.version()))).isInstanceOf(IllegalArgumentException.class);
        var button=service.list(admin).stream().filter(n->Objects.equals(n.parentId(),menu.id()) && n.type()==MenuType.BUTTON).findFirst().orElseThrow();
        assertThatThrownBy(()->service.move(admin,button.id(),new MenuPositionCommand(dashboard.id(),0,button.version()))).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.list(admin).stream().filter(n->n.id().equals(menu.id())).findFirst().orElseThrow().version()).isEqualTo(menu.version());
    }
    @Test void auditorIsReadOnlyAndProtectedEntrypointsStayEnabled() {
        var auditor=principal("SYS_AUDITOR");
        assertThat(service.list(auditor)).isNotEmpty();
        assertThatThrownBy(()->service.create(auditor,command("auditor-dir",MenuType.DIRECTORY,null,null))).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
        var admin=principal("SYS_ADMIN");
        var core=service.list(admin).stream().filter(n->Objects.equals(n.route(),"/menu-management")).findFirst().orElseThrow();
        // 核心入口无变化更新允许；停用被拒绝
        var coreUpdated=service.update(admin,core.id(),new MenuWriteCommand(core.code(),core.name(),core.type(),core.parentId(),core.route(),core.icon(),false,core.sortOrder(),MenuStatus.ENABLED,core.version()));
        assertThat(coreUpdated.version()).isEqualTo(core.version()+1);
        assertThatThrownBy(()->service.update(admin,core.id(),new MenuWriteCommand(core.code(),core.name(),core.type(),core.parentId(),core.route(),core.icon(),false,core.sortOrder(),MenuStatus.DISABLED,core.version()+1))).isInstanceOf(IllegalArgumentException.class);
        var button=service.list(admin).stream().filter(n->Objects.equals(n.parentId(),core.id()) && n.type()==MenuType.BUTTON).findFirst().orElseThrow();
        assertThatThrownBy(()->service.update(admin,button.id(),new MenuWriteCommand(button.code(),button.name(),button.type(),button.parentId(),null,button.icon(),button.grantable(),button.sortOrder(),MenuStatus.DISABLED,button.version()))).isInstanceOf(IllegalArgumentException.class);
        accounts.assignRole(new AssignRoleToUserCommand(admin.userId(),roles.findByCode("SYS_AUDITOR").id(),null));
        assertThatThrownBy(()->service.create(admin,command("mixed-role-dir",MenuType.DIRECTORY,null,null))).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
    }
    @Test void validatesParentTypeRouteAndPermissionCodeShape() {
        var admin=principal("SYS_ADMIN");
        var dir=service.create(admin,command("validate-dir",MenuType.DIRECTORY,null,null));
        assertThatThrownBy(()->service.create(admin,command("root-button",MenuType.BUTTON,null,null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.create(admin,command("directory-button",MenuType.BUTTON,dir.id(),null))).isInstanceOf(IllegalArgumentException.class);
        // 页面必须提供路由
        assertThatThrownBy(()->service.create(admin,command("NO_ROUTE_PAGE",MenuType.PAGE,dir.id(),null))).isInstanceOf(IllegalArgumentException.class);
        // 目录和按钮不允许配置路由
        assertThatThrownBy(()->service.create(admin,new MenuWriteCommand("routed-dir","目录",MenuType.DIRECTORY,dir.id(),"/somewhere",null,false,0,MenuStatus.ENABLED,null))).isInstanceOf(IllegalArgumentException.class);
        // 页面/权限按钮编码必须为权限码格式
        assertThatThrownBy(()->service.create(admin,new MenuWriteCommand("bad-page-code","页面",MenuType.PAGE,dir.id(),"/test-bad-page-code",null,false,0,MenuStatus.ENABLED,null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.create(admin,command(dir.code(),MenuType.DIRECTORY,null,null))).isInstanceOf(IllegalStateException.class);
    }

}
