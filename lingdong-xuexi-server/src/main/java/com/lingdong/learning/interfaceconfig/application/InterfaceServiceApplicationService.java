package com.lingdong.learning.interfaceconfig.application;

import com.lingdong.learning.audit.application.CreateSystemTaskCommand;
import com.lingdong.learning.audit.application.ImpactScope;
import com.lingdong.learning.audit.application.SystemTask;
import com.lingdong.learning.audit.application.SystemTaskApplicationService;
import com.lingdong.learning.audit.application.SystemTaskType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.interfaceconfig.domain.InterfaceAuthorizationScope;
import com.lingdong.learning.interfaceconfig.domain.InterfaceCallResult;
import com.lingdong.learning.interfaceconfig.domain.InterfaceService;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceCallLog;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceChange;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceChangeType;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceStatus;
import com.lingdong.learning.interfaceconfig.infrastructure.persistence.InterfaceServiceChangeMapper;
import com.lingdong.learning.interfaceconfig.infrastructure.persistence.InterfaceServiceCallLogMapper;
import com.lingdong.learning.interfaceconfig.infrastructure.persistence.InterfaceServiceMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.List;

/** 协调接口服务查询、高风险变更、系统任务审批和最小化调用台账。 */
@Service
public class InterfaceServiceApplicationService {
    private static final String SYSTEM_ADMIN_ROLE = "SYS_ADMIN";
    private static final int MAX_QUERY_SIZE = 200;

    private final InterfaceServiceMapper interfaceServiceMapper;
    private final InterfaceServiceChangeMapper changeMapper;
    private final InterfaceServiceCallLogMapper callLogMapper;
    private final SystemTaskApplicationService taskService;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionDecisionService permissionDecisionService;
    private final IdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;

