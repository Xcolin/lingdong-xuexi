package com.lingdong.learning.organization.application;

import com.lingdong.learning.audit.application.CreateSystemTaskCommand;
import com.lingdong.learning.audit.application.ImpactScope;
import com.lingdong.learning.audit.application.SystemTask;
import com.lingdong.learning.audit.application.SystemTaskApplicationService;
import com.lingdong.learning.audit.application.SystemTaskType;
import com.lingdong.learning.audit.infrastructure.persistence.SystemTaskMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.application.ClassTaskInvalidationService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationChange;
import com.lingdong.learning.organization.domain.OrganizationChangeAudit;
import com.lingdong.learning.organization.domain.OrganizationChangeAuditEvent;
import com.lingdong.learning.organization.domain.OrganizationChangeType;
import com.lingdong.learning.organization.domain.OrganizationEffectiveStatus;
import com.lingdong.learning.organization.domain.OrganizationStatus;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationChangeAuditMapper;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationChangeMapper;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 协调高风险组织变更申请与系统任务审核状态机。 */
@Service
public class OrganizationChangeApplicationService {
    private final com.lingdong.learning.datascope.application.OrganizationDataScopeService scopes;
    private final com.lingdong.learning.permission.application.PermissionDecisionService decisions;
    private final OrganizationMapper organizationMapper;
    private final OrganizationChangeMapper organizationChangeMapper;
    private final OrganizationChangeAuditMapper organizationChangeAuditMapper;
    private final SystemTaskApplicationService systemTaskApplicationService;
    private final UserRoleMapper userRoleMapper;
    private final SystemTaskMapper systemTaskMapper;
    private final FeatureAccessService featureAccessService;
    private final ClassTaskInvalidationService classTaskInvalidationService;
    private final IdGenerator idGenerator;
    private final TransactionTemplate applyTransaction;
    private final TransactionTemplate failureTransaction;

