package com.lingdong.learning.responsibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.user.application.*;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in full HTTP/security/service/MySQL chain. No mocked application beans or external delivery. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DomainHttpIntegrationTest {
    @DynamicPropertySource
    static void isolatedDatasource(DynamicPropertyRegistry properties) {
        String host = required("LINGDONG_TEST_DB_HOST"), port = required("LINGDONG_TEST_DB_PORT");
        String schema = required("LINGDONG_TEST_DB_NAME"), user = required("LINGDONG_TEST_DB_USER");
        String password = required("LINGDONG_TEST_DB_PASSWORD");
        if (!(host.equals("localhost") || host.equals("127.0.0.1")) || !port.equals("33306")
                || !schema.equals("ld_codex_domains_20261027")) {
            throw new IllegalStateException("Requires isolated localhost:33306/ld_codex_domains_20261027");
        }
        properties.add("spring.datasource.url", () -> "jdbc:mysql://" + host + ":" + port + "/" + schema
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        properties.add("spring.datasource.username", () -> user);
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.flyway.clean-disabled", () -> "true");
        properties.add("spring.flyway.target", () -> "152");
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Explicit environment required: " + key);
        return value;
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdGenerator ids;
    @Autowired UserAccessApplicationService users;
    @Autowired RoleMapper roles;
    @Autowired AuthenticationApplicationService authentication;
    @Autowired org.apache.ibatis.session.SqlSession sqlSession;

    @Test
    void realLoginScopedRoleCanDiscoverAndRegisterButOtherRoleAndInstitutionAreDenied() throws Exception {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("ld_codex_domains_20261027");
        long school = organization(null, "SCHOOL", "中文培训学校", null);
        long course = organization(school, "CLASS", "中文培训项目", "/" + school + "/");
        long otherSchool = organization(null, "SCHOOL", "另一中文学校", null);
        User bootstrap = user("中文密码管理员");
        users.assignRole(new AssignRoleToUserCommand(bootstrap.id(), roles.findByCode("SYS_ADMIN").id(), null));
        User manager = user("中文培训登记员"), denied = user("中文无权限用户");
        users.associateWithOrganization(new AssociateUserWithOrganizationCommand(manager.id(), school));
        users.associateWithOrganization(new AssociateUserWithOrganizationCommand(denied.id(), school));
        long roleId = ids.nextId();
        roles.insert(Role.custom(roleId, "DOMAIN_HTTP_" + roleId, "中文培训自定义角色", "真实HTTP权限链", RoleDataScope.SCHOOL));
        assertThat(jdbc.update("INSERT INTO sys_role_permission(id,role_id,permission_id,effect) "
                + "SELECT ?,?,id,'ALLOW' FROM sys_permission WHERE permission_code='STUDENT_TRAINING_PROGRAM_MANAGE'", ids.nextId(), roleId)).isEqualTo(1);
        users.assignRole(new AssignRoleToUserCommand(manager.id(), roleId, school));
        // These writes are confined to the whitelisted schema and rolled back with this test.
        jdbc.update("UPDATE sys_feature_toggle SET status='ENABLED' WHERE feature_code='STUDENT_ORGANIZATION_RELATIONSHIP' AND scope_key='GLOBAL'");
        jdbc.update("INSERT INTO sys_feature_toggle(id,feature_code,feature_name,scope_type,organization_id,scope_key,status,built_in) "
                + "VALUES(?,'STUDENT_ORGANIZATION_RELATIONSHIP','中文机构关系测试开关','ORGANIZATION',?,?,'ENABLED',false)",
                ids.nextId(), school, "ORG:" + school);
        String token = login(bootstrap, manager), deniedToken = login(bootstrap, denied);
        String base = "/api/v1/institution-enrollment";
        mvc.perform(get(base + "/training-program-candidates").param("institutionOrganizationId", Long.toString(school))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].programOrganizationId").value(Long.toString(course)));
        String body = "{\"institutionOrganizationId\":" + school + ",\"programType\":\"OFFICIAL_COURSE\",\"enrollable\":\"ENABLED\"}";
        mvc.perform(put(base + "/training-programs/{id}", course).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.programType").value("OFFICIAL_COURSE"));
        assertThat(jdbc.queryForObject("SELECT updated_by FROM org_training_program_registration WHERE program_organization_id=?", Long.class, course)).isEqualTo(manager.id());
        assertThat(jdbc.queryForObject("SELECT institution_organization_id FROM org_training_program_registration WHERE program_organization_id=?", Long.class, course)).isEqualTo(school);
        mvc.perform(put(base + "/training-programs/{id}", course).header("Authorization", "Bearer " + deniedToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(get(base + "/training-program-candidates").param("institutionOrganizationId", Long.toString(otherSchool))
                        .header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"WEB", "MINIAPP"})
    void familyApplicationApprovalAndResponsibilityTransferUseRealPermissions(String familyClient) throws Exception {
        trainingAndResponsibilityFlow(familyClient, false);
    }

    @Test
    void actualOrganizationMiniappOperatorUsesScopedBusinessChainAndExplicitDeny() throws Exception {
        trainingAndResponsibilityFlow("MINIAPP", true);
    }

    private void trainingAndResponsibilityFlow(String familyClient, boolean institutionMobile) throws Exception {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("ld_codex_domains_20261027");
        long school = organization(null, "SCHOOL", "中文业务链学校", null);
        long course = organization(school, "CLASS", "中文业务链课程", "/" + school + "/");
        long family = organization(null, "FAMILY", "中文业务链家庭", null);
        User bootstrap = user("中文业务链密码管理员");
        users.assignRole(new AssignRoleToUserCommand(bootstrap.id(), roles.findByCode("SYS_ADMIN").id(), null));
        User manager = institutionMobile ? organizationUser("中文移动机构管理员") : user("中文业务链机构管理员");
        User successor = institutionMobile ? organizationUser("中文移动接任管理员") : user("中文业务链接任管理员");
        long schoolRole = customRole("中文学校业务角色", RoleDataScope.SCHOOL,
                "STUDENT_TRAINING_PROGRAM_MANAGE", "STUDENT_TRAINING_REQUEST_REVIEW",
                "STUDENT_RESPONSIBILITY_TRANSFER", "STUDENT_RESPONSIBILITY_READ");
        for (User administrator : java.util.List.of(manager, successor)) {
            users.associateWithOrganization(new AssociateUserWithOrganizationCommand(administrator.id(), school));
            users.assignRole(new AssignRoleToUserCommand(administrator.id(), schoolRole, school));
            jdbc.update("INSERT INTO sys_organization_admin(id,organization_id,user_id) VALUES(?,?,?)", ids.nextId(), school, administrator.id());
            if (institutionMobile) {
                // Real mobile operator identity. This case does not claim the custom role is the sole grant source.
                users.assignSchoolIdentityRole(new AssignRoleToUserCommand(administrator.id(), roles.findByCode("ORG_ADMIN").id(), school));
            }
        }
        User parent = users.createUser(new CreateUserCommand("domain_parent_" + ids.nextId(), "中文业务链家长", null, UserType.FAMILY));
        User child = users.createUser(new CreateUserCommand("domain_child_" + ids.nextId(), "中文业务链孩子账号", null, UserType.STUDENT));
        users.associateWithOrganization(new AssociateUserWithOrganizationCommand(parent.id(), family));
        users.associateWithOrganization(new AssociateUserWithOrganizationCommand(child.id(), family));
        long parentRole = customRole("中文家庭培训申请角色", RoleDataScope.FAMILY,
                "STUDENT_TRAINING_REQUEST_CREATE", "PARENT_RELATIONSHIP_READ");
        users.assignRole(new AssignRoleToUserCommand(parent.id(), parentRole, family));
        jdbc.update("INSERT INTO auth_parent_profile(id,user_id,onboarding_status,first_login_at,onboarding_completed_at) VALUES(?,?,'COMPLETED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", ids.nextId(), parent.id());
        // Acceptance is unique per user/type/version, independent of the client that recorded it.
        String agreementVersion = jdbc.queryForObject("SELECT config_value FROM sys_config WHERE config_key='auth.parent-agreement.current-version' AND status='ENABLED'", String.class);
        assertThat(agreementVersion).isNotBlank();
        Integer accepted = jdbc.queryForObject("SELECT COUNT(*) FROM auth_user_agreement_acceptance WHERE user_id=? AND agreement_type='PARENT_USER_AGREEMENT' AND agreement_version=?", Integer.class, parent.id(), agreementVersion);
        if (accepted == 0) {
            assertThat(jdbc.update("INSERT INTO auth_user_agreement_acceptance(id,user_id,agreement_type,agreement_version,client_type,accepted_at) VALUES(?,?,'PARENT_USER_AGREEMENT',?,?,CURRENT_TIMESTAMP)",
                    ids.nextId(), parent.id(), agreementVersion, familyClient)).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_user_agreement_acceptance WHERE user_id=? AND agreement_type='PARENT_USER_AGREEMENT' AND agreement_version=? AND accepted_at IS NOT NULL", Integer.class, parent.id(), agreementVersion)).isEqualTo(1);
        long student = ids.nextId();
        jdbc.update("INSERT INTO edu_student(id,student_name,student_user_id) VALUES(?,'中文业务链学员',?)", student, child.id());
        jdbc.update("INSERT INTO edu_parent_student(id,parent_user_id,student_id,relation_role,primary_scope_key) VALUES(?,?,?,'FATHER','PRIMARY')", ids.nextId(), parent.id(), student);
        jdbc.update("INSERT INTO edu_student_organization(id,student_id,organization_id,relation_type,effective_from) VALUES(?,?,?,'ENROLLMENT',DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 DAY))", ids.nextId(), student, school);
        jdbc.update("INSERT INTO edu_student_organization(id,student_id,organization_id,relation_type,effective_from) VALUES(?,?,?,'CLASS',DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 DAY))", ids.nextId(), student, course);
        assertThat(jdbc.update("UPDATE sys_feature_toggle SET status='ENABLED' WHERE feature_code IN ('STUDENT_ORGANIZATION_RELATIONSHIP','PARENT_RELATIONSHIP_MANAGEMENT') AND scope_key='GLOBAL'")).isEqualTo(2);
        jdbc.update("INSERT INTO sys_feature_toggle(id,feature_code,feature_name,scope_type,organization_id,scope_key,status,built_in) VALUES(?,'STUDENT_ORGANIZATION_RELATIONSHIP','中文业务链开关','ORGANIZATION',?,?,'ENABLED',false)", ids.nextId(), school, "ORG:" + school);
        if (institutionMobile) {
            assertThat(jdbc.update("UPDATE sys_feature_toggle SET status='ENABLED' WHERE feature_code='ORGANIZATION_MINIAPP_AUTH' AND scope_key='GLOBAL'")).isEqualTo(1);
        }
        String schoolToken = login(bootstrap, manager), parentToken = login(bootstrap, parent, familyClient);
        if (institutionMobile) schoolToken = organizationMobileSession(manager.username(), "Password123");
        String base = "/api/v1/institution-enrollment";
        if (institutionMobile) {
            mvc.perform(get(base + "/training-program-candidates").header("Authorization", "Bearer " + schoolToken).param("institutionOrganizationId", Long.toString(school)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
            long otherSchool = organization(null, "SCHOOL", "中文移动跨校拒绝", null);
            mvc.perform(get(base + "/training-program-candidates").header("Authorization", "Bearer " + schoolToken).param("institutionOrganizationId", Long.toString(otherSchool)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(put(base + "/training-programs/{id}", course).header("Authorization", "Bearer " + schoolToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("institutionOrganizationId", school, "programType", "OFFICIAL_COURSE", "enrollable", "ENABLED"))))
                .andExpect(status().isOk());
        mvc.perform(get(base + "/training-institutions").header("Authorization", "Bearer " + parentToken)
                        .param("studentId", Long.toString(student)).param("familyOrganizationId", Long.toString(family)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(Long.toString(school)));
        var application = mvc.perform(post(base + "/training-requests").header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("studentId", student, "familyOrganizationId", family,
                                "institutionOrganizationId", school, "programOrganizationId", course, "requestType", "OFFICIAL_COURSE", "reason", "中文家庭申请培训"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING")).andReturn();
        long request = json.readTree(application.getResponse().getContentAsString()).path("id").asLong();
        mvc.perform(post(base + "/training-requests/{id}/approve", request).header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(base + "/training-requests/{id}/approve", request).header("Authorization", "Bearer " + schoolToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reviewReason\":\"中文审核通过\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(jdbc.queryForObject("SELECT status FROM edu_training_participation WHERE training_request_id=?", String.class, request)).isEqualTo("ACTIVE");
        String responsibility = "/api/v1/students/" + student;
        mvc.perform(get(responsibility + "/institution-responsibility").header("Authorization", "Bearer " + schoolToken).param("institutionOrganizationId", Long.toString(school)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versionNo").value(0));
        mvc.perform(get(responsibility + "/institution-responsibility-transfer-options").header("Authorization", "Bearer " + schoolToken).param("institutionOrganizationId", Long.toString(school)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        String transfer = json.writeValueAsString(java.util.Map.of("institutionOrganizationId", school, "targetUserId", successor.id(), "reasonType", "OTHER", "reasonText", "中文职责交接", "expectedVersion", 0));
        mvc.perform(post(responsibility + "/institution-responsibility-transfer").header("Authorization", "Bearer " + schoolToken).contentType(MediaType.APPLICATION_JSON).content(transfer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versionNo").value(1)).andExpect(jsonPath("$.responsibleUser.id").value(successor.id().toString()));
        mvc.perform(post(responsibility + "/institution-responsibility-transfer").header("Authorization", "Bearer " + schoolToken).contentType(MediaType.APPLICATION_JSON).content(transfer))
                .andExpect(status().isConflict());
        mvc.perform(get(responsibility + "/institution-responsibility-transfers").header("Authorization", "Bearer " + schoolToken).param("institutionOrganizationId", Long.toString(school)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        assertThat(jdbc.queryForObject("SELECT responsible_user_id FROM edu_student_institution_responsibility WHERE student_id=? AND institution_organization_id=?", Long.class, student, school)).isEqualTo(successor.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM edu_student_responsibility_transfer WHERE student_id=? AND institution_organization_id=? AND before_version_no=0 AND after_version_no=1 AND reason_text='中文职责交接'", Integer.class, student, school)).isEqualTo(1);
        if (institutionMobile) {
            assertThat(jdbc.update("INSERT INTO sys_user_permission(id,user_id,permission_id,effect) SELECT ?,?,id,'DENY' FROM sys_permission WHERE permission_code='STUDENT_TRAINING_PROGRAM_MANAGE'", ids.nextId(), manager.id())).isEqualTo(1);
            // JDBC fixture mutation bypasses MyBatis's transaction-local cache invalidation.
            sqlSession.clearCache();
            mvc.perform(get(base + "/training-program-candidates").header("Authorization", "Bearer " + schoolToken).param("institutionOrganizationId", Long.toString(school)))
                    .andExpect(status().isForbidden());
            mvc.perform(put(base + "/training-programs/{id}", course).header("Authorization", "Bearer " + schoolToken)
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("institutionOrganizationId", school, "programType", "OFFICIAL_COURSE", "enrollable", "DISABLED"))))
                    .andExpect(status().isForbidden());
            assertThat(jdbc.queryForObject("SELECT enrollable FROM org_training_program_registration WHERE program_organization_id=?", String.class, course)).isEqualTo("ENABLED");
        }
    }

    private User organizationUser(String name) {
        return users.createUser(new CreateUserCommand("domain_org_" + ids.nextId(), name, null, UserType.ORGANIZATION));
    }

    private String organizationMobileSession(String username, String password) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of("username", username, "password", password,
                "deviceId", username + "-institution-miniapp", "deviceName", "中文机构移动权限链"));
        var response = mvc.perform(post("/api/v1/auth/organization-sessions/password").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        String token = json.readTree(response.getResponse().getContentAsString()).path("accessToken").asText();
        assertThat(token).isNotBlank();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clientType").value("MINIAPP"));
        return token;
    }

    private long customRole(String name, RoleDataScope scope, String... permissions) {
        long id = ids.nextId();
        roles.insert(Role.custom(id, "DOMAIN_HTTP_" + id, name, "真实业务权限链", scope));
        for (String permission : permissions) {
            assertThat(jdbc.update("INSERT INTO sys_role_permission(id,role_id,permission_id,effect) SELECT ?,?,id,'ALLOW' FROM sys_permission WHERE permission_code=?", ids.nextId(), id, permission)).isEqualTo(1);
        }
        return id;
    }

    private User user(String name) {
        return users.createUser(new CreateUserCommand("domain_http_" + ids.nextId(), name, null, UserType.PLATFORM));
    }

    private long organization(Long parent, String type, String name, String parentPath) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO sys_organization(id,parent_id,organization_code,organization_name,organization_type,organization_path) VALUES(?,?,?,?,?,?)",
                id, parent, "HTTP_" + id, name, type, (parentPath == null ? "/" : parentPath) + id + "/");
        return id;
    }

    private String login(User bootstrap, User target) throws Exception {
        return login(bootstrap, target, "WEB");
    }

    private String login(User bootstrap, User target, String clientType) throws Exception {
        authentication.setPlatformUserPassword(new SetPlatformUserPasswordCommand(bootstrap.id(), target.id(), "Bootstrap123"));
        String initial = session(target.username(), "Bootstrap123", clientType);
        mvc.perform(post("/api/v1/auth/password").header("Authorization", "Bearer " + initial)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"oldPassword\":\"Bootstrap123\",\"newPassword\":\"Password123\"}"))
                .andExpect(status().isNoContent());
        return session(target.username(), "Password123", clientType);
    }

    private String session(String username, String password, String clientType) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of("username", username, "password", password,
                "deviceId", username + "-" + clientType, "deviceName", "中文权限链测试浏览器", "clientType", clientType));
        // The legacy password endpoint always issues WEB. The generic account endpoint accepts clientType.
        boolean mobile = "MINIAPP".equals(clientType);
        var response = mvc.perform(post(mobile ? "/api/v1/auth/registration-sessions/password" : "/api/v1/auth/sessions/password").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        var payload = json.readTree(response.getResponse().getContentAsString());
        String token = (mobile ? payload.path("session") : payload).path("accessToken").asText();
        assertThat(token).isNotBlank();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clientType").value(clientType));
        return token;
    }
}
