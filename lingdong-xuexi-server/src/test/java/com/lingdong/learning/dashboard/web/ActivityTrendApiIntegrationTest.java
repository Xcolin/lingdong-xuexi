package com.lingdong.learning.dashboard.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachmentRuleApplicationService;
import com.lingdong.learning.attachment.application.CreateAttachmentRuleCommand;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.exportjob.application.ExportJobClaimService;
import com.lingdong.learning.exportjob.application.ExportJobExecutionService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** H2/MyBatis 专项：学员活跃度按行为事实按日聚合（R-002，docs/design/09 第 2.7 节）、
 * 他人行为与自动积分排除、授权范围归属，以及转班不改任务实例来源班级（R-004）。 */
@SpringBootTest(properties = "lingdong.export-job.scheduling-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ActivityTrendApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationService;
    @Autowired private UserAccessApplicationService userService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private AttachmentRuleApplicationService ruleService;
    @Autowired private ImportExportTemplateApplicationService templateService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private org.mybatis.spring.SqlSessionTemplate sqlSession;
    @Autowired private ExportJobClaimService claimService;
    @Autowired private ExportJobExecutionService executionService;

    @Test
    void activityTrendAggregatesStudentInitiatedFactsByDay() throws Exception {
        var f = fixture();
        // 2026-09-21：s1 认领 + s2 打卡计活跃；s3（范围外班级）认领不计。
        event(f.nextEvent(), f.assignmentS1, "CLAIMED", f.admin.id(), "PENDING_CLAIM", "IN_PROGRESS", "2026-09-21 08:00:00");
        event(f.nextEvent(), f.assignmentS2, "CHECKED_IN", f.admin.id(), "IN_PROGRESS", "PENDING_REVIEW", "2026-09-21 09:00:00");
        event(f.nextEvent(), f.assignmentS3, "CLAIMED", f.admin.id(), "PENDING_CLAIM", "IN_PROGRESS", "2026-09-21 10:00:00");
        // 2026-09-22：s1 同日暂停/恢复/打卡去重为一；s2 审核驳回（他人行为）不计。
        event(f.nextEvent(), f.assignmentS1, "PAUSED", f.admin.id(), "IN_PROGRESS", "IN_PROGRESS", "2026-09-22 08:00:00");
        event(f.nextEvent(), f.assignmentS1, "RESUMED", f.admin.id(), "IN_PROGRESS", "IN_PROGRESS", "2026-09-22 09:00:00");
        event(f.nextEvent(), f.assignmentS1, "CHECKED_IN", f.admin.id(), "IN_PROGRESS", "PENDING_REVIEW", "2026-09-22 10:00:00");
        event(f.nextEvent(), f.assignmentS2, "REVIEW_REJECTED", f.admin.id(), "PENDING_REVIEW", "NEEDS_IMPROVEMENT", "2026-09-22 11:00:00");
        // 2026-09-23：s2 家长代补录计活跃；s1 免执行（他人设置）与审核转交不计。
        supplement(f, f.studentS2, "2026-09-23 10:00:00");
        event(f.nextEvent(), f.assignmentS1, "EXEMPTED", f.admin.id(), "IN_PROGRESS", "EXEMPT", "2026-09-23 09:00:00");
        event(f.nextEvent(), f.assignmentS2, "REVIEWER_TRANSFERRED", f.admin.id(), "PENDING_REVIEW", "PENDING_REVIEW", "2026-09-23 08:00:00");

        MvcResult trends = mockMvc.perform(get("/api/v1/dashboard/activity-trends?start=2026-09-21&end=2026-09-23")
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(trends.getResponse().getContentAsString()).path("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).path("date").asText()).isEqualTo("2026-09-21");
        assertThat(items.get(0).path("activeStudents").asInt()).isEqualTo(2);
        assertThat(items.get(1).path("date").asText()).isEqualTo("2026-09-22");
        assertThat(items.get(1).path("activeStudents").asInt()).isEqualTo(1);
        assertThat(items.get(2).path("date").asText()).isEqualTo("2026-09-23");
        assertThat(items.get(2).path("activeStudents").asInt()).isEqualTo(1);
    }

    @Test
    void activityTrendRejectsNonAdminRolesAndInvalidRange() throws Exception {
        var f = fixture();
        mockMvc.perform(get("/api/v1/dashboard/activity-trends?start=2026-09-21&end=2026-09-23")
                        .header("Authorization", bearer(f.teacherLogin.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dashboard/activity-trends?start=2026-09-21&end=2026-09-23")
                        .header("Authorization", bearer(f.parentLogin.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dashboard/activity-trends?start=2026-09-23&end=2026-09-21")
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void orgTaskStatisticsKeepSourceClassAfterTransfer() throws Exception {
        var f = fixture();
        ensureTemplateRule(f.admin.id());
        templateService.createTemplate(new CreateImportExportTemplateUploadCommand(f.admin.id(), "活跃度机构统计模板", TemplateType.EXPORT,
                "ORGANIZATION_TASK_STATISTICS_EXPORT", "V1", "org-task-stats.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                templateFor("SCHOOL_NAME", "CLASS_NAME", "TASK_COUNT", "COMPLETED_COUNT", "COMPLETION_RATE", "AVG_POINTS"), true, List.of()));

        // R-004：s1 从班级甲转班到班级乙后，任务实例来源班级保持不变，统计仍按来源班级归属。
        mockMvc.perform(post("/api/v1/students/{studentId}/class-transfers", f.studentS1)
                        .header("Authorization", bearer(f.orgLogin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classOrganizationId\":\"" + f.classB + "\",\"reason\":\"R-004 转班核对\"}"))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT organization_id FROM edu_student_organization WHERE student_id=? AND relation_type='CLASS' AND status='ACTIVE'",
                Long.class, f.studentS1)).isEqualTo(f.classB);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_organization_id FROM learn_task_assignment WHERE id=?", Long.class, f.assignmentS1))
                .isEqualTo(f.classA);

        long id = createExport(f.orgLogin);
        assertThat(executionService.execute(claimService.claim(id, 0L))).isTrue();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(download(f.orgLogin, id)))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("活跃班级甲");
            assertThat(sheet.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(1);
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("活跃班级乙");
            assertThat(sheet.getRow(2).getCell(2).getNumericCellValue()).isEqualTo(1);
        }
    }

    private long createExport(Login login) throws Exception {
        var result = mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(login.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"ORGANIZATION_TASK_STATISTICS\",\"outputFormat\":\"XLSX\",\"reason\":\"R-004 转班归属核对\"}"))
                .andExpect(status().isCreated()).andReturn();
        return Long.parseLong(body(result).path("id").asText());
    }

    private byte[] download(Login login, long id) throws Exception {
        return mockMvc.perform(get("/api/v1/export-jobs/{id}/download", id).header("Authorization", bearer(login.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private Fixture fixture() throws Exception {
        var admin = createUserWithRole("act_trend_root", "平台管理员", "SYS_ADMIN", null);
        var orgAdmin = createUserWithRole("act_trend_org", "活跃度机构管理员", "ORG_ADMIN", admin);
        var teacher = createUserWithRole("act_trend_teacher", "活跃度教师", "TEACHER", admin);
        var parent = createUserWithRole("act_trend_parent", "活跃度家长", "PARENT", admin);
        var orgLogin = setPasswordAndLogin(admin, orgAdmin, "act-trend-org-device");
        var teacherLogin = setPasswordAndLogin(admin, teacher, "act-trend-teacher-device");
        var parentLogin = setPasswordAndLogin(admin, parent, "act-trend-parent-device");
        ensureTemplateRule(admin.id());
        long school = 1900000000000185001L, classA = 1900000000000185002L, classB = 1900000000000185003L;
        long outsideSchool = 1900000000000185004L, outsideClass = 1900000000000185005L;
        long studentS1 = 1900000000000185011L, studentS2 = 1900000000000185012L, studentS3 = 1900000000000185013L;
        jdbcTemplate.update("INSERT INTO sys_organization(id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,'ACT_SCHOOL','活跃学校','SCHOOL','/ACT_SCHOOL/',1,'ENABLED')", school);
        jdbcTemplate.update("INSERT INTO sys_organization(id,parent_id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,?,'ACT_CLASS_A','活跃班级甲','CLASS','/ACT_SCHOOL/ACT_CLASS_A/',1,'ENABLED')", classA, school);
        jdbcTemplate.update("INSERT INTO sys_organization(id,parent_id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,?,'ACT_CLASS_B','活跃班级乙','CLASS','/ACT_SCHOOL/ACT_CLASS_B/',2,'ENABLED')", classB, school);
        jdbcTemplate.update("INSERT INTO sys_organization(id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,'ACT_OUTSIDE','范围外学校','SCHOOL','/ACT_OUTSIDE/',1,'ENABLED')", outsideSchool);
        jdbcTemplate.update("INSERT INTO sys_organization(id,parent_id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,?,'ACT_OUT_CLASS','范围外班级','CLASS','/ACT_OUTSIDE/ACT_OUT_CLASS/',1,'ENABLED')", outsideClass, outsideSchool);
        jdbcTemplate.update("INSERT INTO edu_student(id,student_name,status) VALUES(?,'活跃学生一','ENABLED')", studentS1);
        jdbcTemplate.update("INSERT INTO edu_student(id,student_name,status) VALUES(?,'活跃学生二','ENABLED')", studentS2);
        jdbcTemplate.update("INSERT INTO edu_student(id,student_name,status) VALUES(?,'范围外学生','ENABLED')", studentS3);
        // 入学组织与班级关系；s3 归属范围外班级。
        jdbcTemplate.update("INSERT INTO edu_student_organization(id,student_id,organization_id,relation_type,status,effective_from) VALUES(1900000000000185031,?,?,'ENROLLMENT','ACTIVE','2026-09-01 10:00:00')", studentS1, school);
        jdbcTemplate.update("INSERT INTO edu_student_organization(id,student_id,organization_id,relation_type,status,effective_from) VALUES(1900000000000185032,?,?,'CLASS','ACTIVE','2026-09-01 10:00:00')", studentS1, classA);
        jdbcTemplate.update("INSERT INTO edu_student_organization(id,student_id,organization_id,relation_type,status,effective_from) VALUES(1900000000000185033,?,?,'ENROLLMENT','ACTIVE','2026-09-01 10:00:00')", studentS2, school);
        jdbcTemplate.update("INSERT INTO edu_student_organization(id,student_id,organization_id,relation_type,status,effective_from) VALUES(1900000000000185034,?,?,'CLASS','ACTIVE','2026-09-01 10:00:00')", studentS2, classB);
        jdbcTemplate.update("INSERT INTO edu_student_organization(id,student_id,organization_id,relation_type,status,effective_from) VALUES(1900000000000185035,?,?,'CLASS','ACTIVE','2026-09-01 10:00:00')", studentS3, outsideClass);
        jdbcTemplate.update("INSERT INTO sys_user_organization(id,user_id,organization_id) VALUES(1900000000000185021,?,?)", orgAdmin.id(), school);
        userService.assignRole(new AssignRoleToUserCommand(orgAdmin.id(), roleMapper.findByCode("ORG_ADMIN").id(), school));
        // 机构任务实例：来源班级各归其位；任务来源不影响活跃度归属（按学员当前班级）。
        long taskSeq = 1900000000000185950L;
        long assignmentS1 = assignment(taskSeq, admin.id(), classA, studentS1, classA, "2026-09-21");
        taskSeq += 10;
        long assignmentS2 = assignment(taskSeq, admin.id(), classB, studentS2, classB, "2026-09-21");
        taskSeq += 10;
        long assignmentS3 = assignment(taskSeq, admin.id(), outsideClass, studentS3, outsideClass, "2026-09-21");
        taskSeq += 10;
        return new Fixture(admin, orgLogin, teacherLogin, parentLogin, school, classA, classB, outsideClass,
                studentS1, studentS2, studentS3, assignmentS1, assignmentS2, assignmentS3, 1900000000000185900L, taskSeq);
    }

    private long assignment(long id, long reviewerId, Long taskOrgId, long studentId, long sourceOrgId, String scheduledDate) {
        jdbcTemplate.update("""
                INSERT INTO learn_task(id,source_type,source_organization_id,creator_user_id,title,difficulty_level,base_points,
                    duration_minutes,scheduled_date,reviewer_user_id,status)
                VALUES(?,'ORGANIZATION',?,?, '活跃度任务',1,10,30,?,?,'PUBLISHED')
                """, id, taskOrgId, reviewerId, java.sql.Date.valueOf(scheduledDate), reviewerId);
        jdbcTemplate.update("""
                INSERT INTO learn_task_assignment(id,task_id,student_id,source_type,source_organization_id,current_status,
                    current_reviewer_id,scheduled_date,due_at)
                VALUES(?,?,?,'ORGANIZATION',?,'IN_PROGRESS',?,?,?)
                """, id + 1, id, studentId, sourceOrgId, reviewerId,
                java.sql.Date.valueOf(scheduledDate), java.sql.Timestamp.valueOf(scheduledDate + " 23:59:00"));
        return id + 1;
    }

    private void event(long id, long assignmentId, String eventType, long operatorId, String fromStatus, String toStatus, String occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO learn_task_assignment_event(id,assignment_id,event_type,operator_user_id,from_status,to_status,occurred_at)
                VALUES(?,?,?,?,?,?,?)
                """, id, assignmentId, eventType, operatorId, fromStatus, toStatus, java.sql.Timestamp.valueOf(occurredAt));
    }

    private void supplement(Fixture f, long studentId, String supplementedAt) {
        long reviewId = f.nextSupplementId();
        jdbcTemplate.update("""
                INSERT INTO growth_review(id,student_id,period_type,period_start,period_end,status)
                VALUES(?,?,'DAY','2026-09-23','2026-09-23','DRAFT')
                """, reviewId, studentId);
        jdbcTemplate.update("""
                INSERT INTO growth_review_supplement(id,review_id,editor_user_id,editor_role,supplement_type,content,supplemented_at)
                VALUES(?,?,?,'PARENT','INSIGHT','今天主动补录了复盘观察。',?)
                """, reviewId + 1, reviewId, f.admin.id(), java.sql.Timestamp.valueOf(supplementedAt));
    }

    private void ensureTemplateRule(Long operatorId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from sys_attachment_rule
                where module_code = 'IMPORT_EXPORT_TEMPLATE' and file_category = 'TEMPLATE_FILE'
                """, Integer.class);
        if (count == null || count == 0) {
            ruleService.createRule(new CreateAttachmentRuleCommand(
                    operatorId, "IMPORT_EXPORT_TEMPLATE", "TEMPLATE_FILE", "导出模板文件",
                    List.of("xlsx"), 10_485_760L, 1, false));
        }
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
                                 "deviceId":"%s","deviceName":"活跃度测试浏览器"}
                                """.formatted(user.username(), deviceId)))
                .andExpect(status().isOk()).andReturn();
        return new Login(user, body(result).path("accessToken").asText());
    }

    private byte[] templateFor(String... columns) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             var output = new java.io.ByteArrayOutputStream()) {
            var row = workbook.createSheet("机构任务统计").createRow(0);
            for (int index = 0; index < columns.length; index++) {
                row.createCell(index).setCellValue("${" + columns[index] + "}");
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private record Login(User user, String token) {
        long id() {
            return user.id();
        }
    }

    private static final class Fixture {
        final User admin;
        final Login orgLogin;
        final Login teacherLogin;
        final Login parentLogin;
        final long school;
        final long classA;
        final long classB;
        final long outsideClass;
        final long studentS1;
        final long studentS2;
        final long studentS3;
        final long assignmentS1;
        final long assignmentS2;
        final long assignmentS3;
        private long eventId;
        private long taskId;
        private long supplementId;

        private Fixture(User admin, Login orgLogin, Login teacherLogin, Login parentLogin,
                long school, long classA, long classB, long outsideClass,
                long studentS1, long studentS2, long studentS3,
                long assignmentS1, long assignmentS2, long assignmentS3, long eventId, long taskId) {
            this.admin = admin;
            this.orgLogin = orgLogin;
            this.teacherLogin = teacherLogin;
            this.parentLogin = parentLogin;
            this.school = school;
            this.classA = classA;
            this.classB = classB;
            this.outsideClass = outsideClass;
            this.studentS1 = studentS1;
            this.studentS2 = studentS2;
            this.studentS3 = studentS3;
            this.assignmentS1 = assignmentS1;
            this.assignmentS2 = assignmentS2;
            this.assignmentS3 = assignmentS3;
            this.eventId = eventId;
            this.taskId = taskId;
        }

        private long nextEvent() {
            return eventId++;
        }

        private long nextTask() {
            return taskId++;
        }

        private long nextSupplementId() {
            return taskId++;
        }
    }
}
