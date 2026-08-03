package com.lingdong.learning.cache.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.audit.application.CreateSystemTaskCommand;
import com.lingdong.learning.audit.application.ImpactScope;
import com.lingdong.learning.audit.application.SystemTask;
import com.lingdong.learning.audit.application.SystemTaskApplicationService;
import com.lingdong.learning.audit.application.SystemTaskType;
import com.lingdong.learning.cache.domain.CacheDomain;
import com.lingdong.learning.cache.domain.CacheOperation;
import com.lingdong.learning.cache.domain.CacheOperationStatus;
import com.lingdong.learning.cache.domain.CacheOperationType;
import com.lingdong.learning.cache.infrastructure.persistence.CacheOperationMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Coordinates cache actions, high-risk task approval, and the immutable operation audit trail. */
@Service
public class CacheOperationApplicationService {
    private final CacheOperationMapper cacheOperationMapper;
    private final SystemTaskApplicationService systemTaskApplicationService;
    private final UserRoleMapper userRoleMapper;
    private final CacheManager cacheManager;
    private final Map<CacheDomain, ManagedCacheHandler> handlers;
    private final IdGenerator idGenerator;

    public CacheOperationApplicationService(
            CacheOperationMapper cacheOperationMapper,
            SystemTaskApplicationService systemTaskApplicationService,
            UserRoleMapper userRoleMapper,
            CacheManager cacheManager,
            List<ManagedCacheHandler> handlers,
            IdGenerator idGenerator
    ) {
        this.cacheOperationMapper = cacheOperationMapper;
        this.systemTaskApplicationService = systemTaskApplicationService;
        this.userRoleMapper = userRoleMapper;
        this.cacheManager = cacheManager;
        this.handlers = Map.copyOf(handlers.stream().collect(Collectors.toMap(
                ManagedCacheHandler::domain,
                Function.identity()
        )));
        this.idGenerator = idGenerator;
    }

    /** Executes a non-global, non-session cache action and always records its outcome. */
    @Transactional
    public CacheOperation execute(ExecuteCacheOperationCommand command) {
        Objects.requireNonNull(command, "缓存操作请求不能为空");
        requireSystemAdministrator(command.operatorId());
        CacheDomain domain = requireDomain(command.cacheDomain());
        CacheOperationType operationType = requireOperationType(command.operationType());
        requireDirectOperationAllowed(domain);
        return createAndExecute(
                null,
                domain,
                operationType,
                requiredText(command.impactDescription(), "影响说明", 1000),
                command.operatorId()
        );
    }

    /** Creates a pending audit record linked to a high-risk cache-clear system task. */
    @Transactional
    public CacheOperation createHighRiskDraft(CreateHighRiskCacheOperationCommand command) {
        Objects.requireNonNull(command, "高风险缓存操作请求不能为空");
        requireSystemAdministrator(command.submitterId());
        CacheDomain domain = requireDomain(command.cacheDomain());
        CacheOperationType operationType = requireOperationType(command.operationType());
        requireHighRiskOperation(domain, operationType, command.confirmed());

        String title = requiredText(command.title(), "任务标题", 100);
        String description = requiredText(command.description(), "影响说明", 1000);
        SystemTask task = systemTaskApplicationService.createDraft(new CreateSystemTaskCommand(
                command.submitterId(),
                SystemTaskType.CACHE_CLEAR,
                title,
                description,
                ImpactScope.GLOBAL
        ));
        return createPending(task.id(), domain, operationType, description, command.submitterId());
    }

    /** Submits only the system task that belongs to the requested cache operation. */
    @Transactional
    public void submit(Long taskId, Long submitterId) {
        requirePendingOperation(taskId);
        systemTaskApplicationService.submit(taskId, submitterId);
    }

    /** Approves, executes, and marks the task effective only when the cache action succeeds. */
    @Transactional
    public CacheOperation approveAndExecute(Long taskId, Long auditorId, String comment) {
        CacheOperation operation = requirePendingOperation(taskId);
        systemTaskApplicationService.approve(taskId, auditorId, comment);
        CacheOperation result = executeAndRecord(operation, auditorId);
        if (result.status() == CacheOperationStatus.SUCCEEDED) {
            systemTaskApplicationService.markEffective(taskId);
        }
        return result;
    }

