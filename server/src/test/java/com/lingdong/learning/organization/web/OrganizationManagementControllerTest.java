package com.lingdong.learning.organization.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.iam.application.CreateCustomRoleCommand;
import com.lingdong.learning.iam.application.RoleApplicationService;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.permission.application.GrantRolePermissionCommand;
import com.lingdong.learning.permission.application.PermissionAdministrationService;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrganizationManagementControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private RoleApplicationService roleApplicationService;
    @Autowired private PermissionMapper permissionMapper;
    @Autowired private PermissionAdministrationService permissionAdministrationService;

    @Test
    void letsSystemAdministratorsCreateOrganizationTypesAndOrganizationTrees() throws Exception {
        User administrator = createSystemAdministrator("org_api_admin", "组织接口管理员");
        setPassword(administrator, administrator);
        String administratorAccessToken = loginAccessToken("org_api_admin");

        mockMvc.perform(get("/api/v1/organization-types")
                        .header("Authorization", "Bearer " + administratorAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isString())
                .andExpect(jsonPath("$[0].code").value("REGION"));

        mockMvc.perform(post("/api/v1/organization-types")
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"COMMUNITY_API","name":"接口测试社区","sortOrder":100}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.code").value("COMMUNITY_API"));

        MvcResult regionResult = mockMvc.perform(post("/api/v1/organizations")
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"REGION_EAST_API","name":"接口测试东部区域","typeCode":"REGION","sortOrder":10}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.parentId").doesNotExist())
                .andExpect(jsonPath("$.code").value("REGION_EAST_API"))
                .andReturn();
        Long regionId = objectMapper.readTree(regionResult.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(post("/api/v1/organizations")
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"SCHOOL_EAST_API_1","name":"接口测试东部第一小学","typeCode":"SCHOOL","parentId":"%s","sortOrder":10}
                                """.formatted(regionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.parentId").value(regionId.toString()))
                .andExpect(jsonPath("$.path").value("/REGION_EAST_API/SCHOOL_EAST_API_1/"));

        MvcResult organizationTreeResult = mockMvc.perform(get("/api/v1/organizations")
                        .header("Authorization", "Bearer " + administratorAccessToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode organizationTree = objectMapper.readTree(organizationTreeResult.getResponse().getContentAsString());
        JsonNode regionNode = findNodeByCode(organizationTree, "REGION_EAST_API");
        JsonNode schoolNode = findNodeByCode(regionNode.path("children"), "SCHOOL_EAST_API_1");
        assertThat(regionNode).isNotNull();
        assertThat(regionNode.path("id").isTextual()).isTrue();
        assertThat(schoolNode).isNotNull();
        assertThat(schoolNode.path("parentId").asText()).isEqualTo(regionId.toString());

        mockMvc.perform(post("/api/v1/organizations")
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"UNKNOWN_TYPE_ORG","name":"未知类型组织","typeCode":"UNKNOWN","sortOrder":10}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/organizations")
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"UNKNOWN_PARENT_SCHOOL","name":"未知父级学校","typeCode":"SCHOOL","parentId":"1000000000000000000","sortOrder":10}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void keepsFullOrganizationTreeAccessLimitedToSystemAdministrators() throws Exception {
        User administrator = createSystemAdministrator("org_scope_admin", "组织范围管理员");
        User ordinaryUser = createUser("org_scope_user", "组织范围普通用户");
        setPassword(administrator, administrator);
        setPassword(administrator, ordinaryUser);
        String administratorAccessToken = loginAccessToken("org_scope_admin");

        mockMvc.perform(get("/api/v1/organization-types")
                        .header("Authorization", "Bearer " + loginAccessToken("org_scope_user")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        Role treeViewerRole = roleApplicationService.createCustomRole(
                new CreateCustomRoleCommand("ORG_TREE_VIEWER", "组织树查看角色", "仅用于验证全量树专属限制", RoleDataScope.ALL)
        );
        permissionAdministrationService.grantRolePermission(new GrantRolePermissionCommand(
                administrator.id(), treeViewerRole.id(), permissionMapper.findByCode("ORG_NODE_READ").id()
        ));
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(ordinaryUser.id(), treeViewerRole.id(), null));

        mockMvc.perform(get("/api/v1/organizations")
                        .header("Authorization", "Bearer " + loginAccessToken("org_scope_user")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private User createSystemAdministrator(String username, String displayName) {
        User administrator = createUser(username, displayName);
        Role systemAdministratorRole = roleMapper.findByCode("SYS_ADMIN");
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(
                administrator.id(), systemAdministratorRole.id(), null
        ));
        return administrator;
    }

    private User createUser(String username, String displayName) {
        return userAccessApplicationService.createUser(
                new CreateUserCommand(username, displayName, null, UserType.PLATFORM)
        );
    }

    private void setPassword(User administrator, User targetUser) {
        authenticationApplicationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                administrator.id(), targetUser.id(), "Password123"
        ));
    }

    private String loginAccessToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password123","deviceId":"%s-device","deviceName":"组织管理测试浏览器"}
                                """.formatted(username, username)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = response.path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }

    private JsonNode findNodeByCode(JsonNode nodes, String code) {
        for (JsonNode node : nodes) {
            if (code.equals(node.path("code").asText())) {
                return node;
            }
        }
        return null;
    }
}
