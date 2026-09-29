package com.lingdong.learning.exportjob.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachmentRuleApplicationService;
import com.lingdong.learning.attachment.application.CreateAttachmentRuleCommand;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.exportjob.application.adapter.ExportRequestDefinition;
import com.lingdong.learning.exportjob.domain.ExportJobType;
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

/** H2/MyBatis 专项：考勤台账事实行导出、四身份范围冻结与撤权复核、家庭亲子/本人校验、跨数据集筛选拒绝、真实 XLSX 与 PDF 及 205 行分页上界。 */
@SpringBootTest(properties = "lingdong.export-job.scheduling-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrganizationAttendanceLedgerExportApiIntegrationTest {

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
    @Autowired private com.lingdong.learning.exportjob.application.adapter.ExportAdapterRegistry adapterRegistry;

    @Test
    void attendanceLedgerExportsFactsAndRevokesScope() throws Exception {
        var f = attendanceFixture();
        // 班级甲：同学生不同自然日两条（迟到带时间、缺勤无时间）；班级乙：请假一条；范围外班级排除。
        attendance(1900000000000176001L, f.studentId(), f.classAId(), "2026-09-28", "LATE", "09:10:00", "16:30:00", f.admin().id());
        attendance(1900000000000176002L, f.studentId(), f.classAId(), "2026-09-29", "ABSENT", null, null, f.admin().id());
        attendance(1900000000000176003L, f.studentId(), f.classBId(), "2026-09-28", "LEAVE", null, null, f.admin().id());
        attendance(1900000000000176004L, f.otherStudentId(), f.outsideClassId(), "2026-09-28", "NORMAL", "08:00:00", "16:00:00", f.admin().id());

        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=ATTENDANCE_LEDGER").header("Authorization", bearer(f.orgLogin().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.columns.length()").value(6))
                .andExpect(jsonPath("$.attClasses.length()").value(2));
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=ATTENDANCE_LEDGER").header("Authorization", bearer(f.teacherLogin().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attClasses.length()").value(1));

        long id = createExport(f.orgLogin(), null, null, "XLSX", "机构导出考勤台账");
        // 冻结后新增的考勤记录不进入旧作业。
        attendance(1900000000000176999L, f.studentId(), f.classAId(), "2026-09-30", "NORMAL", "08:00:00", "16:30:00", f.admin().id());
        assertThat(executionService.execute(claimService.claim(id, 0L))).isTrue();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(download(f.orgLogin(), id)))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 6);
            var late = sheet.getRow(1);
            assertThat(late.getCell(0).getStringCellValue()).isEqualTo("考*");
            assertThat(late.getCell(1).getStringCellValue()).isEqualTo("考勤班级甲");
            assertThat(late.getCell(2).getStringCellValue()).isEqualTo("2026-09-28");
            assertThat(late.getCell(3).getStringCellValue()).isEqualTo("LATE");
            assertThat(late.getCell(4).getStringCellValue()).isEqualTo("09:10");
            assertThat(late.getCell(5).getStringCellValue()).isEqualTo("16:30");
            var absent = sheet.getRow(2);
            assertThat(absent.getCell(3).getStringCellValue()).isEqualTo("ABSENT");
            assertThat(absent.getCell(4).getStringCellValue()).isEmpty();
            assertThat(absent.getCell(5).getStringCellValue()).isEmpty();
            var leave = sheet.getRow(3);
            assertThat(leave.getCell(1).getStringCellValue()).isEqualTo("考勤班级乙");
            assertThat(leave.getCell(3).getStringCellValue()).isEqualTo("LEAVE");
        }

        // 班级筛选：仅输出所选授权班级；越权班级拒绝。冻结发生在新增行之后，含 classA 全部 3 行。
        long filtered = createExport(f.orgLogin(), f.classAId(), null, "XLSX", "机构筛选班级考勤导出");
        assertThat(executionService.execute(claimService.claim(filtered, 0L))).isTrue();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(download(f.orgLogin(), filtered)))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("考勤班级甲");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("考勤班级甲");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("考勤班级甲");
        }
        mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(f.orgLogin().token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"ATTENDANCE_LEDGER\",\"attClassId\":\"" + f.outsideClassId() + "\",\"reason\":\"越权班级必须拒绝\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(f.orgLogin().token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"ATTENDANCE_LEDGER\",\"outputFormat\":\"DOCX\",\"reason\":\"非法格式\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(f.orgLogin().token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"ATTENDANCE_LEDGER\",\"orgStatClassId\":\"" + f.classAId() + "\",\"reason\":\"他类机构统计筛选拒绝\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(f.orgLogin().token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"ATTENDANCE_LEDGER\",\"eventType\":\"USER_CREATE\",\"reason\":\"权限事件筛选拒绝\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(f.orgLogin().token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"ORGANIZATION_TASK_STATISTICS\",\"attClassId\":\"" + f.classAId() + "\",\"reason\":\"机构统计不支持考勤筛选\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(f.orgLogin().token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"ATTENDANCE_LEDGER\",\"studentId\":\"" + f.studentId() + "\",\"reason\":\"机构身份不能指定学生\"}"))
                .andExpect(status().isBadRequest());

        // 排队撤权：教师班级授权删除后执行失败，旧文件下载拒绝；恢复后可读；兼任审核员后拒绝。
        long teacherId = createExport(f.teacherLogin(), null, null, "XLSX", "教师排队撤权验证");
        jdbcTemplate.update("DELETE FROM edu_teacher_class WHERE teacher_user_id=?", f.teacher().id());
        sqlSession.clearCache();
        assertThat(executionService.execute(claimService.claim(teacherId, 0L))).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM sys_export_job WHERE id=?", String.class, teacherId)).isEqualTo("FAILED");
        assertFileDenied(f.teacherLogin(), id);
        jdbcTemplate.update("INSERT INTO edu_teacher_class(id,teacher_user_id,class_organization_id,status) VALUES(1900000000000175999,?,?,'ACTIVE')", f.teacher().id(), f.classAId());
        sqlSession.clearCache();
        // 恢复授权后范围复核重新通过，可读自己的作业（作业已 FAILED 无文件，下载仍不可用）。
        mockMvc.perform(get("/api/v1/export-jobs/{id}", teacherId).header("Authorization", bearer(f.teacherLogin().token())))
                .andExpect(status().isOk());
        userService.assignRole(new AssignRoleToUserCommand(f.teacher().id(), roleMapper.findByCode("SYS_AUDITOR").id(), null));
        assertFileDenied(f.teacherLogin(), teacherId);
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=ATTENDANCE_LEDGER").header("Authorization", bearer(f.teacherLogin().token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void attendanceFamilyExportsOwnChildrenOnly() throws Exception {
        var f = attendanceFixture();
        attendance(1900000000000176101L, f.studentId(), f.classAId(), "2026-09-28", "NORMAL", "08:00:00", "16:30:00", f.admin().id());
        attendance(1900000000000176102L, f.otherStudentId(), f.outsideClassId(), "2026-09-28", "ABSENT", null, null, f.admin().id());

        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=ATTENDANCE_LEDGER").header("Authorization", bearer(f.parentLogin().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.students.length()").value(1));
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=ATTENDANCE_LEDGER").header("Authorization", bearer(f.studentLogin().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.students.length()").value(1));
        long parentId = createExport(f.parentLogin(), null, String.valueOf(f.studentId()), "XLSX", "家长导出亲子考勤");
        assertThat(executionService.execute(claimService.claim(parentId, 0L))).isTrue();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(download(f.parentLogin(), parentId)))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("考*");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("NORMAL");
        }
        long selfId = createExport(f.studentLogin(), null, String.valueOf(f.studentId()), "XLSX", "学生导出本人考勤");
        assertThat(executionService.execute(claimService.claim(selfId, 0L))).isTrue();

        // 无关学生、家长指定班级、教师指定学生均拒绝。
        mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(f.parentLogin().token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"ATTENDANCE_LEDGER\",\"studentId\":\"" + f.otherStudentId() + "\",\"reason\":\"非亲子学生必须拒绝\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(f.parentLogin().token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"ATTENDANCE_LEDGER\",\"attClassId\":\"" + f.classAId() + "\",\"reason\":\"家庭身份不能指定班级\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(f.teacherLogin().token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"ATTENDANCE_LEDGER\",\"studentId\":\"" + f.studentId() + "\",\"reason\":\"教师不能指定学生\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(f.parentLogin().token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"ATTENDANCE_LEDGER\",\"reason\":\"家庭身份必须指定学生\"}"))
                .andExpect(status().isBadRequest());

        // 亲子关系删除后旧文件拒绝；学生本人解绑后同样拒绝。
        jdbcTemplate.update("DELETE FROM edu_parent_student WHERE parent_user_id=?", f.parent().id());
        sqlSession.clearCache();
        assertFileDenied(f.parentLogin(), parentId);
        jdbcTemplate.update("UPDATE edu_student SET student_user_id=NULL WHERE id=?", f.studentId());
        sqlSession.clearCache();
        assertFileDenied(f.studentLogin(), selfId);
    }

    @Test
    void attendancePdfRendersAndFreeze205RowsPageByCursor() throws Exception {
        var f = attendanceFixture();
        attendance(1900000000000176201L, f.studentId(), f.classAId(), "2026-09-28", "LATE", "09:10:00", "16:30:00", f.admin().id());
        long pdfId = createExport(f.orgLogin(), null, null, "PDF", "考勤 PDF 导出");
        assertThat(executionService.execute(claimService.claim(pdfId, 0L))).isTrue();
        MvcResult result = mockMvc.perform(get("/api/v1/export-jobs/{id}/download", pdfId).header("Authorization", bearer(f.orgLogin().token())))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentType()).isEqualTo("application/pdf");
        try (var document = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("考勤台账", "考*", "考勤班级甲", "LATE", "09:10");
        }

        // 205 行分页上界：单班级 205 名学生记录，冻结后迟到记录排除，适配器游标分页累计等于冻结总量。
        long batchClass = 1900000000000176300L;
        jdbcTemplate.update("""
                INSERT INTO sys_organization(id,parent_id,organization_code,organization_name,organization_type,organization_path,sort_order,status)
                VALUES(?,?,'ATT_BATCH','批量考勤班级','CLASS','/ATT_SCHOOL/ATT_BATCH/',3,'ENABLED')
                """, batchClass, f.schoolId());
        sqlSession.clearCache();
        for (int i = 0; i < 205; i++) {
            long batchStudent = 1900000000000177000L + i;
            jdbcTemplate.update("INSERT INTO edu_student(id,student_name,status) VALUES(?,'批量考勤学生','ENABLED')", batchStudent);
            attendance(1900000000000178000L + i, batchStudent, batchClass, "2026-09-28", "NORMAL", "08:00:00", "16:30:00", f.admin().id());
        }
        long id = createExport(f.orgLogin(), null, null, "XLSX", "考勤台账批量导出");
        attendance(1900000000000178999L, f.studentId(), batchClass, "2026-09-30", "NORMAL", "08:00:00", "16:30:00", f.admin().id());
        assertThat(executionService.execute(claimService.claim(id, 0L))).isTrue();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(download(f.orgLogin(), id)))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(206);
        }
        var adapter = adapterRegistry.require(ExportJobType.ATTENDANCE_LEDGER);
        ExportRequestDefinition request = new ExportRequestDefinition(f.orgAdmin().id(), null,
                java.time.LocalDate.of(2026, 9, 1).atStartOfDay(), java.time.LocalDate.of(2026, 9, 30).atStartOfDay(),
                // 位置 5-31 全部为 null，末位传冻结班级集合。
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                List.of(f.classAId(), f.classBId(), batchClass));
        long upper = adapter.captureUpperBound(request);
        assertThat(adapter.count(request, upper)).isEqualTo(207);
        int total = 0;
        long cursor = 0;
        boolean more;
        do {
            var page = adapter.fetchAfter(request, upper, cursor, 100);
            total += page.rows().size();
            for (var row : page.rows()) {
                assertThat(row.keySet()).containsExactlyInAnyOrder("STUDENT_NAME", "CLASS_NAME", "ATTENDANCE_DATE", "STATUS", "CHECKIN_TIME", "CHECKOUT_TIME");
            }
            more = page.hasMore();
            if (page.nextCursor() != null) {
                assertThat(page.nextCursor()).isGreaterThan(cursor);
                cursor = page.nextCursor();
            }
        } while (more);
        assertThat(total).isEqualTo(207);
        assertThat(jdbcTemplate.queryForList("""
                SELECT r.role_code FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id
                JOIN sys_permission p ON p.id=rp.permission_id WHERE p.permission_code='ATTENDANCE_LEDGER_EXPORT'
                """, String.class)).containsExactlyInAnyOrder("ORG_ADMIN", "TEACHER", "PARENT", "STUDENT");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_dictionary_item WHERE item_code='ATTENDANCE_LEDGER_EXPORT'", Integer.class)).isEqualTo(1);
    }

    private AttendanceFixture attendanceFixture() throws Exception {
        var admin = createUserWithRole("att_export_root", "平台管理员", "SYS_ADMIN", null);
        var orgAdmin = createUserWithRole("att_export_org", "考勤机构管理员", "ORG_ADMIN", admin);
        var teacher = createUserWithRole("att_export_teacher", "考勤教师", "TEACHER", admin);
        var parent = createUserWithRole("att_export_parent", "考勤家长", "PARENT", admin);
        var studentUser = createUserWithRole("att_export_student_user", "考勤学生用户", "STUDENT", admin);
        var orgLogin = setPasswordAndLogin(admin, orgAdmin, "att-org-device");
        var teacherLogin = setPasswordAndLogin(admin, teacher, "att-teacher-device");
        var parentLogin = setPasswordAndLogin(admin, parent, "att-parent-device");
        var studentLogin = setPasswordAndLogin(admin, studentUser, "att-student-device");
        ensureTemplateRule(admin.id());
        templateService.createTemplate(new CreateImportExportTemplateUploadCommand(admin.id(), "考勤台账模板", TemplateType.EXPORT,
                "ATTENDANCE_LEDGER_EXPORT", "V1", "attendance-ledger.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                templateFor("STUDENT_NAME", "CLASS_NAME", "ATTENDANCE_DATE", "STATUS", "CHECKIN_TIME", "CHECKOUT_TIME"), true, List.of()));
        long school = 1900000000000175001L, classA = 1900000000000175002L, classB = 1900000000000175003L;
        long outsideSchool = 1900000000000175004L, outsideClass = 1900000000000175005L;
        long student = 1900000000000175011L, otherStudent = 1900000000000175012L;
        jdbcTemplate.update("INSERT INTO sys_organization(id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,'ATT_SCHOOL','考勤学校','SCHOOL','/ATT_SCHOOL/',1,'ENABLED')", school);
        jdbcTemplate.update("INSERT INTO sys_organization(id,parent_id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,?,'ATT_CLASS_A','考勤班级甲','CLASS','/ATT_SCHOOL/ATT_CLASS_A/',1,'ENABLED')", classA, school);
        jdbcTemplate.update("INSERT INTO sys_organization(id,parent_id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,?,'ATT_CLASS_B','考勤班级乙','CLASS','/ATT_SCHOOL/ATT_CLASS_B/',2,'ENABLED')", classB, school);
        jdbcTemplate.update("INSERT INTO sys_organization(id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,'ATT_OUTSIDE','范围外学校','SCHOOL','/ATT_OUTSIDE/',1,'ENABLED')", outsideSchool);
        jdbcTemplate.update("INSERT INTO sys_organization(id,parent_id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,?,'ATT_OUT_CLASS','范围外班级','CLASS','/ATT_OUTSIDE/ATT_OUT_CLASS/',1,'ENABLED')", outsideClass, outsideSchool);
        jdbcTemplate.update("INSERT INTO edu_student(id,student_name,student_user_id,status) VALUES(?,'考勤学生',?,'ENABLED')", student, studentUser.id());
        jdbcTemplate.update("INSERT INTO edu_student(id,student_name,status) VALUES(?,'无关学生','ENABLED')", otherStudent);
        jdbcTemplate.update("INSERT INTO edu_student_organization(id,student_id,organization_id,relation_type,status,effective_from) VALUES(1900000000000175031,?,?, 'CLASS','ACTIVE','2026-09-01 10:00:00')", student, classA);
        jdbcTemplate.update("INSERT INTO edu_student_organization(id,student_id,organization_id,relation_type,status,effective_from) VALUES(1900000000000175032,?,?, 'CLASS','ACTIVE','2026-09-01 10:00:00')", otherStudent, outsideClass);
        jdbcTemplate.update("INSERT INTO edu_teacher_class(id,teacher_user_id,class_organization_id,status) VALUES(1900000000000175041,?,?,'ACTIVE')", teacher.id(), classA);
        jdbcTemplate.update("INSERT INTO edu_parent_student(id,parent_user_id,student_id,relation_role,status,primary_scope_key) VALUES(1900000000000175051,?,?,'PRIMARY_GUARDIAN','ACTIVE','PRIMARY')", parent.id(), student);
        jdbcTemplate.update("INSERT INTO sys_user_organization(id,user_id,organization_id) VALUES(1900000000000175021,?,?)", orgAdmin.id(), school);
        userService.assignRole(new AssignRoleToUserCommand(orgAdmin.id(), roleMapper.findByCode("ORG_ADMIN").id(), school));
        return new AttendanceFixture(admin, orgAdmin, teacher, parent, orgLogin, teacherLogin, parentLogin, studentLogin,
                school, classA, classB, outsideClass, student, otherStudent);
    }

    private void attendance(long id, long studentId, long classId, String date, String status, String checkin, String checkout, long recorderId) {
        jdbcTemplate.update("""
                INSERT INTO attendance_record(id,student_id,class_organization_id,attendance_date,status,checkin_time,checkout_time,source,recorded_by,version_no,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,'MANUAL',?,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, id, studentId, classId, java.sql.Date.valueOf(date), status, checkin, checkout, recorderId);
    }

    private long createExport(Login login, Long classId, String studentId, String outputFormat, String reason) throws Exception {
        var result = mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(login.token()))
                .contentType(MediaType.APPLICATION_JSON).content(exportRequest(classId, studentId, outputFormat, reason)))
                .andExpect(status().isCreated()).andReturn();
        return Long.parseLong(body(result).path("id").asText());
    }

    private String exportRequest(Long classId, String studentId, String outputFormat, String reason) {
        return "{\"exportType\":\"ATTENDANCE_LEDGER\""
                + (classId == null ? "" : ",\"attClassId\":\"" + classId + "\"")
                + (studentId == null ? "" : ",\"studentId\":\"" + studentId + "\"")
                + ",\"startedAt\":\"2026-09-01T00:00:00\",\"endedAt\":\"2026-09-30T23:59:59\""
                + ",\"outputFormat\":\"" + outputFormat + "\",\"reason\":\"" + reason + "\"}";
    }

    private byte[] download(Login login, long id) throws Exception {
        return mockMvc.perform(get("/api/v1/export-jobs/{id}/download", id).header("Authorization", bearer(login.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private void assertFileDenied(Login login, long id) throws Exception {
        mockMvc.perform(get("/api/v1/export-jobs/{id}/download", id).header("Authorization", bearer(login.token()))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/export-jobs/{id}", id).header("Authorization", bearer(login.token()))).andExpect(status().isForbidden());
    }

    private User createUserWithRole(String username, String displayName, String roleCode, User administrator) {
        User user = userService.createUser(new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
        var role = roleMapper.findByCode(roleCode);
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
                                 "deviceId":"%s","deviceName":"考勤台账测试浏览器"}
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
            var row = workbook.createSheet("考勤台账").createRow(0);
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

    private record AttendanceFixture(User admin, User orgAdmin, User teacher, User parent,
                                     Login orgLogin, Login teacherLogin, Login parentLogin, Login studentLogin,
                                     long schoolId, long classAId, long classBId, long outsideClassId,
                                     long studentId, long otherStudentId) { }
}
