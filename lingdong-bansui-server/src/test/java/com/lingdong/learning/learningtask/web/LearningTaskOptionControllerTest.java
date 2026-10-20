package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.application.CreateOrganizationCommand;
import com.lingdong.learning.organization.application.OrganizationApplicationService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
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

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LearningTaskOptionControllerTest {
    @Autowired private org.apache.ibatis.session.SqlSession sqlSession;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private OrganizationApplicationService organizationApplicationService;
    @Autowired private OrganizationAdminMapper organizationAdminMapper;
    @Autowired private ParentStudentMapper parentStudentMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void returnsOnlyRoleScopedAndMaskedTaskOptions() throws Exception {
        User systemAdministrator = createUserWithRole(
                "task_option_sys_admin", "任务候选系统管理员", "SYS_ADMIN", null);
        User parent = createUserWithRole("task_option_parent", "任务候选家长", "PARENT", null);
        User secondaryParent = createUserWithRole(
                "task_option_secondary_parent", "任务候选副家长", "PARENT", null);
        User organizationAdministrator = createUser("task_option_org_admin", "任务候选机构管理员");
        User teacher = createUser("task_option_teacher", "任务候选教师");
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TASK_OPTION_SCHOOL", "任务候选学校", "SCHOOL", null, 10));
        Organization schoolGrade = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TASK_OPTION_CLASS_GRADE", "测试年级", "GRADE", school.id(), 10));
        Organization classOrganization = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TASK_OPTION_CLASS", "任务候选一班", "CLASS", schoolGrade.id(), 10));
        Organization outsideSchool = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TASK_OPTION_OUTSIDE_SCHOOL", "候选范围外学校", "SCHOOL", null, 20));
        Organization outsideSchoolGrade = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TASK_OPTION_OUTSIDE_CLASS_GRADE", "测试年级", "GRADE", outsideSchool.id(), 10));
        organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TASK_OPTION_OUTSIDE_CLASS", "候选范围外班级", "CLASS", outsideSchoolGrade.id(), 10));

        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(organizationAdministrator.id(), school.id()));
        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(teacher.id(), school.id()));
        assignRole(organizationAdministrator, "ORG_ADMIN", school.id());
        assignRole(teacher, "TEACHER", school.id());
        organizationAdminMapper.insert(
                1_874_244_142_494_646_403L, organizationAdministrator.id(), school.id());
        setPassword(systemAdministrator, systemAdministrator);
        setPassword(systemAdministrator, parent);
        setPassword(systemAdministrator, secondaryParent);
        setPassword(systemAdministrator, organizationAdministrator);
        setPassword(systemAdministrator, teacher);
        // V135 后教师职业身份独立持久化：需机构账号类型与启用教师档案，否则分班被判非启用教师
        makeTeacher("task_option_teacher");

        // V118 后家长需为 FAMILY 身份且持有家长档案，否则 service 层 requireParent 拒绝建学生
        makeFamilyParent("task_option_parent");
        makeFamilyParent("task_option_secondary_parent");

        String parentToken = loginAccessToken("task_option_parent");
        String secondaryParentToken = loginAccessToken("task_option_secondary_parent");
        String administratorToken = loginAccessToken("task_option_org_admin");
        String teacherToken = loginAccessToken("task_option_teacher");
        Long familyStudentId = createStudent(parentToken, "家庭候选学生", null);
        parentStudentMapper.insertSecondary(
                8_910_000_000_000_000_821L, secondaryParent.id(), familyStudentId,
                LocalDateTime.now());
        Long organizationStudentId = createStudent(administratorToken, "机构候选学生", school.id());

        mockMvc.perform(put("/api/v1/students/{studentId}/class", organizationStudentId)
                        .header("Authorization", "Bearer " + administratorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classOrganizationId\":\"%s\"}".formatted(classOrganization.id())))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/teachers/{teacherUserId}/classes/{classId}",
                        teacher.id(), classOrganization.id())
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/learning-task-options/students")
                        .param("sourceType", "FAMILY")
                        .header("Authorization", "Bearer " + parentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(familyStudentId.toString()))
                .andExpect(jsonPath("$[0].relationshipRole").value("PRIMARY_GUARDIAN"))
                // 学号序列随库环境变化（H2/MySQL 起始序列不同），仅断言掩码格式不泄露完整账号
                .andExpect(jsonPath("$[0].studentAccountMasked").value(org.hamcrest.Matchers.matchesPattern("^26\\*{4}\\d{2}$")))
                .andExpect(jsonPath("$[0].studentAccount").doesNotExist())
                .andExpect(jsonPath("$[0].mobile").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());

        mockMvc.perform(get("/api/v1/learning-task-options/students")
                        .param("sourceType", "FAMILY")
                        .header("Authorization", "Bearer " + secondaryParentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(familyStudentId.toString()))
                .andExpect(jsonPath("$[0].relationshipRole").value("SECONDARY_GUARDIAN"));

        mockMvc.perform(get("/api/v1/learning-task-options/organizations")
                        .param("sourceType", "ORGANIZATION")
                        .param("organizationType", "CLASS")
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(classOrganization.id().toString()))
                .andExpect(jsonPath("$[0].name").value("任务候选学校·测试年级·任务候选一班"));

        mockMvc.perform(get("/api/v1/learning-task-options/students")
                        .param("sourceType", "ORGANIZATION")
                        .param("organizationId", classOrganization.id().toString())
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(organizationStudentId.toString()))
                .andExpect(jsonPath("$[0].currentClassId").value(classOrganization.id().toString()));

        mockMvc.perform(get("/api/v1/learning-task-options/students")
                        .param("sourceType", "TEACHER")
                        .param("organizationId", classOrganization.id().toString())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(organizationStudentId.toString()));

        mockMvc.perform(get("/api/v1/learning-task-options/teachers")
                        .param("classId", classOrganization.id().toString())
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(teacher.id().toString()))
                .andExpect(jsonPath("$[0].displayName").value("任务候选教师"))
                .andExpect(jsonPath("$[0].classOrganizationIds[0]").value(classOrganization.id().toString()))
                .andExpect(jsonPath("$[0].mobile").doesNotExist());

        mockMvc.perform(get("/api/v1/learning-task-options/students")
                        .param("sourceType", "ORGANIZATION")
                        .header("Authorization", "Bearer " + parentToken))
                // V118 后任务来源与角色强绑定：家长无 ORG_ADMIN 身份，请求机构学生选项直接 403
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        jdbcTemplate.update(
                "update sys_organization set effective_status = 'DISABLED' where id in (?, ?, ?)",
                school.id(), schoolGrade.id(), classOrganization.id());
        sqlSession.clearCache();

        mockMvc.perform(get("/api/v1/learning-task-options/organizations")
                        .param("sourceType", "ORGANIZATION")
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/learning-task-options/students")
                        .param("sourceType", "ORGANIZATION")
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/learning-task-options/students")
                        .param("sourceType", "TEACHER")
                        .header("Authorization", "Bearer " + teacherToken))
                // V135 后教师任务来源身份要求带班组织运营：机构停用后教师选项直接 403，不再返回空列表
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/learning-task-options/teachers")
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private Long createStudent(String accessToken, String name, Long organizationId) throws Exception {
        String organizationField = organizationId == null
                ? ""
                : ",\"organizationId\":\"%s\"".formatted(organizationId);
        MvcResult result = mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentName\":\"%s\"%s}".formatted(name, organizationField)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
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
        // bf34c6a 管理员设密后要求首登改密，业务测试直接清除首登改密标记
        jdbcTemplate.update("UPDATE auth_initial_password_state SET change_required=false WHERE user_id=?", targetUser.id());
    }

    /** V135 后教师职业身份独立持久化：机构账号类型 + edu_teacher_profile 启用记录。 */
    private void makeTeacher(String username) {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
        jdbcTemplate.update("UPDATE sys_user SET user_type='ORGANIZATION' WHERE id=?", id);
        jdbcTemplate.update("INSERT INTO edu_teacher_profile(user_id,status) SELECT ?,'ENABLED' "
                + "WHERE NOT EXISTS(SELECT 1 FROM edu_teacher_profile WHERE user_id=?)", id, id);
        sqlSession.clearCache();
    }

    /** V118 后家长账号需为 FAMILY 身份且持有家长档案；WEB 会话仅保留学生档案等家庭基础能力。 */
    private void makeFamilyParent(String username) {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
        jdbcTemplate.update("UPDATE sys_user SET user_type='FAMILY' WHERE id=?", id);
        jdbcTemplate.update("INSERT INTO auth_parent_profile(id,user_id,onboarding_status,first_login_at,onboarding_completed_at) "
                + "VALUES(?,?,'COMPLETED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) "
                + "ON DUPLICATE KEY UPDATE onboarding_status = VALUES(onboarding_status)", id, id);
        jdbcTemplate.update("INSERT INTO auth_user_agreement_acceptance(id,user_id,agreement_type,agreement_version,client_type,accepted_at) "
                + "SELECT ?,?,'PARENT_USER_AGREEMENT',config_value,'WEB',CURRENT_TIMESTAMP FROM sys_config "
                + "WHERE config_key='auth.parent-agreement.current-version' AND status='ENABLED' "
                + "ON DUPLICATE KEY UPDATE agreement_version = VALUES(agreement_version)", id, id);
        sqlSession.clearCache();
    }

    private String loginAccessToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password123","deviceId":"%s-device","deviceName":"任务候选测试浏览器"}
                                """.formatted(username, username)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("accessToken").asText();
    }
}