    public OrganizationChangeApplicationService(
            OrganizationMapper organizationMapper,
            OrganizationChangeMapper organizationChangeMapper,
            OrganizationChangeAuditMapper organizationChangeAuditMapper,
            SystemTaskApplicationService systemTaskApplicationService,
            UserRoleMapper userRoleMapper,
            SystemTaskMapper systemTaskMapper,
            FeatureAccessService featureAccessService,
            ClassTaskInvalidationService classTaskInvalidationService,
            IdGenerator idGenerator,
            PlatformTransactionManager transactionManager, com.lingdong.learning.permission.application.PermissionDecisionService decisions, com.lingdong.learning.datascope.application.OrganizationDataScopeService scopes) {
        this.scopes = scopes;
        this.decisions = decisions;
        this.organizationMapper = organizationMapper;
        this.organizationChangeMapper = organizationChangeMapper;
        this.organizationChangeAuditMapper = organizationChangeAuditMapper;
        this.systemTaskApplicationService = systemTaskApplicationService;
        this.userRoleMapper = userRoleMapper;
        this.systemTaskMapper = systemTaskMapper;
        this.featureAccessService = featureAccessService;
        this.classTaskInvalidationService = classTaskInvalidationService;
        this.idGenerator = idGenerator;
        this.applyTransaction = new TransactionTemplate(transactionManager);
        this.failureTransaction = new TransactionTemplate(transactionManager);
        this.failureTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 创建组织变更快照并由发起人立即提交系统审核。 */
    @Transactional
    public OrganizationChange createAndSubmit(CreateOrganizationChangeCommand command) {
        requireFeatureEnabled();
        Objects.requireNonNull(command, "组织变更申请不能为空");
        Objects.requireNonNull(command.submitterId(), "提交人不能为空");
        requirePermission(command.submitterId(), "ORG_NODE_CHANGE_SUBMIT");
        requireScope(command.submitterId(), command.organizationId());
        if (command.targetParentId() != null) requireScope(command.submitterId(), command.targetParentId());
        Objects.requireNonNull(command.organizationId(), "组织ID不能为空");
        Objects.requireNonNull(command.changeType(), "组织变更类型不能为空");
        Objects.requireNonNull(command.expectedVersion(), "组织版本号不能为空");
        if (command.expectedVersion() < 1) {
            throw new IllegalArgumentException("组织版本号必须大于0");
        }
        String reason = requiredText(command.reason(), "变更原因", 500);
        validateTargetParent(command.changeType(), command.targetParentId());

        Organization organization = organizationMapper.findByIdForUpdate(command.organizationId());
        if (organization == null) {
            throw new ResourceNotFoundException("组织不存在：" + command.organizationId());
        }
        if (!organization.versionNo().equals(command.expectedVersion())) {
            throw new OrganizationVersionConflictException();
        }
        if (command.changeType() == OrganizationChangeType.MOVE) {
            validateMoveTargetAtSubmission(organization, command.targetParentId());
        }
        if (organizationChangeMapper.existsActiveByOrganizationId(organization.id())) {
            throw new IllegalStateException("该组织已有待处理的高风险变更申请");
        }

        SystemTask task = systemTaskApplicationService.createDraft(new CreateSystemTaskCommand(
                command.submitterId(), taskType(command.changeType()),
                taskTitle(command.changeType(), organization.name()), reason, ImpactScope.ORGANIZATION));
        OrganizationChange change = OrganizationChange.pending(
                idGenerator.nextId(), task.id(), organization, command.changeType(),
                command.targetParentId(), reason);
        if (organizationChangeMapper.insert(change) != 1) {
            throw new IllegalStateException("组织变更申请保存失败");
        }
        organizationChangeAuditMapper.insert(OrganizationChangeAudit.request(
                idGenerator.nextId(), organization, task.id(), command.changeType(),
                command.targetParentId(), command.submitterId(), reason, LocalDateTime.now()));
        systemTaskApplicationService.submit(task.id(), command.submitterId());
        return organizationChangeMapper.findByTaskId(task.id());
    }

    @Transactional
    public OrganizationChangeReviewItem createAndSubmitItem(CreateOrganizationChangeCommand command) {
        OrganizationChange change = createAndSubmit(command);
        return toReviewItem(change);
    }

    /**
     * 拖拽同级排序直接生效：完整提交同级兄弟集合与乐观锁版本，按提交顺序重排（步长 10），逐节点记 DIRECT_REORDER 审计。
     */
    @Transactional
    public List<Organization> directReorder(ReorderOrganizationsCommand command) {
        requireFeatureEnabled();
        Objects.requireNonNull(command, "排序命令不能为空");
        requirePermission(command.operatorId(), "ORG_NODE_UPDATE");
        requireScope(command.operatorId(), command.parentId());
        List<OrganizationOrderItem> items = command.items() == null ? List.of() : command.items();
        if (items.isEmpty() || items.stream().anyMatch(Objects::isNull)
                || items.stream().anyMatch(item -> item.organizationId() == null || item.expectedVersion() == null)) {
            throw new IllegalArgumentException("排序参数无效");
        }

        List<Organization> siblings = organizationMapper.findChildrenForUpdate(command.parentId());
        Map<Long, Integer> requestedOrder = new java.util.LinkedHashMap<>();
        for (OrganizationOrderItem item : items) {
            if (requestedOrder.put(item.organizationId(), item.expectedVersion()) != null) {
                throw new IllegalArgumentException("排序条目重复：" + item.organizationId());
            }
        }
        if (requestedOrder.size() != siblings.size()
                || !requestedOrder.keySet().equals(siblings.stream().map(Organization::id).collect(java.util.stream.Collectors.toSet()))) {
            throw new IllegalStateException("同级节点集合已变化，请刷新后重试");
        }
        List<Long> requestedIds = new ArrayList<>(requestedOrder.keySet());
        for (Organization sibling : siblings) {
            Integer expectedVersion = requestedOrder.get(sibling.id());
            int sortOrder = requestedIds.indexOf(sibling.id()) * 10;
            if (organizationMapper.updateSortOrder(sibling.id(), sortOrder, expectedVersion) != 1) {
                throw new OrganizationVersionConflictException();
            }
            organizationChangeAuditMapper.insert(OrganizationChangeAudit.directReorder(
                    idGenerator.nextId(), sibling, sortOrder, command.operatorId(), LocalDateTime.now()));
        }
        return organizationMapper.findChildrenForUpdate(command.parentId());
    }

    /**
     * 拖拽改父级直接生效：复用申请-审核执行器中的路径重建与唯一名校验，记 DIRECT_MOVE 审计。
     */
    @Transactional
    public Organization directMove(MoveOrganizationCommand command) {
        requireFeatureEnabled();
        Objects.requireNonNull(command, "移动命令不能为空");
        requirePermission(command.operatorId(), "ORG_NODE_UPDATE");
        requireScope(command.operatorId(), command.organizationId());
        requireScope(command.operatorId(), command.targetParentId());
        Objects.requireNonNull(command.organizationId(), "组织ID不能为空");
        Objects.requireNonNull(command.targetParentId(), "目标父级不能为空");
        Objects.requireNonNull(command.expectedVersion(), "组织版本号不能为空");

        Organization before = organizationMapper.findByIdForUpdate(command.organizationId());
        if (before == null) {
            throw new ResourceNotFoundException("组织不存在：" + command.organizationId());
        }
        if (!before.versionNo().equals(command.expectedVersion())) {
            throw new OrganizationVersionConflictException();
        }
        if (Objects.equals(before.parentId(), command.targetParentId())) {
            throw new IllegalStateException("组织已属于目标父级，同级顺序请使用排序接口");
        }
        OrganizationChange pendingMove = OrganizationChange.pending(
                idGenerator.nextId(), null, before, OrganizationChangeType.MOVE,
                command.targetParentId(), null);
        Organization after = applyMove(before, pendingMove);
        organizationChangeAuditMapper.insert(OrganizationChangeAudit.directMove(
                idGenerator.nextId(), before, after, command.operatorId(), LocalDateTime.now()));
        return after;
    }

    @Transactional(readOnly = true)
    public List<OrganizationChangeReviewItem> listChanges(Long operatorId) {
        requireFeatureEnabled();
        requireAdministratorOrAuditor(operatorId);
        boolean auditor = decisions.isAllowed(operatorId, com.lingdong.learning.permission.domain.PermissionClient.WEB, "ORG_NODE_CHANGE_REVIEW");
        return organizationChangeMapper.findAll().stream()
                .map(this::toReviewItem)
                .filter(item -> auditor || item.task().submittedBy().equals(operatorId))
                .filter(item -> scopes.canAccess(operatorId, item.change().organizationId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizationChangeReviewItem getChange(Long operatorId, Long taskId) {
        requireFeatureEnabled();
        requireAdministratorOrAuditor(operatorId);
        OrganizationChange change = requireChange(taskId);
        requireScope(operatorId, change.organizationId());
        OrganizationChangeReviewItem item = toReviewItem(change);
        if (!decisions.isAllowed(operatorId, com.lingdong.learning.permission.domain.PermissionClient.WEB, "ORG_NODE_CHANGE_REVIEW")
                && !item.task().submittedBy().equals(operatorId)) {
            throw new SystemOperationAccessDeniedException("无权查看其他系统管理员提交的组织变更");
        }
        return item;
    }

    /** 驳回待审核申请，审批意见与审计记录在同一事务内提交。 */
    @Transactional
    public SystemTask reject(Long taskId, Long auditorId, String comment) {
        requireFeatureEnabled();
        requirePermission(auditorId, "ORG_NODE_CHANGE_REVIEW");
        OrganizationChange change = requireChange(taskId);
        requireChangeScope(auditorId, change);
        SystemTask rejected = systemTaskApplicationService.reject(taskId, auditorId, comment);
        organizationChangeAuditMapper.insert(OrganizationChangeAudit.review(
                idGenerator.nextId(), change, OrganizationChangeAuditEvent.REJECT,
                auditorId, comment.trim(), LocalDateTime.now()));
        return rejected;
    }

    /**
     * 先提交审核结论，再在独立事务中应用组织变更；执行失败不会撤销审核结论。
     */
    public SystemTask approveAndApply(Long taskId, Long auditorId, String comment) {
        requireFeatureEnabled();
        requirePermission(auditorId, "ORG_NODE_CHANGE_REVIEW");
        OrganizationChange change = requireChange(taskId);
        requireChangeScope(auditorId, change);
        systemTaskApplicationService.approve(taskId, auditorId, comment);
        try {
            SystemTask effective = applyTransaction.execute(status -> applyApprovedChange(change, auditorId));
            return Objects.requireNonNull(effective, "组织变更生效失败");
        } catch (RuntimeException exception) {
            String failureReason = safeFailureReason(exception);
            failureTransaction.executeWithoutResult(status -> recordExecutionFailure(
                    change, auditorId, failureReason));
            throw new OrganizationChangeExecutionException(failureReason, exception);
        }
    }

    private SystemTask applyApprovedChange(OrganizationChange change, Long auditorId) {
        Organization before = organizationMapper.findByIdForUpdate(change.organizationId());
        if (before == null) {
            throw new ResourceNotFoundException("组织不存在：" + change.organizationId());
        }
        if (!before.versionNo().equals(change.expectedVersion())) {
            throw new OrganizationVersionConflictException();
        }

        Organization after = switch (change.changeType()) {
            case DISABLE -> applyDisable(before, change.expectedVersion(), auditorId);
            case MOVE -> applyMove(before, change);
            case DELETE -> applyDelete(before, change.expectedVersion());
        };
        if (organizationChangeMapper.markApplied(change.id()) != 1) {
            throw new IllegalStateException("组织变更执行状态更新失败");
        }
        organizationChangeAuditMapper.insert(OrganizationChangeAudit.apply(
                idGenerator.nextId(), change, before, after, auditorId, LocalDateTime.now()));
        return systemTaskApplicationService.markEffective(change.taskId());
    }

    private Organization applyDisable(
            Organization organization,
            Integer expectedVersion,
            Long operatorUserId
    ) {
        if (organization.status() == OrganizationStatus.DISABLED) {
            throw new IllegalStateException("组织已停用，请重新核对申请");
        }
        int affectedRows = organizationMapper.updateStatusAndEffectiveStatus(
                organization.id(), OrganizationStatus.DISABLED,
                OrganizationEffectiveStatus.DISABLED, expectedVersion);
        if (affectedRows != 1) {
            throw new OrganizationVersionConflictException();
        }
        organizationMapper.disableDescendantEffectiveStatus(organization.id(), organization.path());
        if ("CLASS".equals(organization.typeCode())) {
            classTaskInvalidationService.invalidateUnfinishedAssignments(
                    organization.id(), operatorUserId);
        }
        return organizationMapper.findById(organization.id());
    }

    private Organization applyMove(Organization organization, OrganizationChange change) {
        Organization targetParent = organizationMapper.findByIdForUpdate(change.targetParentId());
        validateMoveTarget(organization, targetParent);
        if (Objects.equals(organization.parentId(), targetParent.id())) {
            throw new IllegalStateException("组织已属于目标父级");
        }
        String targetScopeKey = "PARENT:" + targetParent.id();
        if (organizationMapper.existsByParentScopeAndNameExcludingId(
                targetScopeKey, organization.name(), organization.id())) {
            throw new DuplicateOrganizationNameException(organization.name());
        }

        List<Organization> subtree = organizationMapper.findSubtreeByPathForUpdate(organization.path());
        String newRootPath = targetParent.path() + organization.code() + "/";
        Map<Long, String> newPathById = calculateMovedPaths(organization, subtree, newRootPath);
        Map<Long, OrganizationEffectiveStatus> effectiveStatusById = new HashMap<>();
        OrganizationEffectiveStatus rootEffectiveStatus = calculateEffectiveStatus(
                organization.status(), targetParent.effectiveStatus());
        if (organizationMapper.updateLocationAndPath(
                organization.id(), targetParent.id(), targetScopeKey, newRootPath,
                rootEffectiveStatus, change.expectedVersion()) != 1) {
            throw new OrganizationVersionConflictException();
        }
        effectiveStatusById.put(organization.id(), rootEffectiveStatus);

        for (Organization descendant : subtree) {
            if (descendant.id().equals(organization.id())) {
                continue;
            }
            OrganizationEffectiveStatus parentEffectiveStatus = effectiveStatusById.get(descendant.parentId());
            if (parentEffectiveStatus == null) {
                throw new IllegalStateException("组织树路径与父子关系不一致：" + descendant.id());
            }
            OrganizationEffectiveStatus effectiveStatus = calculateEffectiveStatus(
                    descendant.status(), parentEffectiveStatus);
            if (organizationMapper.updatePathAndEffectiveStatus(
                    descendant.id(), newPathById.get(descendant.id()),
                    effectiveStatus, descendant.versionNo()) != 1) {
                throw new OrganizationVersionConflictException();
            }
            effectiveStatusById.put(descendant.id(), effectiveStatus);
        }
        return organizationMapper.findById(organization.id());
    }

    private Organization applyDelete(Organization organization, Integer expectedVersion) {
        long referenceCount = organizationMapper.countDeleteReferences(organization.id());
        if (referenceCount > 0) {
            throw new IllegalStateException("组织存在下级节点或业务引用，不能删除");
        }
        if (organizationMapper.deleteLeaf(organization.id(), expectedVersion) != 1) {
            throw new OrganizationVersionConflictException();
        }
        return null;
    }

    private Map<Long, String> calculateMovedPaths(
            Organization root,
            List<Organization> subtree,
            String newRootPath
    ) {
        Map<Long, String> newPathById = new HashMap<>();
        for (Organization node : subtree) {
            String suffix = node.path().substring(root.path().length());
            String newPath = newRootPath + suffix;
            if (newPath.length() > 1024) {
                throw new IllegalArgumentException("移动后组织路径不能超过1024个字符");
            }
            newPathById.put(node.id(), newPath);
        }
        return newPathById;
    }

    private void validateMoveTargetAtSubmission(Organization organization, Long targetParentId) {
        Organization targetParent = organizationMapper.findById(targetParentId);
        validateMoveTarget(organization, targetParent);
    }

    private void validateMoveTarget(Organization organization, Organization targetParent) {
        if (targetParent == null) {
            throw new ResourceNotFoundException("目标父级组织不存在");
        }
        if (organization.id().equals(targetParent.id())) {
            throw new IllegalArgumentException("组织不能移动到自身");
        }
        if (targetParent.path().startsWith(organization.path())) {
            throw new IllegalArgumentException("组织不能移动到自身后代");
        }
        if (targetParent.status() != OrganizationStatus.ENABLED
                || targetParent.effectiveStatus() != OrganizationEffectiveStatus.ENABLED) {
            throw new OrganizationNotOperationalException(targetParent.id());
        }
    }

    private OrganizationEffectiveStatus calculateEffectiveStatus(
            OrganizationStatus ownStatus,
            OrganizationEffectiveStatus parentEffectiveStatus
    ) {
        return ownStatus == OrganizationStatus.ENABLED
                && parentEffectiveStatus == OrganizationEffectiveStatus.ENABLED
                ? OrganizationEffectiveStatus.ENABLED
                : OrganizationEffectiveStatus.DISABLED;
    }

    private void recordExecutionFailure(
            OrganizationChange change,
            Long auditorId,
            String failureReason
    ) {
        if (organizationChangeMapper.markFailed(change.id(), failureReason) != 1) {
            throw new IllegalStateException("组织变更失败状态记录失败");
        }
        organizationChangeAuditMapper.insert(OrganizationChangeAudit.executionFailed(
                idGenerator.nextId(), change, auditorId, failureReason, LocalDateTime.now()));
    }

    private String safeFailureReason(RuntimeException exception) {
        if (exception instanceof OrganizationVersionConflictException) {
            return exception.getMessage();
        }
        return "组织变更条件已变化，请核对后重新申请";
    }

    private OrganizationChange requireChange(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("系统任务标识不能为空");
        }
        OrganizationChange change = organizationChangeMapper.findByTaskId(taskId);
        if (change == null) {
            throw new ResourceNotFoundException("组织变更任务不存在：" + taskId);
        }
        return change;
    }

    private void validateTargetParent(OrganizationChangeType changeType, Long targetParentId) {
        if (changeType == OrganizationChangeType.MOVE && targetParentId == null) {
            throw new IllegalArgumentException("移动组织时目标父级不能为空");
        }
        if (changeType != OrganizationChangeType.MOVE && targetParentId != null) {
            throw new IllegalArgumentException("非移动申请不能指定目标父级");
        }
    }

    private void requirePermission(Long userId, String code) {
        if (!decisions.isAllowed(userId, com.lingdong.learning.permission.domain.PermissionClient.WEB, code))
            throw new SystemOperationAccessDeniedException("当前账号无组织变更操作权限");
    }
    private void requireAdministratorOrAuditor(Long userId) {
        if (!decisions.isAllowed(userId, com.lingdong.learning.permission.domain.PermissionClient.WEB, "ORG_NODE_CHANGE_SUBMIT")
                && !decisions.isAllowed(userId, com.lingdong.learning.permission.domain.PermissionClient.WEB, "ORG_NODE_CHANGE_REVIEW"))
            throw new SystemOperationAccessDeniedException("当前账号无组织变更读取权限");
    }
    private void requireScope(Long userId, Long organizationId) {
        if (scopes.resolve(userId).allOrganizations()) return;
        boolean allowed = organizationId == null ? scopes.resolve(userId).allOrganizations() : scopes.canAccess(userId, organizationId);
        if (!allowed) throw new SystemOperationAccessDeniedException("组织不在可管理数据范围内");
    }
    private void requireChangeScope(Long userId, OrganizationChange change) {
        requireScope(userId, change.organizationId());
        if (change.targetParentId() != null) requireScope(userId, change.targetParentId());
    }
    private void requireFeatureEnabled() {
        featureAccessService.requireEnabled("ORGANIZATION_MANAGEMENT", null);
    }

    private OrganizationChangeReviewItem toReviewItem(OrganizationChange change) {
        SystemTask task = systemTaskMapper.findById(change.taskId());
        if (task == null) {
            throw new IllegalStateException("组织变更关联的系统任务不存在");
        }
        return new OrganizationChangeReviewItem(change, task);
    }

    private SystemTaskType taskType(OrganizationChangeType changeType) {
        return switch (changeType) {
            case DISABLE -> SystemTaskType.ORGANIZATION_DISABLE;
            case MOVE -> SystemTaskType.ORGANIZATION_MOVE;
            case DELETE -> SystemTaskType.ORGANIZATION_DELETE;
        };
    }

    private String taskTitle(OrganizationChangeType changeType, String organizationName) {
        String action = switch (changeType) {
            case DISABLE -> "停用";
            case MOVE -> "移动";
            case DELETE -> "删除";
        };
        return "组织" + action + "申请：" + organizationName;
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
}
