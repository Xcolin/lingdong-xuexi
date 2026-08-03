package com.lingdong.learning.organization.application;

import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationType;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OrganizationApplicationServiceTest {
    @Autowired
    private OrganizationApplicationService organizationApplicationService;

    @Autowired
    private OrganizationMapper organizationMapper;

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
}
