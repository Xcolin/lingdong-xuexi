package com.lingdong.learning.iam.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.datascope.infrastructure.persistence.RoleDataScopeMapper;
import com.lingdong.learning.iam.application.CreateCustomRoleCommand;
import com.lingdong.learning.iam.application.RoleApplicationService;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.application.CreateOrganizationCommand;
import com.lingdong.learning.organization.application.OrganizationApplicationService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IamManagementControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private PermissionMapper permissionMapper;
    @Autowired private OrganizationApplicationService organizationApplicationService;
    @Autowired private RoleApplicationService roleApplicationService;
    @Autowired private RoleDataScopeMapper roleDataScopeMapper;
    @Autowired private OrganizationAdminMapper organizationAdminMapper;

    @Test
    void appliesPermissionDecisionBeforeAccessingUserManagementEndpoints() throws Exception {
        User administrator = createUserWithRole("iam_api_admin", "IAM 接口管理员", "SYS_ADMIN");
        User targetUser = createUser("iam_api_target", "IAM 查询目标");
        User ordinaryUser = createUser("iam_api_ordinary", "IAM 普通用户");
        setPassword(administrator, administrator);
        setPassword(administrator, ordinaryUser);

        mockMvc.perform(get("/api/v1/users/{id}", targetUser.id())
                        .header("Authorization", "Bearer " + loginAccessToken("iam_api_admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.id").value(targetUser.id().toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(get("/api/v1/users/{id}", targetUser.id())
                        .header("Authorization", "Bearer " + loginAccessToken("iam_api_ordinary")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        mockMvc.perform(get("/api/v1/users/{id}", "1000000000000000000")
                        .header("Authorization", "Bearer " + loginAccessToken("iam_api_admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void createsPlatformUsersAndAppliesOrganizationScopedRoleRules() throws Exception {
        User administrator = createUserWithRole("iam_user_admin", "用户管理管理员", "SYS_ADMIN");
        setPassword(administrator, administrator);
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("IAM_API_SCHOOL", "IAM 接口学校", "SCHOOL", null, 1)
        );
        String administratorAccessToken = loginAccessToken("iam_user_admin");

        MvcResult createdUserResult = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"iam_created_user","displayName":"新建平台用户","mobile":"13900000001","type":"PLATFORM"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.username").value("iam_created_user"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        Long createdUserId = objectMapper.readTree(createdUserResult.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(post("/api/v1/users/{id}/organizations", createdUserId)
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"%s\"}".formatted(school.id())))
                .andExpect(status().isNoContent());

        Role organizationAdministratorRole = roleMapper.findByCode("ORG_ADMIN");
        mockMvc.perform(post("/api/v1/users/{id}/roles", createdUserId)
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\",\"organizationId\":\"%s\"}"
                                .formatted(organizationAdministratorRole.id(), school.id())))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/users/{id}/password", createdUserId)
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Password456\"}"))
                .andExpect(status().isNoContent());
        loginAccessToken("iam_created_user", "Password456");

        User unassociatedUser = userAccessApplicationService.createUser(
                new CreateUserCommand("iam_unassociated_user", "未关联用户", null, UserType.PLATFORM)
        );
        mockMvc.perform(post("/api/v1/users/{id}/roles", unassociatedUser.id())
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\",\"organizationId\":\"%s\"}"
                                .formatted(organizationAdministratorRole.id(), school.id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
    }

    @Test
    void listsUsersWithFiltersAndMasksMobile() throws Exception {
        User administrator = createUserWithRole("iam_directory_admin", "用户目录管理员", "SYS_ADMIN");
        userAccessApplicationService.createUser(
                new CreateUserCommand("directory_teacher", "目录张老师", "13800138000", UserType.ORGANIZATION)
        );
        userAccessApplicationService.createUser(
                new CreateUserCommand("directory_parent", "目录李家长", "13900139000", UserType.FAMILY)
        );
        setPassword(administrator, administrator);

        mockMvc.perform(get("/api/v1/users")
                        .param("keyword", "张老师")
                        .param("type", "ORGANIZATION")
                        .param("status", "ENABLED")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .header("Authorization", "Bearer " + loginAccessToken("iam_directory_admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").isString())
                .andExpect(jsonPath("$.items[0].username").value("directory_teacher"))
                .andExpect(jsonPath("$.items[0].mobile").value("138****8000"));
    }

    @Test
    void deniesUserDirectoryWithoutListPermission() throws Exception {
        User administrator = createUserWithRole("iam_directory_permission_admin", "用户目录授权管理员", "SYS_ADMIN");
        User ordinaryUser = createUser("iam_directory_ordinary", "用户目录普通用户");
        setPassword(administrator, ordinaryUser);

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + loginAccessToken("iam_directory_ordinary")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void revokesActiveSessionsWhenUserIsDisabled() throws Exception {
        User administrator = createUserWithRole("iam_status_admin", "账号状态管理员", "SYS_ADMIN");
        User targetUser = createUser("iam_status_target", "待停用用户");
        setPassword(administrator, administrator);
        setPassword(administrator, targetUser);
        String targetAccessToken = loginAccessToken("iam_status_target");

        mockMvc.perform(patch("/api/v1/users/{id}/status", targetUser.id())
                        .header("Authorization", "Bearer " + loginAccessToken("iam_status_admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(UserStatus.DISABLED.name()));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + targetAccessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void supportsDynamicOperationsRolesAndUserLevelPermissionDenial() throws Exception {
        User administrator = createUserWithRole("iam_ops_admin", "运维授权管理员", "SYS_ADMIN");
        User operationsUser = createUser("iam_ops_user", "运维用户");
        setPassword(administrator, administrator);
        setPassword(administrator, operationsUser);
        String administratorAccessToken = loginAccessToken("iam_ops_admin");

        mockMvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + administratorAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isString());
        mockMvc.perform(get("/api/v1/permissions").header("Authorization", "Bearer " + administratorAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isString());
        mockMvc.perform(post("/api/v1/permissions")
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"OPS_SAMPLE_OPERATION","name":"运维示例操作","resourceType":"OPERATION","client":"WEB"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.code").value("OPS_SAMPLE_OPERATION"));

        MvcResult roleResult = mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"OPS_USER_MANAGER","name":"用户运维","description":"仅维护用户创建","dataScope":"ALL"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andReturn();
        Long operationsRoleId = objectMapper.readTree(roleResult.getResponse().getContentAsString()).path("id").asLong();
        Long createUserPermissionId = permissionMapper.findByCode("IAM_USER_CREATE").id();
        Long createPermissionCatalogId = permissionMapper.findByCode("IAM_PERMISSION_CREATE").id();

        mockMvc.perform(post("/api/v1/roles/{roleId}/permissions", operationsRoleId)
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionId\":\"%s\"}".formatted(createUserPermissionId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/roles/{roleId}/permissions", operationsRoleId)
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionId\":\"%s\"}".formatted(createPermissionCatalogId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/users/{id}/roles", operationsUser.id())
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\"}".formatted(operationsRoleId)))
                .andExpect(status().isNoContent());

        String operationsAccessToken = loginAccessToken("iam_ops_user");
        mockMvc.perform(post("/api/v1/permissions")
                        .header("Authorization", "Bearer " + operationsAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"OPS_FORBIDDEN_PERMISSION","name":"非系统管理员权限目录","resourceType":"OPERATION","client":"WEB"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + operationsAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"iam_ops_created","displayName":"运维创建用户","type":"PLATFORM"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/users/{id}/roles", operationsUser.id())
                        .header("Authorization", "Bearer " + operationsAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\"}".formatted(operationsRoleId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(put("/api/v1/users/{userId}/permissions/{permissionId}", operationsUser.id(), createUserPermissionId)
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effect\":\"DENY\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + operationsAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"iam_ops_denied","displayName":"被拒绝的运维创建","type":"PLATFORM"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void configuresCustomRoleDataScopesAndOrganizationAdministrators() throws Exception {
        User administrator = createUserWithRole("iam_scope_admin", "数据范围管理员", "SYS_ADMIN");
        User organizationAdministrator = createUser("iam_org_admin", "机构管理员候选人");
        setPassword(administrator, administrator);
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("IAM_SCOPE_SCHOOL", "数据范围学校", "SCHOOL", null, 1)
        );
        userAccessApplicationService.associateWithOrganization(
                new com.lingdong.learning.user.application.AssociateUserWithOrganizationCommand(organizationAdministrator.id(), school.id())
        );
        Role customScopeRole = roleApplicationService.createCustomRole(
                new CreateCustomRoleCommand("IAM_SCOPE_ROLE", "范围运维角色", "允许配置自定义学校范围", RoleDataScope.CUSTOM)
        );
        String administratorAccessToken = loginAccessToken("iam_scope_admin");

        mockMvc.perform(post("/api/v1/roles/{roleId}/data-scopes", customScopeRole.id())
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"%s\"}".formatted(school.id())))
                .andExpect(status().isNoContent());
        assertThat(roleDataScopeMapper.exists(customScopeRole.id(), school.id())).isTrue();

        mockMvc.perform(post("/api/v1/organization-admins")
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"%s\",\"organizationId\":\"%s\"}"
                                .formatted(organizationAdministrator.id(), school.id())))
                .andExpect(status().isNoContent());
        assertThat(organizationAdminMapper.exists(organizationAdministrator.id(), school.id())).isTrue();
    }

    @Test
    void returnsNotFoundForMissingResourcesInIamWriteOperations() throws Exception {
        User administrator = createUserWithRole("iam_not_found_admin", "资源校验管理员", "SYS_ADMIN");
        setPassword(administrator, administrator);
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("IAM_NOT_FOUND_SCHOOL", "资源校验学校", "SCHOOL", null, 1)
        );
        String administratorAccessToken = loginAccessToken("iam_not_found_admin");
        long missingId = 1_000_000_000_000_000_000L;
        Long permissionId = permissionMapper.findByCode("IAM_USER_CREATE").id();

        mockMvc.perform(post("/api/v1/users/{id}/organizations", missingId)
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"%s\"}".formatted(school.id())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/users/{id}/roles", administrator.id())
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\"}".formatted(missingId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/users/{id}/password", missingId)
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Password456\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/roles/{roleId}/permissions", missingId)
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionId\":\"%s\"}".formatted(permissionId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(put("/api/v1/users/{userId}/permissions/{permissionId}", missingId, permissionId)
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effect\":\"ALLOW\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/roles/{roleId}/data-scopes", missingId)
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"%s\"}".formatted(school.id())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/organization-admins")
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"%s\",\"organizationId\":\"%s\"}".formatted(missingId, school.id())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/permissions")
                        .header("Authorization", "Bearer " + administratorAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"IAM_MISSING_PARENT","name":"缺失父级权限","resourceType":"OPERATION","client":"WEB","parentId":"1000000000000000000"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private void setPassword(User administrator, User targetUser) {
        authenticationApplicationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                administrator.id(), targetUser.id(), "Password123"
        ));
    }

    private String loginAccessToken(String username) throws Exception {
        return loginAccessToken(username, "Password123");
    }

    private String loginAccessToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","deviceId":"%s-device","deviceName":"IAM 管理测试浏览器"}
                                """.formatted(username, password, username)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("accessToken").asText();
    }

    private User createUserWithRole(String username, String displayName, String roleCode) {
        User user = createUser(username, displayName);
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }

    private User createUser(String username, String displayName) {
        return userAccessApplicationService.createUser(new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
    }
}
