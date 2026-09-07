package com.lingdong.learning.templateconfig.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachmentRuleApplicationService;
import com.lingdong.learning.attachment.application.CreateAttachmentRuleCommand;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.iam.application.CreateCustomRoleCommand;
import com.lingdong.learning.iam.application.RoleApplicationService;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.permission.application.ConfigureRolePermissionCommand;
import com.lingdong.learning.permission.application.ConfigureUserPermissionCommand;
import com.lingdong.learning.permission.application.PermissionAdministrationService;
import com.lingdong.learning.permission.domain.Permission;
import com.lingdong.learning.permission.domain.PermissionEffect;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImportExportTemplateManagementControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleApplicationService roleApplicationService;
    @Autowired private PermissionAdministrationService permissionAdministrationService;
    @Autowired private AttachmentRuleApplicationService attachmentRuleApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private PermissionMapper permissionMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void completesSevenEndpointWorkflowWithStringIdentifiersAndControlledDownload() throws Exception {
        Login administrator = administratorLogin("template_api_admin");
        ensureTemplateFileRule(administrator.user().id());
        byte[] fileContent = "student,name\n10000001,张同学".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "students.csv", "text/csv", fileContent);

        mockMvc.perform(get("/api/v1/import-export-templates/options")
                        .header("Authorization", bearer(administrator.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateTypes[0].code").value("IMPORT"))
                .andExpect(jsonPath("$.modules[0].code").value("STUDENT"));

        MvcResult createdResult = mockMvc.perform(multipart("/api/v1/import-export-templates")
                        .file(file)
                        .param("templateName", "学生接口导入模板")
                        .param("templateType", "IMPORT")
                        .param("moduleCode", "STUDENT")
                        .param("version", "API_V1")
                        .param("defaultTemplate", "false")
                        .param("fields", """
                                [{"fieldCode":"STUDENT_CODE","columnName":"学生账号","dataType":"TEXT",
                                  "required":true,"maxLength":8,"sortOrder":10}]
                                """)
                        .header("Authorization", bearer(administrator.token())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.fileId").isString())
                .andExpect(jsonPath("$.versionNo").value(0))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.contentSha256").doesNotExist())
                .andReturn();
        String templateId = body(createdResult).path("id").asText();
        assertThat(templateId).hasSize(19);

        mockMvc.perform(get("/api/v1/import-export-templates")
                        .param("templateName", "接口")
                        .param("templateType", "IMPORT")
                        .param("moduleCode", "student")
                        .param("status", "ENABLED")
                        .header("Authorization", bearer(administrator.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(templateId));

        mockMvc.perform(post("/api/v1/import-export-templates/{id}/default", templateId)
                        .header("Authorization", bearer(administrator.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionNo\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultTemplate").value(true))
                .andExpect(jsonPath("$.versionNo").value(1));

        mockMvc.perform(post("/api/v1/import-export-templates/{id}/disable", templateId)
                        .header("Authorization", bearer(administrator.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionNo\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.versionNo").value(2));

        mockMvc.perform(get("/api/v1/import-export-templates/{id}/download", templateId)
                        .header("Authorization", bearer(administrator.token())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().bytes(fileContent));

        mockMvc.perform(get("/api/v1/import-export-templates/{id}/fields", templateId)
                        .header("Authorization", bearer(administrator.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isString())
                .andExpect(jsonPath("$[0].fieldCode").value("STUDENT_CODE"));

        mockMvc.perform(put("/api/v1/import-export-templates/{id}/fields", templateId)
                        .header("Authorization", bearer(administrator.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"versionNo":2,"fields":[
                                  {"fieldCode":"STUDENT_CODE","columnName":"学生账号", "dataType":"TEXT",
                                   "required":true,"maxLength":8,"sortOrder":10},
                                  {"fieldCode":"NAME","columnName":"姓名","dataType":"TEXT",
                                   "required":true,"maxLength":50,"sortOrder":20}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(3))
                .andExpect(jsonPath("$.fields[1].fieldCode").value("NAME"));

        mockMvc.perform(post("/api/v1/import-export-templates/{id}/enable", templateId)
                        .header("Authorization", bearer(administrator.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionNo\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.versionNo").value(4));
    }

    @Test
    void separatesReadAndManagePermissionsAndHonorsUserDeny() throws Exception {
        Login administrator = administratorLogin("template_api_permission_admin");
        Role readRole = roleApplicationService.createCustomRole(new CreateCustomRoleCommand(
                "TEMPLATE_API_READ", "模板接口只读", "只读访问模板配置", RoleDataScope.ALL,
                administrator.user().id()
        ));
        grantRolePermission(administrator.user().id(), readRole.id(), "IMPORT_EXPORT_TEMPLATE_READ");
        User reader = createUser("template_api_reader", "模板接口只读用户");
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(reader.id(), readRole.id(), null));
        authenticationApplicationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                administrator.user().id(), reader.id(), "Password123"));
        String readerToken = login(reader);

        mockMvc.perform(get("/api/v1/import-export-templates/options")
                        .header("Authorization", bearer(readerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/import-export-templates")
                        .file(new MockMultipartFile("file", "read.csv", "text/csv", new byte[]{1}))
                        .param("templateName", "无管理权限模板")
                        .param("templateType", "IMPORT")
                        .param("moduleCode", "STUDENT")
                        .param("version", "READ_ONLY_V1")
                        .param("defaultTemplate", "false")
                        .header("Authorization", bearer(readerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        Permission readPermission = permissionMapper.findByCode("IMPORT_EXPORT_TEMPLATE_READ");
        permissionAdministrationService.configureUserPermission(new ConfigureUserPermissionCommand(
                administrator.user().id(), administrator.user().id(), readPermission.id(), PermissionEffect.DENY
        ));
        mockMvc.perform(get("/api/v1/import-export-templates/options")
                        .header("Authorization", bearer(administrator.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void blocksEveryEndpointWhenEitherRequiredFeatureIsDisabled() throws Exception {
        Login administrator = administratorLogin("template_api_disabled");
        jdbcTemplate.update("""
                update sys_feature_toggle set status = 'DISABLED'
                where feature_code = 'IMPORT_EXPORT_TEMPLATE_MANAGEMENT' and scope_key = 'GLOBAL'
                """);
        mockMvc.perform(get("/api/v1/import-export-templates/options")
                        .header("Authorization", bearer(administrator.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));

        jdbcTemplate.update("""
                update sys_feature_toggle set status = 'ENABLED'
                where feature_code = 'IMPORT_EXPORT_TEMPLATE_MANAGEMENT' and scope_key = 'GLOBAL'
                """);
        jdbcTemplate.update("""
                update sys_feature_toggle set status = 'DISABLED'
                where feature_code = 'ATTACHMENT_SERVICE' and scope_key = 'GLOBAL'
                """);
        mockMvc.perform(get("/api/v1/import-export-templates/options")
                        .header("Authorization", bearer(administrator.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
    }

    @Test
    void publishesAllTemplateManagementPathsInAuthenticatedOpenApi() throws Exception {
        Login administrator = administratorLogin("template_api_openapi");
        mockMvc.perform(get("/api/v1/openapi")
                        .header("Authorization", bearer(administrator.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/import-export-templates/options']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/import-export-templates']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/import-export-templates/{id}/enable']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/import-export-templates/{id}/disable']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/import-export-templates/{id}/default']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/import-export-templates/{id}/download']").exists());
    }

    private void ensureTemplateFileRule(Long administratorId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from sys_attachment_rule
                where module_code = 'IMPORT_EXPORT_TEMPLATE' and file_category = 'TEMPLATE_FILE'
                """, Integer.class);
        if (count != null && count == 0) {
            attachmentRuleApplicationService.createRule(new CreateAttachmentRuleCommand(
                    administratorId, "IMPORT_EXPORT_TEMPLATE", "TEMPLATE_FILE", "导入导出模板文件",
                    List.of("csv", "xlsx"), 10_485_760L, 1, false
            ));
        }
    }

    private Login administratorLogin(String username) throws Exception {
        User user = createUser(username, "模板接口管理员");
        Role role = roleMapper.findByCode("SYS_ADMIN");
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        authenticationApplicationService.setPlatformUserPassword(
                new SetPlatformUserPasswordCommand(user.id(), user.id(), "Password123"));
        return new Login(user, login(user));
    }

    private void grantRolePermission(Long operatorId, Long roleId, String permissionCode) {
        Permission permission = permissionMapper.findByCode(permissionCode);
        permissionAdministrationService.configureRolePermission(new ConfigureRolePermissionCommand(
                operatorId, roleId, permission.id(), PermissionEffect.ALLOW
        ));
    }

    private User createUser(String username, String displayName) {
        return userAccessApplicationService.createUser(
                new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
    }

    private String login(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password123","deviceId":"%s-device",
                                 "deviceName":"模板管理测试浏览器"}
                                """.formatted(user.username(), user.username())))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).path("accessToken").asText();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Login(User user, String token) { }
}
