package com.lingdong.learning.organization.application;

import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.organization.web.OrganizationTreeNodeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 组织节点行政区划字段的保存、回显与字典校验。 */
@SpringBootTest
@ActiveProfiles("test")
class OrganizationAdminDivisionTest {
    private static final long OPERATOR_ID = 8800000000000000777L;

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
                ) KEY (id) VALUES (?, 'admin_division_operator', '区划编辑测试员', 'PLATFORM', 'ENABLED')
                """, OPERATOR_ID);
    }

    @Test
    void savesAndEchoesAdminDivisionCodeAcrossCreateEditAndTree() {
        Organization created = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("REGION_DIV_A", "行政区划区域", "REGION", null, 10, "500101"));
        assertThat(created.adminDivisionCode()).isEqualTo("500101");
        assertThat(organizationMapper.findByCode("REGION_DIV_A").adminDivisionCode()).isEqualTo("500101");
        assertThat(OrganizationTreeNodeResponse.from(created).adminDivisionCode()).isEqualTo("500101");

        Organization updated = organizationApplicationService.updateOrganization(
                OPERATOR_ID,
                new UpdateOrganizationCommand(created.id(), "行政区划区域", 10, created.versionNo(), "500102"));
        assertThat(updated.adminDivisionCode()).isEqualTo("500102");
        assertThat(updated.versionNo()).isEqualTo(2);

        Organization kept = organizationApplicationService.updateOrganization(
                OPERATOR_ID,
                new UpdateOrganizationCommand(updated.id(), "行政区划区域", 10, updated.versionNo(), null));
        assertThat(kept.adminDivisionCode()).isEqualTo("500102");
    }

    @Test
    void rejectsUnknownAdminDivisionCodeOnCreateAndEdit() {
        assertThatThrownBy(() -> organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("REGION_DIV_BAD", "无效区划区域", "REGION", null, 10, "999999")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("行政区划");

        Organization organization = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("REGION_DIV_B", "区划编辑区域", "REGION", null, 10, "500101"));
        assertThatThrownBy(() -> organizationApplicationService.updateOrganization(
                OPERATOR_ID,
                new UpdateOrganizationCommand(organization.id(), "区划编辑区域", 10, organization.versionNo(), "999999")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("行政区划");
    }

    @Test
    void allowsCreatingOrganizationWithoutAdminDivisionCode() {
        Organization created = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("REGION_DIV_NULL", "无区划区域", "REGION", null, 10, null));
        assertThat(created.adminDivisionCode()).isNull();
    }
}
