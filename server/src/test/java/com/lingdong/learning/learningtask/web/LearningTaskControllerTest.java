package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
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

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    @Autowired private JdbcTemplate jdbcTemplate;

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
                        .content("{\"content\":\"已完成今日阅读并记录摘要。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.effectiveStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.latestCheckIn.submissionNo").value(1))
                .andExpect(jsonPath("$.latestCheckIn.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.latestCheckIn.id").isString());
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
                        .value("已完成今日阅读并记录摘要。"));
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
        mockMvc.perform(post("/api/v1/task-assignments/{id}/check-ins", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"补充了主要人物和故事转折。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestCheckIn.submissionNo").value(2))
                .andExpect(jsonPath("$.latestCheckIn.status").value("SUBMITTED"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learn_task_checkin WHERE assignment_id = ?",
                Long.class, assignmentId)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learn_task_assignment_event WHERE assignment_id = ?",
                Long.class, assignmentId)).isEqualTo(6L);

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
    }

    private Fixture createFixture() throws Exception {
        User systemAdministrator = createUserWithRole(
                "task_flow_sys_admin", "任务闭环系统管理员", "SYS_ADMIN", null);
        User parent = createUserWithRole("task_flow_parent", "任务闭环家长", "PARENT", null);
        User organizationAdministrator = createUser("task_flow_org_admin", "任务闭环机构管理员");
        User teacher = createUser("task_flow_teacher", "任务闭环教师");
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TASK_FLOW_SCHOOL", "任务闭环学校", "SCHOOL", null, 10));
        Organization classOrganization = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TASK_FLOW_CLASS", "任务闭环一班", "CLASS", school.id(), 10));
        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(organizationAdministrator.id(), school.id()));
        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(teacher.id(), school.id()));
        assignRole(organizationAdministrator, "ORG_ADMIN", school.id());
        assignRole(teacher, "TEACHER", school.id());
        organizationAdminMapper.insert(
                1_874_244_142_494_646_404L, organizationAdministrator.id(), school.id());
        setPassword(systemAdministrator, systemAdministrator);
        setPassword(systemAdministrator, parent);
        setPassword(systemAdministrator, organizationAdministrator);
        setPassword(systemAdministrator, teacher);
        String parentToken = platformLoginToken("task_flow_parent");
        String administratorToken = platformLoginToken("task_flow_org_admin");
        String teacherToken = platformLoginToken("task_flow_teacher");
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
        return new Fixture(parent, organizationAdministrator, teacher, school, classOrganization,
                familyStudent, organizationStudent, parentToken, administratorToken, teacherToken);
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
            Organization school,
            Organization classOrganization,
            IssuedStudent familyStudent,
            IssuedStudent organizationStudent,
            String parentToken,
            String administratorToken,
            String teacherToken
    ) {
    }
}
