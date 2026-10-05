package com.lingdong.learning.organization.application;

import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 组织拖拽直接生效：同级排序（DIRECT_REORDER）与改父级移动（DIRECT_MOVE），不经申请-审核流。 */
@SpringBootTest @ActiveProfiles("test") @Transactional
class OrganizationDirectChangeTest {
    @Autowired OrganizationChangeApplicationService changeService;
    @Autowired OrganizationApplicationService organizationApplicationService;
    @Autowired UserAccessApplicationService accounts;
    @Autowired RoleMapper roles;
    @Autowired OrganizationMapper organizations;
    @Autowired JdbcTemplate jdbc;

    private Long adminId() {
        var u=accounts.createUser(new CreateUserCommand("org_direct_"+UUID.randomUUID().toString().substring(0,10),"组织拖拽测试员",null,UserType.PLATFORM));
        accounts.assignRole(new AssignRoleToUserCommand(u.id(),roles.findByCode("SYS_ADMIN").id(),null));
        return u.id();
    }
    private Organization createRegion(String code,String name) {
        return organizationApplicationService.createOrganization(new CreateOrganizationCommand(code,name,"REGION",null,10));
    }

    @Test void reordersSiblingsDirectlyAndAuditsEachNode() {
        var admin=adminId();
        var parent=createRegion("DIRECT_REORDER_PARENT","拖拽排序父级");
        var a=organizationApplicationService.createOrganization(new CreateOrganizationCommand("DIRECT_REORDER_A","拖拽排序甲","SCHOOL",parent.id(),10));
        var b=organizationApplicationService.createOrganization(new CreateOrganizationCommand("DIRECT_REORDER_B","拖拽排序乙","SCHOOL",parent.id(),20));
        var c=organizationApplicationService.createOrganization(new CreateOrganizationCommand("DIRECT_REORDER_C","拖拽排序丙","SCHOOL",parent.id(),30));

        changeService.directReorder(new ReorderOrganizationsCommand(admin,parent.id(),List.of(
                new OrganizationOrderItem(c.id(),c.versionNo()),
                new OrganizationOrderItem(a.id(),a.versionNo()),
                new OrganizationOrderItem(b.id(),b.versionNo()))));

        assertThat(organizations.findById(c.id()).sortOrder()).isEqualTo(0);
        assertThat(organizations.findById(a.id()).sortOrder()).isEqualTo(10);
        assertThat(organizations.findById(b.id()).sortOrder()).isEqualTo(20);
        assertThat(organizations.findById(c.id()).versionNo()).isEqualTo(c.versionNo()+1);
        Integer audits=jdbc.queryForObject(
                "select count(*) from sys_organization_change_audit where organization_id=? and event_type='DIRECT_REORDER'",
                Integer.class,a.id());
        assertThat(audits).isEqualTo(1);
        // 同级兄弟集合必须完整提交，缺失或多余被拒绝
        assertThatThrownBy(()->changeService.directReorder(new ReorderOrganizationsCommand(admin,parent.id(),List.of(
                new OrganizationOrderItem(a.id(),a.versionNo())))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void movesNodeAcrossParentsDirectlyAndRebuildsSubtreePaths() {
        var admin=adminId();
        var regionA=createRegion("DIRECT_MOVE_A","拖拽移动区域甲");
        var regionB=createRegion("DIRECT_MOVE_B","拖拽移动区域乙");
        var school=organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("DIRECT_MOVE_SCHOOL","拖拽移动学校","SCHOOL",regionA.id(),10));
        var clazz=organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("DIRECT_MOVE_CLASS","拖拽移动班级","CLASS",school.id(),10));

        Organization moved=changeService.directMove(new MoveOrganizationCommand(
                admin,school.id(),regionB.id(),school.versionNo()));

        Organization storedSchool=organizations.findById(school.id());
        Organization storedClass=organizations.findById(clazz.id());
        assertThat(storedSchool.parentId()).isEqualTo(regionB.id());
        assertThat(storedSchool.parentScopeKey()).isEqualTo("PARENT:"+regionB.id());
        assertThat(storedSchool.path()).isEqualTo("/DIRECT_MOVE_B/DIRECT_MOVE_SCHOOL/");
        assertThat(storedClass.path()).isEqualTo("/DIRECT_MOVE_B/DIRECT_MOVE_SCHOOL/DIRECT_MOVE_CLASS/");
        assertThat(moved.versionNo()).isEqualTo(school.versionNo()+1);
        Integer audits=jdbc.queryForObject(
                "select count(*) from sys_organization_change_audit where organization_id=? and event_type='DIRECT_MOVE'",
                Integer.class,school.id());
        assertThat(audits).isEqualTo(1);
    }

    @Test void rejectsMoveIntoOwnDescendantStaleVersionAndSameParent() {
        var admin=adminId();
        var regionA=createRegion("DIRECT_MOVE_C","拖拽防护区域甲");
        var regionB=createRegion("DIRECT_MOVE_D","拖拽防护区域乙");
        var school=organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("DIRECT_MOVE_SCHOOL_C","拖拽防护学校","SCHOOL",regionA.id(),10));

        // 拖入自身后代被拒绝
        assertThatThrownBy(()->changeService.directMove(new MoveOrganizationCommand(
                admin,regionA.id(),school.id(),regionA.versionNo())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("自身后代");
        // 乐观锁版本过期被拒绝
        assertThatThrownBy(()->changeService.directMove(new MoveOrganizationCommand(
                admin,school.id(),regionB.id(),school.versionNo()+1)))
                .isInstanceOf(OrganizationVersionConflictException.class);
        // 同父级移动被拒绝：同级顺序应使用排序接口
        assertThatThrownBy(()->changeService.directMove(new MoveOrganizationCommand(
                admin,school.id(),regionA.id(),school.versionNo())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("排序接口");
    }
}