    private CacheOperation createAndExecute(
            Long taskId,
            CacheDomain domain,
            CacheOperationType operationType,
            String impactDescription,
            Long requestedBy
    ) {
        CacheOperation operation = createPending(taskId, domain, operationType, impactDescription, requestedBy);
        return executeAndRecord(operation, requestedBy);
    }

    private CacheOperation createPending(
            Long taskId,
            CacheDomain domain,
            CacheOperationType operationType,
            String impactDescription,
            Long requestedBy
    ) {
        String operationCode = UUID.randomUUID().toString();
        cacheOperationMapper.insert(CacheOperation.pending(
                idGenerator.nextId(),
                operationCode,
                taskId,
                domain,
                operationType,
                impactDescription,
                requestedBy
        ));
        CacheOperation operation = cacheOperationMapper.findByCode(operationCode);
        if (operation == null) {
            throw new IllegalStateException("缓存操作台账创建失败");
        }
        return operation;
    }

    private CacheOperation executeAndRecord(CacheOperation operation, Long executedBy) {
        try {
            executeOperation(operation);
            if (cacheOperationMapper.markSucceeded(operation.id(), executedBy) != 1) {
                throw new IllegalStateException("缓存操作状态更新失败");
            }
        } catch (RuntimeException exception) {
            String failureMessage = normalizeFailureMessage(exception);
            if (cacheOperationMapper.markFailed(operation.id(), executedBy, failureMessage) != 1) {
                throw new IllegalStateException("缓存操作失败且无法记录结果", exception);
            }
        }
        return cacheOperationMapper.findById(operation.id());
    }

    private void executeOperation(CacheOperation operation) {
        if (operation.domain() == CacheDomain.ALL) {
            clearAllRegisteredCaches();
            return;
        }

        ManagedCacheHandler handler = handlers.get(operation.domain());
        if (handler == null) {
            throw new IllegalStateException("未注册缓存处理器：" + operation.domain());
        }
        if (operation.operationType() == CacheOperationType.CLEAR) {
            handler.clear();
        } else {
            handler.refresh();
        }
    }

    private void clearAllRegisteredCaches() {
        for (String cacheName : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    private CacheOperation requirePendingOperation(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("系统任务不能为空");
        }
        CacheOperation operation = cacheOperationMapper.findByTaskId(taskId);
        if (operation == null || operation.status() != CacheOperationStatus.PENDING) {
            throw new IllegalStateException("待执行缓存操作不存在：" + taskId);
        }
        return operation;
    }

    private void requireDirectOperationAllowed(CacheDomain domain) {
        if (domain == CacheDomain.ALL || domain == CacheDomain.USER_SESSION) {
            throw new IllegalStateException("全量清除和用户会话缓存操作必须通过系统任务审批");
        }
    }

    private void requireHighRiskOperation(CacheDomain domain, CacheOperationType operationType, boolean confirmed) {
        boolean supported = operationType == CacheOperationType.CLEAR
                && (domain == CacheDomain.ALL || domain == CacheDomain.USER_SESSION);
        if (!supported) {
            throw new IllegalArgumentException("仅全量清除或用户会话缓存清除可创建高风险缓存任务");
        }
        if (!confirmed) {
            throw new IllegalArgumentException("高风险缓存操作必须二次确认");
        }
    }

    private CacheDomain requireDomain(CacheDomain domain) {
        return Objects.requireNonNull(domain, "缓存类型不能为空");
    }

    private CacheOperationType requireOperationType(CacheOperationType operationType) {
        return Objects.requireNonNull(operationType, "缓存操作类型不能为空");
    }

    private void requireSystemAdministrator(Long operatorId) {
        if (operatorId == null || !userRoleMapper.hasRoleCode(operatorId, "SYS_ADMIN")) {
            throw new IllegalStateException("仅系统管理员可管理缓存");
        }
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private String normalizeFailureMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "缓存操作执行失败";
        }
        String normalized = message.trim();
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }
}
