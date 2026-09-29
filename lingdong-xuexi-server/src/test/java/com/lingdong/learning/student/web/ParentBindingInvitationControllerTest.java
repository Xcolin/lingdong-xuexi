package com.lingdong.learning.student.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ParentBindingInvitationControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private OrganizationApplicationService organizationApplicationService;
    @Autowired private com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper organizationAdminMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void createsAndRespondsToInstitutionParentInvitationsWithinTheStudentOrganizationScope() throws Exception {
        User systemAdministrator = createUserWithRole("invite_scope_sys_admin", "邀请系统管理员", "SYS_ADMIN", null);
        User eastOrganizationAdministrator = createUser("invite_scope_east_org_admin", "东区机构管理员");
        User westOrganizationAdministrator = createUser("invite_scope_west_org_admin", "西区机构管理员");
        User parent = createUserWithRole("invite_scope_parent", "邀请家长", "PARENT", null);
        User ordinaryUser = createUser("invite_scope_ordinary", "邀请普通用户");
        Organization eastSchool = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("INVITE_SCOPE_EAST", "邀请东区学校", "SCHOOL", null, 10)
        );
        Organization westSchool = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("INVITE_SCOPE_WEST", "邀请西区学校", "SCHOOL", null, 20)
        );
        configureOrganizationAdministrator(eastOrganizationAdministrator, eastSchool);
        configureOrganizationAdministrator(westOrganizationAdministrator, westSchool);
        setPassword(systemAdministrator, systemAdministrator, eastOrganizationAdministrator, westOrganizationAdministrator, parent, ordinaryUser);

        Long studentId = createInstitutionStudent(eastOrganizationAdministrator, eastSchool, "邀请学生甲");
        String eastOrganizationAdministratorToken = loginAccessToken("invite_scope_east_org_admin");
        String parentToken = loginAccessToken("invite_scope_parent");

        MvcResult invitationResult = mockMvc.perform(post("/api/v1/students/{studentId}/parent-invitations", studentId)
                        .header("Authorization", "Bearer " + eastOrganizationAdministratorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"%s\"}".formatted(eastSchool.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.studentId").value(studentId.toString()))
                .andExpect(jsonPath("$.organizationId").value(eastSchool.id().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.acceptToken").isString())
                .andReturn();
        JsonNode invitationResponse = objectMapper.readTree(invitationResult.getResponse().getContentAsString());
        Long invitationId = invitationResponse.path("id").asLong();
        String acceptToken = invitationResponse.path("acceptToken").asText();
        String tokenHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM edu_parent_binding_invitation WHERE id = ?", String.class, invitationId
        );
        assertThat(tokenHash).hasSize(64).isNotEqualTo(acceptToken);

        mockMvc.perform(post("/api/v1/parent-invitations/{invitationId}/accept", invitationId)
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptToken\":\"错误令牌\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/students/{studentId}/parent-invitations", studentId)
                        .header("Authorization", "Bearer " + eastOrganizationAdministratorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"%s\"}".formatted(eastSchool.id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        mockMvc.perform(post("/api/v1/students/{studentId}/parent-invitations", studentId)
                        .header("Authorization", "Bearer " + loginAccessToken("invite_scope_west_org_admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"%s\"}".formatted(westSchool.id())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/parent-invitations/{invitationId}/accept", invitationId)
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptToken\":\"%s\"}".formatted(acceptToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/students/{studentId}", studentId)
                        .header("Authorization", "Bearer " + parentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(studentId.toString()));

        mockMvc.perform(post("/api/v1/students/{studentId}/parent-invitations", studentId)
                        .header("Authorization", "Bearer " + eastOrganizationAdministratorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"%s\"}".formatted(eastSchool.id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        Long rejectedStudentId = createInstitutionStudent(eastOrganizationAdministrator, eastSchool, "邀请学生乙");
        MvcResult rejectedInvitationResult = mockMvc.perform(post("/api/v1/students/{studentId}/parent-invitations", rejectedStudentId)
                        .header("Authorization", "Bearer " + eastOrganizationAdministratorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"%s\"}".formatted(eastSchool.id())))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode rejectedInvitation = objectMapper.readTree(rejectedInvitationResult.getResponse().getContentAsString());

        mockMvc.perform(post("/api/v1/parent-invitations/{invitationId}/reject", rejectedInvitation.path("id").asLong())
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptToken\":\"%s\"}".formatted(rejectedInvitation.path("acceptToken").asText())))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/students/{studentId}/parent-invitations", rejectedStudentId)
                        .header("Authorization", "Bearer " + eastOrganizationAdministratorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"%s\"}".formatted(eastSchool.id())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/students/{studentId}/parent-invitations", rejectedStudentId)
                        .header("Authorization", "Bearer " + loginAccessToken("invite_scope_ordinary"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"%s\"}".formatted(eastSchool.id())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void expiresPendingInvitationBeforeAllowingTheOrganizationToReissueIt() throws Exception {
        User systemAdministrator = createUserWithRole("invite_expire_sys_admin", "过期系统管理员", "SYS_ADMIN", null);
        User organizationAdministrator = createUser("invite_expire_org_admin", "过期机构管理员");
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("INVITE_EXPIRE_SCHOOL", "过期邀请学校", "SCHOOL", null, 30)
        );
        configureOrganizationAdministrator(organizationAdministrator, school);
        setPassword(systemAdministrator, systemAdministrator, organizationAdministrator);
        Long studentId = createInstitutionStudent(organizationAdministrator, school, "过期邀请学生");
        String organizationAdministratorToken = loginAccessToken(organizationAdministrator.username());

        MvcResult invitationResult = mockMvc.perform(post("/api/v1/students/{studentId}/parent-invitations", studentId)
                        .header("Authorization", "Bearer " + organizationAdministratorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"%s\"}".formatted(school.id())))
                .andExpect(status().isCreated())
                .andReturn();
        Long expiredInvitationId = objectMapper.readTree(invitationResult.getResponse().getContentAsString()).path("id").asLong();
        jdbcTemplate.update(
                "UPDATE edu_parent_binding_invitation SET expires_at = DATEADD('SECOND', -1, CURRENT_TIMESTAMP) WHERE id = ?",
                expiredInvitationId
        );

        mockMvc.perform(post("/api/v1/students/{studentId}/parent-invitations", studentId)
                        .header("Authorization", "Bearer " + organizationAdministratorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"%s\"}".formatted(school.id())))
                .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM edu_parent_binding_invitation WHERE id = ?", String.class, expiredInvitationId
        )).isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT pending_scope_key FROM edu_parent_binding_invitation WHERE id = ?", String.class, expiredInvitationId
        )).isEqualTo("CLOSED:" + expiredInvitationId);
    }

    private Long createInstitutionStudent(User organizationAdministrator, Organization organization, String studentName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", "Bearer " + loginAccessToken(organizationAdministrator.username()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentName\":\"%s\",\"organizationId\":\"%s\"}".formatted(studentName, organization.id())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private User createUserWithRole(String username, String displayName, String roleCode, Long organizationId) {
        User user = createUser(username, displayName);
        assignRole(user, roleCode, organizationId);
        return user;
    }

    private User createUser(String username, String displayName) {
        return userAccessApplicationService.createUser(new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
    }

    private void configureOrganizationAdministrator(User administrator, Organization organization) {
        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(administrator.id(), organization.id())
        );
        assignRole(administrator, "ORG_ADMIN", organization.id());
        organizationAdminMapper.insert(
                administrator.id() + organization.id(), administrator.id(), organization.id()
        );
    }

    private void assignRole(User user, String roleCode, Long organizationId) {
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), organizationId));
    }

    private void setPassword(User systemAdministrator, User... targetUsers) {
        for (User targetUser : targetUsers) {
            authenticationApplicationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                    systemAdministrator.id(), targetUser.id(), "Password123"
            ));
        }
    }

    private String loginAccessToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password123","deviceId":"%s-device","deviceName":"家长邀请测试浏览器"}
                                """.formatted(username, username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }
}