    public InterfaceServiceApplicationService(
            InterfaceServiceMapper interfaceServiceMapper,
            InterfaceServiceChangeMapper changeMapper,
            InterfaceServiceCallLogMapper callLogMapper,
            SystemTaskApplicationService taskService,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            PermissionDecisionService permissionDecisionService,
            IdGenerator idGenerator,
            PlatformTransactionManager transactionManager
    ) {
        this.interfaceServiceMapper = interfaceServiceMapper;
        this.changeMapper = changeMapper;
        this.callLogMapper = callLogMapper;
        this.taskService = taskService;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.permissionDecisionService = permissionDecisionService;
        this.idGenerator = idGenerator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 创建待审核登记快照，此时不写入生效服务。 */
    @Transactional
    public InterfaceServiceChange createDraft(CreateInterfaceServiceChangeCommand command) {
        Objects.requireNonNull(command, "接口服务登记请求不能为空");
        requireSystemAdmin(command.submitterId());
        requirePermission(command.submitterId(), "INTERFACE_SERVICE_MANAGE");
        String serviceName = requiredText(command.serviceName(), "服务名称", 100);
        String callerName = requiredText(command.callerName(), "调用方", 100);
        if (command.direction() == null || command.purpose() == null) {
            throw new IllegalArgumentException("服务方向和用途不能为空");
        }
        requireOwner(command.ownerId());
        Scope scope = normalizeScope(command.authorizationScope(), command.authorizationScopeValue());

        SystemTask task = createTask(command.submitterId(), command.taskTitle(), command.taskDescription());
        InterfaceServiceChange change = InterfaceServiceChange.create(
                idGenerator.nextId(), task.id(), serviceName, command.direction(), command.purpose(), callerName,
                scope.type(), scope.value(), command.ownerId()
        );
        insertChange(change);
        return change;
    }

    /** 创建待审核停用快照，审批生效前继续保持当前启用状态。 */
    @Transactional
    public InterfaceServiceChange createDisableDraft(CreateInterfaceServiceDisableCommand command) {
        Objects.requireNonNull(command, "接口服务停用请求不能为空");
        requireSystemAdmin(command.submitterId());
        requirePermission(command.submitterId(), "INTERFACE_SERVICE_MANAGE");
        InterfaceService service = requireService(command.serviceId());
        if (service.status() != InterfaceServiceStatus.ENABLED) {
            throw new IllegalStateException("接口服务已停用：" + service.id());
        }

        SystemTask task = createTask(command.submitterId(), command.taskTitle(), command.taskDescription());
        InterfaceServiceChange change = InterfaceServiceChange.disable(idGenerator.nextId(), task.id(), service.id()).withBefore(service);
        insertChange(change);
        return change;
    }

    /** 创建待审核启用快照，审批生效前继续保持当前停用状态。 */
    @Transactional
    public InterfaceServiceChange createEnableDraft(CreateInterfaceServiceEnableCommand command) {
        Objects.requireNonNull(command, "接口服务启用请求不能为空");
        requireSystemAdmin(command.submitterId());
        requirePermission(command.submitterId(), "INTERFACE_SERVICE_MANAGE");
        InterfaceService service = requireService(command.serviceId());
        if (service.status() != InterfaceServiceStatus.DISABLED) {
            throw new IllegalStateException("仅已停用接口服务可重新启用：" + service.id());
        }

        SystemTask task = createTask(command.submitterId(), command.taskTitle(), command.taskDescription());
        InterfaceServiceChange change = InterfaceServiceChange.enable(idGenerator.nextId(), task.id(), service.id()).withBefore(service);
        insertChange(change);
        return change;
    }

    /** 创建待审核授权范围变更快照，不立即改变当前授权边界。 */
    @Transactional
    public InterfaceServiceChange createAuthorizationChangeDraft(CreateInterfaceServiceAuthorizationChangeCommand command) {
        Objects.requireNonNull(command, "接口服务授权范围变更请求不能为空");
        requireSystemAdmin(command.submitterId());
        requirePermission(command.submitterId(), "INTERFACE_SERVICE_MANAGE");
        InterfaceService service = requireService(command.serviceId());
        Scope scope = normalizeScope(command.authorizationScope(), command.authorizationScopeValue());

        SystemTask task = createTask(command.submitterId(), command.taskTitle(), command.taskDescription());
        InterfaceServiceChange change = InterfaceServiceChange.changeAuthorization(
                idGenerator.nextId(), task.id(), service.id(), scope.type(), scope.value()
        ).withBefore(service);
        insertChange(change);
        return change;
    }

    /** 将草稿提交到共享系统任务状态机。 */
    public void submit(Long taskId, Long submitterId) {
        requirePermission(submitterId, "INTERFACE_SERVICE_MANAGE");
        taskService.submit(taskId, submitterId);
    }

    @Transactional
    public InterfaceServiceChange createAndSubmitRegistration(CreateInterfaceServiceChangeCommand command) {
        InterfaceServiceChange change = createDraft(command);
        submit(change.taskId(), command.submitterId());
        return change;
    }

    @Transactional
    public InterfaceServiceChange createAndSubmitDisable(CreateInterfaceServiceDisableCommand command) {
        InterfaceServiceChange change = createDisableDraft(command);
        submit(change.taskId(), command.submitterId());
        return change;
    }

    @Transactional
    public InterfaceServiceChange createAndSubmitEnable(CreateInterfaceServiceEnableCommand command) {
        InterfaceServiceChange change = createEnableDraft(command);
        submit(change.taskId(), command.submitterId());
        return change;
    }

    @Transactional
    public InterfaceServiceChange createAndSubmitAuthorization(
            CreateInterfaceServiceAuthorizationChangeCommand command
    ) {
        InterfaceServiceChange change = createAuthorizationChangeDraft(command);
        submit(change.taskId(), command.submitterId());
        return change;
    }

    /**
     * 先完成审批，再在独立事务中执行已保存的变更提案。
     * 执行失败时只回滚业务变更，任务保持已批准但未生效状态，便于后续处置和审计。
     */
    public SystemTask approveAndApply(Long taskId, Long auditorId, String comment) {
        requirePermission(auditorId, "INTERFACE_SERVICE_REVIEW");
        InterfaceServiceChange change = requireChange(taskId);
        taskService.approve(taskId, auditorId, comment);
        try {
            SystemTask effectiveTask = transactionTemplate.execute(status -> {
                apply(change);
                if (changeMapper.markApplied(change.id()) != 1) {
                    throw new IllegalStateException("接口服务变更执行状态更新失败");
                }
                return taskService.markEffective(taskId);
            });
            return Objects.requireNonNull(effectiveTask, "接口服务变更生效失败");
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> {
                if (changeMapper.markFailed(change.id(), "接口服务变更执行失败") != 1) {
                    throw new IllegalStateException("接口服务变更失败状态更新失败", exception);
                }
            });
            throw exception;
        }
    }

