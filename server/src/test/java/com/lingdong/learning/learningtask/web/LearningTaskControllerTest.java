package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.attachment.infrastructure.persistence.FileRelationMapper;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.feature.domain.FeatureStatus;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleMapper;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.learningtask.application.RecurringTaskGenerationResult;
import com.lingdong.learning.learningtask.application.RecurringTaskGenerationService;
import com.lingdong.learning.learningtask.application.TaskDeferService;
import com.lingdong.learning.learningtask.application.TaskOverdueService;
import com.lingdong.learning.organization.application.CreateOrganizationCommand;
import com.lingdong.learning.organization.application.OrganizationApplicationService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.AssociateUserWithOrganizationCommand;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LearningTaskControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private OrganizationApplicationService organizationApplicationService;
    @Autowired private OrganizationAdminMapper organizationAdminMapper;
    @Autowired private FeatureToggleMapper featureToggleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RecurringTaskGenerationService recurringTaskGenerationService;
    @Autowired private FileRelationMapper fileRelationMapper;
    @Autowired private ManagedFileMapper managedFileMapper;
    @Autowired private TaskOverdueService taskOverdueService;
    @Autowired private TaskDeferService taskDeferService;

    @Test
    void createsEditsPublishesAndReadsFamilyOrganizationAndTeacherTasks() throws Exception {
        Fixture fixture = createFixture();
        LocalDate scheduledDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1);

        MvcResult familyCreateResult = mockMvc.perform(post("/api/v1/learning-tasks")
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("FAMILY", null, "家庭阅读", 2, scheduledDate,
                                null, target("STUDENT", fixture.familyStudent().id()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.sourceType").value("FAMILY"))
                .andExpect(jsonPath("$.basePoints").value(20))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.reviewerUserId").value(fixture.parent().id().toString()))
                .andReturn();
        Long familyTaskId = responseId(familyCreateResult, "id");

        mockMvc.perform(patch("/api/v1/learning-tasks/{id}", familyTaskId)
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("FAMILY", null, "家庭阅读调整", 1, scheduledDate,
                                null, target("STUDENT", fixture.familyStudent().id()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("家庭阅读调整"))
                .andExpect(jsonPath("$.basePoints").value(10));

        mockMvc.perform(get("/api/v1/learning-tasks/{id}", familyTaskId)
                        .header("Authorization", "Bearer " + fixture.teacherToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        MvcResult organizationCreateResult = mockMvc.perform(post("/api/v1/learning-tasks")
                        .header("Authorization", "Bearer " + fixture.administratorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("ORGANIZATION", fixture.school().id(), "学校阅读", 3,
                                scheduledDate, fixture.teacher().id(),
                                target("ORGANIZATION", fixture.classOrganization().id()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewerUserId").value(fixture.teacher().id().toString()))
                .andReturn();
        Long organizationTaskId = responseId(organizationCreateResult, "id");

        MvcResult teacherCreateResult = mockMvc.perform(post("/api/v1/learning-tasks")
                        .header("Authorization", "Bearer " + fixture.teacherToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("TEACHER", fixture.classOrganization().id(), "班级口算", 1,
                                scheduledDate, null,
                                target("STUDENT", fixture.organizationStudent().id()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewerUserId").value(fixture.teacher().id().toString()))
                .andReturn();
        Long teacherTaskId = responseId(teacherCreateResult, "id");

        mockMvc.perform(get("/api/v1/learning-tasks")
                        .header("Authorization", "Bearer " + fixture.administratorToken())
                        .param("sourceType", "ORGANIZATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(organizationTaskId.toString()));

        publish(fixture.parentToken(), familyTaskId, 1);
        publish(fixture.administratorToken(), organizationTaskId, 1);
        publish(fixture.teacherToken(), teacherTaskId, 1);

        mockMvc.perform(post("/api/v1/learning-tasks/{id}/publish", familyTaskId)
                        .header("Authorization", "Bearer " + fixture.parentToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        mockMvc.perform(patch("/api/v1/learning-tasks/{id}", familyTaskId)
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("FAMILY", null, "不可编辑", 1, scheduledDate,
                                null, target("STUDENT", fixture.familyStudent().id()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        String familyStudentToken = studentLoginToken(fixture.familyStudent());
        String organizationStudentToken = studentLoginToken(fixture.organizationStudent());
        MvcResult familyAssignments = mockMvc.perform(get("/api/v1/task-assignments")
                        .header("Authorization", "Bearer " + familyStudentToken)
                        .param("sourceType", "FAMILY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("家庭阅读调整"))
                .andExpect(jsonPath("$.items[0].currentStatus").value("PENDING_CLAIM"))
                .andReturn();
        Long familyAssignmentId = responseId(familyAssignments, "items.0.id");

        mockMvc.perform(get("/api/v1/task-assignments")
                        .header("Authorization", "Bearer " + organizationStudentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(get("/api/v1/task-assignments/{id}", familyAssignmentId)
                        .header("Authorization", "Bearer " + organizationStudentToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/task-assignments")
                        .header("Authorization", "Bearer " + fixture.parentToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(familyTaskId.toString()).hasSize(19);
        assertThat(organizationTaskId.toString()).hasSize(19);
        assertThat(teacherTaskId.toString()).hasSize(19);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from learn_task_recurrence", Integer.class)).isZero();
    }

    @Test
    void createsAndReadsDailyRecurringTaskConfiguration() throws Exception {
        Fixture fixture = createFixture();
        LocalDate scheduledDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1);
        LocalDate recurrenceEndDate = scheduledDate.plusDays(10);
        ObjectNode request = (ObjectNode) objectMapper.readTree(taskBody(
                "FAMILY", null, "每日固定阅读", 2, scheduledDate,
                null, target("STUDENT", fixture.familyStudent().id())));
        request.put("recurrenceEnabled", true);
        request.put("recurrenceEndDate", recurrenceEndDate.toString());

        MvcResult result = mockMvc.perform(post("/api/v1/learning-tasks")
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recurrenceEnabled").value(true))
                .andExpect(jsonPath("$.recurrenceEndDate").value(recurrenceEndDate.toString()))
                .andReturn();

        Long taskId = responseId(result, "id");
        mockMvc.perform(get("/api/v1/learning-tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + fixture.parentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurrenceEnabled").value(true))
                .andExpect(jsonPath("$.recurrenceEndDate").value(recurrenceEndDate.toString()));

        publish(fixture.parentToken(), taskId, 1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from learn_task_recurrence where task_id = ?
                """, Integer.class, taskId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select status from learn_task_recurrence where task_id = ?
                """, String.class, taskId)).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("""
                select next_generation_date from learn_task_recurrence where task_id = ?
                """, LocalDate.class, taskId)).isEqualTo(scheduledDate.plusDays(1));
        assertThat(jdbcTemplate.queryForObject("""
                select end_date from learn_task_recurrence where task_id = ?
                """, LocalDate.class, taskId)).isEqualTo(recurrenceEndDate);
    }

    @Test
    void rejectsInvalidRecurringTaskDateCombinations() throws Exception {
        Fixture fixture = createFixture();
        LocalDate scheduledDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1);
        ObjectNode beforeStart = (ObjectNode) objectMapper.readTree(taskBody(
                "FAMILY", null, "结束日错误", 1, scheduledDate,
                null, target("STUDENT", fixture.familyStudent().id())));
        beforeStart.put("recurrenceEnabled", true);
        beforeStart.put("recurrenceEndDate", scheduledDate.minusDays(1).toString());

        mockMvc.perform(post("/api/v1/learning-tasks")
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(beforeStart)))
                .andExpect(status().isBadRequest());

        ObjectNode disabledWithEndDate = beforeStart.deepCopy();
        disabledWithEndDate.put("title", "非固定任务错误携带结束日");
        disabledWithEndDate.put("recurrenceEnabled", false);
        disabledWithEndDate.put("recurrenceEndDate", scheduledDate.plusDays(1).toString());
        mockMvc.perform(post("/api/v1/learning-tasks")
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disabledWithEndDate)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stopsRecurringTaskWithScopeStateAuditAndFeatureChecks() throws Exception {
        Fixture fixture = createFixture();
        LocalDate scheduledDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1);
        ObjectNode recurringRequest = (ObjectNode) objectMapper.readTree(taskBody(
                "FAMILY", null, "需要停止的每日任务", 1, scheduledDate,
                null, target("STUDENT", fixture.familyStudent().id())));
        recurringRequest.put("recurrenceEnabled", true);
        MvcResult recurringResult = mockMvc.perform(post("/api/v1/learning-tasks")
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recurringRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        Long recurringTaskId = responseId(recurringResult, "id");
        publish(fixture.parentToken(), recurringTaskId, 1);

        mockMvc.perform(post("/api/v1/learning-tasks/{id}/recurrence/stop", recurringTaskId)
                        .header("Authorization", "Bearer " + fixture.teacherToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/learning-tasks/{id}/recurrence/stop", recurringTaskId)
                        .header("Authorization", "Bearer " + fixture.parentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(recurringTaskId.toString()))
                .andExpect(jsonPath("$.recurrenceId").isString())
                .andExpect(jsonPath("$.status").value("STOPPED"))
                .andExpect(jsonPath("$.stoppedByUserId").value(fixture.parent().id().toString()))
                .andExpect(jsonPath("$.stoppedAt").exists());
        assertThat(jdbcTemplate.queryForObject("""
                select status from learn_task_recurrence where task_id = ?
                """, String.class, recurringTaskId)).isEqualTo("STOPPED");

        mockMvc.perform(post("/api/v1/learning-tasks/{id}/recurrence/stop", recurringTaskId)
                        .header("Authorization", "Bearer " + fixture.parentToken()))
                .andExpect(status().isConflict());

        MvcResult normalResult = mockMvc.perform(post("/api/v1/learning-tasks")
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("FAMILY", null, "普通任务不可停止周期", 1, scheduledDate,
                                null, target("STUDENT", fixture.familyStudent().id()))))
                .andExpect(status().isCreated())
                .andReturn();
        Long normalTaskId = responseId(normalResult, "id");
        publish(fixture.parentToken(), normalTaskId, 1);
        mockMvc.perform(post("/api/v1/learning-tasks/{id}/recurrence/stop", normalTaskId)
                        .header("Authorization", "Bearer " + fixture.parentToken()))
                .andExpect(status().isConflict());

        featureToggleMapper.updateGlobalStatus("LEARNING_TASK_MANAGEMENT", FeatureStatus.DISABLED);
        mockMvc.perform(post("/api/v1/learning-tasks/{id}/recurrence/stop", recurringTaskId)
                        .header("Authorization", "Bearer " + fixture.parentToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
    }

    @Test
    void generatesNextDailyAssignmentWithRealMyBatisAndCompletesPlan() throws Exception {
        Fixture fixture = createFixture();
        LocalDate scheduledDate = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate recurrenceEndDate = scheduledDate.plusDays(1);
        ObjectNode request = (ObjectNode) objectMapper.readTree(taskBody(
                "FAMILY", null, "真实跨日固定任务", 1, scheduledDate,
                null, target("STUDENT", fixture.familyStudent().id())));
        request.put("recurrenceEnabled", true);
        request.put("recurrenceEndDate", recurrenceEndDate.toString());
        MvcResult createResult = mockMvc.perform(post("/api/v1/learning-tasks")
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        Long taskId = responseId(createResult, "id");
        publish(fixture.parentToken(), taskId, 1);
        Long recurrenceId = jdbcTemplate.queryForObject(
                "select id from learn_task_recurrence where task_id = ?", Long.class, taskId);

        RecurringTaskGenerationResult result = recurringTaskGenerationService.generate(
                recurrenceId, recurrenceEndDate);

        assertThat(result.generatedDateCount()).isEqualTo(1);
        assertThat(result.generatedAssignmentCount()).isEqualTo(1);
        assertThat(result.completed()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from learn_task_assignment where task_id = ?",
                Integer.class, taskId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select status from learn_task_recurrence where id = ?",
                String.class, recurrenceId)).isEqualTo("COMPLETED");
        assertThat(recurringTaskGenerationService.generate(recurrenceId, recurrenceEndDate)
                .generatedAssignmentCount()).isZero();
    }

    @Test
    void studentClaimsPausesResumesAndSubmitsOwnTask() throws Exception {
        Fixture fixture = createFixture();
        LocalDate scheduledDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1);
        MvcResult createResult = mockMvc.perform(post("/api/v1/learning-tasks")
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("FAMILY", null, "学生执行闭环", 2, scheduledDate,
                                null, target("STUDENT", fixture.familyStudent().id()))))
                .andExpect(status().isCreated())
                .andReturn();
        Long taskId = responseId(createResult, "id");
        publish(fixture.parentToken(), taskId, 1);

        String studentToken = studentLoginToken(fixture.familyStudent());
        String otherStudentToken = studentLoginToken(fixture.organizationStudent());
        MvcResult assignments = mockMvc.perform(get("/api/v1/task-assignments")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].effectiveStatus").value("PENDING_CLAIM"))
                .andReturn();
        Long assignmentId = responseId(assignments, "items.0.id");

        mockMvc.perform(post("/api/v1/task-assignments/{id}/claim", assignmentId)
                        .header("Authorization", "Bearer " + otherStudentToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/task-assignments/{id}/claim", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.effectiveStatus").value("IN_PROGRESS"));
        mockMvc.perform(post("/api/v1/task-assignments/{id}/claim", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        mockMvc.perform(post("/api/v1/task-assignments/{id}/pause", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pauseType\":\"EMOTION\",\"durationMinutes\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.effectiveStatus").value("PAUSED"))
                .andExpect(jsonPath("$.activePause.pauseType").value("EMOTION"));
        mockMvc.perform(post("/api/v1/task-assignments/{id}/check-ins", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"暂停时不能提交\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        mockMvc.perform(post("/api/v1/task-assignments/{id}/resume", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveStatus").value("IN_PROGRESS"));
        mockMvc.perform(post("/api/v1/task-assignments/{id}/check-ins", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        byte[] imageContent = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01};
        MockMultipartFile image = new MockMultipartFile(
                "file", "reading.jpg", "image/jpeg", imageContent);
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/attachments/uploads")
                        .file(image)
                        .param("moduleCode", "LEARNING_TASK_CHECKIN")
                        .param("fileCategory", "IMAGE")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.originalName").value("reading.jpg"))
                .andReturn();
        Long fileId = responseId(uploadResult, "id");
        assertThat(managedFileMapper.findById(fileId).moduleCode())
                .isEqualTo("LEARNING_TASK_CHECKIN");
        Long otherStudentUserId = jdbcTemplate.queryForObject(
                "SELECT student_user_id FROM edu_student WHERE id = ?",
                Long.class, fixture.organizationStudent().id());
        assertThat(fileRelationMapper.countReadableByCurrentReviewer(
                fileId, otherStudentUserId)).isZero();
        mockMvc.perform(get("/api/v1/attachments/{id}/content", fileId)
                        .header("Authorization", "Bearer " + otherStudentToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/attachments/{id}/content", fileId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType("image/jpeg"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .bytes(imageContent));
        mockMvc.perform(post("/api/v1/task-assignments/{id}/check-ins", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"已完成今日阅读并记录摘要。\",\"fileIds\":[\"%s\"]}"
                                .formatted(fileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.effectiveStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.latestCheckIn.submissionNo").value(1))
                .andExpect(jsonPath("$.latestCheckIn.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.latestCheckIn.attachments[0].id").value(fileId.toString()))
                .andExpect(jsonPath("$.latestCheckIn.id").isString());
        mockMvc.perform(delete("/api/v1/attachments/{id}", fileId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/task-assignments/{id}/check-ins", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"重复提交\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learn_task_assignment_event WHERE assignment_id = ?",
                Long.class, assignmentId)).isEqualTo(4L);
        Long checkInId = jdbcTemplate.queryForObject(
                "SELECT id FROM learn_task_checkin WHERE assignment_id = ?",
                Long.class, assignmentId);
        assertThat(checkInId).isNotNull();
        assertThat(checkInId.toString()).hasSize(19);

        mockMvc.perform(get("/api/v1/task-reviews")
                        .header("Authorization", "Bearer " + fixture.parentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].assignmentId").value(assignmentId.toString()))
                .andExpect(jsonPath("$.items[0].latestCheckIn.content")
                        .value("已完成今日阅读并记录摘要。"))
                .andExpect(jsonPath("$.items[0].latestCheckIn.attachments[0].id")
                        .value(fileId.toString()));
        mockMvc.perform(get("/api/v1/attachments/{id}/content", fileId)
                        .header("Authorization", "Bearer " + fixture.parentToken()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .bytes(imageContent));
        mockMvc.perform(get("/api/v1/task-reviews/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + fixture.teacherToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/task-reviews/{assignmentId}/reject", assignmentId)
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewComment\":\"摘要需要补充主要人物。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(assignmentId.toString()))
                .andExpect(jsonPath("$.currentStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.checkInStatus").value("REJECTED"));
        mockMvc.perform(get("/api/v1/task-assignments/{id}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestCheckIn.status").value("REJECTED"))
                .andExpect(jsonPath("$.latestCheckIn.reviewComment")
                        .value("摘要需要补充主要人物。"));

        MockMultipartFile supplementImage = new MockMultipartFile(
                "file", "reading-supplement.jpg", "image/jpeg", imageContent);
        MvcResult supplementUploadResult = mockMvc.perform(multipart("/api/v1/attachments/uploads")
                        .file(supplementImage)
                        .param("moduleCode", "LEARNING_TASK_CHECKIN")
                        .param("fileCategory", "IMAGE")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn();
        Long supplementFileId = responseId(supplementUploadResult, "id");
        mockMvc.perform(post("/api/v1/task-assignments/{id}/check-ins", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileIds\":[\"%s\"]}".formatted(supplementFileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestCheckIn.submissionNo").value(2))
                .andExpect(jsonPath("$.latestCheckIn.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.latestCheckIn.content").doesNotExist())
                .andExpect(jsonPath("$.latestCheckIn.attachments[0].id")
                        .value(supplementFileId.toString()));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learn_task_checkin WHERE assignment_id = ?",
                Long.class, assignmentId)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learn_task_assignment_event WHERE assignment_id = ?",
                Long.class, assignmentId)).isEqualTo(6L);

        mockMvc.perform(post("/api/v1/task-reviews/{assignmentId}/approve", assignmentId)
                        .header("Authorization", "Bearer " + fixture.teacherToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/task-reviews/{assignmentId}/approve", assignmentId)
                        .header("Authorization", "Bearer " + fixture.parentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(assignmentId.toString()))
                .andExpect(jsonPath("$.currentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.checkInStatus").value("APPROVED"))
                .andExpect(jsonPath("$.awardedPoints").value(20))
                .andExpect(jsonPath("$.totalPoints").value(20))
                .andExpect(jsonPath("$.availablePoints").value(20))
                .andExpect(jsonPath("$.ledgerId").isString());
        mockMvc.perform(post("/api/v1/task-reviews/{assignmentId}/approve", assignmentId)
                        .header("Authorization", "Bearer " + fixture.parentToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_status FROM learn_task_assignment WHERE id = ?",
                String.class, assignmentId)).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM learn_task_checkin WHERE assignment_id = ? AND submission_no = 2",
                String.class, assignmentId)).isEqualTo("APPROVED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT total_points FROM growth_point_account WHERE student_id = ?",
                Long.class, fixture.familyStudent().id())).isEqualTo(20L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT available_points FROM growth_point_account WHERE student_id = ?",
                Long.class, fixture.familyStudent().id())).isEqualTo(20L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM growth_point_ledger WHERE source_assignment_id = ? "
                        + "AND change_type = 'TASK_REWARD'",
                Long.class, assignmentId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learn_task_assignment_event WHERE assignment_id = ? "
                        + "AND event_type = 'REVIEW_APPROVED'",
                Long.class, assignmentId)).isEqualTo(1L);

        mockMvc.perform(get("/api/v1/growth-points/me/account")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(fixture.familyStudent().id().toString()))
                .andExpect(jsonPath("$.studentName").value("任务家庭学生"))
                .andExpect(jsonPath("$.totalPoints").value(20))
                .andExpect(jsonPath("$.availablePoints").value(20))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        mockMvc.perform(get("/api/v1/growth-points/me/ledgers")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").isString())
                .andExpect(jsonPath("$.items[0].changeType").value("TASK_REWARD"))
                .andExpect(jsonPath("$.items[0].amount").value(20))
                .andExpect(jsonPath("$.items[0].availableDelta").value(20))
                .andExpect(jsonPath("$.items[0].sourceAssignmentId").value(assignmentId.toString()))
                .andExpect(jsonPath("$.items[0].sourceTaskId").isString())
                .andExpect(jsonPath("$.items[0].basePointsSnapshot").value(20))
                .andExpect(jsonPath("$.items[0].decayPercent").value(0))
                .andExpect(jsonPath("$.items[0].streakDays").value(1))
                .andExpect(jsonPath("$.items[0].decayRuleId").doesNotExist())
                .andExpect(jsonPath("$.items[0].sourceType").value("FAMILY"))
                .andExpect(jsonPath("$.items[0].taskTitle").value("学生执行闭环"))
                .andExpect(jsonPath("$.items[0].reviewerDisplayName").value("任务闭环家长"))
                .andExpect(jsonPath("$.items[0].occurredAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/growth-points/students")
                        .header("Authorization", "Bearer " + fixture.parentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(fixture.familyStudent().id().toString()))
                .andExpect(jsonPath("$[0].studentName").value("任务家庭学生"))
                .andExpect(jsonPath("$[1]").doesNotExist());
        mockMvc.perform(get("/api/v1/growth-points/students/{studentId}/account",
                        fixture.familyStudent().id())
                        .header("Authorization", "Bearer " + fixture.parentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(20));
        mockMvc.perform(get("/api/v1/growth-points/students/{studentId}/ledgers",
                        fixture.familyStudent().id())
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/api/v1/growth-points/students/{studentId}/account",
                        fixture.organizationStudent().id())
                        .header("Authorization", "Bearer " + fixture.parentToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/growth-points/students/{studentId}/account",
                        fixture.familyStudent().id())
                        .header("Authorization", "Bearer " + fixture.teacherToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/v1/growth-points/me/account")
                        .header("Authorization", "Bearer " + fixture.parentToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/v1/growth-points/students/{studentId}/account",
                        fixture.familyStudent().id())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        featureToggleMapper.updateGlobalStatus("GROWTH_POINT_QUERY", FeatureStatus.DISABLED);
        mockMvc.perform(get("/api/v1/growth-points/me/account")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
        featureToggleMapper.updateGlobalStatus("GROWTH_POINT_QUERY", FeatureStatus.ENABLED);

        Long originalLedgerId = jdbcTemplate.queryForObject(
                "SELECT id FROM growth_point_ledger WHERE source_assignment_id = ? "
                        + "AND change_type = 'TASK_REWARD'",
                Long.class, assignmentId);
        LocalDateTime originalOccurredAt = jdbcTemplate.queryForObject(
                "SELECT occurred_at FROM growth_point_ledger WHERE id = ?",
                LocalDateTime.class, originalLedgerId);

        mockMvc.perform(get("/api/v1/growth-points/students/{studentId}/ledgers",
                        fixture.familyStudent().id())
                        .header("Authorization", "Bearer " + fixture.parentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].correctable").value(true))
                .andExpect(jsonPath("$.items[0].correctionDeadline").isNotEmpty())
                .andExpect(jsonPath("$.items[0].correctionLedgerId").doesNotExist());
        mockMvc.perform(post("/api/v1/growth-points/students/{studentId}/corrections",
                        fixture.familyStudent().id())
                        .header("Authorization", "Bearer " + fixture.teacherToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalLedgerId":"%s","reason":"教师不得纠正家庭积分"}
                                """.formatted(originalLedgerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(post("/api/v1/growth-points/students/{studentId}/corrections",
                        fixture.organizationStudent().id())
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalLedgerId":"%s","reason":"越权学生不应泄露"}
                                """.formatted(originalLedgerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/growth-points/students/{studentId}/corrections",
                        fixture.familyStudent().id())
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalLedgerId":"%s","reason":"  "}
                                """.formatted(originalLedgerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        jdbcTemplate.update("UPDATE growth_point_ledger SET occurred_at = ? WHERE id = ?",
                originalOccurredAt.minusHours(73), originalLedgerId);
        mockMvc.perform(post("/api/v1/growth-points/students/{studentId}/corrections",
                        fixture.familyStudent().id())
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalLedgerId":"%s","reason":"超过纠错时限"}
                                """.formatted(originalLedgerId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        jdbcTemplate.update("UPDATE growth_point_ledger SET occurred_at = ? WHERE id = ?",
                originalOccurredAt, originalLedgerId);

        featureToggleMapper.updateGlobalStatus("GROWTH_POINT_CORRECTION", FeatureStatus.DISABLED);
        mockMvc.perform(post("/api/v1/growth-points/students/{studentId}/corrections",
                        fixture.familyStudent().id())
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalLedgerId":"%s","reason":"功能停用时不得纠错"}
                                """.formatted(originalLedgerId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
        featureToggleMapper.updateGlobalStatus("GROWTH_POINT_CORRECTION", FeatureStatus.ENABLED);

        MvcResult correctionResult = mockMvc.perform(post(
                        "/api/v1/growth-points/students/{studentId}/corrections",
                        fixture.familyStudent().id())
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalLedgerId":"%s","reason":"误点审核通过，重新核对摘要"}
                                """.formatted(originalLedgerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(fixture.familyStudent().id().toString()))
                .andExpect(jsonPath("$.assignmentId").value(assignmentId.toString()))
                .andExpect(jsonPath("$.originalLedgerId").value(originalLedgerId.toString()))
                .andExpect(jsonPath("$.correctionLedgerId").isString())
                .andExpect(jsonPath("$.correctedPoints").value(20))
                .andExpect(jsonPath("$.totalPoints").value(0))
                .andExpect(jsonPath("$.availablePoints").value(0))
                .andExpect(jsonPath("$.currentStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.occurredAt").isNotEmpty())
                .andReturn();
        Long correctionLedgerId = responseId(correctionResult, "correctionLedgerId");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_status FROM learn_task_assignment WHERE id = ?",
                String.class, assignmentId)).isEqualTo("PENDING_REVIEW");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT completed_at FROM learn_task_assignment WHERE id = ?",
                LocalDateTime.class, assignmentId)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM learn_task_checkin WHERE assignment_id = ? AND submission_no = 2",
                String.class, assignmentId)).isEqualTo("SUBMITTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT amount FROM growth_point_ledger WHERE id = ?",
                Long.class, correctionLedgerId)).isEqualTo(-20L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT correction_of_id FROM growth_point_ledger WHERE id = ?",
                Long.class, correctionLedgerId)).isEqualTo(originalLedgerId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learn_task_assignment_event WHERE assignment_id = ? "
                        + "AND event_type = 'POINT_CORRECTED'",
                Long.class, assignmentId)).isEqualTo(1L);

        mockMvc.perform(post("/api/v1/growth-points/students/{studentId}/corrections",
                        fixture.familyStudent().id())
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalLedgerId":"%s","reason":"重复纠错"}
                                """.formatted(originalLedgerId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        mockMvc.perform(get("/api/v1/growth-points/me/ledgers")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].changeType").value("CORRECTION"))
                .andExpect(jsonPath("$.items[0].amount").value(-20))
                .andExpect(jsonPath("$.items[0].correctionOfId").value(originalLedgerId.toString()))
                .andExpect(jsonPath("$.items[0].correctable").value(false))
                .andExpect(jsonPath("$.items[1].correctionLedgerId").value(correctionLedgerId.toString()))
                .andExpect(jsonPath("$.items[1].correctable").value(false));

        mockMvc.perform(post("/api/v1/task-reviews/{assignmentId}/approve", assignmentId)
                        .header("Authorization", "Bearer " + fixture.parentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.awardedPoints").value(20))
                .andExpect(jsonPath("$.totalPoints").value(20))
                .andExpect(jsonPath("$.availablePoints").value(20));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM growth_point_ledger WHERE source_assignment_id = ? "
                        + "AND change_type = 'TASK_REWARD'",
                Long.class, assignmentId)).isEqualTo(2L);

        MvcResult teacherTaskResult = mockMvc.perform(post("/api/v1/learning-tasks")
                        .header("Authorization", "Bearer " + fixture.teacherToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("TEACHER", fixture.classOrganization().id(),
                                "需要转交审核的任务", 2, scheduledDate, null,
                                target("STUDENT", fixture.organizationStudent().id()))))
                .andExpect(status().isCreated())
                .andReturn();
        Long teacherTaskId = responseId(teacherTaskResult, "id");
        publish(fixture.teacherToken(), teacherTaskId, 1);
        MvcResult organizationAssignments = mockMvc.perform(get("/api/v1/task-assignments")
                        .header("Authorization", "Bearer " + otherStudentToken))
                .andExpect(status().isOk())
                .andReturn();
        Long transferAssignmentId = assignmentIdByTitle(
                organizationAssignments, "需要转交审核的任务");
        mockMvc.perform(post("/api/v1/task-assignments/{id}/claim", transferAssignmentId)
                        .header("Authorization", "Bearer " + otherStudentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/task-assignments/{id}/check-ins", transferAssignmentId)
                        .header("Authorization", "Bearer " + otherStudentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"完成了需要转交审核的任务。\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/task-reviews/{assignmentId}/reviewer-options", transferAssignmentId)
                        .header("Authorization", "Bearer " + fixture.teacherToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(fixture.backupTeacher().id().toString()));
        mockMvc.perform(post("/api/v1/task-reviews/{assignmentId}/transfer", transferAssignmentId)
                        .header("Authorization", "Bearer " + fixture.teacherToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerUserId\":\"%s\",\"transferReason\":\"由同班教师接续审核\"}"
                                .formatted(fixture.backupTeacher().id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentReviewerId")
                        .value(fixture.backupTeacher().id().toString()));
        mockMvc.perform(get("/api/v1/task-reviews/{assignmentId}", transferAssignmentId)
                        .header("Authorization", "Bearer " + fixture.teacherToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/task-reviews/{assignmentId}", transferAssignmentId)
                        .header("Authorization", "Bearer " + fixture.backupTeacherToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentReviewerId")
                        .value(fixture.backupTeacher().id().toString()));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learn_task_reviewer_transfer WHERE assignment_id = ?",
                Long.class, transferAssignmentId)).isEqualTo(1L);

        MvcResult abandonTaskResult = mockMvc.perform(post("/api/v1/learning-tasks")
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("FAMILY", null, "允许放弃的任务", 1, scheduledDate,
                                null, target("STUDENT", fixture.familyStudent().id()))))
                .andExpect(status().isCreated())
                .andReturn();
        Long abandonTaskId = responseId(abandonTaskResult, "id");
        publish(fixture.parentToken(), abandonTaskId, 1);
        MvcResult refreshedAssignments = mockMvc.perform(get("/api/v1/task-assignments")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andReturn();
        Long abandonAssignmentId = assignmentIdByTitle(refreshedAssignments, "允许放弃的任务");
        mockMvc.perform(post("/api/v1/task-assignments/{id}/claim", abandonAssignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/task-assignments/{id}/pause", abandonAssignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pauseType\":\"DIFFICULTY\",\"durationMinutes\":121}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/task-assignments/{id}/resume", abandonAssignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/task-assignments/{id}/abandon", abandonAssignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"今天状态不适合继续\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("NEEDS_IMPROVEMENT"))
                .andExpect(jsonPath("$.effectiveStatus").value("NEEDS_IMPROVEMENT"));

        mockMvc.perform(get("/api/v1/managed-task-assignments")
                        .header("Authorization", "Bearer " + fixture.parentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].assignmentId")
                        .value(abandonAssignmentId.toString()))
                .andExpect(jsonPath("$.items[0].studentId")
                        .value(fixture.familyStudent().id().toString()))
                .andExpect(jsonPath("$.items[0].currentStatus").value("NEEDS_IMPROVEMENT"));

        LocalDate deferredDate = scheduledDate.plusDays(1);
        mockMvc.perform(post("/api/v1/managed-task-assignments/{id}/defer", abandonAssignmentId)
                        .header("Authorization", "Bearer " + fixture.teacherToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetDate\":\"%s\"}".formatted(deferredDate)))
                .andExpect(status().isNotFound());
        MvcResult deferResult = mockMvc.perform(post(
                        "/api/v1/managed-task-assignments/{id}/defer", abandonAssignmentId)
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetDate\":\"%s\"}".formatted(deferredDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(abandonAssignmentId.toString()))
                .andExpect(jsonPath("$.targetTaskId").isString())
                .andExpect(jsonPath("$.status").value("PENDING_CLAIM"))
                .andExpect(jsonPath("$.targetDate").value(deferredDate.toString()))
                .andExpect(jsonPath("$.deferType").value("MANUAL"))
                .andExpect(jsonPath("$.overnightMigrated").value(true))
                .andReturn();
        Long deferredTaskId = responseId(deferResult, "targetTaskId");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_status FROM learn_task_assignment WHERE id = ?",
                String.class, abandonAssignmentId)).isEqualTo("PENDING_CLAIM");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT scheduled_date FROM learn_task_assignment WHERE id = ?",
                LocalDate.class, abandonAssignmentId)).isEqualTo(deferredDate);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT base_points FROM learn_task WHERE id = ?",
                Integer.class, deferredTaskId)).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learn_task_tag WHERE task_id = ? AND tag_code = 'DAILY'",
                Long.class, deferredTaskId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learn_task_defer_history "
                        + "WHERE assignment_id = ? AND target_task_id = ? AND defer_type = 'MANUAL'",
                Long.class, abandonAssignmentId, deferredTaskId)).isEqualTo(1L);

        MvcResult exemptTaskResult = mockMvc.perform(post("/api/v1/learning-tasks")
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("FAMILY", null, "允许免执行的任务", 1, scheduledDate,
                                null, target("STUDENT", fixture.familyStudent().id()))))
                .andExpect(status().isCreated())
                .andReturn();
        Long exemptTaskId = responseId(exemptTaskResult, "id");
        publish(fixture.parentToken(), exemptTaskId, 1);
        MvcResult finalAssignments = mockMvc.perform(get("/api/v1/task-assignments")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();
        Long exemptAssignmentId = assignmentIdByTitle(finalAssignments, "允许免执行的任务");
        mockMvc.perform(post("/api/v1/managed-task-assignments/{id}/exempt", exemptAssignmentId)
                        .header("Authorization", "Bearer " + fixture.teacherToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"越权操作不应成功\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/managed-task-assignments/{id}/exempt", exemptAssignmentId)
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"家庭安排调整\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(exemptAssignmentId.toString()))
                .andExpect(jsonPath("$.currentStatus").value("EXEMPT"));
        mockMvc.perform(get("/api/v1/task-assignments/{id}", exemptAssignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveStatus").value("EXEMPT"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learn_task_assignment_event "
                        + "WHERE assignment_id = ? AND event_type = 'EXEMPTED'",
                Long.class, exemptAssignmentId)).isEqualTo(1L);
    }

    @Test
    void marksDueTaskAsNeedsImprovementAndAutomaticallyDefersItIdempotently() throws Exception {
        Fixture fixture = createFixture();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        MvcResult createResult = mockMvc.perform(post("/api/v1/learning-tasks")
                        .header("Authorization", "Bearer " + fixture.parentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("FAMILY", null, "自动顺延任务", 2, today,
                                null, target("STUDENT", fixture.familyStudent().id()))))
                .andExpect(status().isCreated())
                .andReturn();
        Long taskId = responseId(createResult, "id");
        publish(fixture.parentToken(), taskId, 1);
        String studentToken = studentLoginToken(fixture.familyStudent());
        MvcResult assignments = mockMvc.perform(get("/api/v1/task-assignments")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();
        Long assignmentId = assignmentIdByTitle(assignments, "自动顺延任务");
        mockMvc.perform(post("/api/v1/task-assignments/{id}/claim", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        LocalDateTime cutoff = today.atTime(23, 59, 59);
        jdbcTemplate.update(
                "UPDATE learn_task_assignment SET due_at = ? WHERE id = ?", cutoff, assignmentId);
        assertThat(taskOverdueService.markNeedsImprovement(assignmentId, cutoff)).isTrue();
        assertThat(taskOverdueService.markNeedsImprovement(assignmentId, cutoff)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learn_task_assignment_event "
                        + "WHERE assignment_id = ? AND event_type = 'MARKED_NEEDS_IMPROVEMENT' "
                        + "AND operator_user_id IS NULL",
                Long.class, assignmentId)).isEqualTo(1L);

        LocalDate targetDate = today.plusDays(1);
        assertThat(taskDeferService.deferAutomatically(assignmentId, targetDate)).isNotNull();
        assertThat(taskDeferService.deferAutomatically(assignmentId, targetDate)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_status FROM learn_task_assignment WHERE id = ?",
                String.class, assignmentId)).isEqualTo("PENDING_CLAIM");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT overnight_migrated FROM learn_task_assignment WHERE id = ?",
                Boolean.class, assignmentId)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learn_task_defer_history "
                        + "WHERE assignment_id = ? AND defer_type = 'AUTO'",
                Long.class, assignmentId)).isEqualTo(1L);
    }

    private Fixture createFixture() throws Exception {
        User systemAdministrator = createUserWithRole(
                "task_flow_sys_admin", "任务闭环系统管理员", "SYS_ADMIN", null);
        User parent = createUserWithRole("task_flow_parent", "任务闭环家长", "PARENT", null);
        User organizationAdministrator = createUser("task_flow_org_admin", "任务闭环机构管理员");
        User teacher = createUser("task_flow_teacher", "任务闭环教师");
        User backupTeacher = createUser("task_flow_backup_teacher", "任务闭环备用教师");
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TASK_FLOW_SCHOOL", "任务闭环学校", "SCHOOL", null, 10));
        Organization classOrganization = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TASK_FLOW_CLASS", "任务闭环一班", "CLASS", school.id(), 10));
        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(organizationAdministrator.id(), school.id()));
        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(teacher.id(), school.id()));
        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(backupTeacher.id(), school.id()));
        assignRole(organizationAdministrator, "ORG_ADMIN", school.id());
        assignRole(teacher, "TEACHER", school.id());
        assignRole(backupTeacher, "TEACHER", school.id());
        organizationAdminMapper.insert(
                1_874_244_142_494_646_404L, organizationAdministrator.id(), school.id());
        setPassword(systemAdministrator, systemAdministrator);
        setPassword(systemAdministrator, parent);
        setPassword(systemAdministrator, organizationAdministrator);
        setPassword(systemAdministrator, teacher);
        setPassword(systemAdministrator, backupTeacher);
        String parentToken = platformLoginToken("task_flow_parent");
        String administratorToken = platformLoginToken("task_flow_org_admin");
        String teacherToken = platformLoginToken("task_flow_teacher");
        String backupTeacherToken = platformLoginToken("task_flow_backup_teacher");
        IssuedStudent familyStudent = createStudent(parentToken, "任务家庭学生", null);
        IssuedStudent organizationStudent = createStudent(administratorToken, "任务机构学生", school.id());
        mockMvc.perform(put("/api/v1/students/{studentId}/class", organizationStudent.id())
                        .header("Authorization", "Bearer " + administratorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classOrganizationId\":\"%s\"}".formatted(classOrganization.id())))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/teachers/{teacherUserId}/classes/{classId}",
                        teacher.id(), classOrganization.id())
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/teachers/{teacherUserId}/classes/{classId}",
                        backupTeacher.id(), classOrganization.id())
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isOk());
        return new Fixture(parent, organizationAdministrator, teacher, backupTeacher,
                school, classOrganization, familyStudent, organizationStudent,
                parentToken, administratorToken, teacherToken, backupTeacherToken);
    }

    private String taskBody(
            String sourceType,
            Long sourceOrganizationId,
            String title,
            int difficulty,
            LocalDate scheduledDate,
            Long reviewerUserId,
            ObjectNode target
    ) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("sourceType", sourceType)
                .put("title", title)
                .put("difficultyLevel", difficulty)
                .put("durationMinutes", 30)
                .put("scheduledDate", scheduledDate.toString())
                .put("categoryCode", "GENERAL")
                .put("remark", "任务闭环测试");
        if (sourceOrganizationId != null) {
            body.put("sourceOrganizationId", sourceOrganizationId.toString());
        }
        if (reviewerUserId != null) {
            body.put("reviewerUserId", reviewerUserId.toString());
        }
        body.putArray("tagCodes").add("DAILY");
        body.putArray("targets").add(target);
        return objectMapper.writeValueAsString(body);
    }

    private ObjectNode target(String targetType, Long targetId) {
        return objectMapper.createObjectNode()
                .put("targetType", targetType)
                .put("targetId", targetId.toString());
    }

    private void publish(String token, Long taskId, int assignmentCount) throws Exception {
        mockMvc.perform(post("/api/v1/learning-tasks/{id}/publish", taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId.toString()))
                .andExpect(jsonPath("$.assignmentCount").value(assignmentCount))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    private IssuedStudent createStudent(String accessToken, String name, Long organizationId) throws Exception {
        ObjectNode request = objectMapper.createObjectNode().put("studentName", name);
        if (organizationId != null) {
            request.put("organizationId", organizationId.toString());
        }
        MvcResult result = mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return new IssuedStudent(response.path("id").asLong(), response.path("studentAccount").asText(),
                response.path("initialLoginCode").asText());
    }

    private String studentLoginToken(IssuedStudent student) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/student-sessions/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentAccount":"%s","loginCode":"%s","deviceId":"task-student-%s","deviceName":"任务学生测试设备"}
                                """.formatted(student.account(), student.loginCode(), student.id())))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }

    private Long responseId(MvcResult result, String dottedPath) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        for (String part : dottedPath.split("\\.")) {
            node = part.chars().allMatch(Character::isDigit) ? node.path(Integer.parseInt(part)) : node.path(part);
        }
        return node.asLong();
    }

    private Long assignmentIdByTitle(MvcResult result, String title) throws Exception {
        for (JsonNode item : objectMapper.readTree(result.getResponse().getContentAsString()).path("items")) {
            if (title.equals(item.path("title").asText())) {
                return item.path("id").asLong();
            }
        }
        throw new AssertionError("未找到任务实例：" + title);
    }

    private User createUserWithRole(String username, String displayName, String roleCode, Long organizationId) {
        User user = createUser(username, displayName);
        assignRole(user, roleCode, organizationId);
        return user;
    }

    private User createUser(String username, String displayName) {
        return userAccessApplicationService.createUser(
                new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
    }

    private void assignRole(User user, String roleCode, Long organizationId) {
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), organizationId));
    }

    private void setPassword(User systemAdministrator, User targetUser) {
        authenticationApplicationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                systemAdministrator.id(), targetUser.id(), "Password123"));
    }

    private String platformLoginToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password123","deviceId":"%s-device","deviceName":"任务闭环测试浏览器"}
                                """.formatted(username, username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }

    private record IssuedStudent(Long id, String account, String loginCode) {
    }

    private record Fixture(
            User parent,
            User organizationAdministrator,
            User teacher,
            User backupTeacher,
            Organization school,
            Organization classOrganization,
            IssuedStudent familyStudent,
            IssuedStudent organizationStudent,
            String parentToken,
            String administratorToken,
            String teacherToken,
            String backupTeacherToken
    ) {
    }
}
