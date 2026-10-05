package com.lingdong.learning.menu.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.menu.domain.*;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.permission.domain.PermissionResourceType;
import com.lingdong.learning.permission.domain.PermissionStatus;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** 菜单编码即权限编码（自动同步）、按钮分类、批量维护与拖拽层级调整能力。 */
@SpringBootTest @ActiveProfiles("test") @Transactional
class MenuCatalogUpgradeTest {
    @Autowired MenuApplicationService service;
    @Autowired UserAccessApplicationService accounts;
    @Autowired RoleMapper roles;
    @Autowired PermissionMapper permissions;
    @Autowired IdGenerator ids;
    @Autowired JdbcTemplate jdbc;

    private AuthenticatedUser principal(String role) {
        var u=accounts.createUser(new CreateUserCommand("menu_up_"+UUID.randomUUID().toString().substring(0,10),"菜单升级测试",null,UserType.PLATFORM));
        accounts.assignRole(new AssignRoleToUserCommand(u.id(),roles.findByCode(role).id(),null));
        return new AuthenticatedUser(u.id(),1L,u.username(),u.displayName(),AuthClientType.WEB,List.of(role));
    }
    private MenuWriteCommand directory(String code) {
        return new MenuWriteCommand(code,"目录",MenuType.DIRECTORY,null,null,null,false,0,MenuStatus.ENABLED,null);
    }
    private String availableRoute() {
        jdbc.update("delete from sys_menu where parent_id in (select id from (select id from sys_menu where route='/users') routes)");
        jdbc.update("delete from sys_menu where route='/users'");
        return "/users";
    }
    private MenuWriteCommand page(String code) {
        return new MenuWriteCommand(code,"页面",MenuType.PAGE,null,availableRoute(),null,false,0,MenuStatus.ENABLED,null);
    }

    @Test void pageAndPermissionButtonDerivePermissionCodeAndGrantable() {
        var admin=principal("SYS_ADMIN");
        var dir=service.create(admin,directory("upgrade-dir"));
        var p=service.create(admin,new MenuWriteCommand("TEST_UPGRADE_PAGE","页面",MenuType.PAGE,dir.id(),availableRoute(),null,false,0,MenuStatus.ENABLED,null));
        assertThat(p.grantable()).isTrue();
        assertThat(p.permissionCode()).isEqualTo("TEST_UPGRADE_PAGE");
        var permBtn=service.create(admin,new MenuWriteCommand("TEST_UPGRADE_BTN","权限按钮",MenuType.BUTTON,p.id(),null,null,true,0,MenuStatus.ENABLED,null));
        assertThat(permBtn.grantable()).isTrue();
        assertThat(permBtn.permissionCode()).isEqualTo("TEST_UPGRADE_BTN");
        var uiBtn=service.create(admin,new MenuWriteCommand("upgrade-page.ui-button","纯UI按钮",MenuType.BUTTON,p.id(),null,null,false,10,MenuStatus.ENABLED,null));
        assertThat(uiBtn.grantable()).isTrue();
        assertThat(uiBtn.permissionCode()).isEqualTo("upgrade-page.ui-button");
        var synced=permissions.findByCode("TEST_UPGRADE_BTN");
        assertThat(synced).isNotNull();
        assertThat(synced.client()).isEqualTo(PermissionClient.WEB);
        assertThat(synced.resourceType()).isEqualTo(PermissionResourceType.BUTTON);
        assertThat(permissions.findByCode("upgrade-page.ui-button")).isNotNull();
    }

    @Test void editingLegacyFalseFlagPromotesButtonAndAcceptsSlug() {
        var admin=principal("SYS_ADMIN");
        var page=service.create(admin,page("TEST_EDIT_SLUG_PAGE"));
        var button=service.create(admin,new MenuWriteCommand("edit-page.query-action","按钮",MenuType.BUTTON,page.id(),null,null,true,0,MenuStatus.ENABLED,null));
        var edited=service.update(admin,button.id(),new MenuWriteCommand("edit-page.query-action","查询",MenuType.BUTTON,page.id(),null,null,false,0,MenuStatus.ENABLED,button.version()));
        assertThat(edited.grantable()).isTrue();
        assertThat(edited.permissionCode()).isEqualTo("edit-page.query-action");
        assertThat(permissions.findByCode(edited.code())).isNotNull();
    }