    /** 驳回变更任务，保留变更快照但不修改生效服务。 */
    public SystemTask reject(Long taskId, Long auditorId, String comment) {
        requirePermission(auditorId, "INTERFACE_SERVICE_REVIEW");
        requireChange(taskId);
        return taskService.reject(taskId, auditorId, comment);
    }

    public List<InterfaceService> listServices(
            Long operatorId,
            String serviceName,
            String callerName,
            InterfaceServiceStatus status,
            com.lingdong.learning.interfaceconfig.domain.InterfacePurpose purpose,
            Long ownerId,
            Integer limit
    ) {
        requirePermission(operatorId, "INTERFACE_SERVICE_READ");
        return interfaceServiceMapper.findAll(
                optionalText(serviceName, 100),
                optionalText(callerName, 100),
                status,
                purpose,
                ownerId,
                normalizeLimit(limit)
        );
    }

    public List<InterfaceServiceChangeView> listChanges(Long operatorId, Integer limit) {
        requirePermission(operatorId, "INTERFACE_SERVICE_READ");
        return changeMapper.findRecent(false, normalizeLimit(limit));
    }

    public List<InterfaceServiceChangeView> listPendingReviews(Long operatorId, Integer limit) {
        requirePermission(operatorId, "INTERFACE_SERVICE_REVIEW");
        return changeMapper.findRecent(true, normalizeLimit(limit));
    }

    public List<InterfaceServiceCallLogView> listCallLogs(
            Long operatorId,
            Long serviceId,
            InterfaceCallResult result,
            Integer limit
    ) {
        requirePermission(operatorId, "INTERFACE_SERVICE_READ");
        return callLogMapper.findRecent(serviceId, result, normalizeLimit(limit));
    }

    /** 仅在目标服务启用时记录最小化调用结果。 */
    @Transactional
    public InterfaceServiceCallLog recordCall(RecordInterfaceServiceCallCommand command) {
        Objects.requireNonNull(command, "接口服务调用记录不能为空");
        InterfaceService service = requireService(command.serviceId());
        if (service.status() != InterfaceServiceStatus.ENABLED) {
            throw new IllegalStateException("接口服务已停用，不能记录调用：" + service.id());
        }
        String callerName = requiredText(command.callerName(), "调用方", 100);
        if (command.result() == null) {
            throw new IllegalArgumentException("调用结果不能为空");
        }
        if (command.occurredAt() == null) {
            throw new IllegalArgumentException("调用时间不能为空");
        }
        InterfaceServiceCallLog callLog = InterfaceServiceCallLog.create(
                idGenerator.nextId(),
                service.id(),
                callerName,
                command.result(),
                optionalText(command.errorSummary(), 1000),
                optionalText(command.traceId(), 64),
                command.occurredAt()
        );
        if (callLogMapper.insert(callLog) != 1) {
            throw new IllegalStateException("接口服务调用记录保存失败");
        }
        return callLog;
    }

    private SystemTask createTask(Long submitterId, String title, String description) {
        return taskService.createDraft(new CreateSystemTaskCommand(
                submitterId,
                SystemTaskType.INTERFACE_SERVICE_CHANGE,
                title,
                description,
                ImpactScope.GLOBAL
        ));
    }

