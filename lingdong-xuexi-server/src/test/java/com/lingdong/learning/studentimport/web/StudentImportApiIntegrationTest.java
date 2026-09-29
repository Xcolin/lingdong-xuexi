package com.lingdong.learning.studentimport.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachmentRuleApplicationService;
import com.lingdong.learning.attachment.application.CreateAttachmentRuleCommand;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.importjob.application.ImportJobBatchService;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.studentimport.application.StudentImportBatchService;
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
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 学员批量导入端到端验收：真实 XLSX 样例走通用校验作业、执行、逐行开户与一次性凭证全链路，
 * 并核对数据库事实；不使用模拟调用认定闭环。
 * 逐行处理器为 REQUIRES_NEW 独立事务，读不到测试事务未提交数据，因此本类不使用 @Transactional，
 * 改为全程真实提交并在 @AfterEach 按记录清理。
 */
@SpringBootTest(properties = "lingdong.import-validation.scheduling-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudentImportApiIntegrationTest {
    private static final long SCHOOL_ID = 1900000000000195001L;
    private static final long CLASS_A_ID = 1900000000000195002L;
    private static final long OUTSIDE_SCHOOL_ID = 1900000000000195004L;
    private static final long OUTSIDE_CLASS_ID = 1900000000000195005L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationService;
    @Autowired private UserAccessApplicationService userService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private AttachmentRuleApplicationService ruleService;
    @Autowired private ImportExportTemplateApplicationService templateService;
    @Autowired private ImportJobBatchService importJobBatch;
    @Autowired private StudentImportBatchService studentImportBatch;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<Long> jobIds = new ArrayList<>();
    private final List<Long> executionIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private Long templateId;
    private boolean createdTemplateRule;

    @AfterEach
    void cleanupFixtures() {
        // 先收集学员及其账号，再按依赖顺序精确删除本类 fixture；未记录到的不动。
        List<Long> studentIds = jobIds.isEmpty()
                ? List.of() : jdbcTemplate.queryForList("""
                SELECT DISTINCT row_record.student_id FROM sys_student_import_row row_record
                JOIN sys_student_import_execution execution_record ON execution_record.id = row_record.execution_id
                WHERE row_record.student_id IS NOT NULL
                  AND execution_record.validation_job_id IN (%s)
                """.formatted(jobIdList()), Long.class).stream().distinct().toList();
        List<Long> studentUserIds = studentIds.isEmpty()
                ? List.of() : jdbcTemplate.queryForList(
                "SELECT student_user_id FROM edu_student WHERE id IN (%s) AND student_user_id IS NOT NULL"
                        .formatted(idList(studentIds)), Long.class);
        jdbcTemplate.update("DELETE FROM sys_student_import_row WHERE execution_id IN "
                + "(SELECT id FROM sys_student_import_execution WHERE validation_job_id IN (%s))"
                .formatted(jobIdList()));
        jdbcTemplate.update("DELETE FROM sys_student_import_execution WHERE validation_job_id IN (%s)"
                .formatted(jobIdList()));
        jdbcTemplate.update("DELETE FROM sys_import_job_row_result WHERE job_id IN (%s)"
                .formatted(jobIdList()));
        jdbcTemplate.update("DELETE FROM sys_import_job WHERE id IN (%s)".formatted(jobIdList()));
        // 模板与附件规则引用上传文件，必须先于 sys_file 删除。
        if (templateId != null) {
            jdbcTemplate.update("DELETE FROM sys_import_export_template_field WHERE template_id=?", templateId);
            jdbcTemplate.update("DELETE FROM sys_import_export_template WHERE id=?", templateId);
        }
        if (createdTemplateRule) {
            jdbcTemplate.update("DELETE FROM sys_attachment_rule_extension WHERE rule_id IN "
                    + "(SELECT id FROM sys_attachment_rule WHERE module_code='IMPORT_EXPORT_TEMPLATE' "
                    + "AND file_category='TEMPLATE_FILE')");
            jdbcTemplate.update("DELETE FROM sys_attachment_rule WHERE module_code='IMPORT_EXPORT_TEMPLATE' "
                    + "AND file_category='TEMPLATE_FILE'");
        }
        if (!userIds.isEmpty()) {
            // 本类所有用户上传的文件（源文件/错误文件/模板/凭证）必须先于 sys_user 删除。
            List<Long> allOwnerIds = new ArrayList<>(userIds);
            allOwnerIds.addAll(studentUserIds);
            String ownerIds = idList(allOwnerIds);
            jdbcTemplate.update("DELETE FROM sys_file_relation WHERE file_id IN "
                    + "(SELECT id FROM sys_file WHERE uploader_id IN (%s))".formatted(ownerIds));
            jdbcTemplate.update("DELETE FROM sys_file WHERE uploader_id IN (%s)".formatted(ownerIds));
        }
        if (!studentIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM edu_student_organization WHERE student_id IN (%s)"
                    .formatted(idList(studentIds)));
            jdbcTemplate.update("DELETE FROM growth_point_dormancy_state WHERE student_id IN (%s)"
                    .formatted(idList(studentIds)));
            jdbcTemplate.update("DELETE FROM growth_point_ledger WHERE student_id IN (%s)"
                    .formatted(idList(studentIds)));
            jdbcTemplate.update("DELETE FROM growth_point_account WHERE student_id IN (%s)"
                    .formatted(idList(studentIds)));
            jdbcTemplate.update("DELETE FROM edu_student WHERE id IN (%s)".formatted(idList(studentIds)));
        }
        if (!studentUserIds.isEmpty()) {
            String studentOwnerIds = idList(studentUserIds);
            jdbcTemplate.update("DELETE FROM auth_student_credential WHERE student_user_id IN (%s)"
                    .formatted(studentOwnerIds));
            jdbcTemplate.update("DELETE FROM auth_security_event WHERE session_id IN "
                    + "(SELECT id FROM auth_device_session WHERE user_id IN (%s))"
                    .formatted(studentOwnerIds));
            jdbcTemplate.update("DELETE FROM auth_device_session WHERE user_id IN (%s)"
                    .formatted(studentOwnerIds));
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (%s)"
                    .formatted(studentOwnerIds));
            jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (%s)".formatted(studentOwnerIds));
        }
        if (!userIds.isEmpty()) {
            String ownerIds = idList(userIds);
            jdbcTemplate.update("DELETE FROM auth_security_event WHERE session_id IN "
                    + "(SELECT id FROM auth_device_session WHERE user_id IN (%s))".formatted(ownerIds));
            jdbcTemplate.update("DELETE FROM auth_device_session WHERE user_id IN (%s)"
                    .formatted(ownerIds));
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (%s)"
                    .formatted(ownerIds));
            jdbcTemplate.update("DELETE FROM sys_user_organization WHERE user_id IN (%s)"
                    .formatted(ownerIds));
            jdbcTemplate.update("DELETE FROM sys_organization_admin WHERE user_id IN (%s)"
                    .formatted(ownerIds));
            jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (%s)".formatted(ownerIds));
        }
        // 组织及残留引用兑底：本类固定组织 ID 不与其他测试共享，按 organization_id 精确清理。
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE organization_id IN (?,?,?,?)",
                SCHOOL_ID, CLASS_A_ID, OUTSIDE_SCHOOL_ID, OUTSIDE_CLASS_ID);
        jdbcTemplate.update("DELETE FROM sys_user_organization WHERE organization_id IN (?,?,?,?)",
                SCHOOL_ID, CLASS_A_ID, OUTSIDE_SCHOOL_ID, OUTSIDE_CLASS_ID);
        jdbcTemplate.update("DELETE FROM sys_organization_admin WHERE organization_id IN (?,?,?,?)",
                SCHOOL_ID, CLASS_A_ID, OUTSIDE_SCHOOL_ID, OUTSIDE_CLASS_ID);
        jdbcTemplate.update("DELETE FROM sys_import_job WHERE organization_id IN (?,?,?,?)",
                SCHOOL_ID, CLASS_A_ID, OUTSIDE_SCHOOL_ID, OUTSIDE_CLASS_ID);
        jdbcTemplate.update(
                "DELETE FROM sys_student_import_execution WHERE organization_id IN (?,?,?,?) "
                        + "OR class_organization_id IN (?,?,?,?)",
                SCHOOL_ID, CLASS_A_ID, OUTSIDE_SCHOOL_ID, OUTSIDE_CLASS_ID,
                SCHOOL_ID, CLASS_A_ID, OUTSIDE_SCHOOL_ID, OUTSIDE_CLASS_ID);
        jdbcTemplate.update("DELETE FROM edu_student_organization WHERE organization_id IN (?,?,?,?)",
                SCHOOL_ID, CLASS_A_ID, OUTSIDE_SCHOOL_ID, OUTSIDE_CLASS_ID);
        jdbcTemplate.update("DELETE FROM edu_student WHERE student_name IN ('张同学','李同学','王同学')");
        jdbcTemplate.update("DELETE FROM sys_organization WHERE id IN (?,?)",
                CLASS_A_ID, OUTSIDE_CLASS_ID);
        jdbcTemplate.update("DELETE FROM sys_organization WHERE id IN (?,?)",
                SCHOOL_ID, OUTSIDE_SCHOOL_ID);
        jobIds.clear();
        executionIds.clear();
        userIds.clear();
        templateId = null;
        createdTemplateRule = false;
    }

    private String jobIdList() {
        return idList(jobIds);
    }

    private String idList(List<Long> ids) {
        return ids.stream().map(Object::toString).reduce((left, right) -> left + "," + right).orElse("NULL");
    }

    @Test
    void studentImportFullFlowCreatesStudentsAndMatchesDatabaseFacts() throws Exception {
        Fixture f = fixture();
        byte[] source = workbook(new String[][] {
                {"张同学", "GRADE_ONE"}, {"李同学", "GRADE_TWO"}, {"王同学", null}});
        long jobId = createValidatedJob(f, source);
        jobIds.add(jobId);

        MvcResult created = mockMvc.perform(post("/api/v1/student-import-executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validationJobId\":\"%s\",\"classOrganizationId\":\"%s\"}"
                                .formatted(jobId, CLASS_A_ID))
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.credentialStatus").value("NONE"))
                .andReturn();
        long executionId = body(created).path("id").asLong();
        executionIds.add(executionId);

        assertThat(studentImportBatch.processAvailable()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/student-import-executions/{id}", executionId)
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.succeededRows").value(3))
                .andExpect(jsonPath("$.failedRows").value(0))
                .andExpect(jsonPath("$.credentialStatus").value("AVAILABLE"));

        // 逐行结果与数据库事实核对：学生档案、年级、入学与班级关系、学生账号、行级状态。
        mockMvc.perform(get("/api/v1/student-import-executions/{id}/rows", executionId)
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[0].status").value("SUCCEEDED"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edu_student WHERE student_name IN ('张同学','李同学','王同学')",
                Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT grade_code FROM edu_student WHERE student_name='张同学'", String.class))
                .isEqualTo("GRADE_ONE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edu_student WHERE student_name='王同学' AND grade_code IS NULL",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                "SELECT student_user_id FROM edu_student WHERE student_name IN ('张同学','李同学','王同学')",
                Long.class)).allMatch(id -> id != null);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE user_type='STUDENT' AND id IN "
                        + "(SELECT student_user_id FROM edu_student WHERE student_name IN ('张同学','李同学','王同学'))",
                Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM edu_student_organization
                WHERE relation_type='ENROLLMENT' AND status='ACTIVE' AND organization_id=?
                AND student_id IN (SELECT id FROM edu_student WHERE student_name IN ('张同学','李同学','王同学'))
                """, Integer.class, SCHOOL_ID)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM edu_student_organization
                WHERE relation_type='CLASS' AND status='ACTIVE' AND organization_id=?
                AND student_id IN (SELECT id FROM edu_student WHERE student_name IN ('张同学','李同学','王同学'))
                """, Integer.class, CLASS_A_ID)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_student_import_row WHERE execution_id=? "
                        + "AND status='SUCCEEDED' AND student_id IS NOT NULL",
                Integer.class, executionId)).isEqualTo(3);

        // 一次性凭证：首次可下载为 XLSX，重复下载拒绝，状态转为 CONSUMED。
        byte[] credentials = mockMvc.perform(get("/api/v1/student-import-executions/{id}/credentials", executionId)
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook credentialBook = new XSSFWorkbook(new ByteArrayInputStream(credentials))) {
            Sheet sheet = credentialBook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
            Row first = sheet.getRow(1);
            // 凭证行号为数字单元格（对应导入源文件行号）。
            assertThat(first.getCell(0).getNumericCellValue()).isEqualTo(2);
        }
        mockMvc.perform(get("/api/v1/student-import-executions/{id}/credentials", executionId)
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/student-import-executions/{id}", executionId)
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialStatus").value("CONSUMED"));
        // 越权：非本人且数据范围外账号不能读取该执行。
        mockMvc.perform(get("/api/v1/student-import-executions/{id}", executionId)
                        .header("Authorization", bearer(f.outsideLogin.token())))
                .andExpect(status().isForbidden());
        // 重复执行拒绝：同一校验作业只能创建一次学员导入。
        mockMvc.perform(post("/api/v1/student-import-executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validationJobId\":\"%s\",\"classOrganizationId\":\"%s\"}"
                                .formatted(jobId, CLASS_A_ID))
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isConflict());
    }

    @Test
    void studentImportRejectsInvalidWorkbookAndUnauthorizedAccess() throws Exception {
        Fixture f = fixture();
        // 无权限角色不能创建校验作业。
        mockMvc.perform(multipart("/api/v1/import-jobs")
                        .file(new MockMultipartFile("file", "students.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                workbook(new String[][]{{"张同学", "GRADE_ONE"}})))
                        .param("templateId", Long.toString(templateId))
                        .param("organizationId", Long.toString(SCHOOL_ID))
                        .header("Authorization", bearer(f.teacherLogin.token())))
                .andExpect(status().isForbidden());

        // 表头缺姓名列：格式错误导致校验失败并产出错误文件。
        byte[] badHeader = workbookWithHeaders("学号", "年级");
        long headerJobId = createJob(f, badHeader);
        importJobBatch.processQueuedJobs();
        mockMvc.perform(get("/api/v1/import-jobs/{id}", headerJobId)
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.status").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/import-jobs/{id}/error-file", headerJobId)
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isOk());

        // 重复数据：表头重复列在格式校验阶段即拒绝，避免歧义列映射；错误详情写入错误文件。
        byte[] duplicatedHeader = workbookWithHeaders("姓名", "姓名", "年级");
        long duplicateJobId = createJob(f, duplicatedHeader);
        importJobBatch.processQueuedJobs();
        mockMvc.perform(get("/api/v1/import-jobs/{id}", duplicateJobId)
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.status").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/import-jobs/{id}/error-file", duplicateJobId)
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isOk());

        // 逐行错误：姓名空行被逐行校验拒绝，行号定位到第二行。
        byte[] blankName = workbook(new String[][]{{"张同学", "GRADE_ONE"}, {"", "GRADE_ONE"}});
        long rowJobId = createJob(f, blankName);
        importJobBatch.processQueuedJobs();
        mockMvc.perform(get("/api/v1/import-jobs/{id}/errors", rowJobId)
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].rowNumber").value(3));

        // 越权：其他学校机构管理员不能在别人的作业上创建执行，也不能绑定范围外班级。
        long jobId = createValidatedJob(f, workbook(new String[][] {
                {"张同学", "GRADE_ONE"}, {"李同学", "GRADE_TWO"}}));
        mockMvc.perform(post("/api/v1/student-import-executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validationJobId\":\"%s\",\"classOrganizationId\":\"%s\"}"
                                .formatted(jobId, OUTSIDE_CLASS_ID))
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/student-import-executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validationJobId\":\"%s\"}".formatted(jobId))
                        .header("Authorization", bearer(f.outsideLogin.token())))
                .andExpect(status().isForbidden());
        // 校验未通过的作业不能创建学员导入。
        mockMvc.perform(post("/api/v1/student-import-executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validationJobId\":\"%s\"}".formatted(rowJobId))
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isConflict());
    }

    @Test
    void studentImportRowFailureRetriesWithRowLevelTransactions() throws Exception {
        Fixture f = fixture();
        long jobId = createValidatedJob(f, workbook(new String[][] {
                {"张同学", "GRADE_ONE"}, {"李同学", "GRADE_TWO"}}));
        MvcResult created = mockMvc.perform(post("/api/v1/student-import-executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validationJobId\":\"%s\",\"classOrganizationId\":\"%s\"}"
                                .formatted(jobId, CLASS_A_ID))
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isCreated())
                .andReturn();
        long executionId = body(created).path("id").asLong();

        // 禁用班级绑定依赖的功能开关：逐行独立事务失败，行状态与执行计数如实落库，学生未开户。
        jdbcTemplate.update(
                "UPDATE sys_feature_toggle SET status='DISABLED' WHERE feature_code='LEARNING_TASK_MANAGEMENT'");
        assertThat(studentImportBatch.processAvailable()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/student-import-executions/{id}", executionId)
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.succeededRows").value(0))
                .andExpect(jsonPath("$.failedRows").value(2))
                .andExpect(jsonPath("$.credentialStatus").value("NONE"));
        assertThat(jdbcTemplate.queryForList(
                "SELECT attempt_count FROM sys_student_import_row WHERE execution_id=?", Integer.class,
                executionId)).containsExactly(1, 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edu_student WHERE student_name IN ('张同学','李同学')", Integer.class))
                .isZero();

        // 恢复开关后失败重试：仅重排失败行，成功后生成可用凭证。
        jdbcTemplate.update(
                "UPDATE sys_feature_toggle SET status='ENABLED' WHERE feature_code='LEARNING_TASK_MANAGEMENT'");
        mockMvc.perform(post("/api/v1/student-import-executions/{id}/retry-failures", executionId)
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"));
        assertThat(studentImportBatch.processAvailable()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/student-import-executions/{id}", executionId)
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.succeededRows").value(2))
                .andExpect(jsonPath("$.credentialStatus").value("AVAILABLE"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edu_student WHERE student_name IN ('张同学','李同学') AND "
                        + "student_user_id IS NOT NULL", Integer.class)).isEqualTo(2);
        mockMvc.perform(get("/api/v1/student-import-executions/{id}/credentials", executionId)
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isOk());
    }

    // ---------- fixture ----------

    private Fixture fixture() throws Exception {
        insertOrganization(SCHOOL_ID, null, "STU_SCHOOL", "导入学校", "SCHOOL", "/STU_SCHOOL/", "ENABLED");
        insertOrganization(CLASS_A_ID, SCHOOL_ID, "STU_CLASS_A", "导入班级甲", "CLASS", "/STU_SCHOOL/STU_CLASS_A/", "ENABLED");
        insertOrganization(OUTSIDE_SCHOOL_ID, null, "STU_OUT_SCHOOL", "范围外导入学校", "SCHOOL", "/STU_OUT_SCHOOL/", "ENABLED");
        insertOrganization(OUTSIDE_CLASS_ID, OUTSIDE_SCHOOL_ID, "STU_OUT_CLASS", "范围外班级", "CLASS", "/STU_OUT_SCHOOL/STU_OUT_CLASS/", "ENABLED");
        User admin = createUser("sys_admin");
        assignRole(admin.id(), "SYS_ADMIN", null);
        User orgAdmin = createUser("stu_org_admin");
        jdbcTemplate.update("INSERT INTO sys_user_organization(id,user_id,organization_id) VALUES (?,?,?)",
                orgAdmin.id() + 10, orgAdmin.id(), SCHOOL_ID);
        jdbcTemplate.update("INSERT INTO sys_organization_admin(id,organization_id,user_id) VALUES (?,?,?)",
                orgAdmin.id() + 20, SCHOOL_ID, orgAdmin.id());
        assignRole(orgAdmin.id(), "ORG_ADMIN", SCHOOL_ID);
        User teacher = createUser("stu_teacher");
        assignRole(teacher.id(), "TEACHER", null);
        User outsideAdmin = createUser("stu_outside_admin");
        jdbcTemplate.update("INSERT INTO sys_user_organization(id,user_id,organization_id) VALUES (?,?,?)",
                outsideAdmin.id() + 10, outsideAdmin.id(), OUTSIDE_SCHOOL_ID);
        jdbcTemplate.update("INSERT INTO sys_organization_admin(id,organization_id,user_id) VALUES (?,?,?)",
                outsideAdmin.id() + 20, OUTSIDE_SCHOOL_ID, outsideAdmin.id());
        assignRole(outsideAdmin.id(), "ORG_ADMIN", OUTSIDE_SCHOOL_ID);
        Login orgLogin = setPasswordAndLogin(admin, orgAdmin, "stu-import-org");
        Login teacherLogin = setPasswordAndLogin(admin, teacher, "stu-import-teacher");
        Login outsideLogin = setPasswordAndLogin(admin, outsideAdmin, "stu-import-outside");
        Integer ruleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_attachment_rule WHERE module_code='IMPORT_EXPORT_TEMPLATE' "
                        + "AND file_category='TEMPLATE_FILE'", Integer.class);
        if (ruleCount == null || ruleCount == 0) {
            ruleService.createRule(new CreateAttachmentRuleCommand(
                    admin.id(), "IMPORT_EXPORT_TEMPLATE", "TEMPLATE_FILE", "导入模板文件",
                    List.of("xlsx"), 10_485_760L, 1, false));
            createdTemplateRule = true;
        }
        byte[] templateBook;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Row header = workbook.createSheet("学员").createRow(0);
            header.createCell(0).setCellValue("姓名");
            header.createCell(1).setCellValue("年级");
            workbook.write(output);
            templateBook = output.toByteArray();
        }
        templateId = templateService.createTemplate(new CreateImportExportTemplateUploadCommand(
                admin.id(), "学员导入模板", TemplateType.IMPORT, "STUDENT", "V1",
                "student-template.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, templateBook, false,
                List.of(new ImportTemplateFieldInput("STUDENT_NAME", "姓名", ImportTemplateFieldDataType.TEXT,
                                true, 64, null, 10),
                        new ImportTemplateFieldInput("GRADE_CODE", "年级", ImportTemplateFieldDataType.TEXT,
                                false, 64, null, 20)))).id();
        return new Fixture(admin, orgLogin, teacherLogin, outsideLogin);
    }

    private long createValidatedJob(Fixture f, byte[] source) throws Exception {
        long jobId = createJob(f, source);
        importJobBatch.processQueuedJobs();
        mockMvc.perform(get("/api/v1/import-jobs/{id}", jobId)
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.status").value("VALIDATED"));
        return jobId;
    }

    private long createJob(Fixture f, byte[] source) throws Exception {
        MvcResult created = mockMvc.perform(multipart("/api/v1/import-jobs")
                        .file(new MockMultipartFile("file", "students.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", source))
                        .param("templateId", Long.toString(templateId))
                        .param("organizationId", Long.toString(SCHOOL_ID))
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn();
        long jobId = body(created).path("id").asLong();
        jobIds.add(jobId);
        return jobId;
    }

    private void insertOrganization(long id, Long parentId, String code, String name,
            String type, String path, String status) {
        if (parentId == null) {
            jdbcTemplate.update("""
                    INSERT INTO sys_organization(id,organization_code,organization_name,organization_type,
                        organization_path,sort_order,status) VALUES(?,?,?,?,?,1,?)
                    """, id, code, name, type, path, status);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO sys_organization(id,parent_id,organization_code,organization_name,organization_type,
                        organization_path,sort_order,status) VALUES(?,?,?,?,?,?,1,?)
                    """, id, parentId, code, name, type, path, status);
        }
    }

    private User createUser(String username) {
        User user = userService.createUser(new CreateUserCommand(
                username + "-" + System.nanoTime(), username, null, UserType.PLATFORM));
        userIds.add(user.id());
        return user;
    }

    private void assignRole(long userId, String roleCode, Long organizationId) {
        Role role = roleMapper.findByCode(roleCode);
        userService.assignRole(new AssignRoleToUserCommand(userId, role.id(), organizationId));
    }

    private Login setPasswordAndLogin(User administrator, User user, String deviceId) throws Exception {
        authenticationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                administrator.id(), user.id(), "Password123"));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password123",
                                 "deviceId":"%s","deviceName":"学员导入测试浏览器"}
                                """.formatted(user.username(), deviceId)))
                .andExpect(status().isOk()).andReturn();
        return new Login(user, body(result).path("accessToken").asText());
    }

    private byte[] workbook(String[][] dataRows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Row header = workbook.createSheet("学员").createRow(0);
            header.createCell(0).setCellValue("姓名");
            header.createCell(1).setCellValue("年级");
            for (int index = 0; index < dataRows.length; index++) {
                Row row = workbook.getSheetAt(0).createRow(index + 1);
                row.createCell(0).setCellValue(dataRows[index][0] == null ? "" : dataRows[index][0]);
                if (dataRows[index][1] != null) {
                    row.createCell(1).setCellValue(dataRows[index][1]);
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] workbookWithHeaders(String... headers) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Row header = workbook.createSheet("学员").createRow(0);
            for (int index = 0; index < headers.length; index++) {
                header.createCell(index).setCellValue(headers[index]);
            }
            Row data = workbook.getSheetAt(0).createRow(1);
            data.createCell(0).setCellValue("S001");
            data.createCell(1).setCellValue("GRADE_ONE");
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

    private record Login(User user, String token) { }

    private record Fixture(User admin, Login orgLogin, Login teacherLogin, Login outsideLogin) { }
}
