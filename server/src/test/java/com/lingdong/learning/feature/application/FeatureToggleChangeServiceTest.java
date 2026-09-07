package com.lingdong.learning.feature.application;

import com.lingdong.learning.auth.application.AuthenticatedSession;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.OrganizationPasswordLoginCommand;
import com.lingdong.learning.auth.application.PasswordLoginCommand;
import com.lingdong.learning.auth.domain.DeviceSessionStatus;
import com.lingdong.learning.auth.infrastructure.persistence.DeviceSessionMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.application.CreateOrganizationCommand;
import com.lingdong.learning.organization.application.OrganizationApplicationService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.feature.domain.FeatureStatus;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FeatureToggleChangeServiceTest {
    @Autowired private FeatureToggleChangeService featureToggleChangeService;
    @Autowired private FeatureAccessService featureAccessService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private OrganizationApplicationService organizationApplicationService;
    @Autowired private OrganizationAdminMapper organizationAdminMapper;
    @Autowired private DeviceSessionMapper deviceSessionMapper;
    @Autowired private FeatureToggleMapper featureToggleMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private RoleMapper roleMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private IdGenerator idGenerator;

    @Test
    void appliesGlobalToggleOnlyAfterSystemAuditorApproves() {
        User administrator = createUserWithRole("feature_admin", "开关管理员", "SYS_ADMIN");
        User auditor = createUserWithRole("feature_auditor", "开关审核员", "SYS_AUDITOR");

        FeatureToggleChange change = featureToggleChangeService.createDraft(new CreateGlobalFeatureToggleChangeCommand(
                administrator.id(), "GEO_ATTENDANCE", FeatureStatus.ENABLED, "启用地理考勤", "完成合规审核后启用"
        ));
        assertThat(Long.toString(change.id())).hasSize(19);
        featureToggleChangeService.submit(change.taskId(), administrator.id());
        assertThat(featureAccessService.isEnabled("GEO_ATTENDANCE", null)).isFalse();

        featureToggleChangeService.approveAndApply(change.taskId(), auditor.id(), "同意启用");

        assertThat(featureAccessService.isEnabled("GEO_ATTENDANCE", null)).isTrue();
    }

    @Test
    void revokesOnlyOrganizationMiniappSessionsWhenTheFeatureIsDisabled() {
        User administrator = createUserWithRole("feature_org_admin", "机构开关管理员", "SYS_ADMIN");
        User auditor = createUserWithRole("feature_org_auditor", "机构开关审核员", "SYS_AUDITOR");
        User organizationUser = createUserWithRole(
                "feature_org_session", "机构会话用户", "ORG_ADMIN", UserType.ORGANIZATION);
        setPassword(organizationUser, "ValidPass123!");
        Organization school = organizationApplicationService.createOrganization(new CreateOrganizationCommand(
                "FEATURE_ORG_SESSION", "机构会话学校", "SCHOOL", null, 10));
        assertThat(organizationAdminMapper.insert(
                idGenerator.nextId(), organizationUser.id(), school.id())).isEqualTo(1);
        AuthenticatedSession miniappSession = authenticationApplicationService.loginOrganizationByPassword(
                new OrganizationPasswordLoginCommand(
                        organizationUser.username(), "ValidPass123!", "feature-org-mini", "机构小程序"));
        AuthenticatedSession webSession = authenticationApplicationService.loginByPassword(
                new PasswordLoginCommand(
                        organizationUser.username(), "ValidPass123!", "feature-org-web", "机构浏览器"));

        FeatureToggleChange change = featureToggleChangeService.createDraft(new CreateGlobalFeatureToggleChangeCommand(
                administrator.id(), "ORGANIZATION_MINIAPP_AUTH", FeatureStatus.DISABLED,
                "停用机构小程序认证", "撤销机构小程序会话"));
        featureToggleChangeService.submit(change.taskId(), administrator.id());
        try {
            featureToggleChangeService.approveAndApply(change.taskId(), auditor.id(), "同意停用");

            assertThat(deviceSessionMapper.findById(miniappSession.sessionId()).status())
                    .isEqualTo(DeviceSessionStatus.REVOKED);
            assertThat(deviceSessionMapper.findById(webSession.sessionId()).status())
                    .isEqualTo(DeviceSessionStatus.ACTIVE);
        } finally {
            featureToggleMapper.updateGlobalStatus("ORGANIZATION_MINIAPP_AUTH", FeatureStatus.ENABLED);
        }
    }

    private User createUserWithRole(String username, String displayName, String roleCode) {
        return createUserWithRole(username, displayName, roleCode, UserType.PLATFORM);
    }

    private User createUserWithRole(String username, String displayName, String roleCode, UserType userType) {
        User user = userAccessApplicationService.createUser(
                new CreateUserCommand(username, displayName, null, userType));
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }

    private void setPassword(User user, String password) {
        assertThat(userMapper.updatePasswordHash(user.id(), passwordEncoder.encode(password))).isEqualTo(1);
    }
}