    @Test void migrationMapsEveryButtonAndGrantsOnlyAdministratorNewActions() {
        assertThat(jdbc.queryForObject("select count(*) from sys_menu m left join sys_permission p on p.permission_code=m.code where m.type='BUTTON' and (m.grantable<>1 or m.permission_code is null or m.permission_code<>m.code or p.id is null)",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sys_menu m join sys_permission p on p.permission_code=m.code where m.type='BUTTON' and not exists (select 1 from sys_role_permission rp join sys_role r on r.id=rp.role_id where r.role_code='SYS_ADMIN' and rp.permission_id=p.id and rp.effect='ALLOW')",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sys_role_permission rp join sys_permission p on p.id=rp.permission_id join sys_role r on r.id=rp.role_id where p.permission_code='menu-management.menu-management-page.10' and r.role_code<>'SYS_ADMIN'",Integer.class)).isZero();
        assertThat(permissions.findByCode("menu-management.menu-management-page.10")).isNotNull();
    }

    @Test void codeChangeIsRejectedAndKeepsGrants() {
        var admin=principal("SYS_ADMIN");
        var p=service.create(admin,page("TEST_RENAME_PAGE"));
        var btn=service.create(admin,new MenuWriteCommand("TEST_RENAME_BTN","权限按钮",MenuType.BUTTON,p.id(),null,null,true,0,MenuStatus.ENABLED,null));
        var roleId=roles.findByCode("SYS_AUDITOR").id();
        var permissionId=permissions.findByCode("TEST_RENAME_BTN").id();
        jdbc.update("insert into sys_role_permission (id,role_id,permission_id,effect) values (?,?,?,'ALLOW')",ids.nextId(),roleId,permissionId);
        assertThatThrownBy(()->service.update(admin,btn.id(),new MenuWriteCommand("TEST_RENAMED_BTN","权限按钮",MenuType.BUTTON,btn.parentId(),null,null,true,0,MenuStatus.ENABLED,btn.version())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(permissions.findByCode("TEST_RENAMED_BTN")).isNull();
        assertThat(permissions.findByCode("TEST_RENAME_BTN").id()).isEqualTo(permissionId);
        assertThat(jdbc.queryForObject("select count(*) from sys_role_permission where permission_id=?",Integer.class,permissionId)).isEqualTo(1);
    }

    @Test void disablingPageDisablesDerivedPermission() {
        var admin=principal("SYS_ADMIN");
        var p=service.create(admin,page("TEST_DISABLE_PAGE"));
        assertThat(permissions.findByCode("TEST_DISABLE_PAGE").status()).isEqualTo(PermissionStatus.ENABLED);
        service.update(admin,p.id(),new MenuWriteCommand("TEST_DISABLE_PAGE","页面",MenuType.PAGE,p.parentId(),p.route(),null,false,0,MenuStatus.DISABLED,p.version()));
        assertThat(permissions.findByCode("TEST_DISABLE_PAGE").status()).isEqualTo(PermissionStatus.DISABLED);
    }

    @Test void batchCreatesButtonsAtomicallyAndReportsConflicts() {
        var admin=principal("SYS_ADMIN");
        var p=service.create(admin,page("TEST_BATCH_PAGE"));
        var created=service.createButtons(admin,p.id(),List.of(
                new MenuButtonBatchCommand("TEST_BATCH_BTN_A","按钮A",true,null,0,MenuStatus.ENABLED),
                new MenuButtonBatchCommand("batch-page.ui-btn","按钮B",false,null,10,MenuStatus.ENABLED),
                new MenuButtonBatchCommand("TEST_BATCH_BTN_C","按钮C",true,null,20,MenuStatus.ENABLED)));
        assertThat(created).hasSize(3);
        assertThat(permissions.findByCode("TEST_BATCH_BTN_A")).isNotNull();
        assertThat(permissions.findByCode("batch-page.ui-btn")).isNotNull();
        assertThatThrownBy(()->service.createButtons(admin,p.id(),List.of(
                new MenuButtonBatchCommand("TEST_BATCH_DUP","重复",true,null,0,MenuStatus.ENABLED),
                new MenuButtonBatchCommand("TEST_BATCH_DUP","重复2",true,null,10,MenuStatus.ENABLED))))
                .isInstanceOf(MenuBatchConflictException.class)
                .hasMessageContaining("TEST_BATCH_DUP");
        assertThatThrownBy(()->service.createButtons(admin,p.id(),List.of(
                new MenuButtonBatchCommand("TEST_BATCH_PAGE","撞页面编码",true,null,0,MenuStatus.ENABLED))))
                .isInstanceOf(MenuBatchConflictException.class)
                .hasMessageContaining("TEST_BATCH_PAGE");
        assertThat(service.list(admin).stream().filter(n->"TEST_BATCH_DUP".equals(n.code())).findAny()).isEmpty();
        assertThat(service.list(admin).stream().filter(n->"TEST_BATCH_BTN_A".equals(n.code())).count()).isEqualTo(1);
    }

    @Test void batchUpdatesButtonsAtomically() {
        var admin=principal("SYS_ADMIN");
        var p=service.create(admin,page("TEST_BATCH_UPD_PAGE"));
        var created=service.createButtons(admin,p.id(),List.of(
                new MenuButtonBatchCommand("TEST_BUP_A","按钮A",true,null,0,MenuStatus.ENABLED),
                new MenuButtonBatchCommand("TEST_BUP_B","按钮B",true,null,10,MenuStatus.ENABLED)));
        var updated=service.updateButtons(admin,List.of(
                new MenuButtonBatchUpdateCommand(created.get(0).id(),created.get(0).version(),"TEST_BUP_A","按钮A改",false,null,0,MenuStatus.ENABLED),
                new MenuButtonBatchUpdateCommand(created.get(1).id(),created.get(1).version(),"TEST_BUP_B","按钮B改",false,null,10,MenuStatus.DISABLED)));
        assertThat(updated.get(0).name()).isEqualTo("按钮A改");
        assertThat(updated).allMatch(n->n.grantable() && n.code().equals(n.permissionCode()));
        assertThat(permissions.findByCode("TEST_BUP_A")).isNotNull();
        assertThat(updated.get(1).status()).isEqualTo(MenuStatus.DISABLED);
        assertThat(permissions.findByCode("TEST_BUP_B").status()).isEqualTo(PermissionStatus.DISABLED);
        // 编码改冲突：与批内另一条新编码冲突，整批拒绝
        assertThatThrownBy(()->service.updateButtons(admin,List.of(
                new MenuButtonBatchUpdateCommand(created.get(0).id(),updated.get(0).version(),"TEST_BUP_CLASH","按钮A",true,null,0,MenuStatus.ENABLED),
                new MenuButtonBatchUpdateCommand(created.get(1).id(),updated.get(1).version(),"TEST_BUP_CLASH","按钮B",true,null,10,MenuStatus.ENABLED))))
                .isInstanceOf(MenuBatchConflictException.class)
                .hasMessageContaining("不可修改");
    }

    @Test void movesNodeAcrossLevelsAndRejectsOwnDescendant() {
        var admin=principal("SYS_ADMIN");
        var rootA=service.create(admin,directory("move-root-a"));
        var rootC=service.create(admin,directory("move-root-c"));
        var p=service.create(admin,new MenuWriteCommand("TEST_MOVE_PAGE","页面",MenuType.PAGE,rootA.id(),availableRoute(),null,false,0,MenuStatus.ENABLED,null));
        var moved=service.move(admin,p.id(),new MenuPositionCommand(rootC.id(),0,p.version()));
        assertThat(moved.parentId()).isEqualTo(rootC.id());
        Integer audits=jdbc.queryForObject(
                "select count(*) from sys_iam_change_audit where event_type='MENU_REORDER' and target_type='MENU' and target_id=?",
                Integer.class,p.id());
        assertThat(audits).isGreaterThanOrEqualTo(1);
        // 拖入自身子孙被拒绝
        assertThatThrownBy(()->service.move(admin,rootA.id(),new MenuPositionCommand(p.id(),0,rootA.version())))
                .isInstanceOf(IllegalArgumentException.class);
        // 按钮不可拖出页面
        var btn=service.create(admin,new MenuWriteCommand("TEST_MOVE_BTN","按钮",MenuType.BUTTON,p.id(),null,null,true,0,MenuStatus.ENABLED,null));
        assertThatThrownBy(()->service.move(admin,btn.id(),new MenuPositionCommand(rootA.id(),0,btn.version())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
