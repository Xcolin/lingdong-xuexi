package com.lingdong.learning.organization.web;

import com.lingdong.learning.auth.application.AuthenticatedSession;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.OrganizationPasswordLoginCommand;
import com.lingdong.learning.auth.application.PasswordLoginCommand;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.application.CreateOrganizationCommand;
import com.lingdong.learning.organization.application.OrganizationApplicationService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.AssociateUserWithOrganizationCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClassManagementControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private OrganizationApplicationService organizationApplicationService;
    @Autowired private OrganizationAdminMapper organizationAdminMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private RoleMapper roleMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private IdGenerator idGenerator;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void webAdministratorManagesClassesWithStringSnowflakeIdentifiers() throws Exception {
        Fixture fixture = createFixture("class_api_web");
        String token = loginWeb(fixture.administrator(), "class-api-web");

        mockMvc.perform(get("/api/v1/classes/schools")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isString())
                .andExpect(jsonPath("$[0].id").value(fixture.school().id().toString()));

        String response = mockMvc.perform(post("/api/v1/classes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolOrganizationId":"%s","name":"接口测试一班","sortOrder":10}
                                """.formatted(fixture.school().id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern(
                        "CLS_[0-9]{19}")))
                .andReturn().getResponse().getContentAsString();
        String classId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).path("id").asText();

        mockMvc.perform(put("/api/v1/classes/{classId}", classId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"接口测试更新班","sortOrder":20,"versionNo":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("接口测试更新班"))
                .andExpect(jsonPath("$.versionNo").value(2));

        mockMvc.perform(post("/api/v1/classes/{classId}/disable", classId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionNo\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.versionNo").value(3));

        mockMvc.perform(post("/api/v1/classes/{classId}/enable", classId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionNo\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.versionNo").value(4));
    }

    @Test
    void miniappAdministratorCanListAndCreateClasses() throws Exception {
        Fixture fixture = createFixture("class_api_miniapp");
        String token = loginMiniapp(fixture.administrator(), "class-api-miniapp");

        mockMvc.perform(post("/api/v1/classes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolOrganizationId":"%s","name":"小程序班级","sortOrder":10}
                                """.formatted(fixture.school().id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(fixture.school().id().toString()));

        mockMvc.perform(get("/api/v1/classes")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("小程序班级"));
    }

    @Test
    void enforcesDynamicPermissionAndOrganizationAdministratorRole() throws Exception {
        Fixture fixture = createFixture("class_api_permission");
        String organizationToken = loginWeb(fixture.administrator(), "class-api-permission");
        jdbcTemplate.update("""
                DELETE FROM sys_role_permission
                WHERE role_id = (SELECT id FROM sys_role WHERE role_code = 'ORG_ADMIN')
                  AND permission_id = (
                    SELECT id FROM sys_permission WHERE permission_code = 'CLASS_READ'
                  )
                """);

        mockMvc.perform(get("/api/v1/classes")
                        .header("Authorization", bearer(organizationToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        User systemAdministrator = createUser("class_api_system", "班级接口系统管理员");
        assignRole(systemAdministrator, "SYS_ADMIN", null);
        String systemToken = loginWeb(systemAdministrator, "class-api-system");
        mockMvc.perform(post("/api/v1/classes")
                        .header("Authorization", bearer(systemToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolOrganizationId":"%s","name":"越权班级","sortOrder":10}
                                """.formatted(fixture.school().id())))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesClassesAcrossRegionsSchoolsAndClassNodes() throws Exception {
        Organization regionA = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("CLASS_SCOPE_REGION_A", "班级范围区域甲", "REGION", null, 1));
        Organization schoolA = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("CLASS_SCOPE_SCHOOL_A", "班级范围学校甲", "SCHOOL", regionA.id(), 1));
        Organization classA = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("CLASS_SCOPE_CLASS_A", "班级范围一班", "CLASS", schoolA.id(), 1));
        Organization regionB = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("CLASS_SCOPE_REGION_B", "班级范围区域乙", "REGION", null, 2));
        Organization schoolB = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("CLASS_SCOPE_SCHOOL_B", "班级范围学校乙", "SCHOOL", regionB.id(), 1));
        Organization classB = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("CLASS_SCOPE_CLASS_B", "班级范围二班", "CLASS", schoolB.id(), 1));

        User administrator = createUser("class_scope_admin", "跨组织范围机构管理员");
        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(administrator.id(), schoolA.id()));
        assignRole(administrator, "ORG_ADMIN", schoolA.id());
        assertThat(organizationAdminMapper.insert(
                idGenerator.nextId(), administrator.id(), schoolA.id())).isEqualTo(1);
        String token = loginWeb(administrator, "class-scope-admin");

        mockMvc.perform(get("/api/v1/classes/schools").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(schoolA.id().toString()))
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(schoolB.id())).isEmpty());
        mockMvc.perform(get("/api/v1/classes").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(classA.id().toString()))
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(classB.id())).isEmpty());

        mockMvc.perform(post("/api/v1/classes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolOrganizationId":"%s","name":"跨校越权班级","sortOrder":1}
                                """.formatted(schoolB.id())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(put("/api/v1/classes/{classId}", classB.id())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"跨班越权修改","sortOrder":2,"versionNo":1}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void invalidatesExistingSessionAuthorizationImmediatelyAfterRoleIsDisabled() throws Exception {
        Fixture fixture = createFixture("class_api_stale_role");
        String token = loginWeb(fixture.administrator(), "class-api-stale-role");

        assertThat(jdbcTemplate.update("""
                UPDATE sys_role SET status = 'DISABLED'
                WHERE role_code = 'ORG_ADMIN'
                """)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/classes").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void blocksAllClassEndpointsAndHidesBothCapabilitiesWhenFeatureDisabled() throws Exception {
        Fixture fixture = createFixture("class_api_switch");
        String token = loginWeb(fixture.administrator(), "class-api-switch");
        jdbcTemplate.update("""
                UPDATE sys_feature_toggle SET status = 'DISABLED'
                WHERE feature_code = 'CLASS_MANAGEMENT' AND scope_key = 'GLOBAL'
                """);

        mockMvc.perform(get("/api/v1/classes")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
        mockMvc.perform(get("/api/v1/public/capabilities").param("client", "WEB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classManagementEnabled").value(false));
        mockMvc.perform(get("/api/v1/public/capabilities").param("client", "MINIAPP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classManagementEnabled").value(false));
    }

    private Fixture createFixture(String suffix) {
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(
                        suffix.toUpperCase(), "班级接口学校" + suffix, "SCHOOL", null, 10));
        User administrator = createUser(suffix, "班级接口机构管理员" + suffix);
        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(administrator.id(), school.id()));
        assignRole(administrator, "ORG_ADMIN", school.id());
        assertThat(organizationAdminMapper.insert(
                idGenerator.nextId(), administrator.id(), school.id())).isEqualTo(1);
        return new Fixture(administrator, school);
    }

    private User createUser(String username, String displayName) {
        User user = userAccessApplicationService.createUser(
                new CreateUserCommand(username, displayName, null, UserType.ORGANIZATION));
        assertThat(userMapper.updatePasswordHash(
                user.id(), passwordEncoder.encode("ValidPass123!"))).isEqualTo(1);
        return userMapper.findById(user.id());
    }

    private void assignRole(User user, String roleCode, Long organizationId) {
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(
                new AssignRoleToUserCommand(user.id(), role.id(), organizationId));
    }

    private String loginWeb(User user, String deviceId) {
        AuthenticatedSession session = authenticationApplicationService.loginByPassword(
                new PasswordLoginCommand(
                        user.username(), "ValidPass123!", deviceId, "班级接口浏览器"));
        return session.accessToken();
    }

    private String loginMiniapp(User user, String deviceId) {
        AuthenticatedSession session = authenticationApplicationService.loginOrganizationByPassword(
                new OrganizationPasswordLoginCommand(
                        user.username(), "ValidPass123!", deviceId, "班级接口小程序"));
        return session.accessToken();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Fixture(User administrator, Organization school) {
    }
}
