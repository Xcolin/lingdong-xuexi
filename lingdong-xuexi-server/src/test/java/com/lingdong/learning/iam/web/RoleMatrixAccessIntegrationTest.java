package com.lingdong.learning.iam.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.apache.ibatis.session.ExecutorType;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 角色矩阵四项动态验证（任务 3.2）：账号停用、角色变更、组织范围收敛均即时生效；
 * 无机构学生独立使用家庭功能且机构入口拒绝；功能停用既隐藏能力位又拒绝直达 API。
 * 全链路决策（会话、角色、权限、数据范围、功能开关）每次请求实时查库，本类使用
 * 事务回滚隔离，不依赖任何缓存或重启行为。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoleMatrixAccessIntegrationTest {
    private static final long SCHOOL_A_ID = 1900000000000215001L;
    private static final long SCHOOL_B_ID = 1900000000000215002L;
    private static final long PARENT_ID = 1900000000000215101L;
    private static final long FAMILY_STUDENT_ID = 1900000000000215102L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationService;
    @Autowired private UserAccessApplicationService userService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SqlSessionTemplate sqlSessionTemplate;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void userStatusChangeTakesEffectImmediately() throws Exception {
        Fixture f = fixture();
        String orgToken = f.orgLogin.token();
        mockMvc.perform(get("/api/v1/students").header("Authorization", bearer(orgToken)))
                .andExpect(status().isOk());

        // 停用账号后旧访问凭证立即失效，会话被服务端撤销。
        mockMvc.perform(patch("/api/v1/users/{id}/status", f.orgAdmin.id())
                        .header("Authorization", bearer(f.adminLogin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/students").header("Authorization", bearer(orgToken)))
                .andExpect(status().isUnauthorized());

        // 重新启用后旧会话不可复活，必须重新登录。
        mockMvc.perform(patch("/api/v1/users/{id}/status", f.orgAdmin.id())
                        .header("Authorization", bearer(f.adminLogin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENABLED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/students").header("Authorization", bearer(orgToken)))
                .andExpect(status().isUnauthorized());
        assertThat(login(f.orgAdmin.username()).token()).isNotBlank();
    }

    @Test
    void roleRemovalTakesEffectImmediately() throws Exception {
        Fixture f = fixture();
        String orgToken = f.orgLogin.token();
        mockMvc.perform(get("/api/v1/students").header("Authorization", bearer(orgToken)))
                .andExpect(status().isOk());

        // 撤销角色后同一会话立即失去操作权限（动态 RBAC 实时决策）。
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id=?", f.orgAdmin.id());
        // 直连 SQL 变更不经过 MyBatis，需手动失效一级缓存以保证后续请求读到新数据。
        sqlSessionTemplate.clearCache();
        mockMvc.perform(get("/api/v1/students").header("Authorization", bearer(orgToken)))
                .andExpect(status().isForbidden());

        // 角色恢复后权限立即恢复，无需重新登录。
        Role orgAdminRole = roleMapper.findByCode("ORG_ADMIN");
        userService.assignRole(new AssignRoleToUserCommand(f.orgAdmin.id(), orgAdminRole.id(), SCHOOL_A_ID));
        mockMvc.perform(get("/api/v1/students").header("Authorization", bearer(orgToken)))
                .andExpect(status().isOk());
    }

    @Test
    void organizationScopeChangeRestrictsAccessImmediately() throws Exception {
        Fixture f = fixture();
        MvcResult created = mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", bearer(f.orgLogin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentName\":\"范围收敛学生\",\"organizationId\":\"%s\"}"
                                .formatted(SCHOOL_B_ID)))
                .andExpect(status().isCreated())
                .andReturn();
        long studentId = body(created).path("id").asLong();
        mockMvc.perform(get("/api/v1/students/{id}", studentId)
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isOk());

        // 移除对学校 B 的管理员授权后，范围立即收敛：B 学生不可达，A 范围不变。
        jdbcTemplate.update("DELETE FROM sys_organization_admin WHERE user_id=? AND organization_id=?",
                f.orgAdmin.id(), SCHOOL_B_ID);
        jdbcTemplate.update("DELETE FROM sys_user_organization WHERE user_id=? AND organization_id=?",
                f.orgAdmin.id(), SCHOOL_B_ID);
        // 直连 SQL 变更不经过 MyBatis，需手动失效一级缓存以保证后续请求读到新数据。
        sqlSessionTemplate.clearCache();
        mockMvc.perform(get("/api/v1/students/{id}", studentId)
                        .header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/students").header("Authorization", bearer(f.orgLogin.token())))
                .andExpect(status().isOk());
    }

    @Test
    void studentWithoutOrganizationUsesFamilyFeaturesOnly() throws Exception {
        Fixture f = fixture();
        insertFamilyStudentFixture();
        String parentToken = login("parent_matrix").token();

        // 家长独立创建家庭学生：不传 organizationId，学生无任何机构关联。
        MvcResult created = mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", bearer(parentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentName\":\"家庭学生\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long studentId = body(created).path("id").asLong();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edu_student_organization WHERE student_id=?", Integer.class, studentId))
                .isZero();

        // 家庭能力可用：亲子关系列表包含该学生。
        mockMvc.perform(get("/api/v1/parent-relationships/students")
                        .header("Authorization", bearer(parentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentName").value("家庭学生"));

        // 机构入口拒绝：家长无学员组织管理权限，直达 API 被拒绝。
        mockMvc.perform(get("/api/v1/students/{id}/organization-relationships", studentId)
                        .header("Authorization", bearer(parentToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void featureDisabledHidesEntryAndRejectsApi() throws Exception {
        Fixture f = fixture();
        insertFamilyStudentFixture();
        jdbcTemplate.update("""
                INSERT INTO edu_parent_student(id,parent_user_id,student_id,relation_role,status,
                    primary_scope_key,bound_at) VALUES (?,?,?,'PRIMARY','ACTIVE','GLOBAL',CURRENT_TIMESTAMP)
                """, FAMILY_STUDENT_ID + 10, PARENT_ID, FAMILY_STUDENT_ID);
        String parentToken = login("parent_matrix").token();

        String studentPath = "/api/v1/reward-exchanges/students/" + FAMILY_STUDENT_ID;
        mockMvc.perform(get(studentPath).header("Authorization", bearer(parentToken)))
                .andExpect(status().isOk());

        // 功能停用：能力位隐藏（前端入口判定依据）+ 直达 API 拒绝。
        jdbcTemplate.update(
                "UPDATE sys_feature_toggle SET status='DISABLED' WHERE feature_code='REWARD_EXCHANGE'");
        // 直连 SQL 变更不经过 MyBatis，需手动失效一级缓存以保证后续请求读到新数据。
        sqlSessionTemplate.clearCache();
        mockMvc.perform(get("/api/v1/public/capabilities").param("client", "WEB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rewardExchangeEnabled").value(false));
        mockMvc.perform(get(studentPath).header("Authorization", bearer(parentToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));

        // 功能恢复后入口与 API 同步恢复。
        jdbcTemplate.update(
                "UPDATE sys_feature_toggle SET status='ENABLED' WHERE feature_code='REWARD_EXCHANGE'");
        sqlSessionTemplate.clearCache();
        mockMvc.perform(get(studentPath).header("Authorization", bearer(parentToken)))
                .andExpect(status().isOk());
    }

    // ---------- fixture ----------

    private Fixture fixture() {
        jdbcTemplate.update("""
                INSERT INTO sys_organization(id,organization_code,organization_name,organization_type,
                    organization_path,sort_order,status) VALUES (?,?,?,'SCHOOL',?,1,'ENABLED')
                """, SCHOOL_A_ID, "MATRIX_SCHOOL_A", "矩阵学校甲", "/MATRIX_SCHOOL_A/");
        jdbcTemplate.update("""
                INSERT INTO sys_organization(id,organization_code,organization_name,organization_type,
                    organization_path,sort_order,status) VALUES (?,?,?,'SCHOOL',?,1,'ENABLED')
                """, SCHOOL_B_ID, "MATRIX_SCHOOL_B", "矩阵学校乙", "/MATRIX_SCHOOL_B/");
        User admin = createUser("matrix_sys_admin");
        assignRole(admin.id(), "SYS_ADMIN", null);
        User orgAdmin = createUser("org_admin_matrix");
        jdbcTemplate.update("INSERT INTO sys_user_organization(id,user_id,organization_id) VALUES (?,?,?)",
                orgAdmin.id() + 10, orgAdmin.id(), SCHOOL_A_ID);
        jdbcTemplate.update("INSERT INTO sys_user_organization(id,user_id,organization_id) VALUES (?,?,?)",
                orgAdmin.id() + 11, orgAdmin.id(), SCHOOL_B_ID);
        jdbcTemplate.update("INSERT INTO sys_organization_admin(id,organization_id,user_id) VALUES (?,?,?)",
                orgAdmin.id() + 20, SCHOOL_A_ID, orgAdmin.id());
        jdbcTemplate.update("INSERT INTO sys_organization_admin(id,organization_id,user_id) VALUES (?,?,?)",
                orgAdmin.id() + 21, SCHOOL_B_ID, orgAdmin.id());
        assignRole(orgAdmin.id(), "ORG_ADMIN", SCHOOL_A_ID);
        Login adminLogin = setPasswordAndLogin(admin, admin, "matrix-admin-device");
        Login orgLogin = setPasswordAndLogin(admin, orgAdmin, "matrix-org-device");
        return new Fixture(admin, orgAdmin, adminLogin, orgLogin);
    }

    /** 家长 WEB 会话 fixture：FAMILY 账号 + PARENT 角色 + 已完成引导 + 已接受当前协议。 */
    private void insertFamilyStudentFixture() {
        // 家长关系管理开关默认停用（V38），家庭关系验证需开启；变更后失效 MyBatis 一级缓存。
        jdbcTemplate.update(
                "UPDATE sys_feature_toggle SET status='ENABLED' WHERE feature_code='PARENT_RELATIONSHIP_MANAGEMENT'");
        sqlSessionTemplate.clearCache();
        jdbcTemplate.update("""
                INSERT INTO sys_user(id,username,display_name,user_type,status,password_hash)
                VALUES (?,?,?,?, 'ENABLED', ?)
                """, PARENT_ID, "parent_matrix", "矩阵家长", "FAMILY",
                passwordEncoder.encode("Password123"));
        jdbcTemplate.update("""
                INSERT INTO sys_user_role(id,user_id,role_id,organization_scope_key)
                SELECT ?, ?, id, 'GLOBAL' FROM sys_role WHERE role_code='PARENT'
                """, PARENT_ID + 10, PARENT_ID);
        jdbcTemplate.update("""
                INSERT INTO auth_parent_profile(id,user_id,onboarding_status,first_login_at,onboarding_completed_at)
                VALUES (?, ?, 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, PARENT_ID + 20, PARENT_ID);
        String agreementVersion = jdbcTemplate.queryForObject(
                "SELECT config_value FROM sys_config WHERE config_key='auth.parent-agreement.current-version' "
                        + "AND status='ENABLED'", String.class);
        jdbcTemplate.update("""
                INSERT INTO auth_user_agreement_acceptance(id,user_id,agreement_type,agreement_version,client_type)
                VALUES (?, ?, 'PARENT_USER_AGREEMENT', ?, 'WEB')
                """, PARENT_ID + 30, PARENT_ID, agreementVersion);
        jdbcTemplate.update(
                "INSERT INTO edu_student(id,student_name,status) VALUES (?, '家庭学生', 'ENABLED')",
                FAMILY_STUDENT_ID);
    }

    private User createUser(String username) {
        return userService.createUser(new CreateUserCommand(
                username + "-" + System.nanoTime(), username, null, UserType.PLATFORM));
    }

    private void assignRole(long userId, String roleCode, Long organizationId) {
        Role role = roleMapper.findByCode(roleCode);
        userService.assignRole(new AssignRoleToUserCommand(userId, role.id(), organizationId));
    }

    private Login setPasswordAndLogin(User administrator, User user, String deviceId) {
        authenticationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                administrator.id(), user.id(), "Password123"));
        return login(user.username());
    }

    private Login login(String username) {
        try {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"%s","password":"Password123",
                                     "deviceId":"%s-device","deviceName":"角色矩阵测试浏览器"}
                                    """.formatted(username, username)))
                    .andExpect(status().isOk())
                    .andReturn();
            return new Login(body(result).path("accessToken").asText());
        } catch (Exception exception) {
            throw new IllegalStateException("登录失败：" + username, exception);
        }
    }

    private com.fasterxml.jackson.databind.JsonNode body(MvcResult result) {
        try {
            return objectMapper.readTree(result.getResponse().getContentAsString());
        } catch (Exception exception) {
            throw new IllegalStateException("响应解析失败", exception);
        }
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Fixture(User admin, User orgAdmin, Login adminLogin, Login orgLogin) { }

    private record Login(String token) { }
}
