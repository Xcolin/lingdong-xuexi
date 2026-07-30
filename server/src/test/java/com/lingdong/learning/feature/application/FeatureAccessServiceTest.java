package com.lingdong.learning.feature.application;

import com.lingdong.learning.organization.application.CreateOrganizationCommand;
import com.lingdong.learning.organization.application.OrganizationApplicationService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.feature.domain.FeatureStatus;
import com.lingdong.learning.feature.domain.FeatureToggle;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class FeatureAccessServiceTest {
    @Autowired
    private FeatureAccessService featureAccessService;

    @Autowired
    private OrganizationApplicationService organizationApplicationService;

    @Autowired
    private FeatureToggleMapper featureToggleMapper;

    @Test
    void keepsGeographyAttendanceDisabledByDefault() {
        assertThat(featureAccessService.isEnabled("GEO_ATTENDANCE", null)).isFalse();
        assertThatThrownBy(() -> featureAccessService.requireEnabled("GEO_ATTENDANCE", null))
                .isInstanceOf(FeatureDisabledException.class);
    }

    @Test
    void letsGlobalDisableOverrideOrganizationEnablement() {
        Organization organization = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("REGION_FEATURE", "功能区域", "REGION", null, 10)
        );
        featureToggleMapper.insert(FeatureToggle.organizationOverride(
                "GEO_ATTENDANCE", "地理位置考勤", organization.id(), FeatureStatus.ENABLED
        ));

        assertThat(featureAccessService.isEnabled("GEO_ATTENDANCE", organization.id())).isFalse();
    }
}
