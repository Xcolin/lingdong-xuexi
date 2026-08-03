package com.lingdong.learning.interfaceconfig.application;

import com.lingdong.learning.audit.application.CreateSystemTaskCommand;
import com.lingdong.learning.audit.application.ImpactScope;
import com.lingdong.learning.audit.application.SystemTask;
import com.lingdong.learning.audit.application.SystemTaskApplicationService;
import com.lingdong.learning.audit.application.SystemTaskType;
import com.lingdong.learning.common.id.IdGenerator;
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
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

/**
 * Coordinates high-risk interface-service changes with the existing system-task approval workflow.
 */
@Service
public class InterfaceServiceApplicationService {
    private static final String SYSTEM_ADMIN_ROLE = "SYS_ADMIN";

    private final InterfaceServiceMapper interfaceServiceMapper;
    private final InterfaceServiceChangeMapper changeMapper;
    private final InterfaceServiceCallLogMapper callLogMapper;
    private final SystemTaskApplicationService taskService;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final IdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;

    public InterfaceServiceApplicationService(
            InterfaceServiceMapper interfaceServiceMapper,
            InterfaceServiceChangeMapper changeMapper,
            InterfaceServiceCallLogMapper callLogMapper,
            SystemTaskApplicationService taskService,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            IdGenerator idGenerator,
            PlatformTransactionManager transactionManager
    ) {
        this.interfaceServiceMapper = interfaceServiceMapper;
        this.changeMapper = changeMapper;
        this.callLogMapper = callLogMapper;
        this.taskService = taskService;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.idGenerator = idGenerator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Creates a pending registration proposal; no effective service is written at this stage. */
    @Transactional
    public InterfaceServiceChange createDraft(CreateInterfaceServiceChangeCommand command) {
        Objects.requireNonNull(command, "接口服务登记请求不能为空");
        requireSystemAdmin(command.submitterId());
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

    /** Creates a pending stop proposal while leaving the current service enabled until approval is applied. */
    @Transactional
    public InterfaceServiceChange createDisableDraft(CreateInterfaceServiceDisableCommand command) {
        Objects.requireNonNull(command, "接口服务停用请求不能为空");
        requireSystemAdmin(command.submitterId());
        InterfaceService service = requireService(command.serviceId());
        if (service.status() != InterfaceServiceStatus.ENABLED) {
            throw new IllegalStateException("接口服务已停用：" + service.id());
        }

        SystemTask task = createTask(command.submitterId(), command.taskTitle(), command.taskDescription());
        InterfaceServiceChange change = InterfaceServiceChange.disable(idGenerator.nextId(), task.id(), service.id());
        insertChange(change);
        return change;
    }

    /** Creates a pending authorization-boundary proposal without changing the effective service immediately. */
    @Transactional
    public InterfaceServiceChange createAuthorizationChangeDraft(CreateInterfaceServiceAuthorizationChangeCommand command) {
        Objects.requireNonNull(command, "接口服务授权范围变更请求不能为空");
        requireSystemAdmin(command.submitterId());
        InterfaceService service = requireService(command.serviceId());
        Scope scope = normalizeScope(command.authorizationScope(), command.authorizationScopeValue());

        SystemTask task = createTask(command.submitterId(), command.taskTitle(), command.taskDescription());
        InterfaceServiceChange change = InterfaceServiceChange.changeAuthorization(
                idGenerator.nextId(), task.id(), service.id(), scope.type(), scope.value()
        );
        insertChange(change);
        return change;
    }

    /** Delegates draft submission to the shared system-task state machine. */
    public void submit(Long taskId, Long submitterId) {
        taskService.submit(taskId, submitterId);
    }

    /**
     * Approves first, then applies the already stored proposal in a separate transaction.
     * An execution failure therefore rolls back only the business mutation and leaves the task approved, not effective.
     */
    public SystemTask approveAndApply(Long taskId, Long auditorId, String comment) {
        InterfaceServiceChange change = requireChange(taskId);
        taskService.approve(taskId, auditorId, comment);
        SystemTask effectiveTask = transactionTemplate.execute(status -> {
            apply(change);
            return taskService.markEffective(taskId);
        });
        return Objects.requireNonNull(effectiveTask, "接口服务变更生效失败");
    }

    /** Records a minimal call outcome only after the target service is confirmed as enabled. */
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
        if (userId == null || !userRoleMapper.hasRoleCode(userId, SYSTEM_ADMIN_ROLE)) {
            throw new IllegalStateException("仅系统管理员可发起接口服务变更");
        }
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
