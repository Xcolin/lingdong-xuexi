package com.lingdong.learning.menu.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.menu.domain.MenuStatus;
import com.lingdong.learning.menu.domain.MenuType;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.permission.domain.PermissionEffect;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** 菜单树授权视图与差量保存：勾选保存、回显、取消勾选，全部按钮可授权，目录不出现。 */
@SpringBootTest @ActiveProfiles("test") @Transactional
class MenuGrantViewTest {
    @Autowired MenuApplicationService menus;
    @Autowired MenuGrantApplicationService grants;
    @Autowired UserAccessApplicationService accounts;
    @Autowired RoleMapper roles;
    @Autowired PermissionMapper permissions;
    @Autowired IdGenerator ids;
    @Autowired JdbcTemplate jdbc;

    private AuthenticatedUser principal(String role) {
        var u=accounts.createUser(new CreateUserCommand("menu_grant_"+UUID.randomUUID().toString().substring(0,10),"菜单授权测试",null,UserType.PLATFORM));
        accounts.assignRole(new AssignRoleToUserCommand(u.id(),roles.findByCode(role).id(),null));
        return new AuthenticatedUser(u.id(),1L,u.username(),u.displayName(),AuthClientType.WEB,List.of(role));
    }
    private String availableRoute() {
        jdbc.update("delete from sys_menu where parent_id in (select id from (select id from sys_menu where route='/users') routes)");
        jdbc.update("delete from sys_menu where route='/users'");
        return "/users";
    }
    private Long roleId(String code) { return roles.findByCode(code).id(); }
    private int grantCount(Long rId,String code) {
        Integer n=jdbc.queryForObject("""
            select count(*) from sys_role_permission rp join sys_permission p on p.id=rp.permission_id
            where rp.role_id=? and p.permission_code=? and rp.effect='ALLOW'""",Integer.class,rId,code);
        return n==null?0:n;
    }

    @Test void menuGrantViewNodeIdsSerializeAsStringsForJsSafety() throws Exception {
        // 19 位 Long 超出 JS Number 精度，必须以字符串序列化，否则前端建树成环白屏
        var json=new ObjectMapper().writeValueAsString(
                new MenuGrantViewNode(1874300000000000001L,"TEST_PAGE","页面",MenuType.PAGE,1874300000000000001L,true));
        assertThat(json).contains("\"id\":\"1874300000000000001\"")
                .contains("\"parentId\":\"1874300000000000001\"");
    }

    @Test void savesCheckedGrantsWithSingleSummaryAudit() {
        var admin=principal("SYS_ADMIN");
        var rId=roleId("SYS_AUDITOR");
        var dir=menus.create(admin,new MenuWriteCommand("grant-view-dir","目录",MenuType.DIRECTORY,null,null,null,false,0,MenuStatus.ENABLED,null));
        var page=menus.create(admin,new MenuWriteCommand("TEST_GRANT_PAGE","页面",MenuType.PAGE,dir.id(),availableRoute(),null,false,0,MenuStatus.ENABLED,null));
        menus.create(admin,new MenuWriteCommand("TEST_GRANT_BTN","权限按钮",MenuType.BUTTON,page.id(),null,null,true,0,MenuStatus.ENABLED,null));
        menus.create(admin,new MenuWriteCommand("grant-page.ui-btn","纯UI按钮",MenuType.BUTTON,page.id(),null,null,false,10,MenuStatus.ENABLED,null));

        grants.saveGrants(admin,rId,List.of("TEST_GRANT_PAGE","TEST_GRANT_BTN"));
        assertThat(grantCount(rId,"TEST_GRANT_PAGE")).isEqualTo(1);
        assertThat(grantCount(rId,"TEST_GRANT_BTN")).isEqualTo(1);
        Integer audits=jdbc.queryForObject(
                "select count(*) from sys_iam_change_audit where event_type='ROLE_PERMISSION_CONFIGURE' and target_type='ROLE_PERMISSION' and target_id=?",
                Integer.class,rId);
        assertThat(audits).isEqualTo(1);
    }

