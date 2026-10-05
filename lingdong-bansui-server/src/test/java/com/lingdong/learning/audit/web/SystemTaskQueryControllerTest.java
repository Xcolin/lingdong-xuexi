package com.lingdong.learning.audit.web;

import com.lingdong.learning.audit.application.*;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.user.application.*;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 合成 Web 会话经过真实认证与动态权限链；不执行任何远程操作。 */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class SystemTaskQueryControllerTest {
    private static final String DB="system_task_query_"+UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->"jdbc:h2:mem:"+DB+";MODE=MySQL;DB_CLOSE_DELAY=0;DATABASE_TO_LOWER=TRUE");
    }
    @Autowired MockMvc mvc;
    @Autowired UserAccessApplicationService users;
    @Autowired UserMapper userMapper;
    @Autowired RoleMapper roles;
    @Autowired PasswordEncoder encoder;
    @Autowired AuthenticationApplicationService auth;
    @Autowired SystemTaskApplicationService tasks;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    @BeforeEach void enableCache(){jdbc.update("UPDATE sys_feature_toggle SET status='ENABLED' WHERE feature_code='CACHE_MANAGEMENT' AND organization_id IS NULL");}

    @Test void administratorSeesOnlyOwnTasksWithPaginationAndStringIds() throws Exception {
        var admin=account("task_query_admin", "SYS_ADMIN");
        var other=account("task_query_other", "SYS_ADMIN");
        var own=task(admin,"本人任务");task(admin,"本人第二项");var hidden=task(other,"他人草稿");
        mvc.perform(get("/api/v1/system-tasks?page=1&pageSize=1").header("Authorization",admin.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").isString());
        mvc.perform(get("/api/v1/system-tasks/{id}",own.id()).header("Authorization",admin.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.submittedBy").value(admin.user().displayName()))
                .andExpect(jsonPath("$.description").value("合成任务说明"));
        mvc.perform(get("/api/v1/system-tasks/{id}",hidden.id()).header("Authorization",admin.token()))
                .andExpect(status().isNotFound());
    }

    @Test void auditorCannotReadDraftAndCanReadSubmittedAndReviewHistory() throws Exception {
        var admin=account("task_query_submitter", "SYS_ADMIN");
        var auditor=account("task_query_auditor", "SYS_AUDITOR");
        var draft=task(admin,"未提交");var submitted=task(admin,"待审核");tasks.submit(submitted.id(),admin.user().id());
        mvc.perform(get("/api/v1/system-tasks?status=PENDING_REVIEW").header("Authorization",auditor.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v1/system-tasks/{id}",draft.id()).header("Authorization",auditor.token()))
                .andExpect(status().isNotFound());
        tasks.reject(submitted.id(),auditor.user().id(),"请补充影响说明");
        mvc.perform(get("/api/v1/system-tasks/{id}",submitted.id()).header("Authorization",auditor.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewedBy").value(auditor.user().displayName()))
                .andExpect(jsonPath("$.reviewComment").value("请补充影响说明"));
        mvc.perform(get("/api/v1/system-tasks?page=0").header("Authorization",auditor.token())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/system-tasks?status=INVALID").header("Authorization",auditor.token())).andExpect(status().isBadRequest());
    }

    @Test void rejectsOrdinaryRoleRevokedPermissionDisabledAccountAndMiniapp() throws Exception {
        var teacher=account("task_query_teacher", "TEACHER");
        mvc.perform(get("/api/v1/system-tasks").header("Authorization",teacher.token())).andExpect(status().isForbidden());
        var admin=account("task_query_revoke", "SYS_ADMIN");
        mvc.perform(get("/api/v1/system-tasks").header("Authorization",admin.token())).andExpect(status().isOk());
        jdbc.update("UPDATE auth_device_session SET client_type='MINIAPP' WHERE user_id=?",admin.user().id());
        sqlSession.clearCache();
        // 当前平台账号尚不允许小程序会话，真实认证链先拒绝该伪造客户端。
        mvc.perform(get("/api/v1/system-tasks").header("Authorization",admin.token())).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE auth_device_session SET client_type='WEB' WHERE user_id=?",admin.user().id());
        jdbc.update("UPDATE sys_permission SET status='DISABLED' WHERE permission_code='SYSTEM_TASK_READ'");
        sqlSession.clearCache();
        mvc.perform(get("/api/v1/system-tasks").header("Authorization",admin.token())).andExpect(status().isForbidden());
        jdbc.update("UPDATE sys_permission SET status='ENABLED' WHERE permission_code='SYSTEM_TASK_READ'");
        jdbc.update("UPDATE sys_user SET status='DISABLED' WHERE id=?",admin.user().id());
        sqlSession.clearCache();
        mvc.perform(get("/api/v1/system-tasks").header("Authorization",admin.token())).andExpect(status().isUnauthorized());
    }

    @Test void domainFeatureAndPermissionHideBothRowsAndDetails() throws Exception {
        var admin=account("task_query_domain","SYS_ADMIN");
        var task=task(admin,"受缓存领域保护的任务");
        mvc.perform(get("/api/v1/system-tasks").header("Authorization",admin.token())).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        jdbc.update("UPDATE sys_feature_toggle SET status='DISABLED' WHERE feature_code='CACHE_MANAGEMENT'");
        sqlSession.clearCache();
        mvc.perform(get("/api/v1/system-tasks").header("Authorization",admin.token())).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/v1/system-tasks/{id}",task.id()).header("Authorization",admin.token())).andExpect(status().isNotFound());
        enableCache();
        jdbc.update("UPDATE sys_permission SET status='DISABLED' WHERE permission_code='CACHE_READ'");
        sqlSession.clearCache();
        mvc.perform(get("/api/v1/system-tasks").header("Authorization",admin.token())).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/system-tasks/{id}",task.id()).header("Authorization",admin.token())).andExpect(status().isNotFound());
    }

    @Test void detailsIncludePersistedPayloadButListDoesNot() throws Exception {
        var admin=account("task_payload_admin", "SYS_ADMIN");
        var task=task(admin,"会话清除");
        jdbc.update("INSERT INTO sys_cache_operation_log (id,operation_code,task_id,cache_domain,operation_type,status,impact_description,requested_by) VALUES (?,?,?,?,?,?,?,?)",
                1900000000000099001L,"payload-test",task.id(),"USER_SESSION","CLEAR","PENDING","强制退出所有活动会话",admin.user().id());
        mvc.perform(get("/api/v1/system-tasks/{id}",task.id()).header("Authorization",admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.fields[0].label").value("缓存范围"))
                .andExpect(jsonPath("$.payload.fields[0].value").value("USER_SESSION"))
                .andExpect(jsonPath("$.payload.executionStatus").value("PENDING"));
        mvc.perform(get("/api/v1/system-tasks").header("Authorization",admin.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].payload").doesNotExist());
    }

    private SystemTask task(Account account,String title) {
        return tasks.createDraft(new CreateSystemTaskCommand(account.user().id(),SystemTaskType.CACHE_CLEAR,title,"合成任务说明",ImpactScope.GLOBAL));
    }
    private Account account(String username,String role) {
        User user=users.createUser(new CreateUserCommand(username,"测试系统用户",null,UserType.PLATFORM));
        users.assignRole(new AssignRoleToUserCommand(user.id(),roles.findByCode(role).id(),null));
        userMapper.updatePasswordHash(user.id(),encoder.encode("SyntheticTaskPassword1!"));
        var session=auth.loginByPassword(new PasswordLoginCommand(username,"SyntheticTaskPassword1!","synthetic-device","合成设备"));
        return new Account(user,"Bearer "+session.accessToken());
    }
    private record Account(User user,String token) { }
}
