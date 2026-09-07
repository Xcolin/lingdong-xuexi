package com.lingdong.learning.organization.application;

import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationEffectiveStatus;
import com.lingdong.learning.organization.domain.OrganizationStatus;
import com.lingdong.learning.organization.domain.OrganizationType;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OrganizationApplicationServiceTest {
    private static final long OPERATOR_ID = 8800000000000000101L;

    @Autowired
    private OrganizationApplicationService organizationApplicationService;

    @Autowired
    private OrganizationMapper organizationMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpOperator() {
        jdbcTemplate.update("""
                MERGE INTO sys_user (
                    id, username, display_name, user_type, status
                ) KEY (id) VALUES (?, 'organization_update_operator', '组织编辑测试员', 'PLATFORM', 'ENABLED')
                """, OPERATOR_ID);
    }

    @Test
    void createsCustomOrganizationType() {
        OrganizationType organizationType = organizationApplicationService.createOrganizationType(
                new CreateOrganizationTypeCommand("COMMUNITY", "社区", 100)
        );

        assertThat(organizationType.code()).isEqualTo("COMMUNITY");
        assertThat(Long.toString(organizationType.id())).hasSize(19);
        assertThat(organizationType.builtIn()).isFalse();
    }

    @Test
    void createsRegionalSchoolTreeWithMaterializedCodePath() {
        Organization region = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("REGION_NORTH", "北城区", "REGION", null, 10)
        );
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("SCHOOL_NORTH_1", "北城第一小学", "SCHOOL", region.id(), 10)
        );

        Organization storedSchool = organizationMapper.findByCode("SCHOOL_NORTH_1");

        assertThat(storedSchool.parentId()).isEqualTo(region.id());
        assertThat(Long.toString(region.id())).hasSize(19);
        assertThat(Long.toString(storedSchool.id())).hasSize(19);
        assertThat(storedSchool.path()).isEqualTo("/REGION_NORTH/SCHOOL_NORTH_1/");
        assertThat(storedSchool.typeCode()).isEqualTo("SCHOOL");
    }

    @Test
    void rejectsDuplicateOrganizationNameWithinTheSameParent() {
        organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("REGION_REPEAT_A", "重复区域", "REGION", null, 10)
        );

        assertThatThrownBy(() -> organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("REGION_REPEAT_B", "重复区域", "REGION", null, 20)
        )).isInstanceOf(DuplicateOrganizationNameException.class);
    }

    @Test
    void reportsMissingOrganizationTypeAsAResourceNotFoundError() {
        assertThatThrownBy(() -> organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("REGION_UNKNOWN_TYPE", "未知类型区域", "UNKNOWN_TYPE", null, 10)
        )).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("组织类型不存在");
    }

    @Test
    void reportsMissingParentOrganizationAsAResourceNotFoundError() {
        assertThatThrownBy(() -> organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("SCHOOL_UNKNOWN_PARENT", "未知上级学校", "SCHOOL", 1_000_000_000_000_000_000L, 10)
        )).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("父级组织不存在");
    }

    @Test
    void updatesOnlyOrganizationNameAndSortOrderWithOptimisticVersion() {
        Organization organization = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("REGION_UPDATE_A", "待编辑区域", "REGION", null, 10)
        );

        Organization updated = organizationApplicationService.updateOrganization(
                OPERATOR_ID,
                new UpdateOrganizationCommand(organization.id(), "编辑后区域", 30, organization.versionNo())
        );

        assertThat(updated.name()).isEqualTo("编辑后区域");
        assertThat(updated.sortOrder()).isEqualTo(30);
        assertThat(updated.versionNo()).isEqualTo(2);
        assertThat(updated.code()).isEqualTo("REGION_UPDATE_A");
        assertThat(updated.typeCode()).isEqualTo("REGION");
        assertThat(updated.parentId()).isNull();
        Integer auditCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_organization_change_audit
                WHERE organization_id = ? AND event_type = 'DIRECT_UPDATE'
                  AND operator_user_id = ?
                """, Integer.class, organization.id(), OPERATOR_ID);
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateSiblingNameAndStaleOrganizationVersion() {
        Organization first = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("REGION_UPDATE_B", "更新重名区域甲", "REGION", null, 10)
        );
        Organization second = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("REGION_UPDATE_C", "更新重名区域乙", "REGION", null, 20)
        );

        assertThatThrownBy(() -> organizationApplicationService.updateOrganization(
                OPERATOR_ID,
                new UpdateOrganizationCommand(second.id(), first.name(), 20, second.versionNo())
        )).isInstanceOf(DuplicateOrganizationNameException.class);

        assertThatThrownBy(() -> organizationApplicationService.updateOrganization(
                OPERATOR_ID,
                new UpdateOrganizationCommand(second.id(), "不会生效的名称", 20, second.versionNo() + 1)
        )).isInstanceOf(OrganizationVersionConflictException.class)
                .hasMessageContaining("组织数据已变化");
    }

    @Test
    void enablesOrganizationAndRecalculatesEnabledDescendants() {
        Organization region = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("REGION_ENABLE_A", "待启用区域", "REGION", null, 10)
        );
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("SCHOOL_ENABLE_A", "待恢复学校", "SCHOOL", region.id(), 10)
        );
        jdbcTemplate.update("""
                UPDATE sys_organization
                SET status = 'DISABLED', effective_status = 'DISABLED'
                WHERE id = ?
                """, region.id());
        jdbcTemplate.update("""
                UPDATE sys_organization
                SET effective_status = 'DISABLED'
                WHERE id = ?
                """, school.id());

        Organization enabled = organizationApplicationService.enableOrganization(
                OPERATOR_ID, region.id(), region.versionNo());

        Organization enabledSchool = organizationMapper.findById(school.id());
        assertThat(enabled.status()).isEqualTo(OrganizationStatus.ENABLED);
        assertThat(enabled.effectiveStatus()).isEqualTo(OrganizationEffectiveStatus.ENABLED);
        assertThat(enabled.versionNo()).isEqualTo(region.versionNo() + 1);
        assertThat(enabledSchool.status()).isEqualTo(OrganizationStatus.ENABLED);
        assertThat(enabledSchool.effectiveStatus()).isEqualTo(OrganizationEffectiveStatus.ENABLED);
        assertThat(enabledSchool.versionNo()).isEqualTo(school.versionNo() + 1);
    }

    @Test
    void preservesChildOwnDisabledStatusWhenParentIsEnabled() {
        Organization region = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("REGION_ENABLE_B", "父级待启用区域", "REGION", null, 10)
        );
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("SCHOOL_ENABLE_B", "自身停用学校", "SCHOOL", region.id(), 10)
        );
        Organization clazz = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("CLASS_ENABLE_B", "自身停用学校下班级", "CLASS", school.id(), 10)
        );
        jdbcTemplate.update("""
                UPDATE sys_organization
                SET status = CASE WHEN id = ? THEN 'DISABLED' ELSE status END,
                    effective_status = 'DISABLED'
                WHERE organization_path LIKE ?
                """, school.id(), region.path() + "%");

        organizationApplicationService.enableOrganization(OPERATOR_ID, region.id(), region.versionNo());

        Organization storedSchool = organizationMapper.findById(school.id());
        Organization storedClass = organizationMapper.findById(clazz.id());
        assertThat(storedSchool.status()).isEqualTo(OrganizationStatus.DISABLED);
        assertThat(storedSchool.effectiveStatus()).isEqualTo(OrganizationEffectiveStatus.DISABLED);
        assertThat(storedClass.status()).isEqualTo(OrganizationStatus.ENABLED);
        assertThat(storedClass.effectiveStatus()).isEqualTo(OrganizationEffectiveStatus.DISABLED);
    }

    @Test
    void rejectsStaleEnableVersionAndNonOperationalOrganization() {
        Organization region = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("REGION_ENABLE_C", "版本冲突区域", "REGION", null, 10)
        );
        jdbcTemplate.update("""
                UPDATE sys_organization
                SET status = 'DISABLED', effective_status = 'DISABLED'
                WHERE id = ?
                """, region.id());

        assertThatThrownBy(() -> organizationApplicationService.enableOrganization(
                OPERATOR_ID, region.id(), region.versionNo() + 1
        )).isInstanceOf(OrganizationVersionConflictException.class);

        assertThatThrownBy(() -> organizationApplicationService.requireOperational(region.id()))
                .isInstanceOf(OrganizationNotOperationalException.class)
                .hasMessageContaining("不可开展新业务");
    }
}