    private void apply(InterfaceServiceChange change) {
        int affectedRows = switch (change.changeType()) {
            case CREATE -> interfaceServiceMapper.insert(InterfaceService.enabled(idGenerator.nextId(), change));
            case ENABLE -> interfaceServiceMapper.enable(change.serviceId());
            case DISABLE -> interfaceServiceMapper.updateStatus(change.serviceId(), InterfaceServiceStatus.DISABLED);
            case CHANGE_AUTHORIZATION -> interfaceServiceMapper.updateAuthorizationScope(
                    change.serviceId(), change.authorizationScope(), change.authorizationScopeValue()
            );
        };
        if (affectedRows != 1) {
            throw new IllegalStateException("接口服务变更执行失败");
        }
    }

    private InterfaceServiceChange requireChange(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("系统任务标识不能为空");
        }
        InterfaceServiceChange change = changeMapper.findByTaskId(taskId);
        if (change == null) {
            throw new IllegalArgumentException("接口服务变更任务不存在：" + taskId);
        }
        return change;
    }

    private InterfaceService requireService(Long serviceId) {
        if (serviceId == null) {
            throw new IllegalArgumentException("接口服务标识不能为空");
        }
        InterfaceService service = interfaceServiceMapper.findById(serviceId);
        if (service == null) {
            throw new IllegalArgumentException("接口服务不存在：" + serviceId);
        }
        return service;
    }

    private void requireOwner(Long ownerId) {
        if (ownerId == null || userMapper.findById(ownerId) == null) {
            throw new IllegalArgumentException("接口服务责任人不存在：" + ownerId);
        }
    }

    private void requireSystemAdmin(Long userId) {
        if (userId == null || userRoleMapper.hasRoleCode(userId, "SYS_AUDITOR")
                || !userRoleMapper.hasRoleCode(userId, SYSTEM_ADMIN_ROLE)) {
            throw new SystemOperationAccessDeniedException("仅非审核员的系统管理员可发起接口服务变更");
        }
    }

    private void requirePermission(Long operatorId, String permissionCode) {
        if (!permissionDecisionService.isAllowed(operatorId, PermissionClient.WEB, permissionCode)) {
            throw new SystemOperationAccessDeniedException("当前账号无权执行该接口服务管理操作");
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return MAX_QUERY_SIZE;
        }
        if (limit < 1 || limit > MAX_QUERY_SIZE) {
            throw new IllegalArgumentException("查询数量必须在1到" + MAX_QUERY_SIZE + "之间");
        }
        return limit;
    }

    private Scope normalizeScope(InterfaceAuthorizationScope scope, String scopeValue) {
        if (scope == null) {
            throw new IllegalArgumentException("授权范围不能为空");
        }
        String normalizedValue = optionalText(scopeValue, 128);
        if (scope == InterfaceAuthorizationScope.GLOBAL && normalizedValue != null) {
            throw new IllegalArgumentException("全局授权范围不能指定范围值");
        }
        if (scope != InterfaceAuthorizationScope.GLOBAL && normalizedValue == null) {
            throw new IllegalArgumentException("非全局授权范围必须指定范围值");
        }
        return new Scope(scope, normalizedValue);
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        String normalizedValue = optionalText(value, maxLength);
        if (normalizedValue == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalizedValue;
    }

    private String optionalText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalizedValue = value.trim();
        if (normalizedValue.isEmpty()) {
            return null;
        }
        if (normalizedValue.length() > maxLength) {
            throw new IllegalArgumentException("文本长度不能超过" + maxLength + "个字符");
        }
        return normalizedValue;
    }

    private void insertChange(InterfaceServiceChange change) {
        if (changeMapper.insert(change) != 1) {
            throw new IllegalStateException("接口服务变更草稿保存失败");
        }
    }

    private record Scope(InterfaceAuthorizationScope type, String value) { }
}
