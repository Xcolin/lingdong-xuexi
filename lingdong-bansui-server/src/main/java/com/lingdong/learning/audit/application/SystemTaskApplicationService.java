package com.lingdong.learning.audit.application;

import com.lingdong.learning.audit.infrastructure.persistence.SystemTaskMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Keeps system-task role checks and state changes in one transactional boundary. */
@Service
public class SystemTaskApplicationService {
    private final com.lingdong.learning.permission.application.PermissionDecisionService decisions;
    private final SystemTaskMapper taskMapper;
    private final UserRoleMapper userRoleMapper;
    private final IdGenerator idGenerator;
    public SystemTaskApplicationService(SystemTaskMapper taskMapper, UserRoleMapper userRoleMapper, IdGenerator idGenerator, com.lingdong.learning.permission.application.PermissionDecisionService decisions) {
        this.decisions = decisions; this.taskMapper = taskMapper; this.userRoleMapper = userRoleMapper; this.idGenerator = idGenerator; }

    @Transactional
    public SystemTask createDraft(CreateSystemTaskCommand command) {
        requirePermission(command.submitterId(), command.type(), false);
        String title = required(command.title(), "任务标题", 100);
        String description = required(command.description(), "任务说明", 1000);
        if (command.type() == null || command.impactScope() == null) throw new IllegalArgumentException("任务类型和影响范围不能为空");
        String code = UUID.randomUUID().toString();
        taskMapper.insert(new SystemTask(idGenerator.nextId(), code, command.type(), title, description, command.impactScope(), SystemTaskStatus.DRAFT, command.submitterId(), null, null, null, null, null, null));
        return taskMapper.findByCode(code);
    }
    @Transactional
    public SystemTask submit(Long taskId, Long submitterId) {
        SystemTask task = requireTask(taskId); requirePermission(submitterId, task.type(), false);
        if (!task.submittedBy().equals(submitterId)) throw new IllegalStateException("仅任务提交人可提交草稿");
        if (taskMapper.updateSubmission(taskId, SystemTaskStatus.PENDING_REVIEW) != 1) throw new IllegalStateException("仅草稿可提交审核");
        return taskMapper.findById(taskId);
    }
    @Transactional
    public SystemTask approve(Long taskId, Long reviewerId, String comment) { return review(taskId, reviewerId, SystemTaskStatus.APPROVED, comment, false); }
    @Transactional
    public SystemTask reject(Long taskId, Long reviewerId, String comment) { return review(taskId, reviewerId, SystemTaskStatus.REJECTED, comment, true); }
    @Transactional
    public SystemTask markEffective(Long taskId) { if (taskMapper.markEffective(taskId) != 1) throw new IllegalStateException("仅已通过任务可生效"); return taskMapper.findById(taskId); }
    private SystemTask review(Long id, Long reviewer, SystemTaskStatus status, String comment, boolean required) {
        SystemTask task = requireTask(id); requirePermission(reviewer, task.type(), true); String text = comment == null ? null : comment.trim();
        if (required && (text == null || text.isEmpty())) throw new IllegalArgumentException("驳回时审批意见不能为空");
        if (task.submittedBy().equals(reviewer)) throw new IllegalStateException("系统审核员不得审批自己提交的任务");
        if (taskMapper.updateReview(id, status, reviewer, text) != 1) throw new IllegalStateException("仅待审核任务可审批");
        return taskMapper.findById(id);
    }
    private SystemTask requireTask(Long id) { SystemTask task = taskMapper.findById(id); if (task == null) throw new IllegalArgumentException("系统任务不存在：" + id); return task; }
    private void requirePermission(Long userId, SystemTaskType type, boolean review) {
        if (type == null) throw new IllegalArgumentException("任务类型不能为空");
        String permission = switch (type) {
            case GLOBAL_FEATURE_TOGGLE -> review ? "FEATURE_TOGGLE_REVIEW" : "FEATURE_TOGGLE_MANAGE";
            case ORGANIZATION_DISABLE, ORGANIZATION_MOVE, ORGANIZATION_DELETE -> review ? "ORG_NODE_CHANGE_REVIEW" : "ORG_NODE_CHANGE_SUBMIT";
            case CACHE_CLEAR -> review ? "CACHE_REVIEW" : "CACHE_MANAGE";
            case INTERFACE_SERVICE_CHANGE -> review ? "INTERFACE_SERVICE_REVIEW" : "INTERFACE_SERVICE_MANAGE";
            case SENSITIVE_DATA_EXPORT -> review ? "EXPORT_SENSITIVE_REVIEW" : "EXPORT_SENSITIVE_SUBMIT";
            default -> null;
        };
        if (permission == null || !decisions.isAllowed(userId, com.lingdong.learning.permission.domain.PermissionClient.WEB, permission))
            throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("当前账号无系统任务操作权限");
    }
    private String required(String value, String field, int max) { if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + "不能为空"); String text=value.trim(); if(text.length()>max) throw new IllegalArgumentException(field + "长度不能超过"+max+"个字符"); return text; }
}
