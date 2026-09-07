package com.lingdong.learning.importjob.web;

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
import com.lingdong.learning.templateconfig.application.ImportTemplateFieldInput;
import com.lingdong.learning.templateconfig.domain.ImportTemplateFieldDataType;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "lingdong.import-validation.scheduling-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImportJobApiIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationService;
    @Autowired private UserAccessApplicationService userService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private AttachmentRuleApplicationService ruleService;
    @Autowired private ImportExportTemplateApplicationService templateService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void createsQueriesAndDownloadsImportValidationJob() throws Exception {
        Login login = administratorLogin();
        ensureTemplateRule(login.user().id());
        byte[] workbook = workbook();
        long templateId = templateService.createTemplate(new CreateImportExportTemplateUploadCommand(
                login.user().id(), "学生校验模板", TemplateType.IMPORT, "STUDENT", "V1",
                "template.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, workbook, false,
                List.of(new ImportTemplateFieldInput(
                        "NAME", "姓名", ImportTemplateFieldDataType.TEXT,
                        true, 50, null, 10))
        )).id();

        MvcResult created = mockMvc.perform(multipart("/api/v1/import-jobs")
                        .file(new MockMultipartFile(
                                "file", "students.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook))
                        .param("templateId", Long.toString(templateId))
                        .header("Authorization", bearer(login.token())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.sourceFileId").isString())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andReturn();
        String jobId = body(created).path("id").asText();
        assertThat(jobId).hasSize(19);

        mockMvc.perform(get("/api/v1/import-jobs")
                        .param("jobCode", "IMP-")
                        .header("Authorization", bearer(login.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(jobId))
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(get("/api/v1/import-jobs/{id}", jobId)
                        .header("Authorization", bearer(login.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.id").value(jobId))
                .andExpect(jsonPath("$.fields[0].fieldCode").value("NAME"));

        mockMvc.perform(get("/api/v1/import-jobs/{id}/source-file", jobId)
                        .header("Authorization", bearer(login.token())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().bytes(workbook));

        mockMvc.perform(get("/api/v1/openapi")
                        .header("Authorization", bearer(login.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/import-jobs']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/import-jobs/{id}/errors']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/import-jobs/{id}/error-file']").exists());
    }

    private byte[] workbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("学生");
            sheet.createRow(0).createCell(0).setCellValue("姓名");
            sheet.createRow(1).createCell(0).setCellValue("张同学");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void ensureTemplateRule(Long operatorId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from sys_attachment_rule
                where module_code = 'IMPORT_EXPORT_TEMPLATE' and file_category = 'TEMPLATE_FILE'
                """, Integer.class);
        if (count != null && count == 0) {
            ruleService.createRule(new CreateAttachmentRuleCommand(
                    operatorId, "IMPORT_EXPORT_TEMPLATE", "TEMPLATE_FILE", "导入模板文件",
                    List.of("xlsx"), 10_485_760L, 1, false));
        }
    }

    private Login administratorLogin() throws Exception {
        User user = userService.createUser(new CreateUserCommand(
                "import_job_api_admin", "导入作业管理员", null, UserType.PLATFORM));
        Role role = roleMapper.findByCode("SYS_ADMIN");
        userService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        authenticationService.setPlatformUserPassword(
                new SetPlatformUserPasswordCommand(user.id(), user.id(), "Password123"));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"import_job_api_admin","password":"Password123",
                                 "deviceId":"import-job-api-device","deviceName":"导入作业测试浏览器"}
                                """))
                .andExpect(status().isOk()).andReturn();
        return new Login(user, body(result).path("accessToken").asText());
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Login(User user, String token) { }
}
