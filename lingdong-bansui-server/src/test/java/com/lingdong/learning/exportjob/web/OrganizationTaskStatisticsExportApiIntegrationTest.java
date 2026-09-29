package com.lingdong.learning.exportjob.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachmentRuleApplicationService;
import com.lingdong.learning.attachment.application.CreateAttachmentRuleCommand;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.exportjob.application.adapter.ExportRequestDefinition;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** H2/MyBatis 专项：机构管理员按来源班级聚合、R-001 完成率与净积分平均、家庭/教师/草稿排除、班级筛选、撤权与 205 行分页上界，XLSX 与 PDF 真实文件。 */
@SpringBootTest(properties = "lingdong.export-job.scheduling-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrganizationTaskStatisticsExportApiIntegrationTest {

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
    private long statReviewerId;
    @Autowired private com.lingdong.learning.exportjob.application.adapter.ExportAdapterRegistry adapterRegistry;

    @Test
    void organizationAdminExportsClassAggregatedStatisticsAndRevokesScope() throws Exception {
        var f = organizationFixture();
        // 班级甲：任务 3（完成 1、进行中 1、待认领 1）→ 分母 2、完成率 50.00、平均积分 15.00（净 20-5）；同任务同学生同日期唯一，每实例独立任务。
        orgTask(1900000000000160001L, f.schoolId(), f.admin().id(), "班级甲机构任务一", "PUBLISHED");
        assignment(1900000000000161001L, 1900000000000160001L, f.studentId(), "COMPLETED", f.classAId());
        orgTask(1900000000000160008L, f.schoolId(), f.admin().id(), "班级甲机构任务二", "PUBLISHED");
        assignment(1900000000000161002L, 1900000000000160008L, f.studentId(), "IN_PROGRESS", f.classAId());
        orgTask(1900000000000160009L, f.schoolId(), f.admin().id(), "班级甲机构任务三", "PUBLISHED");
        assignment(1900000000000161003L, 1900000000000160009L, f.studentId(), "PENDING_CLAIM", f.classAId());
        reward(1900000000000163001L, f.studentId(), 1900000000000161001L, f.accountId(), f.admin().id(), 20);
        correction(1900000000000163002L, f.studentId(), 1900000000000161001L, f.accountId(), f.admin().id(), -5, 1900000000000163001L);
        // 班级乙：完成 1、进行中 1、免执行 1 → 分母 1、完成率 100.00。
        orgTask(1900000000000160010L, f.schoolId(), f.admin().id(), "班级乙机构任务一", "PUBLISHED");
        assignment(1900000000000161004L, 1900000000000160010L, f.studentId(), "IN_PROGRESS", f.classBId());
        orgTask(1900000000000160011L, f.schoolId(), f.admin().id(), "班级乙机构任务二", "PUBLISHED");
        assignment(1900000000000161005L, 1900000000000160011L, f.studentId(), "EXEMPT", f.classBId());
        orgTask(1900000000000160012L, f.schoolId(), f.admin().id(), "班级乙机构任务三", "PUBLISHED");
        assignment(1900000000000161006L, 1900000000000160012L, f.studentId(), "COMPLETED", f.classBId());
        // 已失效实例不计任务总数；草稿任务、家庭任务、教师任务、范围外机构任务均排除。
        orgTask(1900000000000160003L, f.schoolId(), f.admin().id(), "班级乙机构任务失效", "PUBLISHED");
        assignment(1900000000000161007L, 1900000000000160003L, f.studentId(), "INVALIDATED", f.classBId());
        orgTask(1900000000000160004L, f.schoolId(), f.admin().id(), "草稿机构任务排除", "DRAFT");
        assignment(1900000000000161008L, 1900000000000160004L, f.studentId(), "PENDING_CLAIM", f.classBId());
        familyTask(1900000000000160005L, f.admin().id(), "家庭任务排除", "PUBLISHED");
        assignment(1900000000000161009L, 1900000000000160005L, f.studentId(), "COMPLETED", null);
        teacherTask(1900000000000160006L, f.admin().id(), "教师任务排除", "PUBLISHED");
        assignment(1900000000000161010L, 1900000000000160006L, f.studentId(), "COMPLETED", null);
        orgTask(1900000000000160007L, f.outsideSchoolId(), f.admin().id(), "范围外机构任务排除", "PUBLISHED");
        assignment(1900000000000161011L, 1900000000000160007L, f.studentId(), "COMPLETED", f.outsideSchoolId());

        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=ORGANIZATION_TASK_STATISTICS").header("Authorization", bearer(f.orgLogin().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.columns.length()").value(6))
                .andExpect(jsonPath("$.orgStatClasses.length()").value(2));
        long id = createExport(f.orgLogin(), null, "XLSX", "机构导出任务统计");
        // 冻结后新增的实例不进入旧作业。
        orgTask(1900000000000160013L, f.schoolId(), f.admin().id(), "冻结后机构任务", "PUBLISHED");
        assignment(1900000000000161099L, 1900000000000160013L, f.studentId(), "COMPLETED", f.classAId());
        assertThat(executionService.execute(claimService.claim(id, 0L))).isTrue();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(download(f.orgLogin(), id)))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 6);
            var rowA = sheet.getRow(1);
            assertThat(rowA.getCell(0).getStringCellValue()).isEqualTo("机构统计学校");
            assertThat(rowA.getCell(1).getStringCellValue()).isEqualTo("统计班级甲");
            assertThat(rowA.getCell(2).getNumericCellValue()).isEqualTo(3);
            assertThat(rowA.getCell(3).getNumericCellValue()).isEqualTo(1);
            assertThat(rowA.getCell(4).getNumericCellValue()).isEqualTo(50.0);
            assertThat(rowA.getCell(5).getNumericCellValue()).isEqualTo(15.0);
            var rowB = sheet.getRow(2);
            assertThat(rowB.getCell(1).getStringCellValue()).isEqualTo("统计班级乙");
            assertThat(rowB.getCell(2).getNumericCellValue()).isEqualTo(3);
            assertThat(rowB.getCell(3).getNumericCellValue()).isEqualTo(1);
            assertThat(rowB.getCell(4).getNumericCellValue()).isEqualTo(100.0);
            assertThat(rowB.getCell(5).getNumericCellValue()).isEqualTo(0.0);
        }

        // 班级筛选：仅输出所选授权班级；越权班级拒绝。
        long filtered = createExport(f.orgLogin(), f.classAId(), "XLSX", "机构筛选班级导出");
        assertThat(executionService.execute(claimService.claim(filtered, 0L))).isTrue();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(download(f.orgLogin(), filtered)))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("统计班级甲");
        }
        mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(f.orgLogin().token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"ORGANIZATION_TASK_STATISTICS\",\"orgStatClassId\":\"" + f.outsideSchoolId() + "\",\"reason\":\"越权班级必须拒绝\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(f.orgLogin().token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"ORGANIZATION_TASK_STATISTICS\",\"outputFormat\":\"DOCX\",\"reason\":\"非法格式\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(f.orgLogin().token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportType\":\"STUDENT_TASK_REPORT\",\"orgStatClassId\":\"" + f.classAId() + "\",\"reason\":\"他类不支持机构统计筛选\"}"))
                .andExpect(status().isBadRequest());

        // 排队撤权：机构关系删除后执行失败，旧文件下载拒绝；恢复后可读；兼任审核员后拒绝。
        long queued = createExport(f.orgLogin(), null, "XLSX", "机构排队撤权验证");
        jdbcTemplate.update("DELETE FROM sys_user_organization WHERE user_id=?", f.orgAdmin().id());
        sqlSession.clearCache();
        assertThat(executionService.execute(claimService.claim(queued, 0L))).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM sys_export_job WHERE id=?", String.class, queued)).isEqualTo("FAILED");
        assertFileDenied(f.orgLogin(), id);
        jdbcTemplate.update("INSERT INTO sys_user_organization(id,user_id,organization_id) VALUES(1900000000000160501,?,?)", f.orgAdmin().id(), f.schoolId());
        sqlSession.clearCache();
        assertThat(download(f.orgLogin(), id)).isNotEmpty();
        userService.assignRole(new AssignRoleToUserCommand(f.orgAdmin().id(), roleMapper.findByCode("SYS_AUDITOR").id(), null));
        assertFileDenied(f.orgLogin(), id);
        mockMvc.perform(get("/api/v1/export-jobs/options?exportType=ORGANIZATION_TASK_STATISTICS").header("Authorization", bearer(f.orgLogin().token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void pdfOutputRendersStatisticsTable() throws Exception {
        var f = organizationFixture();
        orgTask(1900000000000160101L, f.schoolId(), f.admin().id(), "PDF 机构任务", "PUBLISHED");
        assignment(1900000000000161101L, 1900000000000160101L, f.studentId(), "COMPLETED", f.classAId());
        long id = createExport(f.orgLogin(), null, "PDF", "机构 PDF 任务统计");
        assertThat(executionService.execute(claimService.claim(id, 0L))).isTrue();
        MvcResult result = mockMvc.perform(get("/api/v1/export-jobs/{id}/download", id).header("Authorization", bearer(f.orgLogin().token())))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentType()).isEqualTo("application/pdf");
        try (var document = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("机构任务统计", "机构统计学校", "统计班级甲", "1", "100.00");
        }
    }

    @Test
    void statisticsFreeze205ClassRowsAndPageByCursor() throws Exception {
        var f = organizationFixture();
        orgTask(1900000000000160201L, f.schoolId(), f.admin().id(), "批量机构任务", "PUBLISHED");
        for (int i = 0; i < 205; i++) {
            long classId = 1900000000000162000L + i;
            long batchStudent = 1900000000000167000L + i;
            jdbcTemplate.update("INSERT INTO edu_student(id,student_name,status) VALUES(?,'批量统计学生','ENABLED')", batchStudent);
            jdbcTemplate.update("""
                    INSERT INTO sys_organization(id,parent_id,organization_code,organization_name,organization_type,organization_path,sort_order,status)
                    VALUES(?,?,'ORG_STAT_BATCH_'||?,'批量统计班级_'||?,
                    'CLASS','/ORG_STAT_SCHOOL/ORG_STAT_BATCH_'||?||'/',1,'ENABLED')
                    """, classId, f.schoolId(), String.valueOf(i), String.valueOf(i), String.valueOf(i));
            assignment(1900000000000164000L + i, 1900000000000160201L, batchStudent, "COMPLETED", classId);
        }
        long id = createExport(f.orgLogin(), null, "XLSX", "机构统计批量导出");
        // 冻结后新增班级与实例不进入旧作业。
        long lateClass = 1900000000000162999L;
        long lateStudent = 1900000000000167999L;
        jdbcTemplate.update("INSERT INTO edu_student(id,student_name,status) VALUES(?,'迟到统计学生','ENABLED')", lateStudent);
        jdbcTemplate.update("""
                INSERT INTO sys_organization(id,parent_id,organization_code,organization_name,organization_type,organization_path,sort_order,status)
                VALUES(?,?,'ORG_STAT_LATE','迟到统计班级','CLASS','/ORG_STAT_SCHOOL/ORG_STAT_LATE/',1,'ENABLED')
                """, lateClass, f.schoolId());
        assignment(1900000000000164999L, 1900000000000160201L, lateStudent, "COMPLETED", lateClass);
        assertThat(executionService.execute(claimService.claim(id, 0L))).isTrue();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(download(f.orgLogin(), id)))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(205);
        }
        // 适配器级游标分页：205 行按来源组织推进，逐页累计等于冻结总量。
        var adapter = adapterRegistry.require(com.lingdong.learning.exportjob.domain.ExportJobType.ORGANIZATION_TASK_STATISTICS);
        var batchOrgIds = java.util.stream.LongStream.range(0, 205).mapToObj(i -> 1900000000000162000L + i).toList();
        ExportRequestDefinition request = new ExportRequestDefinition(f.orgAdmin().id(),
                LocalDate.of(2026, 9, 29).atStartOfDay(), LocalDate.of(2026, 9, 29).atStartOfDay(),
                batchOrgIds);
        assertThat(adapter.count(request, 1900000000000164204L)).isEqualTo(205);
        int total = 0;
        long cursor = 0;
        boolean more;
        do {
            var page = adapter.fetchAfter(request, 1900000000000164204L, cursor, 100);
            total += page.rows().size();
            for (var row : page.rows()) {
                assertThat(row.keySet()).containsExactlyInAnyOrder("SCHOOL_NAME", "CLASS_NAME", "TASK_COUNT", "COMPLETED_COUNT", "COMPLETION_RATE", "AVG_POINTS");
            }
            more = page.hasMore();
            if (page.nextCursor() != null) {
                assertThat(page.nextCursor()).isGreaterThan(cursor);
                cursor = page.nextCursor();
            }
        } while (more);
        assertThat(total).isEqualTo(205);
        assertThat(jdbcTemplate.queryForList("""
                SELECT r.role_code FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id
                JOIN sys_permission p ON p.id=rp.permission_id WHERE p.permission_code='ORGANIZATION_TASK_STATISTICS_EXPORT'
                """, String.class)).containsExactly("ORG_ADMIN");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_dictionary_item WHERE item_code='ORGANIZATION_TASK_STATISTICS_EXPORT'", Integer.class)).isEqualTo(1);
    }

    private OrgStatFixture organizationFixture() throws Exception {
        var admin = createUserWithRole("org_stat_root", "平台管理员", "SYS_ADMIN", null);
        var orgAdmin = createUserWithRole("org_stat_admin", "机构统计管理员", "ORG_ADMIN", admin);
        // 实例的审核人外键必须指向真实用户。
        this.statReviewerId = createUserWithRole("org_stat_reviewer", "机构统计审核人", "SYS_ADMIN", admin).id();
        var orgLogin = setPasswordAndLogin(admin, orgAdmin, "org-stat-device");
        ensureTemplateRule(admin.id());
        templateService.createTemplate(new CreateImportExportTemplateUploadCommand(admin.id(), "机构任务统计模板", TemplateType.EXPORT,
                "ORGANIZATION_TASK_STATISTICS_EXPORT", "V1", "org-task-stats.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                templateFor("SCHOOL_NAME", "CLASS_NAME", "TASK_COUNT", "COMPLETED_COUNT", "COMPLETION_RATE", "AVG_POINTS"), true, List.of()));
        long school = 1900000000000165001L, classA = 1900000000000165002L, classB = 1900000000000165003L, outsideSchool = 1900000000000165004L;
        long student = 1900000000000165011L, account = 1900000000000165012L;
        jdbcTemplate.update("INSERT INTO sys_organization(id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,'ORG_STAT_SCHOOL','机构统计学校','SCHOOL','/ORG_STAT_SCHOOL/',1,'ENABLED')", school);
        jdbcTemplate.update("INSERT INTO sys_organization(id,parent_id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,?,'ORG_STAT_CLASS_A','统计班级甲','CLASS','/ORG_STAT_SCHOOL/ORG_STAT_CLASS_A/',1,'ENABLED')", classA, school);
        jdbcTemplate.update("INSERT INTO sys_organization(id,parent_id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,?,'ORG_STAT_CLASS_B','统计班级乙','CLASS','/ORG_STAT_SCHOOL/ORG_STAT_CLASS_B/',2,'ENABLED')", classB, school);
        jdbcTemplate.update("INSERT INTO sys_organization(id,organization_code,organization_name,organization_type,organization_path,sort_order,status) VALUES(?,'ORG_STAT_OUTSIDE','范围外学校','SCHOOL','/ORG_STAT_OUTSIDE/',1,'ENABLED')", outsideSchool);
        jdbcTemplate.update("INSERT INTO edu_student(id,student_name,status) VALUES(?,'统计学生','ENABLED')", student);
        jdbcTemplate.update("INSERT INTO growth_point_account(id,student_id,total_points,available_points) VALUES(?,?,15,15)", account, student);
        jdbcTemplate.update("INSERT INTO sys_user_organization(id,user_id,organization_id) VALUES(1900000000000165021,?,?)", orgAdmin.id(), school);
        userService.assignRole(new AssignRoleToUserCommand(orgAdmin.id(), roleMapper.findByCode("ORG_ADMIN").id(), school));
        return new OrgStatFixture(admin, orgAdmin, orgLogin, school, classA, classB, outsideSchool, student, account);
    }

    private void orgTask(long id, long sourceOrganizationId, long creator, String title, String status) {
        task(id, "ORGANIZATION", sourceOrganizationId, creator, title, status);
    }

    private void familyTask(long id, long creator, String title, String status) {
        task(id, "FAMILY", null, creator, title, status);
    }

    private void teacherTask(long id, long creator, String title, String status) {
        task(id, "TEACHER", null, creator, title, status);
    }

    private void task(long id, String sourceType, Long sourceOrganizationId, long creator, String title, String status) {
        jdbcTemplate.update("""
                INSERT INTO learn_task(id,source_type,source_organization_id,creator_user_id,title,difficulty_level,base_points,duration_minutes,scheduled_date,category_code,reviewer_user_id,status)
                VALUES(?,?,?,?,?,1,10,30,'2026-09-29','READING',?,?)
                """, id, sourceType, sourceOrganizationId, creator, title, creator, status);
    }

    private void assignment(long id, long taskId, long studentId, String status, Long sourceOrganizationId) {
        jdbcTemplate.update("""
                INSERT INTO learn_task_assignment(id,task_id,student_id,source_type,source_organization_id,current_status,current_reviewer_id,scheduled_date,due_at,completed_at,last_transition_at)
                VALUES(?,?,?,?,?,?,?,'2026-09-29','2026-09-30 10:00:00',NULL,NULL)
                """, id, taskId, studentId, sourceOrganizationId == null ? "FAMILY" : "ORGANIZATION", sourceOrganizationId, status, statReviewerId);
    }

    private void reward(long id, long studentId, long assignmentId, long accountId, long reviewerId, long amount) {
        ledger(id, accountId, studentId, assignmentId, "ORGANIZATION", "TASK_REWARD", amount, amount, reviewerId, null);
    }

    private void correction(long id, long studentId, long assignmentId, long accountId, long reviewerId, long amount, Long correctionOfId) {
        ledger(id, accountId, studentId, assignmentId, "FAMILY", "CORRECTION", amount, amount, reviewerId, correctionOfId);
    }

    private void ledger(long id, long accountId, long studentId, long assignmentId, String sourceType, String changeType, long amount, long availableDelta, long reviewerId, Long correctionOfId) {
        // TASK_REWARD 必须携带完整衰减审计字段；CORRECTION 的衰减字段必须为空且 source_type='FAMILY'（V26/V29 约束）。
        if ("CORRECTION".equals(changeType)) {
            jdbcTemplate.update("""
                    INSERT INTO growth_point_ledger(id,account_id,student_id,source_assignment_id,source_type,change_type,amount,available_delta,reviewer_user_id,occurred_at,correction_of_id,remark)
                    VALUES(?,?,?,?,'FAMILY','CORRECTION',?,?,?,?,?,'机构任务统计测试')
                    """, id, accountId, studentId, assignmentId, amount, availableDelta,
                    reviewerId, java.time.LocalDateTime.of(2026, 9, 29, 12, 0), correctionOfId);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO growth_point_ledger(id,account_id,student_id,source_assignment_id,source_task_id,source_type,change_type,amount,available_delta,base_points_snapshot,decay_percent,streak_days,reviewer_user_id,occurred_at,correction_of_id,remark)
                VALUES(?,?,?,?,?,?,?,?,?,30,0,1,?,?,NULL, '机构任务统计测试')
                """, id, accountId, studentId, assignmentId, 1900000000000160001L, sourceType, changeType, amount, availableDelta,
                reviewerId, java.time.LocalDateTime.of(2026, 9, 29, 11, 0));
    }

    private long createExport(Login login, Long classId, String outputFormat, String reason) throws Exception {
        var result = mockMvc.perform(post("/api/v1/export-jobs").header("Authorization", bearer(login.token()))
                .contentType(MediaType.APPLICATION_JSON).content(exportRequest(classId, outputFormat, reason)))
                .andExpect(status().isCreated()).andReturn();
        return Long.parseLong(body(result).path("id").asText());
    }

    private String exportRequest(Long classId, String outputFormat, String reason) {
        return "{\"exportType\":\"ORGANIZATION_TASK_STATISTICS\""
                + (classId == null ? "" : ",\"orgStatClassId\":\"" + classId + "\"")
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
                                 "deviceId":"%s","deviceName":"机构任务统计测试浏览器"}
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
            var row = workbook.createSheet("机构任务统计").createRow(0);
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

    private record OrgStatFixture(User admin, User orgAdmin, Login orgLogin,
                                  long schoolId, long classAId, long classBId, long outsideSchoolId,
                                  long studentId, long accountId) { }
}
