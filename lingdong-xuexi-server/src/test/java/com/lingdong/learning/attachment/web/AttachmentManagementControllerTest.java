package com.lingdong.learning.attachment.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachFileToBusinessCommand;
import com.lingdong.learning.attachment.application.AttachmentFileApplicationService;
import com.lingdong.learning.attachment.application.CompleteAttachmentUploadCommand;
import com.lingdong.learning.attachment.application.FileRelation;
import com.lingdong.learning.attachment.application.ManagedFile;
import com.lingdong.learning.attachment.application.RegisterAttachmentFileCommand;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AttachmentManagementControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private AttachmentFileApplicationService attachmentFileApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void completesRuleLifecycleWithStringIdentifiersAndOptimisticVersion() throws Exception {
        String token = tokenWithRole("attachment_api_admin", "SYS_ADMIN");
        MvcResult createdResult = mockMvc.perform(post("/api/v1/attachment-management/rules")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"moduleCode":"API_COURSEWORK","fileCategory":"EVIDENCE",
                                 "ruleName":"接口课程凭证","allowedExtensions":["jpg","png"],
                                 "maxFileSizeBytes":1048576,"maxBatchCount":3,"previewEnabled":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.versionNo").value(0))
                .andReturn();
        String ruleId = body(createdResult).path("id").asText();

        mockMvc.perform(get("/api/v1/attachment-management/rules")
                        .param("ruleName", "课程")
                        .param("moduleCode", "api_coursework")
                        .param("status", "ENABLED")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ruleId))
                .andExpect(jsonPath("$[0].moduleCode").value("API_COURSEWORK"));

        mockMvc.perform(put("/api/v1/attachment-management/rules/{id}", ruleId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ruleName":"接口课程材料","allowedExtensions":["pdf"],
                                 "maxFileSizeBytes":2097152,"maxBatchCount":5,
                                 "previewEnabled":false,"versionNo":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.moduleCode").value("API_COURSEWORK"));

        mockMvc.perform(post("/api/v1/attachment-management/rules/{id}/disable", ruleId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionNo\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.versionNo").value(2));
        mockMvc.perform(post("/api/v1/attachment-management/rules/{id}/disable", ruleId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionNo\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(2));
        mockMvc.perform(post("/api/v1/attachment-management/rules/{id}/enable", ruleId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionNo\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.versionNo").value(3));
    }

    @Test
    void listsSafeFileAndRelationLedgerWithoutStorageSecrets() throws Exception {
        String token = tokenWithRole("attachment_ledger_api_admin", "SYS_ADMIN");
        User uploader = createUser("attachment_ledger_api_uploader");
        createRule(token, "API_LEDGER", "DOCUMENT");
        ManagedFile file = attachmentFileApplicationService.registerUpload(new RegisterAttachmentFileCommand(
                uploader.id(), "API_LEDGER", "DOCUMENT", "安全台账.pdf", "application/pdf", 1024L));
        file = attachmentFileApplicationService.completeUpload(new CompleteAttachmentUploadCommand(
                file.id(), 1024L, "application/pdf", "b".repeat(64)));
        FileRelation relation = attachmentFileApplicationService.attachToBusiness(new AttachFileToBusinessCommand(
                file.id(), "API_LEDGER_BUSINESS", 3001L, "DOCUMENT", "BUSINESS_AUTHORIZED"));

        mockMvc.perform(get("/api/v1/attachment-management/files")
                        .param("moduleCode", "api_ledger")
                        .param("fileCategory", "document")
                        .param("uploaderId", uploader.id().toString())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isString())
                .andExpect(jsonPath("$[0].uploaderId").isString())
                .andExpect(jsonPath("$[0].contentSha256Present").value(true))
                .andExpect(jsonPath("$[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$[0].contentSha256").doesNotExist());

        mockMvc.perform(get("/api/v1/attachment-management/files/{id}/relations", file.id())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(relation.id().toString()))
                .andExpect(jsonPath("$[0].fileId").isString())
                .andExpect(jsonPath("$[0].businessId").isString());
    }

    @Test
    void rejectsMissingPermissionAndDisabledFeature() throws Exception {
        String ordinaryToken = tokenWithRole("attachment_api_ordinary", "PARENT");
        mockMvc.perform(get("/api/v1/attachment-management/rules")
                        .header("Authorization", bearer(ordinaryToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        String administratorToken = tokenWithRole("attachment_api_disabled", "SYS_ADMIN");
        jdbcTemplate.update("""
                update sys_feature_toggle set status = 'DISABLED'
                where feature_code = 'ATTACHMENT_SERVICE' and scope_key = 'GLOBAL'
                """);
        mockMvc.perform(get("/api/v1/attachment-management/rules")
                        .header("Authorization", bearer(administratorToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
    }

    @Test
    void publishesAttachmentManagementPathsInAuthenticatedOpenApi() throws Exception {
        String token = tokenWithRole("attachment_api_openapi", "SYS_ADMIN");
        mockMvc.perform(get("/api/v1/openapi").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/attachment-management/rules']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/attachment-management/rules/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/attachment-management/rules/{id}/enable']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/attachment-management/rules/{id}/disable']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/attachment-management/files']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/attachment-management/files/{id}/relations']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/attachments/{id}/download']").exists());
    }

    private void createRule(String token, String moduleCode, String fileCategory) throws Exception {
        mockMvc.perform(post("/api/v1/attachment-management/rules")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"moduleCode":"%s","fileCategory":"%s","ruleName":"接口文件台账规则",
                                 "allowedExtensions":["pdf"],"maxFileSizeBytes":1048576,
                                 "maxBatchCount":3,"previewEnabled":true}
                                """.formatted(moduleCode, fileCategory)))
                .andExpect(status().isCreated());
    }

    private String tokenWithRole(String username, String roleCode) throws Exception {
        User passwordAdministrator = createUser(username + "_password_admin");
        assignRole(passwordAdministrator, "SYS_ADMIN");
        User user = "SYS_ADMIN".equals(roleCode) ? passwordAdministrator : createUser(username);
        if (!"SYS_ADMIN".equals(roleCode)) {
            assignRole(user, roleCode);
        }
        authenticationApplicationService.setPlatformUserPassword(
                new SetPlatformUserPasswordCommand(passwordAdministrator.id(), user.id(), "Password123"));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password123","deviceId":"%s-device",
                                 "deviceName":"附件管理测试浏览器"}
                                """.formatted(user.username(), user.username())))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).path("accessToken").asText();
    }

    private User createUser(String username) {
        return userAccessApplicationService.createUser(
                new CreateUserCommand(username, "附件管理测试用户", null, UserType.PLATFORM));
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
