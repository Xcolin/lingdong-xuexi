package com.lingdong.learning.interfaceconfig.application;

import com.lingdong.learning.audit.application.SystemTask;
import com.lingdong.learning.audit.application.SystemTaskStatus;
import com.lingdong.learning.audit.infrastructure.persistence.SystemTaskMapper;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.interfaceconfig.domain.InterfaceAuthorizationScope;
import com.lingdong.learning.interfaceconfig.domain.InterfaceCallResult;
import com.lingdong.learning.interfaceconfig.domain.InterfaceDirection;
import com.lingdong.learning.interfaceconfig.domain.InterfacePurpose;
import com.lingdong.learning.interfaceconfig.domain.InterfaceService;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceCallLog;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceChange;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceStatus;
import com.lingdong.learning.interfaceconfig.infrastructure.persistence.InterfaceServiceCallLogMapper;
import com.lingdong.learning.interfaceconfig.infrastructure.persistence.InterfaceServiceMapper;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class InterfaceServiceApplicationServiceTest {
    @Autowired private InterfaceServiceApplicationService interfaceServiceApplicationService;
    @Autowired private InterfaceServiceMapper interfaceServiceMapper;
    @Autowired private InterfaceServiceCallLogMapper interfaceServiceCallLogMapper;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private SystemTaskMapper systemTaskMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void createsServiceOnlyAfterTheApprovedChangeIsApplied() {
        User administrator = createUserWithRole("interface_create_admin", "接口管理员", "SYS_ADMIN");
        User auditor = createUserWithRole("interface_create_auditor", "接口审核员", "SYS_AUDITOR");
        User owner = createUser("interface_create_owner", "接口责任人");

        InterfaceServiceChange change = interfaceServiceApplicationService.createDraft(
                new CreateInterfaceServiceChangeCommand(
                        administrator.id(), "微信登录", InterfaceDirection.INBOUND, InterfacePurpose.WECHAT,
                        "miniapp", InterfaceAuthorizationScope.GLOBAL, null, owner.id(),
                        "登记微信登录接口", "登记小程序微信登录调用服务"
                )
        );

        assertThat(Long.toString(change.id())).hasSize(19);
        assertThat(Long.toString(change.taskId())).hasSize(19);
        assertThat(countServices("微信登录", "miniapp")).isZero();

        interfaceServiceApplicationService.submit(change.taskId(), administrator.id());
        SystemTask effectiveTask = interfaceServiceApplicationService.approveAndApply(change.taskId(), auditor.id(), "同意登记");

        InterfaceService service = interfaceServiceMapper.findByNameAndCaller("微信登录", "miniapp");
        assertThat(effectiveTask.status()).isEqualTo(SystemTaskStatus.EFFECTIVE);
        assertThat(service).isNotNull();
        assertThat(Long.toString(service.id())).hasSize(19);
        assertThat(service.status()).isEqualTo(InterfaceServiceStatus.ENABLED);
    }

    @Test
    void defersAuthorizationScopeChangeAndDisableUntilApprovalIsApplied() {
        User administrator = createUserWithRole("interface_change_admin", "接口变更管理员", "SYS_ADMIN");
        User auditor = createUserWithRole("interface_change_auditor", "接口变更审核员", "SYS_AUDITOR");
        User owner = createUser("interface_change_owner", "接口变更责任人");
        InterfaceService service = createEnabledService(administrator, auditor, owner, "地图考勤", "attendance-adapter");

        InterfaceServiceChange scopeChange = interfaceServiceApplicationService.createAuthorizationChangeDraft(
                new CreateInterfaceServiceAuthorizationChangeCommand(
                        administrator.id(), service.id(), InterfaceAuthorizationScope.SCHOOL, "school:1001",
                        "调整地图考勤授权范围", "限定到指定学校"
                )
        );

        assertThat(interfaceServiceMapper.findById(service.id()).authorizationScope())
                .isEqualTo(InterfaceAuthorizationScope.GLOBAL);
        interfaceServiceApplicationService.submit(scopeChange.taskId(), administrator.id());
        interfaceServiceApplicationService.approveAndApply(scopeChange.taskId(), auditor.id(), "同意范围调整");
        assertThat(interfaceServiceMapper.findById(service.id()).authorizationScope())
                .isEqualTo(InterfaceAuthorizationScope.SCHOOL);

        InterfaceServiceChange disableChange = interfaceServiceApplicationService.createDisableDraft(
                new CreateInterfaceServiceDisableCommand(
                        administrator.id(), service.id(), "停用地图考勤接口", "停止地图能力调用"
                )
        );

        assertThat(interfaceServiceMapper.findById(service.id()).status()).isEqualTo(InterfaceServiceStatus.ENABLED);
        interfaceServiceApplicationService.submit(disableChange.taskId(), administrator.id());
        interfaceServiceApplicationService.approveAndApply(disableChange.taskId(), auditor.id(), "同意停用");
        assertThat(interfaceServiceMapper.findById(service.id()).status()).isEqualTo(InterfaceServiceStatus.DISABLED);
    }

    @Test
    void onlySystemAdministratorsCanCreateServiceChangeDrafts() {
        User ordinaryUser = createUser("interface_ordinary_user", "普通用户");
        User owner = createUser("interface_permission_owner", "权限接口责任人");

        assertThatThrownBy(() -> interfaceServiceApplicationService.createDraft(
                new CreateInterfaceServiceChangeCommand(
                        ordinaryUser.id(), "短信通知", InterfaceDirection.OUTBOUND, InterfacePurpose.SMS,
                        "notification-adapter", InterfaceAuthorizationScope.GLOBAL, null, owner.id(),
                        "登记短信接口", "登记短信通知调用服务"
                )
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("系统管理员");
    }

    @Test
    void recordsOnlyEnabledServiceCallResultsAndRejectsUnregisteredOrDisabledServices() {
        User administrator = createUserWithRole("interface_log_admin", "接口日志管理员", "SYS_ADMIN");
        User auditor = createUserWithRole("interface_log_auditor", "接口日志审核员", "SYS_AUDITOR");
        User owner = createUser("interface_log_owner", "接口日志责任人");

        assertThatThrownBy(() -> interfaceServiceApplicationService.recordCall(
                new RecordInterfaceServiceCallCommand(1999999999999999999L, "log-adapter", InterfaceCallResult.FAILED,
                        "未登记服务", "trace-unregistered", LocalDateTime.of(2026, 7, 31, 0, 20))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在");

        InterfaceService service = createEnabledService(administrator, auditor, owner, "学校数据同步", "school-sync-adapter");
        InterfaceServiceCallLog recordedLog = interfaceServiceApplicationService.recordCall(
                new RecordInterfaceServiceCallCommand(service.id(), "school-sync-adapter", InterfaceCallResult.FAILED,
                        "上游服务暂时不可用", "trace-school-sync-001", LocalDateTime.of(2026, 7, 31, 0, 20))
        );

        InterfaceServiceCallLog persistedLog = interfaceServiceCallLogMapper.findById(recordedLog.id());
        assertThat(Long.toString(persistedLog.id())).hasSize(19);
        assertThat(persistedLog.result()).isEqualTo(InterfaceCallResult.FAILED);
        assertThat(persistedLog.callerName()).isEqualTo("school-sync-adapter");
        assertThat(persistedLog.errorSummary()).isEqualTo("上游服务暂时不可用");
        assertThat(persistedLog.traceId()).isEqualTo("trace-school-sync-001");
        Integer sensitiveColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_name = 'sys_interface_call_log'
                  and column_name in ('credential', 'access_token', 'request_body', 'response_body', 'location_data')
                """, Integer.class);
        assertThat(sensitiveColumnCount).isZero();

        InterfaceServiceChange disableChange = interfaceServiceApplicationService.createDisableDraft(
                new CreateInterfaceServiceDisableCommand(administrator.id(), service.id(), "停用学校数据同步", "停止同步调用")
        );
        interfaceServiceApplicationService.submit(disableChange.taskId(), administrator.id());
        interfaceServiceApplicationService.approveAndApply(disableChange.taskId(), auditor.id(), "同意停用");

        assertThatThrownBy(() -> interfaceServiceApplicationService.recordCall(
                new RecordInterfaceServiceCallCommand(service.id(), "school-sync-adapter", InterfaceCallResult.SUCCEEDED,
                        null, "trace-disabled", LocalDateTime.of(2026, 7, 31, 0, 21))
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已停用");
    }

    @Test
    void leavesTheTaskApprovedInsteadOfEffectiveWhenApplyingTheChangeFails() {
        User administrator = createUserWithRole("interface_failure_admin", "接口失败管理员", "SYS_ADMIN");
        User auditor = createUserWithRole("interface_failure_auditor", "接口失败审核员", "SYS_AUDITOR");
        User owner = createUser("interface_failure_owner", "接口失败责任人");
        InterfaceService service = createEnabledService(administrator, auditor, owner, "失败回滚验证", "failure-adapter");
        InterfaceServiceChange disableChange = interfaceServiceApplicationService.createDisableDraft(
                new CreateInterfaceServiceDisableCommand(administrator.id(), service.id(), "停用失败验证", "验证审批后的执行失败状态")
        );
        interfaceServiceApplicationService.submit(disableChange.taskId(), administrator.id());

        // Simulates a concurrent stop after the proposal is submitted but before its approved mutation is applied.
        assertThat(interfaceServiceMapper.updateStatus(service.id(), InterfaceServiceStatus.DISABLED)).isEqualTo(1);
        assertThatThrownBy(() -> interfaceServiceApplicationService.approveAndApply(
                disableChange.taskId(), auditor.id(), "同意停用"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("执行失败");

        assertThat(systemTaskMapper.findById(disableChange.taskId()).status()).isEqualTo(SystemTaskStatus.APPROVED);
    }

    private InterfaceService createEnabledService(User administrator, User auditor, User owner, String serviceName, String callerName) {
        InterfaceServiceChange change = interfaceServiceApplicationService.createDraft(
                new CreateInterfaceServiceChangeCommand(
                        administrator.id(), serviceName, InterfaceDirection.OUTBOUND, InterfacePurpose.MAP,
                        callerName, InterfaceAuthorizationScope.GLOBAL, null, owner.id(),
                        "登记" + serviceName, "登记测试调用服务"
                )
        );
        interfaceServiceApplicationService.submit(change.taskId(), administrator.id());
        interfaceServiceApplicationService.approveAndApply(change.taskId(), auditor.id(), "同意登记");
        return interfaceServiceMapper.findByNameAndCaller(serviceName, callerName);
    }

    private int countServices(String serviceName, String callerName) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from sys_interface_service where service_name = ? and caller_name = ?",
                Integer.class, serviceName, callerName);
        return count == null ? 0 : count;
    }

    private User createUserWithRole(String username, String displayName, String roleCode) {
        User user = createUser(username, displayName);
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }

    private User createUser(String username, String displayName) {
        return userAccessApplicationService.createUser(new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
    }
}
