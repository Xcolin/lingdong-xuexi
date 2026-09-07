package com.lingdong.learning.exportjob.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachmentRuleApplicationService;
import com.lingdong.learning.attachment.application.CreateAttachmentRuleCommand;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.templateconfig.application.CreateImportExportTemplateUploadCommand;
import com.lingdong.learning.templateconfig.application.ImportExportTemplateApplicationService;
import com.lingdong.learning.templateconfig.domain.TemplateType;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 真实验证敏感导出申请、审核、本人查询和 OpenAPI 契约。 */
@SpringBootTest(properties = "lingdong.export-job.scheduling-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExportJobApiIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationService;
    @Autowired private UserAccessApplicationService userService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private AttachmentRuleApplicationService ruleService;
    @Autowired private ImportExportTemplateApplicationService templateService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void submitsReviewsAndQueriesSensitiveExportWithoutLeakingInternalFields() throws Exception {
        User administrator = createUserWithRole(
                "export_api_admin", "导出接口管理员", "SYS_ADMIN", null);
        Login adminLogin = setPasswordAndLogin(administrator, administrator, "export-api-admin-device");
        User auditor = createUserWithRole(
                "export_api_auditor", "导出接口审核员", "SYS_AUDITOR", administrator);
        Login auditorLogin = setPasswordAndLogin(administrator, auditor, "export-api-auditor-device");
        ensureTemplateRule(administrator.id());
        templateService.createTemplate(new CreateImportExportTemplateUploadCommand(
                administrator.id(), "权限日志导出模板", TemplateType.EXPORT, "REPORT", "V1",
                "iam-export.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                exportTemplate(), true, List.of()));

        MvcResult created = mockMvc.perform(post("/api/v1/export-jobs")
                        .header("Authorization", bearer(adminLogin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exportType":"IAM_CHANGE_AUDIT",
                                 "eventType":"USER_STATUS_CHANGE",
                                 "reason":"月度权限安全复核"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.requestSourceHash").doesNotExist())
                .andExpect(jsonPath("$.filterSnapshot").doesNotExist())
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andReturn();
        String jobId = body(created).path("id").asText();
        assertThat(jobId).hasSize(19);
        String taskId = jdbcTemplate.queryForObject(
                "select cast(system_task_id as varchar) from sys_export_job where id = ?",
                String.class, Long.parseLong(jobId));

        mockMvc.perform(get("/api/v1/export-job-reviews")
                        .header("Authorization", bearer(auditorLogin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].jobId").value(jobId))
                .andExpect(jsonPath("$.items[0].systemTaskId").value(taskId))
                .andExpect(jsonPath("$.items[0].requesterName").value("导出接口管理员"))
                .andExpect(jsonPath("$.items[0].resultFileId").doesNotExist());

        mockMvc.perform(post("/api/v1/export-job-reviews/{taskId}/approve", taskId)
                        .header("Authorization", bearer(auditorLogin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意本次复核\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        mockMvc.perform(get("/api/v1/export-jobs/{id}", jobId)
                        .header("Authorization", bearer(adminLogin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.id").value(jobId))
                .andExpect(jsonPath("$.scopeSummary").value("全局权限变更日志"))
                .andExpect(jsonPath("$.events[2].eventType").value("APPROVED"))
                .andExpect(jsonPath("$.job.requestSourceHash").doesNotExist());

        mockMvc.perform(get("/api/v1/export-jobs/{id}/download", jobId)
                        .header("Authorization", bearer(adminLogin.token())))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/openapi")
                        .header("Authorization", bearer(adminLogin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/export-jobs']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/export-jobs/{id}/download']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/export-job-reviews/{taskId}/approve']").exists());
    }

    private User createUserWithRole(
            String username,
            String displayName,
            String roleCode,
            User administrator
    ) {
        User user = userService.createUser(new CreateUserCommand(
                username, displayName, null, UserType.PLATFORM));
        Role role = roleMapper.findByCode(roleCode);
        userService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }

    private Login setPasswordAndLogin(User administrator, User user, String deviceId) throws Exception {
        authenticationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                administrator.id(), user.id(), "Password123"));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password123",
                                 "deviceId":"%s","deviceName":"导出接口测试浏览器"}
                                """.formatted(user.username(), deviceId)))
                .andExpect(status().isOk()).andReturn();
        return new Login(user, body(result).path("accessToken").asText());
    }

    private void ensureTemplateRule(Long operatorId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from sys_attachment_rule
                where module_code = 'IMPORT_EXPORT_TEMPLATE' and file_category = 'TEMPLATE_FILE'
                """, Integer.class);
        if (count != null && count == 0) {
            ruleService.createRule(new CreateAttachmentRuleCommand(
                    operatorId, "IMPORT_EXPORT_TEMPLATE", "TEMPLATE_FILE", "导出模板文件",
                    List.of("xlsx"), 10_485_760L, 1, false));
        }
    }

    private byte[] exportTemplate() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var row = workbook.createSheet("权限日志").createRow(0);
            String[] columns = {"OCCURRED_AT", "EVENT_TYPE", "TARGET_TYPE", "TARGET_ID",
                    "TARGET_NAME", "OPERATOR_NAME", "BEFORE_SUMMARY", "AFTER_SUMMARY", "RESULT"};
            for (int index = 0; index < columns.length; index++) {
                row.createCell(index).setCellValue("${" + columns[index] + "}");
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Login(User user, String token) { }
}
