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
    @Test
    void attachmentLedgerExportsSevenColumnsAndFreezes205Rows() throws Exception {
        var f = attachmentFixture();
        for (int i = 0; i < 205; i++) attachmentRow(1900000000000060100L + i, f.admin().id(),
                i == 0 ? "=公式文件.xlsx" : "文件" + i + ".xlsx", i == 1 ? "RETIRED" : i == 2 ? "UPLOADING" : "AVAILABLE", "2026-09-28 10:00:00");
        attachmentRow(1900000000000060400L, f.admin().id(), "边界外.xlsx", "UPLOADING", "2026-09-28 09:59:59");
        long id = createAttachmentExport(f.login(), "\"attachmentUploaderId\":\"" + f.admin().id() + "\",");
        attachmentRow(1900000000000060500L, f.admin().id(), "迟到.xlsx", "AVAILABLE", "2026-09-28 10:00:00");
        assertThat(executionService.execute(claimService.claim(id, 0L))).isTrue();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(taskDownload(f.login(), id)))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(205);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 7);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("'=公式文件.xlsx");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("附*");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("2026-09-28 10:00:00");
            assertThat(sheet.getRow(1).getCell(5).getNumericCellValue()).isEqualTo(123);
            assertThat(sheet.getRow(2).getCell(6).getStringCellValue()).isEqualTo("RETIRED");
            assertThat(sheet.getRow(3).getCell(6).getStringCellValue()).isEqualTo("UPLOADING");
        }
        var other = createUserWithRole("attachment_other", "其他管理员", "SYS_ADMIN", null);
        assertTaskFileDenied(setPasswordAndLogin(f.admin(), other, "attachment-other"), id);
        jdbcTemplate.update("INSERT INTO sys_user_permission(id,user_id,permission_id,effect) SELECT 1900000000000060801,?,id,'DENY' FROM sys_permission WHERE permission_code='ATTACHMENT_FILE_LEDGER_EXPORT'", f.admin().id());
        sqlSession.clearCache();
        assertTaskFileDenied(f.login(), id);
    }

    @Test
    void attachmentLedgerAllowsExplicitCustomRoleButNeverSourceContent() throws Exception {
        var f = attachmentFixture();
        jdbcTemplate.update("INSERT INTO sys_role(id,role_code,role_name,role_type,data_scope,built_in,status) VALUES(1900000000000060802,'ATTACHMENT_OPERATOR','附件运维','CUSTOM','ALL',0,'ENABLED')");
        var operator = createUserWithRole("attachment_operator", "运维甲", "ATTACHMENT_OPERATOR", f.admin());
        int n = 0;
        for (String permission : List.of("ATTACHMENT_FILE_LEDGER_READ", "ATTACHMENT_FILE_LEDGER_EXPORT", "EXPORT_JOB_READ")) {
            jdbcTemplate.update("INSERT INTO sys_role_permission(id,role_id,permission_id,effect) SELECT ?,1900000000000060802,id,'ALLOW' FROM sys_permission WHERE permission_code=?", 1900000000000060810L + n++, permission);
        }
        var login = setPasswordAndLogin(f.admin(), operator, "attachment-custom");
        attachmentRow(1900000000000060100L, f.admin().id(), "其他人的文件.xlsx", "AVAILABLE", "2026-09-28 10:00:00");
        long id = createAttachmentExport(login, "");
        assertThat(executionService.execute(claimService.claim(id, 0L))).isTrue();
        assertThat(taskDownload(login, id)).isNotEmpty();
        mockMvc.perform(get("/api/v1/export-jobs").header("Authorization", bearer(login.token()))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/attachments/1900000000000060100/content").header("Authorization", bearer(login.token()))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=DICTIONARY_LEDGER").header("Authorization", bearer(login.token()))).andExpect(status().isForbidden());
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id=1900000000000060802 AND permission_id=(SELECT id FROM sys_permission WHERE permission_code='ATTACHMENT_FILE_LEDGER_READ')");
        sqlSession.clearCache();
        assertTaskFileDenied(login, id);
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=ATTACHMENT_LEDGER").header("Authorization", bearer(login.token()))).andExpect(status().isForbidden());
    }

    @Test
    void attachmentLedgerRejectsForeignFiltersAndQueuedRevocation() throws Exception {
        var f = attachmentFixture();
        for (String filter : List.of("\"attachmentUploaderId\":123,", "\"attachmentUploaderId\":\"123\",",
                "\"attachmentUploaderId\":\"9999999999999999999\",", "\"studentId\":\"1900000000000000001\",",
                "\"exceptionType\":\"MENTAL_STATE\",", "\"cacheStatus\":\"FAILED\",",
                "\"templateType\":\"EXPORT\",", "\"dictionaryStatus\":\"ENABLED\",",
                "\"interfaceStatus\":\"ENABLED\",", "\"systemTaskStatus\":\"DRAFT\",",
                "\"rewardExchangeStatus\":\"VERIFIED\",", "\"eventType\":\"USER_CREATE\",",
                "\"columns\":[\"STORAGE_KEY\"],")) {
            mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(f.login().token()))
                    .contentType(MediaType.APPLICATION_JSON).content(attachmentRequest(filter))).andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(f.login().token()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"exportType\":\"DICTIONARY_LEDGER\",\"attachmentModuleCode\":\"TEST\",\"reason\":\"禁止混用筛选\"}"))
                .andExpect(status().isBadRequest());
        long id = createAttachmentExport(f.login(), "");
        jdbcTemplate.update("UPDATE sys_permission SET status='DISABLED' WHERE permission_code='ATTACHMENT_FILE_LEDGER_EXPORT'");
        sqlSession.clearCache();
        assertThat(executionService.execute(claimService.claim(id, 0L))).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM sys_export_job WHERE id=?", String.class, id)).isEqualTo("FAILED");
    }

    private AttachmentExportFixture attachmentFixture() throws Exception {
        var admin = createUserWithRole("attachment_export_admin", "附件管理员", "SYS_ADMIN", null);
        var login = setPasswordAndLogin(admin, admin, "attachment-export");
        ensureTemplateRule(admin.id());
        templateService.createTemplate(new CreateImportExportTemplateUploadCommand(admin.id(), "附件管理台账模板", TemplateType.EXPORT,
                "ATTACHMENT_LEDGER_REPORT", "V1", "attachment.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                templateFor("NAME", "MODULE_CODE", "UPLOADER_NAME", "CREATED_AT", "FILE_CATEGORY", "SIZE_BYTES", "STATUS"), true, List.of()));
        return new AttachmentExportFixture(admin, login);
    }
    private void attachmentRow(long id, long uploader, String name, String status, String created) {
        jdbcTemplate.update("INSERT INTO sys_file(id,storage_key,original_name,extension,content_type,size_bytes,uploader_id,module_code,file_category,status,created_at) VALUES(?,?,?,'xlsx','application/octet-stream',123,?,'ATTACHMENT_SAMPLE','TEST_FILE',?,?)",
                id, "not-exported/" + id, name, uploader, status, created);
    }
    private String attachmentRequest(String filters) {
        return "{\"exportType\":\"ATTACHMENT_LEDGER\",\"attachmentModuleCode\":\" attachment_sample \",\"attachmentFileCategory\":\"test_file\",\"startedAt\":\"2026-09-28T10:00:00\",\"endedAt\":\"2026-09-28T10:00:00\"," + filters + "\"reason\":\"附件元数据盘点\"}";
    }
    private long createAttachmentExport(Login login, String filters) throws Exception {
        var result = mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(login.token()))
                .contentType(MediaType.APPLICATION_JSON).content(attachmentRequest(filters))).andExpect(status().isCreated()).andReturn();
        return body(result).path("id").asLong();
    }
    private record AttachmentExportFixture(User admin, Login login) { }

    @Test
    void attachmentLedgerModuleAndMinimumGrantAreAvailable() {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_dictionary_item WHERE item_code='ATTACHMENT_LEDGER_REPORT'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("SELECT r.role_code FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE p.permission_code='ATTACHMENT_FILE_LEDGER_EXPORT'", String.class)).containsExactly("SYS_ADMIN");
    }
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationService;
    @Autowired private UserAccessApplicationService userService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private AttachmentRuleApplicationService ruleService;
    @Autowired private ImportExportTemplateApplicationService templateService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private com.lingdong.learning.exportjob.application.ExportJobClaimService claimService;
    @Autowired private com.lingdong.learning.exportjob.application.ExportJobExecutionService executionService;

    @Test
    void exceptionWorkbookMasksBothNamesAndRevokesClassAccess() throws Exception {
        var f=exceptionFixture();
        exceptionRow(1900000000000090200L,f,f.login().user().id(),f.classId(),"MENTAL_STATE");
        exceptionRow(1900000000000090201L,f,f.admin().id(),f.classId(),"ATTENDANCE");
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=EXCEPTION_REPORT_LEDGER").header("Authorization",bearer(f.login().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.columns.length()").value(5))
                .andExpect(jsonPath("$.exceptionClasses[0].id").isString()).andExpect(jsonPath("$.exceptionClasses[0].id").value(Long.toString(f.classId())));
        long id=createExceptionExport(f.login(), "");
        assertThat(executionService.execute(claimService.claim(id,0L))).isTrue();
        try(var workbook=new XSSFWorkbook(new java.io.ByteArrayInputStream(taskDownload(f.login(),id)))) {
            var sheet=workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short)5);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("学*");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("MENTAL_STATE");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("教*");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("SUBMITTED");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("2026-09-27 10:00:00");
        }
        for(String column:List.of("status","effective_status")) {
            jdbcTemplate.update("UPDATE sys_organization SET "+column+"='DISABLED' WHERE id=?",f.classId()); sqlSession.clearCache();
            assertTaskFileDenied(f.login(),id);
            jdbcTemplate.update("UPDATE sys_organization SET "+column+"='ENABLED' WHERE id=?",f.classId()); sqlSession.clearCache();
        }
        jdbcTemplate.update("UPDATE edu_teacher_class SET status='INACTIVE' WHERE teacher_user_id=?",f.login().user().id()); sqlSession.clearCache();
        assertTaskFileDenied(f.login(),id);
        jdbcTemplate.update("UPDATE edu_teacher_class SET status='ACTIVE' WHERE teacher_user_id=?",f.login().user().id()); sqlSession.clearCache();
        userService.assignRole(new AssignRoleToUserCommand(f.login().user().id(),roleMapper.findByCode("ORG_ADMIN").id(),null));
        assertTaskFileDenied(f.login(),id);
    }

    @Test
    void exceptionExportRejectsForeignFiltersAndMixedAuditorAndCompensatesRevocation() throws Exception {
        var f=exceptionFixture();
        for(String filters:List.of("\"exceptionType\":\"INVALID\",", "\"exceptionStatus\":\"INVALID\",",
                "\"exceptionClassId\":123,", "\"exceptionClassId\":\"123\",", "\"exceptionClassId\":\"9223372036854775808\",",
                "\"studentId\":\"1900000000000090101\",", "\"rewardExchangeStatus\":\"VERIFIED\",", "\"systemTaskStatus\":\"DRAFT\",",
                "\"cacheDomain\":\"ALL\",", "\"interfaceStatus\":\"ENABLED\",", "\"templateType\":\"EXPORT\",",
                "\"dictionaryStatus\":\"ENABLED\",", "\"eventType\":\"USER_CREATE\",", "\"columns\":[\"CONTENT\"],")) {
            mockMvc.perform(post("/api/v1/export-jobs").header("Authorization",bearer(f.login().token())).contentType(MediaType.APPLICATION_JSON)
                    .content(exceptionRequest(filters))).andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/v1/export-jobs").header("Authorization",bearer(f.login().token())).contentType(MediaType.APPLICATION_JSON)
                .content(exceptionRequest("\"exceptionClassId\":\"1900000000000090999\","))).andExpect(status().isForbidden());
        long queued=createExceptionExport(f.login(), "");
        jdbcTemplate.update("UPDATE edu_teacher_class SET status='INACTIVE' WHERE teacher_user_id=?",f.login().user().id()); sqlSession.clearCache();
        assertThat(executionService.execute(claimService.claim(queued,0L))).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM sys_export_job WHERE id=?",String.class,queued)).isEqualTo("FAILED");
        userService.assignRole(new AssignRoleToUserCommand(f.login().user().id(),roleMapper.findByCode("SYS_AUDITOR").id(),null));
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=EXCEPTION_REPORT_LEDGER").header("Authorization",bearer(f.login().token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void exceptionExportFreezesClassSetAndUpperBoundAcross205Rows() throws Exception {
        var f=exceptionFixture();
        for(int i=0;i<205;i++) exceptionRow(1900000000000090300L+i,f,f.login().user().id(),f.classId(),"MENTAL_STATE");
        exceptionRow(1900000000000090200L,f,f.login().user().id(),f.classId(),"ATTENDANCE");
        long id=createExceptionExport(f.login(),"\"exceptionType\":\"MENTAL_STATE\",\"exceptionStatus\":\"SUBMITTED\",");
        exceptionRow(1900000000000090600L,f,f.login().user().id(),f.classId(),"MENTAL_STATE");
        long otherClass=1900000000000090199L;
        jdbcTemplate.update("INSERT INTO sys_organization(id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,'EXPORT_EXCEPTION_OTHER','新授权班级','CLASS','/EXPORT_EXCEPTION_OTHER/',1,'ENABLED')",otherClass);
        jdbcTemplate.update("INSERT INTO edu_teacher_class(id,teacher_user_id,class_organization_id,status) VALUES(1900000000000090198,?,?,'ACTIVE')",f.login().user().id(),otherClass);
        exceptionRow(1900000000000090250L,f,f.login().user().id(),otherClass,"MENTAL_STATE"); sqlSession.clearCache();
        assertThat(executionService.execute(claimService.claim(id,0L))).isTrue();
        try(var workbook=new XSSFWorkbook(new java.io.ByteArrayInputStream(taskDownload(f.login(),id)))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(205);
        }
        assertThat(jdbcTemplate.queryForList("SELECT r.role_code FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE p.permission_code='EXCEPTION_REPORT_EXPORT'",String.class)).containsExactlyInAnyOrder("TEACHER","ORG_ADMIN");
    }

    @Test
    void exceptionOrganizationScopeIncludesOthersAndRevokesMovedClass() throws Exception {
        var f=exceptionFixture();
        long school=1900000000000090801L, outside=1900000000000090802L;
        jdbcTemplate.update("INSERT INTO sys_organization(id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,'EXCEPTION_EXPORT_SCHOOL','学校','SCHOOL','/EXCEPTION_EXPORT_SCHOOL/',1,'ENABLED')",school);
        jdbcTemplate.update("UPDATE sys_organization SET parent_id=?,organization_path='/EXCEPTION_EXPORT_SCHOOL/EXPORT_EXCEPTION_CLASS/' WHERE id=?",school,f.classId());
        jdbcTemplate.update("INSERT INTO sys_organization(id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,'EXCEPTION_EXPORT_OUTSIDE','范围外班级','CLASS','/EXCEPTION_EXPORT_OUTSIDE/',1,'ENABLED')",outside);
        var manager=createUserWithRole("exception_export_org","机构管理员","TEACHER",f.admin());
        jdbcTemplate.update("INSERT INTO sys_user_organization(id,user_id,organization_id) VALUES(1900000000000090803,?,?)",manager.id(),school);
        userService.assignRole(new AssignRoleToUserCommand(manager.id(),roleMapper.findByCode("ORG_ADMIN").id(),school));
        var login=setPasswordAndLogin(f.admin(),manager,"exception-org-device");
        exceptionRow(1900000000000090820L,f,f.login().user().id(),f.classId(),"MENTAL_STATE");
        exceptionRow(1900000000000090821L,f,f.admin().id(),f.classId(),"LEARNING_STATUS");
        exceptionRow(1900000000000090822L,f,f.login().user().id(),outside,"ATTENDANCE");
        long id=createExceptionExport(login,"\"exceptionClassId\":\""+f.classId()+"\",");
        assertThat(executionService.execute(claimService.claim(id,0L))).isTrue();
        try(var workbook=new XSSFWorkbook(new java.io.ByteArrayInputStream(taskDownload(login,id)))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(2);
        }
        jdbcTemplate.update("UPDATE sys_organization SET parent_id=NULL,organization_path='/MOVED_EXCEPTION_CLASS/' WHERE id=?",f.classId()); sqlSession.clearCache();
        assertTaskFileDenied(login,id);
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=EXCEPTION_REPORT_LEDGER").header("Authorization",bearer(login.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.exceptionClasses.length()").value(0));
    }

    private ExceptionExportFixture exceptionFixture() throws Exception {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_dictionary_item WHERE item_code='EXCEPTION_REPORT_EXPORT'",Integer.class)).isEqualTo(1);
        var admin=createUserWithRole("exception_export_admin","管理员","SYS_ADMIN",null);
        var teacher=createUserWithRole("exception_export_teacher","教师甲","TEACHER",admin);
        var login=setPasswordAndLogin(admin,teacher,"exception-export-device");
        ensureTemplateRule(admin.id());
        templateService.createTemplate(new CreateImportExportTemplateUploadCommand(admin.id(),"异常报备台账模板",TemplateType.EXPORT,"EXCEPTION_REPORT_EXPORT","V1","exception.xlsx",MediaType.APPLICATION_OCTET_STREAM_VALUE,
                templateFor("STUDENT_NAME","EXCEPTION_TYPE","REPORTER_NAME","STATUS","REPORTED_AT"),true,List.of()));
        long student=1900000000000090101L,cl=1900000000000090102L;
        jdbcTemplate.update("INSERT INTO edu_student(id,student_name,status) VALUES(?,'学生甲','ENABLED')",student);
        jdbcTemplate.update("INSERT INTO sys_organization(id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,'EXPORT_EXCEPTION_CLASS','异常导出班级','CLASS','/EXPORT_EXCEPTION_CLASS/',1,'ENABLED')",cl);
        jdbcTemplate.update("INSERT INTO edu_teacher_class(id,teacher_user_id,class_organization_id,status) VALUES(1900000000000090103,?,?,'ACTIVE')",teacher.id(),cl);
        return new ExceptionExportFixture(admin,login,student,cl);
    }
    private void exceptionRow(long id,ExceptionExportFixture f,long reporter,long classId,String type) {
        jdbcTemplate.update("INSERT INTO edu_exception_report(id,student_id,class_organization_id,reporter_user_id,exception_type,content,status,idempotency_key,version_no,reported_at) VALUES(?,?,?,?,?,'不导出异常正文','SUBMITTED',?,0,'2026-09-27 10:00:00')",id,f.studentId(),classId,reporter,type,"exception-export-"+id);
    }
    private String exceptionRequest(String filters) {
        return "{\"exportType\":\"EXCEPTION_REPORT_LEDGER\",\"startedAt\":\"2026-09-27T10:00:00\",\"endedAt\":\"2026-09-27T10:00:00\","+filters+"\"reason\":\"异常报备台账\"}";
    }
    private long createExceptionExport(Login login,String filters) throws Exception {
        var result=mockMvc.perform(post("/api/v1/export-jobs").header("Authorization",bearer(login.token())).contentType(MediaType.APPLICATION_JSON)
                .content(exceptionRequest(filters))).andExpect(status().isCreated()).andReturn();
        return body(result).path("id").asLong();
    }
    private record ExceptionExportFixture(User admin,Login login,long studentId,long classId) { }

    @Test
    void rewardExchangeOptionsRequireDedicatedPermissionEvenWithoutStudents() throws Exception {
        User admin = createUserWithRole("reward_opt_admin", "管理员", "SYS_ADMIN", null);
        User parent = createUserWithRole("reward_opt_parent", "家长", "PARENT", admin);
        Login login = setPasswordAndLogin(admin, parent, "reward-options-device");
        ensureTemplateRule(admin.id());
        templateService.createTemplate(new CreateImportExportTemplateUploadCommand(admin.id(), "奖励兑换模板",
                TemplateType.EXPORT, "REWARD_EXCHANGE_REPORT", "V1", "reward.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                templateFor("REWARD_NAME", "REQUIRED_POINTS", "REQUESTED_AT", "APPROVAL_STATUS", "VERIFICATION_STATUS"), true, List.of()));
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=REWARD_EXCHANGE_LEDGER").header("Authorization", bearer(login.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.columns.length()").value(5)).andExpect(jsonPath("$.students.length()").value(0));
        for(String permission:List.of("REWARD_EXCHANGE_EXPORT","REWARD_EXCHANGE_REVIEW_CHILD")) {
            jdbcTemplate.update("UPDATE sys_permission SET status='DISABLED' WHERE permission_code=?",permission);
            sqlSession.clearCache();
            mockMvc.perform(get("/api/v1/export-jobs/options?exportType=REWARD_EXCHANGE_LEDGER").header("Authorization", bearer(login.token())))
                    .andExpect(status().isForbidden());
            jdbcTemplate.update("UPDATE sys_permission SET status='ENABLED' WHERE permission_code=?",permission);
            sqlSession.clearCache();
        }
    }

    @Test
    void rewardWorkbookUsesSnapshotsAndAllStatusesThenDeniesRevokedFamilyAccess() throws Exception {
        var fixture = rewardExportFixture();
        String[] states = {"PENDING_APPROVAL", "PENDING_VERIFICATION", "REJECTED", "AUTO_REJECTED", "EXPIRED", "VERIFIED"};
        String[] approvals = {"待审批", "已通过", "已驳回", "超时驳回", "未审批", "已通过"};
        String[] verifications = {"未核销", "待核销", "未核销", "未核销", "已过期", "已核销"};
        for (int i=0;i<states.length;i++) rewardExportRow(1900000000000088200L+i, fixture, states[i]);
        jdbcTemplate.update("UPDATE growth_reward SET reward_name='当前名称不应覆盖快照',required_points=999 WHERE id=?", fixture.rewardId());
        long id = createRewardExport(fixture.login(), fixture.studentId(), "");
        assertThat(executionService.execute(claimService.claim(id,0L))).isTrue();
        mockMvc.perform(get("/api/v1/export-jobs/{id}",id).header("Authorization",bearer(fixture.login().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scopeSummary").value("奖励兑换报表（对象标识："+fixture.studentId()+"）"));
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(taskDownload(fixture.login(),id)))) {
            var sheet=workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(6);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short)5);
            for(int i=0;i<states.length;i++) {
                var row=sheet.getRow(i+1);
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo("'=申请时奖励");
                assertThat(row.getCell(1).getNumericCellValue()).isEqualTo(30);
                assertThat(row.getCell(2).getStringCellValue()).isEqualTo("2026-09-27 10:00:00");
                assertThat(row.getCell(3).getStringCellValue()).isEqualTo(approvals[i]);
                assertThat(row.getCell(4).getStringCellValue()).isEqualTo(verifications[i]);
            }
        }
        jdbcTemplate.update("UPDATE edu_parent_student SET status='INACTIVE' WHERE parent_user_id=?",fixture.login().user().id());
        sqlSession.clearCache();
        assertTaskFileDenied(fixture.login(),id);
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=REWARD_EXCHANGE_LEDGER").header("Authorization",bearer(fixture.login().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.students.length()").value(0));
        jdbcTemplate.update("UPDATE edu_parent_student SET status='ACTIVE' WHERE parent_user_id=?",fixture.login().user().id());
        sqlSession.clearCache();
        assertThat(taskDownload(fixture.login(),id)).isNotEmpty();
        for(String feature:List.of("REWARD_EXCHANGE","DATA_EXPORT","ATTACHMENT_SERVICE","IMPORT_EXPORT_TEMPLATE_MANAGEMENT")) {
            jdbcTemplate.update("UPDATE sys_feature_toggle SET status='DISABLED' WHERE feature_code=?",feature);
            sqlSession.clearCache();
            for(String suffix:List.of("","/download")) {
                mockMvc.perform(get("/api/v1/export-jobs/"+id+suffix).header("Authorization",bearer(fixture.login().token())))
                        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
            }
            jdbcTemplate.update("UPDATE sys_feature_toggle SET status='ENABLED' WHERE feature_code=?",feature);
            sqlSession.clearCache();
        }
        jdbcTemplate.update("UPDATE sys_permission SET status='DISABLED' WHERE permission_code='REWARD_EXCHANGE_EXPORT'");
        sqlSession.clearCache();
        assertTaskFileDenied(fixture.login(),id);
    }

    @Test
    void rewardExportRejectsSecondaryOtherStudentsMixedAuditorAndForeignFilters() throws Exception {
        var fixture=rewardExportFixture();
        var secondary=createUserWithRole("export_secondary","副家长","PARENT",fixture.admin());
        var login=setPasswordAndLogin(fixture.admin(),secondary,"secondary-reward-export");
        jdbcTemplate.update("INSERT INTO edu_parent_student(id,parent_user_id,student_id,relation_role,status,primary_scope_key) VALUES(1900000000000088110,?,?,'SECONDARY_GUARDIAN','ACTIVE','SECONDARY')",secondary.id(),fixture.studentId());
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=REWARD_EXCHANGE_LEDGER").header("Authorization",bearer(login.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.students.length()").value(0));
        mockMvc.perform(post("/api/v1/export-jobs").header("Authorization",bearer(login.token())).contentType(MediaType.APPLICATION_JSON)
                .content(rewardRequest(fixture.studentId(),""))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/export-jobs").header("Authorization",bearer(fixture.login().token())).contentType(MediaType.APPLICATION_JSON)
                .content(rewardRequest(1900000000000088999L,""))).andExpect(status().isForbidden());
        for(String filter:List.of("\"rewardExchangeStatus\":\"INVALID\",","\"systemTaskStatus\":\"DRAFT\",","\"cacheDomain\":\"ALL\",",
                "\"interfaceStatus\":\"ENABLED\",","\"templateType\":\"EXPORT\",","\"dictionaryStatus\":\"ENABLED\",","\"eventType\":\"USER_CREATE\",","\"columns\":[\"DESCRIPTION\"],")) {
            mockMvc.perform(post("/api/v1/export-jobs").header("Authorization",bearer(fixture.login().token())).contentType(MediaType.APPLICATION_JSON)
                    .content(rewardRequest(fixture.studentId(),filter))).andExpect(status().isBadRequest());
        }
        for(String type:List.of("GROWTH_POINT_LEDGER","IAM_CHANGE_AUDIT","DICTIONARY_LEDGER","TEMPLATE_LEDGER","INTERFACE_SERVICE_LEDGER","CACHE_OPERATION_LOG","SYSTEM_TASK_LEDGER")) {
            mockMvc.perform(post("/api/v1/export-jobs").header("Authorization",bearer(fixture.login().token())).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"exportType\":\""+type+"\",\"rewardExchangeStatus\":\"VERIFIED\",\"reason\":\"拒绝混用\"}"))
                    .andExpect(status().isBadRequest());
        }
        for(String student:List.of("1900000000000088101","\"123\"","\"9223372036854775808\"","null")) {
            mockMvc.perform(post("/api/v1/export-jobs").header("Authorization",bearer(fixture.login().token())).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"exportType\":\"REWARD_EXCHANGE_LEDGER\",\"studentId\":"+student+",\"reason\":\"标识校验\"}"))
                    .andExpect(status().isBadRequest());
        }
        userService.assignRole(new AssignRoleToUserCommand(fixture.login().user().id(),roleMapper.findByCode("SYS_AUDITOR").id(),null));
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=REWARD_EXCHANGE_LEDGER").header("Authorization",bearer(fixture.login().token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void rewardExportPagesAndFreezesUpperBoundAndRefusesQueuedRevocation() throws Exception {
        var fixture=rewardExportFixture();
        for(int i=0;i<205;i++) rewardExportRow(1900000000000088300L+i,fixture,"VERIFIED");
        rewardExportRow(1900000000000088600L,fixture,"PENDING_APPROVAL");
        rewardExportRow(1900000000000088601L,fixture,"VERIFIED");
        jdbcTemplate.update("UPDATE growth_reward_exchange SET requested_at='2026-09-27 09:59:59' WHERE id=1900000000000088601");
        long id=createRewardExport(fixture.login(),fixture.studentId(),"\"rewardExchangeStatus\":\"VERIFIED\",");
        rewardExportRow(1900000000000088700L,fixture,"VERIFIED");
        assertThat(executionService.execute(claimService.claim(id,0L))).isTrue();
        try(var workbook=new XSSFWorkbook(new java.io.ByteArrayInputStream(taskDownload(fixture.login(),id)))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(205);
        }
        long queued=createRewardExport(fixture.login(),fixture.studentId(),"");
        jdbcTemplate.update("UPDATE edu_parent_student SET status='INACTIVE' WHERE parent_user_id=?",fixture.login().user().id());
        sqlSession.clearCache();
        assertThat(executionService.execute(claimService.claim(queued,0L))).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM sys_export_job WHERE id=?",String.class,queued)).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForList("SELECT r.role_code FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE p.permission_code='REWARD_EXCHANGE_EXPORT'",String.class)).containsExactly("PARENT");
    }

    private RewardExportFixture rewardExportFixture() throws Exception {
        var admin=createUserWithRole("reward_report_admin","奖励导出管理员","SYS_ADMIN",null);
        var parent=createUserWithRole("reward_report_parent","奖励家长","PARENT",admin);
        var login=setPasswordAndLogin(admin,parent,"reward-report-device");
        ensureTemplateRule(admin.id());
        templateService.createTemplate(new CreateImportExportTemplateUploadCommand(admin.id(),"奖励兑换模板",TemplateType.EXPORT,"REWARD_EXCHANGE_REPORT","V1","reward.xlsx",MediaType.APPLICATION_OCTET_STREAM_VALUE,
                templateFor("REWARD_NAME","REQUIRED_POINTS","REQUESTED_AT","APPROVAL_STATUS","VERIFICATION_STATUS"),true,List.of()));
        long student=1900000000000088101L,reward=1900000000000088103L;
        jdbcTemplate.update("INSERT INTO edu_student(id,student_name,status) VALUES(?,'导出学生','ENABLED')",student);
        jdbcTemplate.update("INSERT INTO edu_parent_student(id,parent_user_id,student_id,relation_role,status,primary_scope_key) VALUES(1900000000000088102,?,?,'PRIMARY_GUARDIAN','ACTIVE','PRIMARY')",parent.id(),student);
        jdbcTemplate.update("INSERT INTO growth_reward(id,student_id,created_by_parent_id,reward_name,required_points,status) VALUES(?,?,?,'原奖励',30,'ONLINE')",reward,student,parent.id());
        return new RewardExportFixture(admin,login,student,reward);
    }
    private void rewardExportRow(long id,RewardExportFixture fixture,String state) {
        boolean reviewed=List.of("PENDING_VERIFICATION","VERIFIED","REJECTED").contains(state);
        jdbcTemplate.update("INSERT INTO growth_reward_exchange(id,reward_id,student_id,requester_user_id,reward_name_snapshot,required_points_snapshot,description_snapshot,requested_at,approval_deadline,status,reviewed_by,reviewed_at,reject_reason) VALUES(?,?,?,?,'=申请时奖励',30,'不导出家庭描述','2026-09-27 10:00:00','2026-09-30 10:00:00',?,?,?,?)",
                id,fixture.rewardId(),fixture.studentId(),fixture.login().user().id(),state,reviewed?fixture.login().user().id():null,reviewed?java.time.LocalDateTime.of(2026,9,27,11,0):null,"REJECTED".equals(state)?"不导出驳回原因":null);
    }
    private String rewardRequest(long student,String filters) {
        return "{\"exportType\":\"REWARD_EXCHANGE_LEDGER\",\"studentId\":\""+student+"\","+filters+"\"startedAt\":\"2026-09-27T10:00:00\",\"endedAt\":\"2026-09-27T10:00:00\",\"reason\":\"家庭奖励存档\"}";
    }
    private long createRewardExport(Login login,long student,String filters) throws Exception {
        var result=mockMvc.perform(post("/api/v1/export-jobs").header("Authorization",bearer(login.token())).contentType(MediaType.APPLICATION_JSON)
                .content(rewardRequest(student,filters))).andExpect(status().isCreated()).andReturn();
        return Long.parseLong(body(result).path("id").asText());
    }
    private record RewardExportFixture(User admin,Login login,long studentId,long rewardId) { }
    @Test
    void systemTaskLedgerOffersIndependentTemplateAndVisibleTypes() throws Exception {
        User admin = createUserWithRole("task_export_admin", "任务导出管理员", "SYS_ADMIN", null);
        Login login = setPasswordAndLogin(admin, admin, "task-export-device");
        ensureTemplateRule(admin.id());
        jdbcTemplate.update("INSERT INTO sys_dictionary_item (id,type_id,item_code,item_name,sort_order,is_default,status) SELECT 1900000000000088700,1874244142494646611,'SYSTEM_TASK_REPORT','系统任务审批台账',80,0,'ENABLED' WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary_item WHERE item_code='SYSTEM_TASK_REPORT')");
        templateService.createTemplate(new CreateImportExportTemplateUploadCommand(admin.id(), "系统任务模板",
                TemplateType.EXPORT, "SYSTEM_TASK_REPORT", "V1", "tasks.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                templateFor("TASK_TYPE", "SUBMITTER_ID", "REVIEWER_ID", "STATUS", "CREATED_AT", "REVIEW_COMMENT"), true, List.of()));
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=SYSTEM_TASK_LEDGER").header("Authorization", bearer(login.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.columns.length()").value(6))
                .andExpect(jsonPath("$.systemTaskTypes").isArray());
    }

    @Autowired private org.mybatis.spring.SqlSessionTemplate sqlSession;
    @Autowired private com.lingdong.learning.exportjob.application.adapter.SystemTaskLedgerExportAdapter taskAdapter;
    @Autowired private com.lingdong.learning.exportjob.application.ExportJobAccessService exportAccess;

    @Test
    void systemTaskLedgerExportsOwnSixSafeColumnsAndInvalidatesFileAfterDomainOrRoleChange() throws Exception {
        User admin = createUserWithRole("task_own_admin", "任务管理员", "SYS_ADMIN", null);
        User other = createUserWithRole("task_other_admin", "另一管理员", "SYS_ADMIN", admin);
        Login login = setPasswordAndLogin(admin, admin, "task-own-device");
        prepareTaskTemplate(admin);
        taskRow(1900000000000088711L, admin, "DRAFT", false, "=1+1");
        taskRow(1900000000000088712L, other, "APPROVED", true, "他人不应导出");
        for (String filter : List.of("\"systemTaskType\":\"INVALID\"", "\"systemTaskStatus\":\"INVALID\"",
                "\"studentId\":\"1900000000000000001\"", "\"eventType\":\"USER_CREATE\"",
                "\"dictionaryStatus\":\"ENABLED\"", "\"templateType\":\"EXPORT\"",
                "\"interfaceStatus\":\"ENABLED\"", "\"cacheDomain\":\"ALL\"", "\"columns\":[\"PAYLOAD\"]")) {
            mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(login.token()))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"exportType\":\"SYSTEM_TASK_LEDGER\",\"reason\":\"非法筛选\"," + filter + "}"))
                    .andExpect(status().isBadRequest());
        }
        for (String type : List.of("GROWTH_POINT_LEDGER", "IAM_CHANGE_AUDIT", "DICTIONARY_LEDGER", "TEMPLATE_LEDGER", "INTERFACE_SERVICE_LEDGER", "CACHE_OPERATION_LOG")) {
            mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(login.token()))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"exportType\":\"" + type + "\",\"reason\":\"混用\",\"systemTaskType\":\"CACHE_CLEAR\"}"))
                    .andExpect(status().isBadRequest());
        }
        Long id = createTaskExport(login, "\"systemTaskType\":\"CACHE_CLEAR\",\"systemTaskStatus\":\"DRAFT\",");
        assertThat(executionService.execute(claimService.claim(id, 0L))).isTrue();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(taskDownload(login, id)))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short)6);
            var row = sheet.getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("CACHE_CLEAR");
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo(admin.id().toString());
            assertThat(row.getCell(2).getStringCellValue()).isEmpty();
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo("DRAFT");
            assertThat(row.getCell(4).getStringCellValue()).isEqualTo("2026-09-26 10:00:00");
            assertThat(row.getCell(5).getStringCellValue()).isEqualTo("'=1+1");
        }
        jdbcTemplate.update("UPDATE sys_feature_toggle SET status='DISABLED' WHERE feature_code='CACHE_MANAGEMENT'");
        sqlSession.clearCache();
        assertTaskFileDenied(login, id);
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=SYSTEM_TASK_LEDGER").header("Authorization", bearer(login.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.systemTaskTypes", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("CACHE_CLEAR"))));
        jdbcTemplate.update("UPDATE sys_feature_toggle SET status='ENABLED' WHERE feature_code='CACHE_MANAGEMENT'");
        sqlSession.clearCache();
        assertThat(taskDownload(login, id)).isNotEmpty();
        userService.assignRole(new AssignRoleToUserCommand(admin.id(), roleMapper.findByCode("SYS_AUDITOR").id(), null));
        assertTaskFileDenied(login, id);
    }

    @Test
    void systemTaskLedgerAuditorAndMixedRolesExcludeDraftsAndUnsubmittedRowsAndKeepOwnJobList() throws Exception {
        User admin = createUserWithRole("task_audit_admin", "任务管理员", "SYS_ADMIN", null);
        User auditor = createUserWithRole("task_export_auditor", "任务审核员", "SYS_AUDITOR", admin);
        Login login = setPasswordAndLogin(admin, auditor, "task-audit-device");
        prepareTaskTemplate(admin);
        taskRow(1900000000000088721L, admin, "DRAFT", false, "草稿");
        taskRow(1900000000000088722L, admin, "PENDING_REVIEW", false, "未提交");
        taskRow(1900000000000088723L, admin, "APPROVED", true, "审核意见");
        jdbcTemplate.update("UPDATE sys_system_task SET reviewed_by=? WHERE id=1900000000000088723", auditor.id());
        Long id = createTaskExport(login, "");
        assertThat(executionService.execute(claimService.claim(id, 0L))).isTrue();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(taskDownload(login,id)))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(2).getStringCellValue()).isEqualTo(auditor.id().toString());
        }
        mockMvc.perform(get("/api/v1/export-jobs").header("Authorization", bearer(login.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        userService.assignRole(new AssignRoleToUserCommand(auditor.id(), roleMapper.findByCode("SYS_ADMIN").id(), null));
        assertThat(taskDownload(login,id)).isNotEmpty(); // 增加管理员角色后仍按审核员范围处理。
        Long draft = createTaskExport(login, "\"systemTaskStatus\":\"DRAFT\",");
        assertThat(executionService.execute(claimService.claim(draft, 0L))).isTrue();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(taskDownload(login,draft)))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isZero();
        }
        Login otherLogin = setPasswordAndLogin(admin, admin, "task-audit-admin-device");
        mockMvc.perform(get("/api/v1/export-jobs/{id}",id).header("Authorization",bearer(otherLogin.token())))
                .andExpect(status().isForbidden());
        jdbcTemplate.update("UPDATE sys_permission SET status='DISABLED' WHERE permission_code='CACHE_REVIEW'");
        sqlSession.clearCache();
        assertTaskFileDenied(login,id);
    }

    @Test
    void systemTaskLedgerCapturesUpperBoundAndPagesBeyondManagementLimit() throws Exception {
        User admin = createUserWithRole("task_page_admin", "任务分页管理员", "SYS_ADMIN", null);
        Login login = setPasswordAndLogin(admin, admin, "task-page-device");
        prepareTaskTemplate(admin);
        for (int i=0;i<205;i++) taskRow(1900000000000090000L+i,admin,"APPROVED",true,"记录"+i);
        Long id = createTaskExport(login, "\"systemTaskType\":\"CACHE_CLEAR\",");
        taskRow(1900000000000090300L,admin,"APPROVED",true,"上界之后不应进入");
        var scope = exportAccess.requireSystemTaskExport(admin.id());
        var request = new com.lingdong.learning.exportjob.application.adapter.ExportRequestDefinition(admin.id(),null,
                java.time.LocalDateTime.of(2026,9,26,10,0),java.time.LocalDateTime.of(2026,9,26,10,0),null,
                null,null,null,null,null,null,null,null,null,null,"CACHE_CLEAR",null,scope.auditor(),scope.types());
        long upper=1900000000000090204L;
        assertThat(taskAdapter.count(request,upper)).isEqualTo(205);
        var first = taskAdapter.fetchAfter(request,upper,0,201);
        assertThat(first.rows()).hasSize(201);
        assertThat(first.hasMore()).isTrue();
        var second=taskAdapter.fetchAfter(request,upper,first.nextCursor(),201);
        assertThat(second.rows()).hasSize(4);
        assertThat(second.hasMore()).isFalse();
        assertThat(executionService.execute(claimService.claim(id,0L))).isTrue();
        try(var workbook=new XSSFWorkbook(new java.io.ByteArrayInputStream(taskDownload(login,id)))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(205);
        }
    }

    @Test
    void systemTaskExportRequiresReadAndExportPermissionsAndRevokesQueuedScope() throws Exception {
        User admin=createUserWithRole("task_revoke_admin","任务撤权管理员","SYS_ADMIN",null);
        Login login=setPasswordAndLogin(admin,admin,"task-revoke-device");
        prepareTaskTemplate(admin);
        Long id=createTaskExport(login, "");
        jdbcTemplate.update("UPDATE sys_permission SET status='DISABLED' WHERE permission_code='CACHE_READ'");
        sqlSession.clearCache();
        assertThat(executionService.execute(claimService.claim(id,0L))).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM sys_export_job WHERE id=?",String.class,id)).isEqualTo("FAILED");
        for(String permission:List.of("SYSTEM_TASK_READ","SYSTEM_TASK_EXPORT")) {
            jdbcTemplate.update("UPDATE sys_permission SET status='DISABLED' WHERE permission_code=?",permission);
            sqlSession.clearCache();
            mockMvc.perform(get("/api/v1/export-jobs/options?exportType=SYSTEM_TASK_LEDGER").header("Authorization",bearer(login.token())))
                    .andExpect(status().isForbidden());
            jdbcTemplate.update("UPDATE sys_permission SET status='ENABLED' WHERE permission_code=?",permission);
            sqlSession.clearCache();
        }
        assertThat(jdbcTemplate.queryForList("SELECT r.role_code FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE p.permission_code='SYSTEM_TASK_EXPORT' ORDER BY r.role_code",String.class))
                .containsExactly("SYS_ADMIN","SYS_AUDITOR");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE r.role_code='SYS_AUDITOR' AND p.permission_code='EXPORT_JOB_READ'",Integer.class)).isEqualTo(1);
    }

    private void prepareTaskTemplate(User admin) throws Exception {
        jdbcTemplate.update("UPDATE sys_feature_toggle SET status='ENABLED' WHERE feature_code='CACHE_MANAGEMENT'");
        sqlSession.clearCache();
        ensureTemplateRule(admin.id());
        templateService.createTemplate(new CreateImportExportTemplateUploadCommand(admin.id(),"系统任务模板",TemplateType.EXPORT,
                "SYSTEM_TASK_REPORT","V1","tasks.xlsx",MediaType.APPLICATION_OCTET_STREAM_VALUE,
                templateFor("TASK_TYPE","SUBMITTER_ID","REVIEWER_ID","STATUS","CREATED_AT","REVIEW_COMMENT"),true,List.of()));
    }
    private void taskRow(long id,User owner,String state,boolean submitted,String comment) {
        jdbcTemplate.update("INSERT INTO sys_system_task (id,task_code,task_type,task_title,task_description,impact_scope,status,submitted_by,submitted_at,created_at,review_comment) VALUES (?,?,'CACHE_CLEAR','任务','不导出载荷','GLOBAL',?,?,?,'2026-09-26 10:00:00',?)",
                id,"TASK-"+id,state,owner.id(),submitted?java.time.LocalDateTime.of(2026,9,26,11,0):null,comment);
    }
    private Long createTaskExport(Login login,String filters) throws Exception {
        var result=mockMvc.perform(post("/api/v1/export-jobs").header("Authorization",bearer(login.token()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"exportType\":\"SYSTEM_TASK_LEDGER\","+filters+"\"startedAt\":\"2026-09-26T10:00:00\",\"endedAt\":\"2026-09-26T10:00:00\",\"reason\":\"任务盘点\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("QUEUED")).andReturn();
        return Long.valueOf(body(result).path("id").asText());
    }
    private byte[] taskDownload(Login login,Long id) throws Exception {
        return mockMvc.perform(get("/api/v1/export-jobs/{id}/download",id).header("Authorization",bearer(login.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }
    private void assertTaskFileDenied(Login login,Long id) throws Exception {
        mockMvc.perform(get("/api/v1/export-jobs/{id}/download",id).header("Authorization",bearer(login.token()))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/export-jobs/{id}",id).header("Authorization",bearer(login.token()))).andExpect(status().isForbidden());
    }

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

    @Test
    void dictionaryExportUsesFiltersAndProducesDownloadableWorkbook() throws Exception {
        User admin = createUserWithRole("dictionary_export_admin", "字典导出管理员", "SYS_ADMIN", null);
        Login login = setPasswordAndLogin(admin, admin, "dictionary-export-device");
        ensureTemplateRule(admin.id());
        templateService.createTemplate(new CreateImportExportTemplateUploadCommand(admin.id(), "字典台账模板",
                TemplateType.EXPORT, "DICTIONARY_REPORT", "V1", "dictionary.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                templateFor("TYPE_CODE", "TYPE_NAME", "ITEM_CODE", "ITEM_NAME", "SORT_ORDER", "STATUS", "UPDATED_AT"), true, List.of()));
        jdbcTemplate.update("INSERT INTO sys_dictionary_type (id,type_code,type_name,status,sort_order) VALUES (1900000000000088001,'EXPORT_SAMPLE','导出样例','ENABLED',1)");
        jdbcTemplate.update("INSERT INTO sys_dictionary_item (id,type_id,item_code,item_name,sort_order,is_default,status) VALUES (1900000000000088002,1900000000000088001,'ACTIVE_ITEM','有效样例',1,0,'ENABLED'), (1900000000000088003,1900000000000088001,'INACTIVE_ITEM','停用样例',2,0,'DISABLED')");
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=DICTIONARY_LEDGER").header("Authorization",bearer(login.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.students").isEmpty());
        var created = mockMvc.perform(post("/api/v1/export-jobs").header("Authorization",bearer(login.token()))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"exportType":"DICTIONARY_LEDGER","dictionaryTypeCode":"EXPORT_SAMPLE","dictionaryStatus":"ENABLED","reason":"核对字典台账"}
                """)).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("QUEUED")).andReturn();
        Long id=Long.valueOf(body(created).path("id").asText());
        assertThat(executionService.execute(claimService.claim(id,0L))).isTrue();
        byte[] content=mockMvc.perform(get("/api/v1/export-jobs/{id}/download",id).header("Authorization",bearer(login.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(content))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(2).getStringCellValue()).isEqualTo("ACTIVE_ITEM");
        }
        userService.assignRole(new AssignRoleToUserCommand(admin.id(),roleMapper.findByCode("SYS_AUDITOR").id(),null));
        mockMvc.perform(get("/api/v1/export-jobs/{id}/download",id).header("Authorization",bearer(login.token())))
                .andExpect(status().isForbidden());
    }

    @Autowired
    private com.lingdong.learning.exportjob.application.adapter.DictionaryLedgerExportAdapter dictionaryAdapter;

    @Test
    void dictionaryCursorRetainsDisabledRowsAndFreezesUpperBoundWithDateFilters() {
        jdbcTemplate.update("INSERT INTO sys_dictionary_type (id,type_code,type_name,status,sort_order) VALUES (1900000000000088011,'CURSOR_SAMPLE','分页样例','DISABLED',1)");
        jdbcTemplate.update("""
                INSERT INTO sys_dictionary_item (id,type_id,item_code,item_name,sort_order,is_default,status,updated_at) VALUES
                (1900000000000088012,1900000000000088011,'FIRST','第一项',1,0,'ENABLED','2026-09-19 10:00:00'),
                (1900000000000088013,1900000000000088011,'SECOND','第二项',2,0,'DISABLED','2026-09-19 11:00:00'),
                (1900000000000088014,1900000000000088011,'OLD','范围外',3,0,'ENABLED','2026-09-18 10:00:00')
                """);
        var request = new com.lingdong.learning.exportjob.application.adapter.ExportRequestDefinition(null, null,
                java.time.LocalDateTime.parse("2026-09-19T10:00:00"), java.time.LocalDateTime.parse("2026-09-19T11:00:00"),
                null, "CURSOR_SAMPLE", null);
        long upperBound = dictionaryAdapter.captureUpperBound(request);
        jdbcTemplate.update("INSERT INTO sys_dictionary_item (id,type_id,item_code,item_name,sort_order,is_default,status,updated_at) VALUES (1900000000000088015,1900000000000088011,'LATER','后新增',4,0,'ENABLED','2026-09-19 10:30:00')");
        assertThat(dictionaryAdapter.count(request, upperBound)).isEqualTo(2);
        var first = dictionaryAdapter.fetchAfter(request, upperBound, 0, 1);
        assertThat(first.hasMore()).isTrue();
        assertThat(first.rows().get(0)).containsEntry("ITEM_CODE", "FIRST");
        var second = dictionaryAdapter.fetchAfter(request, upperBound, first.nextCursor(), 1);
        assertThat(second.hasMore()).isFalse();
        assertThat(second.rows().get(0)).containsEntry("ITEM_CODE", "SECOND").containsEntry("STATUS", "停用");
    }

    @Autowired
    private com.lingdong.learning.exportjob.application.adapter.CacheOperationLogExportAdapter cacheAdapter;

    @Test
    void cacheLogPagesByUpperBoundAndKeepsActualExecutor() {
        User admin = createUserWithRole("cache_cursor_admin", "缓存分页管理员", "SYS_ADMIN", null);
        for (int index=1; index<=206; index++) {
            jdbcTemplate.update("""
                    INSERT INTO sys_cache_operation_log
                    (id,operation_code,cache_domain,operation_type,status,impact_description,requested_by,executed_by,created_at)
                    VALUES (?,?,'DICTIONARY','REFRESH','FAILED','不导出',?,?,?)
                    """, 1900000000000097000L+index,"CACHE-CURSOR-"+index,admin.id(),admin.id(),
                    index==206?"2026-09-25 10:00:00":"2026-09-26 10:00:00");
        }
        var request = new com.lingdong.learning.exportjob.application.adapter.ExportRequestDefinition(admin.id(),null,
                java.time.LocalDateTime.parse("2026-09-26T10:00:00"),java.time.LocalDateTime.parse("2026-09-26T10:00:00"),
                null,null,null,null,null,null,null,null,null,"DICTIONARY","FAILED");
        long upper=cacheAdapter.captureUpperBound(request);
        jdbcTemplate.update("""
                INSERT INTO sys_cache_operation_log
                (id,operation_code,cache_domain,operation_type,status,impact_description,requested_by,created_at)
                VALUES (1900000000000097300,'CACHE-LATE','DICTIONARY','REFRESH','FAILED','不导出',?,'2026-09-26 10:00:00')
                """,admin.id());
        assertThat(cacheAdapter.count(request,upper)).isEqualTo(205);
        int total=0;long cursor=0;boolean more;
        do {
            var page=cacheAdapter.fetchAfter(request,upper,cursor,100);
            total+=page.rows().size();
            for(var row:page.rows()) {
                assertThat(row.keySet()).containsExactlyInAnyOrder("CACHE_DOMAIN","MODULE","OPERATION","OPERATOR_ID","OCCURRED_AT","RESULT");
                assertThat(row).containsEntry("OPERATOR_ID",admin.id().toString()).containsEntry("RESULT","失败");
            }
            more=page.hasMore();cursor=page.nextCursor();
        } while(more);
        assertThat(total).isEqualTo(205);
    }

    @Test
    void cacheLogExportsSixColumnsWithoutInventingExecutor() throws Exception {
        User admin = createUserWithRole("cache_export_admin", "缓存导出管理员", "SYS_ADMIN", null);
        Login login = setPasswordAndLogin(admin, admin, "cache-export-device");
        ensureTemplateRule(admin.id());
        templateService.createTemplate(new CreateImportExportTemplateUploadCommand(admin.id(), "缓存日志模板",
                TemplateType.EXPORT, "CACHE_REPORT", "V1", "cache.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                templateFor("CACHE_DOMAIN", "MODULE", "OPERATION", "OPERATOR_ID", "OCCURRED_AT", "RESULT"), true, List.of()));
        jdbcTemplate.update("""
                INSERT INTO sys_cache_operation_log
                (id,operation_code,cache_domain,operation_type,status,impact_description,requested_by,created_at)
                VALUES (1900000000000088601,'CACHE-EXPORT','USER_SESSION','CLEAR','PENDING','不应导出自由文本',?,'2026-09-26 10:00:00')
                """, admin.id());
        for (String filter : List.of("\"cacheDomain\":\"INVALID\"", "\"cacheStatus\":\"INVALID\"",
                "\"studentId\":\"1900000000000000001\"", "\"eventType\":\"USER_CREATE\"",
                "\"dictionaryTypeCode\":\"REPORT\"", "\"templateType\":\"EXPORT\"", "\"interfaceStatus\":\"ENABLED\"")) {
            mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(login.token()))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"exportType\":\"CACHE_OPERATION_LOG\",\"reason\":\"非法筛选\"," + filter + "}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(login.token()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"exportType\":\"INTERFACE_SERVICE_LEDGER\",\"cacheStatus\":\"FAILED\",\"reason\":\"混用\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=CACHE_OPERATION_LOG").header("Authorization", bearer(login.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.columns.length()").value(6));
        var created = mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(login.token()))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"exportType":"CACHE_OPERATION_LOG","cacheDomain":"USER_SESSION","cacheStatus":"PENDING",
                "startedAt":"2026-09-26T10:00:00","endedAt":"2026-09-26T10:00:00","reason":"缓存日志盘点"}
                """)).andExpect(status().isCreated()).andReturn();
        Long id = Long.valueOf(body(created).path("id").asText());
        assertThat(executionService.execute(claimService.claim(id, 0L))).isTrue();
        byte[] content = mockMvc.perform(get("/api/v1/export-jobs/{id}/download", id).header("Authorization", bearer(login.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(content))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 6);
            var row = sheet.getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("USER_SESSION");
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("用户会话");
            assertThat(row.getCell(2).getStringCellValue()).contains("强制退出");
            assertThat(row.getCell(3).getStringCellValue()).isEmpty();
            assertThat(row.getCell(4).getStringCellValue()).contains("2026-09-26");
            assertThat(row.getCell(5).getStringCellValue()).isEqualTo("待处理");
        }
        userService.assignRole(new AssignRoleToUserCommand(admin.id(), roleMapper.findByCode("SYS_AUDITOR").id(), null));
        mockMvc.perform(get("/api/v1/export-jobs/{id}/download", id).header("Authorization", bearer(login.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/export-jobs/{id}", id).header("Authorization", bearer(login.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void interfaceLedgerExportsSixPublicColumnsAndRejectsInvalidFilters() throws Exception {
        User admin = createUserWithRole("interface_export_admin", "接口导出管理员", "SYS_ADMIN", null);
        Login login = setPasswordAndLogin(admin, admin, "interface-export-device");
        ensureTemplateRule(admin.id());
        templateService.createTemplate(new CreateImportExportTemplateUploadCommand(admin.id(), "接口台账模板",
                TemplateType.EXPORT, "INTERFACE_REPORT", "V1", "interface.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                templateFor("SERVICE_NAME", "PURPOSE", "CALLER_NAME", "AUTHORIZATION_SCOPE", "STATUS", "OWNER_ID"), true, List.of()));
        jdbcTemplate.update("""
                INSERT INTO sys_interface_service
                (id,service_name,direction,purpose,caller_name,authorization_scope,authorization_scope_value,owner_id,status,updated_at)
                VALUES (1900000000000088501,'接口样例','OUTBOUND','SMS','ExportCaller','SCHOOL','1900000000000000001',?,'DISABLED','2026-09-19 10:00:00')
                """, admin.id());
        for (String filter : List.of(
                "\"interfaceStatus\":\"UNKNOWN\"", "\"interfaceOwnerId\":\"123\"",
                "\"interfaceOwnerId\":1900000000000000001",
                "\"interfaceOwnerId\":\"9999999999999999999\"", "\"interfaceOwnerId\":\"1e18\"",
                "\"studentId\":\"1900000000000000001\"", "\"eventType\":\"USER_CREATE\"",
                "\"templateType\":\"EXPORT\"", "\"dictionaryTypeCode\":\"REPORT\"", "\"columns\":[\"SECRET\"]")) {
            mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(login.token()))
                    .contentType(MediaType.APPLICATION_JSON).content(
                            "{\"exportType\":\"INTERFACE_SERVICE_LEDGER\",\"reason\":\"非法筛选\"," + filter + "}"))
                    .andExpect(status().isBadRequest());
        }
        for (String type : List.of("TEMPLATE_LEDGER", "DICTIONARY_LEDGER", "IAM_CHANGE_AUDIT", "GROWTH_POINT_LEDGER")) {
            mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(login.token()))
                    .contentType(MediaType.APPLICATION_JSON).content(
                            "{\"exportType\":\"" + type + "\",\"reason\":\"非法混用\",\"interfaceCallerName\":\"Export\"}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=INTERFACE_SERVICE_LEDGER").header("Authorization", bearer(login.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.students").isEmpty())
                .andExpect(jsonPath("$.columns.length()").value(6));
        var created = mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(login.token()))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"exportType":"INTERFACE_SERVICE_LEDGER","interfaceCallerName":" exportcaller ","interfaceStatus":"DISABLED",
                 "interfaceOwnerId":"%s","startedAt":"2026-09-19T10:00:00","endedAt":"2026-09-19T10:00:00","reason":"接口盘点"}
                """.formatted(admin.id()))).andExpect(status().isCreated()).andReturn();
        Long id = Long.valueOf(body(created).path("id").asText());
        assertThat(executionService.execute(claimService.claim(id, 0L))).isTrue();
        byte[] content = mockMvc.perform(get("/api/v1/export-jobs/{id}/download", id).header("Authorization", bearer(login.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(content))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 6);
            var row = sheet.getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("接口样例");
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("SMS");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("ExportCaller");
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo("SCHOOL: 1900000000000000001");
            assertThat(row.getCell(4).getStringCellValue()).isEqualTo("停用");
            assertThat(row.getCell(5).getStringCellValue()).isEqualTo(admin.id().toString());
        }
        userService.assignRole(new AssignRoleToUserCommand(admin.id(), roleMapper.findByCode("SYS_AUDITOR").id(), null));
        mockMvc.perform(get("/api/v1/export-jobs/{id}/download", id).header("Authorization", bearer(login.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/export-jobs/{id}", id).header("Authorization", bearer(login.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=INTERFACE_SERVICE_LEDGER").header("Authorization", bearer(login.token())))
                .andExpect(status().isForbidden());
    }

    @Autowired
    private com.lingdong.learning.exportjob.application.adapter.InterfaceServiceLedgerExportAdapter interfaceAdapter;

    @Test
    void interfaceLedgerPagesPastTwoHundredWithTimeBoundaryAndUpperBound() {
        User admin = createUserWithRole("interface_cursor_admin", "接口分页管理员", "SYS_ADMIN", null);
        for (int index = 1; index <= 206; index++) {
            jdbcTemplate.update("""
                    INSERT INTO sys_interface_service
                    (id,service_name,direction,purpose,caller_name,authorization_scope,owner_id,status,updated_at)
                    VALUES (?,?,'OUTBOUND','SMS','CursorCaller','GLOBAL',?,'DISABLED',?)
                    """, 1900000000000099500L + index, "分页样例" + index, admin.id(),
                    index == 206 ? "2026-09-18 10:00:00" : "2026-09-19 10:00:00");
        }
        var request = new com.lingdong.learning.exportjob.application.adapter.ExportRequestDefinition(admin.id(), null,
                java.time.LocalDateTime.parse("2026-09-19T10:00:00"), java.time.LocalDateTime.parse("2026-09-19T10:00:00"),
                null, null, null, null, null, null, "cursorcaller", "DISABLED", admin.id());
        long upperBound = interfaceAdapter.captureUpperBound(request);
        jdbcTemplate.update("""
                INSERT INTO sys_interface_service
                (id,service_name,direction,purpose,caller_name,authorization_scope,owner_id,status,updated_at)
                VALUES (1900000000000099900,'后新增','OUTBOUND','SMS','CursorCaller','GLOBAL',?,'DISABLED','2026-09-19 10:00:00')
                """, admin.id());
        assertThat(interfaceAdapter.count(request, upperBound)).isEqualTo(205);
        long cursor = 0;
        int total = 0;
        boolean more;
        do {
            var page = interfaceAdapter.fetchAfter(request, upperBound, cursor, 100);
            total += page.rows().size();
            for (var row : page.rows()) {
                assertThat(row.keySet()).containsExactlyInAnyOrder("SERVICE_NAME", "PURPOSE", "CALLER_NAME", "AUTHORIZATION_SCOPE", "STATUS", "OWNER_ID");
                assertThat(row).containsEntry("STATUS", "停用").containsEntry("AUTHORIZATION_SCOPE", "GLOBAL")
                        .containsEntry("OWNER_ID", admin.id().toString());
            }
            more = page.hasMore();
            cursor = page.nextCursor();
        } while (more);
        assertThat(total).isEqualTo(205);
    }

    @Test
    void templateLedgerExportsOnlyFilteredMetadataAndRejectsAuditorDownload() throws Exception {
        User admin = createUserWithRole("template_ledger_admin", "模板台账管理员", "SYS_ADMIN", null);
        Login login = setPasswordAndLogin(admin, admin, "template-ledger-device");
        ensureTemplateRule(admin.id());
        templateService.createTemplate(new CreateImportExportTemplateUploadCommand(admin.id(), "模板台账导出模板",
                TemplateType.EXPORT, "TEMPLATE_REPORT", "V1", "ledger.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                templateFor("TEMPLATE_NAME", "TEMPLATE_TYPE", "MODULE_CODE", "VERSION", "STATUS", "UPDATED_AT"), true, List.of()));
        var sample = templateService.createTemplate(new CreateImportExportTemplateUploadCommand(admin.id(), "停用报表样例",
                TemplateType.EXPORT, "REPORT", "LEDGER_SAMPLE", "sample.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                exportTemplate(), false, List.of()));
        templateService.disableTemplate(admin.id(), sample.id(), sample.versionNo());
        for (String filter : List.of(
                "\"templateStatus\":\"UNKNOWN\"",
                "\"templateType\":\"OTHER\"",
                "\"studentId\":\"1900000000000000001\"",
                "\"dictionaryTypeCode\":\"REPORT\"",
                "\"columns\":[\"FILE_ID\"]")) {
            mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(login.token()))
                    .contentType(MediaType.APPLICATION_JSON).content(
                            "{\"exportType\":\"TEMPLATE_LEDGER\",\"reason\":\"非法筛选校验\"," + filter + "}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=TEMPLATE_LEDGER").header("Authorization", bearer(login.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.students").isEmpty());
        var created = mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(login.token()))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"exportType":"TEMPLATE_LEDGER","templateType":"EXPORT","templateModuleCode":"REPORT","templateStatus":"DISABLED","reason":"模板盘点"}
                """)).andExpect(status().isCreated()).andReturn();
        Long id = Long.valueOf(body(created).path("id").asText());
        assertThat(executionService.execute(claimService.claim(id, 0L))).isTrue();
        byte[] content = mockMvc.perform(get("/api/v1/export-jobs/{id}/download", id).header("Authorization", bearer(login.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(content))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 6);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("停用报表样例");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("导出");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("REPORT");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("停用");
        }
        userService.assignRole(new AssignRoleToUserCommand(admin.id(), roleMapper.findByCode("SYS_AUDITOR").id(), null));
        mockMvc.perform(get("/api/v1/export-jobs/{id}/download", id).header("Authorization", bearer(login.token())))
                .andExpect(status().isForbidden());
    }

    @Autowired
    private com.lingdong.learning.exportjob.application.adapter.TemplateLedgerExportAdapter templateAdapter;

    @Test
    void templateLedgerPagesBeyondManagementLimitAndFreezesUpperBound() throws Exception {
        User admin = createUserWithRole("template_cursor_admin", "模板分页管理员", "SYS_ADMIN", null);
        ensureTemplateRule(admin.id());
        var seed = templateService.createTemplate(new CreateImportExportTemplateUploadCommand(admin.id(), "分页种子",
                TemplateType.EXPORT, "REPORT", "CURSOR_SEED", "seed.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                exportTemplate(), false, List.of()));
        for (int index = 1; index <= 206; index++) {
            jdbcTemplate.update("""
                    INSERT INTO sys_import_export_template
                    (id,template_name,template_type,module_code,version,file_id,is_default,default_scope_key,status,updated_at)
                    SELECT ?, '分页样例', 'EXPORT', 'CURSOR_SAMPLE', ?, file_id, 0, ?, 'DISABLED', ?
                    FROM sys_import_export_template WHERE id = ?
                    """, 1900000000000099000L + index, "V" + index, "ROW_" + index,
                    index == 206 ? "2026-09-18 10:00:00" : "2026-09-19 10:00:00", seed.id());
        }
        var request = new com.lingdong.learning.exportjob.application.adapter.ExportRequestDefinition(admin.id(), null,
                java.time.LocalDateTime.parse("2026-09-19T10:00:00"), java.time.LocalDateTime.parse("2026-09-19T10:00:00"),
                null, null, null, "EXPORT", "CURSOR_SAMPLE", "DISABLED");
        long upperBound = templateAdapter.captureUpperBound(request);
        jdbcTemplate.update("""
                INSERT INTO sys_import_export_template
                (id,template_name,template_type,module_code,version,file_id,is_default,default_scope_key,status,updated_at)
                SELECT 1900000000000099300, '后新增', 'EXPORT', 'CURSOR_SAMPLE', 'LATER', file_id, 0,
                    'LATER', 'DISABLED', '2026-09-19 10:00:00' FROM sys_import_export_template WHERE id = ?
                """, seed.id());
        assertThat(templateAdapter.count(request, upperBound)).isEqualTo(205);
        long cursor = 0;
        int total = 0;
        boolean more;
        do {
            var page = templateAdapter.fetchAfter(request, upperBound, cursor, 100);
            total += page.rows().size();
            for (var row : page.rows()) {
                assertThat(row.keySet()).containsExactlyInAnyOrder("TEMPLATE_NAME", "TEMPLATE_TYPE", "MODULE_CODE", "VERSION", "STATUS", "UPDATED_AT");
                assertThat(row).containsEntry("TEMPLATE_NAME", "分页样例").containsEntry("STATUS", "停用");
            }
            more = page.hasMore();
            if (page.nextCursor() != null) {
                assertThat(page.nextCursor()).isGreaterThan(cursor);
                cursor = page.nextCursor();
            }
            assertThat(total).isLessThanOrEqualTo(205);
        } while (more);
        assertThat(total).isEqualTo(205);
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
        return templateFor("OCCURRED_AT", "EVENT_TYPE", "TARGET_TYPE", "TARGET_ID", "TARGET_NAME", "OPERATOR_NAME", "BEFORE_SUMMARY", "AFTER_SUMMARY", "RESULT");
    }

    private byte[] templateFor(String... columns) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var row = workbook.createSheet("权限日志").createRow(0);
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