    @Test void viewEchoesBackExistingGrantsAndIncludesAllButtonsButHidesDirectories() {
        var admin=principal("SYS_ADMIN");
        var rId=roleId("SYS_AUDITOR");
        var dir=menus.create(admin,new MenuWriteCommand("grant-view-dir2","目录",MenuType.DIRECTORY,null,null,null,false,0,MenuStatus.ENABLED,null));
        var page=menus.create(admin,new MenuWriteCommand("TEST_GRANT_PAGE2","页面",MenuType.PAGE,dir.id(),availableRoute(),null,false,0,MenuStatus.ENABLED,null));
        menus.create(admin,new MenuWriteCommand("TEST_GRANT_BTN2","权限按钮",MenuType.BUTTON,page.id(),null,null,true,0,MenuStatus.ENABLED,null));
        menus.create(admin,new MenuWriteCommand("grant-page2.ui-btn","纯UI按钮",MenuType.BUTTON,page.id(),null,null,false,10,MenuStatus.ENABLED,null));
        // 既有授权（不经菜单保存接口产生）也应回显为勾选
        jdbc.update("insert into sys_role_permission (id,role_id,permission_id,effect) values (?,?,?,'ALLOW')",
                ids.nextId(),rId,permissions.findByCode("TEST_GRANT_BTN2").id());

        var view=grants.grantView(admin,rId);
        var codes=view.stream().map(MenuGrantViewNode::code).toList();
        assertThat(codes).contains("TEST_GRANT_PAGE2","TEST_GRANT_BTN2","grant-page2.ui-btn");
        assertThat(codes).doesNotContain("grant-view-dir2");
        assertThat(view.stream().filter(n->n.code().equals("TEST_GRANT_BTN2")).findFirst().orElseThrow().granted()).isTrue();
        assertThat(view.stream().filter(n->n.code().equals("TEST_GRANT_PAGE2")).findFirst().orElseThrow().granted()).isFalse();
    }

    @Test void uncheckingRemovesGrantKeepsOthersAndNonMenuGrants() {
        var admin=principal("SYS_ADMIN");
        var rId=roleId("SYS_AUDITOR");
        var page=menus.create(admin,new MenuWriteCommand("TEST_GRANT_PAGE3","页面",MenuType.PAGE,null,availableRoute(),null,false,0,MenuStatus.ENABLED,null));
        menus.create(admin,new MenuWriteCommand("TEST_GRANT_BTN_A","按钮A",MenuType.BUTTON,page.id(),null,null,true,0,MenuStatus.ENABLED,null));
        menus.create(admin,new MenuWriteCommand("TEST_GRANT_BTN_B","按钮B",MenuType.BUTTON,page.id(),null,null,true,10,MenuStatus.ENABLED,null));
        grants.saveGrants(admin,rId,List.of("TEST_GRANT_PAGE3","TEST_GRANT_BTN_A","TEST_GRANT_BTN_B"));
        assertThat(grantCount(rId,"TEST_GRANT_BTN_B")).isEqualTo(1);

        // 取消勾选按钮 B：B 移除，A 与页面保持，非菜单来源授权不受差量影响
        Long nonMenuPermissionId=ids.nextId();
        permissions.insert(new com.lingdong.learning.permission.domain.Permission(nonMenuPermissionId,
                "TEST_NON_MENU_GRANT", "独立业务权限", com.lingdong.learning.permission.domain.PermissionResourceType.OPERATION,
                com.lingdong.learning.permission.domain.PermissionClient.WEB, null,
                com.lingdong.learning.permission.domain.PermissionStatus.ENABLED, null));
        jdbc.update("insert into sys_role_permission (id,role_id,permission_id,effect) values (?,?,?,'ALLOW')",ids.nextId(),rId,nonMenuPermissionId);
        grants.saveGrants(admin,rId,List.of("TEST_GRANT_PAGE3","TEST_GRANT_BTN_A"));
        assertThat(grantCount(rId,"TEST_GRANT_BTN_B")).isZero();
        assertThat(grantCount(rId,"TEST_GRANT_BTN_A")).isEqualTo(1);
        assertThat(grantCount(rId,"TEST_GRANT_PAGE3")).isEqualTo(1);
        assertThat(grantCount(rId,"TEST_NON_MENU_GRANT")).isEqualTo(1);
        // 未知或不可授权编码被拒绝
        assertThatThrownBy(()->grants.saveGrants(admin,rId,List.of("grant-page2.ui-btn")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->grants.saveGrants(admin,rId,List.of("NO_SUCH_CODE_XYZ")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
