package com.lingdong.learning.feature.application;

import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.feature.domain.FeatureStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FeatureToggleChangeServiceTest {
    @Autowired private FeatureToggleChangeService featureToggleChangeService;
    @Autowired private FeatureAccessService featureAccessService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;

    @Test
    void appliesGlobalToggleOnlyAfterSystemAuditorApproves() {
        User administrator = createUserWithRole("feature_admin", "开关管理员", "SYS_ADMIN");
        User auditor = createUserWithRole("feature_auditor", "开关审核员", "SYS_AUDITOR");

        FeatureToggleChange change = featureToggleChangeService.createDraft(new CreateGlobalFeatureToggleChangeCommand(
                administrator.id(), "GEO_ATTENDANCE", FeatureStatus.ENABLED, "启用地理考勤", "完成合规审核后启用"
        ));
        featureToggleChangeService.submit(change.taskId(), administrator.id());
        assertThat(featureAccessService.isEnabled("GEO_ATTENDANCE", null)).isFalse();

        featureToggleChangeService.approveAndApply(change.taskId(), auditor.id(), "同意启用");

        assertThat(featureAccessService.isEnabled("GEO_ATTENDANCE", null)).isTrue();
    }

    private User createUserWithRole(String username, String displayName, String roleCode) {
        User user = userAccessApplicationService.createUser(new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }
}
