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
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** H2/MyBatis 专项：主副家长、教师行级、机构范围、混合审核员、冻结撤权、净积分与最新审核，XLSX 与 PDF 真实文件。 */
@SpringBootTest(properties = "lingdong.export-job.scheduling-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentTaskExportApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationService;
    @Autowired private UserAccessApplicationService userService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private AttachmentRuleApplicationService ruleService;
    @Autowired private ImportExportTemplateApplicationService templateService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private org.mybatis.spring.SqlSessionTemplate sqlSession;
    @Autowired private com.lingdong.learning.exportjob.application.ExportJobClaimService claimService;
    @Autowired private com.lingdong.learning.exportjob.application.ExportJobExecutionService executionService;

    @Test
    void parentWorkbookCoversBothGuardiansWithPointsAndLatestReviewThenRevokesScope() throws Exception {
        var f = parentFixture();
        task(1900000000000120001L, "FAMILY", null, f.primary().id(), "已发布家庭任务", "PUBLISHED");
        assignment(1900000000000130001L, 1900000000000120001L, f.studentId(), "FAMILY", "COMPLETED", f.primary().id(), java.time.LocalDate.of(2026, 9, 29));
        assignment(1900000000000130004L, 1900000000000120001L, f.studentId(), "FAMILY", "IN_PROGRESS", f.primary().id(), java.time.LocalDate.of(2026, 9, 28));
        checkin(1900000000000140001L, 1900000000000130001L, 1, "APPROVED", f.admin().id());
        checkin(1900000000000140002L, 1900000000000130001L, 2, "SUBMITTED", f.admin().id());
        reward(1900000000000150001L, f.studentId(), 1900000000000120001L, 1900000000000130001L, f.admin().id(), 20);
        correction(1900000000000150002L, f.studentId(), 1900000000000130001L, -5, 1900000000000150001L, f.admin().id());
        task(1900000000000120002L, "FAMILY", null, f.primary().id(), "本人草稿家庭任务", "DRAFT");
        assignment(1900000000000130002L, 1900000000000120002L, f.studentId(), "FAMILY", "PENDING_CLAIM", f.primary().id());
        task(1900000000000120003L, "FAMILY", null, f.foreignParent().id(), "他人已发布任务", "PUBLISHED");
        assignment(1900000000000130003L, 1900000000000120003L, f.studentId(), "FAMILY", "IN_PROGRESS", f.foreignParent().id());
        task(1900000000000120004L, "FAMILY", null, f.foreignParent().id(), "他人草稿不可见", "DRAFT");
        assignment(1900000000000130005L, 1900000000000120004L, f.studentId(), "FAMILY", "PENDING_CLAIM", f.foreignParent().id());
        task(1900000000000120005L, "FAMILY", null, f.primary().id(), "无关系学生不导出", "PUBLISHED");
        assignment(1900000000000130006L, 1900000000000120005L, f.foreignStudentId(), "FAMILY", "IN_PROGRESS", f.primary().id());

        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=STUDENT_TASK_REPORT").header("Authorization", bearer(f.primaryLogin().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.columns.length()").value(6))
                .andExpect(jsonPath("$.students.length()").value(1))
                .andExpect(jsonPath("$.students[0].id").value(Long.toString(f.studentId())));
        long primaryJob = createReport(f.primaryLogin(), null, null, "XLSX", "主家长导出任务报表");
        // 冻结后新增的实例不进入旧作业。
        assignment(1900000000000130099L, 1900000000000120001L, f.studentId(), "FAMILY", "PENDING_REVIEW", f.primary().id(), java.time.LocalDate.of(2026, 9, 27));
        long secondaryJob = createReport(f.secondaryLogin(), null, null, "XLSX", "副家长导出任务报表");
        assertThat(executionService.execute(claimService.claim(primaryJob, 0L))).isTrue();
        assertThat(executionService.execute(claimService.claim(secondaryJob, 0L))).isTrue();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(taskDownload(f.primaryLogin(), primaryJob)))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(4);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 6);
            var row = sheet.getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("学*");
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("已发布家庭任务");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("家庭");
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo("已完成");
            assertThat(row.getCell(4).getNumericCellValue()).isEqualTo(15);
            assertThat(row.getCell(5).getStringCellValue()).isEqualTo("待审核");
            assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("待认领");
            assertThat(sheet.getRow(2).getCell(5).getStringCellValue()).isEqualTo("未提交");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("他人已发布任务");
            assertThat(sheet.getRow(4).getCell(1).getStringCellValue()).isEqualTo("已发布家庭任务");
            assertThat(sheet.getRow(4).getCell(3).getStringCellValue()).isEqualTo("进行中");
        }
        assertThat(taskDownload(f.secondaryLogin(), secondaryJob)).isNotEmpty();

        jdbcTemplate.update("UPDATE edu_parent_student SET status='INACTIVE' WHERE parent_user_id=?", f.secondary().id());
        sqlSession.clearCache();
        assertTaskFileDenied(f.secondaryLogin(), secondaryJob);
        assertThat(taskDownload(f.primaryLogin(), primaryJob)).isNotEmpty();
        jdbcTemplate.update("UPDATE edu_parent_student SET status='ACTIVE' WHERE parent_user_id=?", f.secondary().id());
        sqlSession.clearCache();
        assertThat(taskDownload(f.secondaryLogin(), secondaryJob)).isNotEmpty();

        mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(f.primaryLogin().token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"STUDENT_TASK_REPORT\",\"studentId\":\"" + f.foreignStudentId() + "\",\"reason\":\"无关系学生必须拒绝\"}"))
                .andExpect(status().isForbidden());
        userService.assignRole(new AssignRoleToUserCommand(f.primary().id(), roleMapper.findByCode("SYS_AUDITOR").id(), null));
        assertTaskFileDenied(f.primaryLogin(), primaryJob);
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=STUDENT_TASK_REPORT").header("Authorization", bearer(f.primaryLogin().token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherExportsOwnAndPublishedOrganizationRowsForActiveClassStudentsOnly() throws Exception {
        var f = teacherFixture();
        task(1900000000000120101L, "TEACHER", null, f.teacher().id(), "本人教师任务", "PUBLISHED");
        assignment(1900000000000130101L, 1900000000000120101L, f.studentId(), "TEACHER", "IN_PROGRESS", f.teacher().id());
        task(1900000000000120102L, "TEACHER", null, f.otherTeacher().id(), "他人教师任务不导出", "PUBLISHED");
        assignment(1900000000000130102L, 1900000000000120102L, f.studentId(), "TEACHER", "IN_PROGRESS", f.otherTeacher().id());
        task(1900000000000120103L, "ORGANIZATION", f.schoolId(), f.admin().id(), "已发布机构任务", "PUBLISHED");
        assignment(1900000000000130103L, 1900000000000120103L, f.studentId(), "ORGANIZATION", "PENDING_REVIEW", f.admin().id());
        checkin(1900000000000140103L, 1900000000000130103L, 1, "REJECTED", f.admin().id());
        task(1900000000000120104L, "ORGANIZATION", f.schoolId(), f.admin().id(), "草稿机构任务不导出", "DRAFT");
        assignment(1900000000000130104L, 1900000000000120104L, f.studentId(), "ORGANIZATION", "PENDING_CLAIM", f.admin().id());
        task(1900000000000120105L, "TEACHER", null, f.teacher().id(), "非本班学生不导出", "PUBLISHED");
        assignment(1900000000000130105L, 1900000000000120105L, f.outsideStudentId(), "TEACHER", "IN_PROGRESS", f.teacher().id());
        task(1900000000000120106L, "FAMILY", null, f.admin().id(), "家庭任务对教师不可见", "PUBLISHED");
        assignment(1900000000000130106L, 1900000000000120106L, f.studentId(), "FAMILY", "IN_PROGRESS", f.admin().id());

        long id = createReport(f.teacherLogin(), null, null, "XLSX", "教师导出任务报表");
        assertThat(executionService.execute(claimService.claim(id, 0L))).isTrue();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(taskDownload(f.teacherLogin(), id)))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("本人教师任务");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("教师");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("已发布机构任务");
            assertThat(sheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo("机构");
            assertThat(sheet.getRow(2).getCell(5).getStringCellValue()).isEqualTo("已驳回");
        }
        long queued = createReport(f.teacherLogin(), null, null, "XLSX", "排队撤权验证");
        jdbcTemplate.update("UPDATE edu_teacher_class SET status='INACTIVE' WHERE teacher_user_id=?", f.teacher().id());
        sqlSession.clearCache();
        assertThat(executionService.execute(claimService.claim(queued, 0L))).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM sys_export_job WHERE id=?", String.class, queued)).isEqualTo("FAILED");
        assertTaskFileDenied(f.teacherLogin(), id);
        jdbcTemplate.update("UPDATE edu_teacher_class SET status='ACTIVE' WHERE teacher_user_id=?", f.teacher().id());
        sqlSession.clearCache();
        assertThat(taskDownload(f.teacherLogin(), id)).isNotEmpty();
        userService.assignRole(new AssignRoleToUserCommand(f.teacher().id(), roleMapper.findByCode("SYS_AUDITOR").id(), null));
        assertTaskFileDenied(f.teacherLogin(), id);
    }

    @Test
    void organizationAdminExportsOrganizationSourceOnlyAndExcludesFamily() throws Exception {
        var f = organizationFixture();
        task(1900000000000120201L, "ORGANIZATION", f.schoolId(), f.admin().id(), "范围内机构任务", "PUBLISHED");
        assignment(1900000000000130201L, 1900000000000120201L, f.studentId(), "ORGANIZATION", "COMPLETED", f.admin().id());
        task(1900000000000120202L, "FAMILY", null, f.admin().id(), "家庭任务排除", "PUBLISHED");
        assignment(1900000000000130202L, 1900000000000120202L, f.studentId(), "FAMILY", "COMPLETED", f.admin().id());
        task(1900000000000120203L, "ORGANIZATION", f.outsideSchoolId(), f.admin().id(), "范围外机构任务", "PUBLISHED");
        assignment(1900000000000130203L, 1900000000000120203L, f.studentId(), "ORGANIZATION", "COMPLETED", f.admin().id());
        task(1900000000000120204L, "TEACHER", null, f.admin().id(), "教师任务对机构不可见", "PUBLISHED");
        assignment(1900000000000130204L, 1900000000000120204L, f.studentId(), "TEACHER", "IN_PROGRESS", f.admin().id());

        long id = createReport(f.orgLogin(), null, null, "XLSX", "机构导出任务报表");
        assertThat(executionService.execute(claimService.claim(id, 0L))).isTrue();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(taskDownload(f.orgLogin(), id)))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("范围内机构任务");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("机构");
        }
        long queued = createReport(f.orgLogin(), null, null, "XLSX", "机构排队撤权验证");
        jdbcTemplate.update("DELETE FROM sys_user_organization WHERE user_id=?", f.orgAdmin().id());
        sqlSession.clearCache();
        assertThat(executionService.execute(claimService.claim(queued, 0L))).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM sys_export_job WHERE id=?", String.class, queued)).isEqualTo("FAILED");
        assertTaskFileDenied(f.orgLogin(), id);
    }

    @Test
    void pdfOutputRendersTableAndFormatIsRestrictedToThisDataset() throws Exception {
        var f = parentFixture();
        task(1900000000000120301L, "FAMILY", null, f.primary().id(), "PDF 家庭任务", "PUBLISHED");
        assignment(1900000000000130301L, 1900000000000120301L, f.studentId(), "FAMILY", "PENDING_REVIEW", f.primary().id());
        checkin(1900000000000140301L, 1900000000000130301L, 1, "SUBMITTED", f.admin().id());
        for (String request : List.of(
                "{\"exportType\":\"STUDENT_TASK_REPORT\",\"outputFormat\":\"DOCX\",\"reason\":\"非法格式\"}",
                "{\"exportType\":\"GROWTH_POINT_LEDGER\",\"outputFormat\":\"PDF\",\"reason\":\"他类不支持 PDF\"}",
                "{\"exportType\":\"DICTIONARY_LEDGER\",\"studentTaskSource\":\"FAMILY\",\"reason\":\"他类不支持任务筛选\"}",
                "{\"exportType\":\"STUDENT_TASK_REPORT\",\"studentTaskSource\":\"INVALID\",\"reason\":\"非法来源\"}",
                "{\"exportType\":\"STUDENT_TASK_REPORT\",\"studentTaskStatus\":\"INVALID\",\"reason\":\"非法状态\"}",
                "{\"exportType\":\"STUDENT_TASK_REPORT\",\"studentId\":\"123\",\"reason\":\"非法学生标识\"}")) {
            mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(f.primaryLogin().token()))
                            .contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest());
        }
        long id = createReport(f.primaryLogin(), null, null, "PDF", "家长 PDF 任务报表");
        assertThat(executionService.execute(claimService.claim(id, 0L))).isTrue();
        MvcResult result = mockMvc.perform(get("/api/v1/export-jobs/{id}/download", id).header("Authorization", bearer(f.primaryLogin().token())))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentType()).isEqualTo("application/pdf");
        try (var document = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("学生任务报表", "学*", "PDF 家庭任务", "家庭", "待审核");
        }
    }

    @Test
    void reportFreezesUpperBoundAcross205RowsAndGrantsExactlyThreeRoles() throws Exception {
        var f = parentFixture();
        task(1900000000000120401L, "FAMILY", null, f.primary().id(), "批量家庭任务", "PUBLISHED");
        for (int i = 0; i < 205; i++) {
            assignment(1900000000000140000L + i, 1900000000000120401L, f.studentId(), "FAMILY", "IN_PROGRESS", f.primary().id(), java.time.LocalDate.of(2026, 1, 1).plusDays(i));
        }
        long id = createReport(f.primaryLogin(), "FAMILY", "IN_PROGRESS", "XLSX", "批量任务报表");
        assignment(1900000000000149999L, 1900000000000120401L, f.studentId(), "FAMILY", "IN_PROGRESS", f.primary().id(), java.time.LocalDate.of(2026, 9, 27));
        assertThat(executionService.execute(claimService.claim(id, 0L))).isTrue();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(taskDownload(f.primaryLogin(), id)))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(205);
        }
        assertThat(jdbcTemplate.queryForList("SELECT r.role_code FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE p.permission_code='STUDENT_TASK_REPORT_EXPORT'", String.class))
                .containsExactlyInAnyOrder("PARENT", "TEACHER", "ORG_ADMIN");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_dictionary_item WHERE item_code='STUDENT_TASK_REPORT_EXPORT'", Integer.class)).isEqualTo(1);
    }

    private ParentFixture parentFixture() throws Exception {
        var admin = createUserWithRole("student_task_admin", "任务报表管理员", "SYS_ADMIN", null);
        var primary = createUserWithRole("student_task_primary", "主家长", "PARENT", admin);
        var secondary = createUserWithRole("student_task_secondary", "副家长", "PARENT", admin);
        var foreignParent = createUserWithRole("student_task_foreign", "无关家长", "PARENT", admin);
        var primaryLogin = setPasswordAndLogin(admin, primary, "student-task-primary");
        var secondaryLogin = setPasswordAndLogin(admin, secondary, "student-task-secondary");
        ensureTemplateRule(admin.id());
        templateService.createTemplate(new CreateImportExportTemplateUploadCommand(admin.id(), "学生任务报表模板", TemplateType.EXPORT,
                "STUDENT_TASK_REPORT_EXPORT", "V1", "student-tasks.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                templateFor("STUDENT_NAME", "TASK_TITLE", "SOURCE_TYPE", "STATUS", "POINTS", "REVIEW_STATUS"), true, List.of()));
        long student = 1900000000000110001L, foreignStudent = 1900000000000110002L, account = 1900000000000110003L;
        jdbcTemplate.update("INSERT INTO edu_student(id,student_name,status) VALUES(?,'学生甲','ENABLED')", student);
        jdbcTemplate.update("INSERT INTO edu_student(id,student_name,status) VALUES(?,'无关学生','ENABLED')", foreignStudent);
        jdbcTemplate.update("INSERT INTO edu_parent_student(id,parent_user_id,student_id,relation_role,status,primary_scope_key) VALUES(1900000000000110010,?,?,'PRIMARY_GUARDIAN','ACTIVE','PRIMARY')", primary.id(), student);
        jdbcTemplate.update("INSERT INTO edu_parent_student(id,parent_user_id,student_id,relation_role,status,primary_scope_key) VALUES(1900000000000110011,?,?,'SECONDARY_GUARDIAN','ACTIVE','SECONDARY')", secondary.id(), student);
        jdbcTemplate.update("INSERT INTO growth_point_account(id,student_id,total_points,available_points) VALUES(?,?,15,15)", account, student);
        return new ParentFixture(admin, primary, secondary, foreignParent, primaryLogin, secondaryLogin, student, foreignStudent);
    }

    private TeacherFixture teacherFixture() throws Exception {
        var admin = createUserWithRole("student_task_school_admin", "机构管理员", "SYS_ADMIN", null);
        var teacher = createUserWithRole("student_task_teacher", "教师甲", "TEACHER", admin);
        var otherTeacher = createUserWithRole("student_task_teacher2", "教师乙", "TEACHER", admin);
        var teacherLogin = setPasswordAndLogin(admin, teacher, "student-task-teacher");
        ensureTemplateRule(admin.id());
        templateService.createTemplate(new CreateImportExportTemplateUploadCommand(admin.id(), "学生任务报表模板", TemplateType.EXPORT,
                "STUDENT_TASK_REPORT_EXPORT", "V1", "student-tasks.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                templateFor("STUDENT_NAME", "TASK_TITLE", "SOURCE_TYPE", "STATUS", "POINTS", "REVIEW_STATUS"), true, List.of()));
        long school = 1900000000000120091L, classroom = 1900000000000120092L, student = 1900000000000120093L, outsideStudent = 1900000000000120094L;
        jdbcTemplate.update("INSERT INTO sys_organization(id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,'STUDENT_TASK_SCHOOL','任务报表学校','SCHOOL','/STUDENT_TASK_SCHOOL/',1,'ENABLED')", school);
        jdbcTemplate.update("INSERT INTO sys_organization(id,parent_id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,?,'STUDENT_TASK_CLASS','任务报表班级','CLASS','/STUDENT_TASK_SCHOOL/STUDENT_TASK_CLASS/',1,'ENABLED')", classroom, school);
        jdbcTemplate.update("INSERT INTO edu_student(id,student_name,status) VALUES(?,'班级学生','ENABLED')", student);
        jdbcTemplate.update("INSERT INTO edu_student(id,student_name,status) VALUES(?,'班外学生','ENABLED')", outsideStudent);
        jdbcTemplate.update("INSERT INTO edu_student_organization(id,student_id,organization_id,relation_type,status,effective_from) VALUES(1900000000000120095,?,?, 'CLASS','ACTIVE','2026-09-01 10:00:00')", student, classroom);
        jdbcTemplate.update("INSERT INTO edu_teacher_class(id,teacher_user_id,class_organization_id,status) VALUES(1900000000000120096,?,?,'ACTIVE')", teacher.id(), classroom);
        return new TeacherFixture(admin, teacher, otherTeacher, teacherLogin, school, student, outsideStudent);
    }

    private OrganizationFixture organizationFixture() throws Exception {
        var admin = createUserWithRole("student_task_org_admin_root", "平台管理员", "SYS_ADMIN", null);
        var orgAdmin = createUserWithRole("student_task_org_admin", "机构管理员", "ORG_ADMIN", admin);
        var orgLogin = setPasswordAndLogin(admin, orgAdmin, "student-task-org");
        ensureTemplateRule(admin.id());
        templateService.createTemplate(new CreateImportExportTemplateUploadCommand(admin.id(), "学生任务报表模板", TemplateType.EXPORT,
                "STUDENT_TASK_REPORT_EXPORT", "V1", "student-tasks.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                templateFor("STUDENT_NAME", "TASK_TITLE", "SOURCE_TYPE", "STATUS", "POINTS", "REVIEW_STATUS"), true, List.of()));
        long school = 1900000000000120291L, classroom = 1900000000000120292L, outsideSchool = 1900000000000120293L, student = 1900000000000120294L;
        jdbcTemplate.update("INSERT INTO sys_organization(id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,'STUDENT_TASK_ORG_SCHOOL','机构范围学校','SCHOOL','/STUDENT_TASK_ORG_SCHOOL/',1,'ENABLED')", school);
        jdbcTemplate.update("INSERT INTO sys_organization(id,parent_id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,?,'STUDENT_TASK_ORG_CLASS','机构范围班级','CLASS','/STUDENT_TASK_ORG_SCHOOL/STUDENT_TASK_ORG_CLASS/',1,'ENABLED')", classroom, school);
        jdbcTemplate.update("INSERT INTO sys_organization(id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,'STUDENT_TASK_OUTSIDE','范围外学校','SCHOOL','/STUDENT_TASK_OUTSIDE/',1,'ENABLED')", outsideSchool);
        jdbcTemplate.update("INSERT INTO edu_student(id,student_name,status) VALUES(?,'机构学生','ENABLED')", student);
        jdbcTemplate.update("INSERT INTO edu_student_organization(id,student_id,organization_id,relation_type,status,effective_from) VALUES(1900000000000120295,?,?,'CLASS','ACTIVE','2026-09-01 10:00:00')", student, classroom);
        jdbcTemplate.update("INSERT INTO sys_user_organization(id,user_id,organization_id) VALUES(1900000000000120296,?,?)", orgAdmin.id(), school);
        userService.assignRole(new AssignRoleToUserCommand(orgAdmin.id(), roleMapper.findByCode("ORG_ADMIN").id(), school));
        return new OrganizationFixture(admin, orgAdmin, orgLogin, school, outsideSchool, student);
    }

    private void task(long id, String sourceType, Long sourceOrganizationId, long creator, String title, String status) {
        jdbcTemplate.update("""
                INSERT INTO learn_task(id,source_type,source_organization_id,creator_user_id,title,difficulty_level,base_points,duration_minutes,scheduled_date,category_code,reviewer_user_id,status)
                VALUES(?,?,?,?,?,1,10,30,'2026-09-29','READING',?,?)
                """, id, sourceType, sourceOrganizationId, creator, title, creator, status);
    }

    private void assignment(long id, long taskId, long studentId, String sourceType, String status, long reviewerId) {
        assignment(id, taskId, studentId, sourceType, status, reviewerId, java.time.LocalDate.of(2026, 9, 29));
    }

    private void assignment(long id, long taskId, long studentId, String sourceType, String status, long reviewerId, java.time.LocalDate scheduledDate) {
        jdbcTemplate.update("""
                INSERT INTO learn_task_assignment(id,task_id,student_id,source_type,current_status,current_reviewer_id,scheduled_date,due_at,completed_at,last_transition_at)
                VALUES(?,?,?,?,?,?,?,'2026-09-30 10:00:00',NULL,NULL)
                """, id, taskId, studentId, sourceType, status, reviewerId, scheduledDate);
    }

    private void checkin(long id, long assignmentId, int submissionNo, String status, long submittedBy) {
        jdbcTemplate.update("""
                INSERT INTO learn_task_checkin(id,assignment_id,submission_no,content,status,submitted_by_user_id,submitted_at)
                VALUES(?,?,?,?,?,?,?)
                """, id, assignmentId, submissionNo, "打卡内容不导出", status, submittedBy, java.time.LocalDateTime.of(2026, 9, 29, 10, 0));
    }

    private void reward(long id, long studentId, long taskId, long assignmentId, long reviewerId, long amount) {
        jdbcTemplate.update("""
                INSERT INTO growth_point_ledger(id,account_id,student_id,source_assignment_id,source_task_id,source_type,change_type,amount,available_delta,base_points_snapshot,decay_percent,streak_days,reviewer_user_id,occurred_at,remark)
                VALUES(?,?,?,?,?,?,'TASK_REWARD',?,?,30,0,1,?,?, '学生任务报表测试')
                """, id, 1900000000000110003L, studentId, assignmentId, taskId, "FAMILY", amount, amount,
                reviewerId, java.time.LocalDateTime.of(2026, 9, 29, 11, 0));
    }

    private void correction(long id, long studentId, long assignmentId, long amount, long correctionOfId, long reviewerId) {
        jdbcTemplate.update("""
                INSERT INTO growth_point_ledger(id,account_id,student_id,source_assignment_id,source_type,change_type,amount,available_delta,reviewer_user_id,occurred_at,correction_of_id,remark)
                VALUES(?,?,?,?,?,'CORRECTION',?,?,?,?,?, '学生任务报表测试')
                """, id, 1900000000000110003L, studentId, assignmentId, "FAMILY", amount, amount,
                reviewerId, java.time.LocalDateTime.of(2026, 9, 29, 12, 0), correctionOfId);
    }

    private long createReport(Login login, String source, String status, String outputFormat, String reason) throws Exception {
        var result = mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(login.token()))
                .contentType(MediaType.APPLICATION_JSON).content(reportRequest(source, status, outputFormat, reason)))
                .andExpect(status().isCreated()).andReturn();
        return Long.parseLong(body(result).path("id").asText());
    }

    private String reportRequest(String source, String status, String outputFormat, String reason) {
        return "{\"exportType\":\"STUDENT_TASK_REPORT\""
                + (source == null ? "" : ",\"studentTaskSource\":\"" + source + "\"")
                + (status == null ? "" : ",\"studentTaskStatus\":\"" + status + "\"")
                + ",\"outputFormat\":\"" + outputFormat + "\",\"reason\":\"" + reason + "\"}";
    }

    private byte[] taskDownload(Login login, long id) throws Exception {
        return mockMvc.perform(get("/api/v1/export-jobs/{id}/download", id).header("Authorization", bearer(login.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private void assertTaskFileDenied(Login login, long id) throws Exception {
        mockMvc.perform(get("/api/v1/export-jobs/{id}/download", id).header("Authorization", bearer(login.token()))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/export-jobs/{id}", id).header("Authorization", bearer(login.token()))).andExpect(status().isForbidden());
    }

    private User createUserWithRole(String username, String displayName, String roleCode, User administrator) {
        User user = userService.createUser(new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
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
                                 "deviceId":"%s","deviceName":"任务报表测试浏览器"}
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

    private byte[] templateFor(String... columns) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             var output = new java.io.ByteArrayOutputStream()) {
            var row = workbook.createSheet("学生任务报表").createRow(0);
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

    private record ParentFixture(User admin, User primary, User secondary, User foreignParent,
                                  Login primaryLogin, Login secondaryLogin, long studentId, long foreignStudentId) { }

    private record TeacherFixture(User admin, User teacher, User otherTeacher, Login teacherLogin,
                                  long schoolId, long studentId, long outsideStudentId) { }

    private record OrganizationFixture(User admin, User orgAdmin, Login orgLogin,
                                       long schoolId, long outsideSchoolId, long studentId) { }
}
