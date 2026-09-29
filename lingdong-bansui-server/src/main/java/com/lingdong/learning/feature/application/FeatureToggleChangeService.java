package com.lingdong.learning.feature.application;

import com.lingdong.learning.auth.infrastructure.persistence.DeviceSessionMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.audit.application.CreateSystemTaskCommand;
import com.lingdong.learning.audit.application.ImpactScope;
import com.lingdong.learning.audit.application.SystemTask;
import com.lingdong.learning.audit.application.SystemTaskApplicationService;
import com.lingdong.learning.audit.application.SystemTaskType;
import com.lingdong.learning.feature.domain.FeatureToggle;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleChangeMapper;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 仅在关联高风险系统任务获批后应用全局功能开关，并同步必要的安全失效动作。 */
@Service
public class FeatureToggleChangeService {
    private static final String ORGANIZATION_MINIAPP_AUTH = "ORGANIZATION_MINIAPP_AUTH";

    private final FeatureToggleMapper toggleMapper;
    private final FeatureToggleChangeMapper changeMapper;
    private final SystemTaskApplicationService taskService;
    private final IdGenerator idGenerator;
    private final DeviceSessionMapper deviceSessionMapper;

    public FeatureToggleChangeService(
            FeatureToggleMapper toggleMapper,
            FeatureToggleChangeMapper changeMapper,
            SystemTaskApplicationService taskService,
            IdGenerator idGenerator,
            DeviceSessionMapper deviceSessionMapper
    ) {
        this.toggleMapper = toggleMapper;
        this.changeMapper = changeMapper;
        this.taskService = taskService;
        this.idGenerator = idGenerator;
        this.deviceSessionMapper = deviceSessionMapper;
    }

    @Transactional
    public FeatureToggleChange createDraft(CreateGlobalFeatureToggleChangeCommand command) {
        FeatureToggle toggle=toggleMapper.findGlobal(command.featureCode());
        if(toggle==null) throw new IllegalArgumentException("未配置的全局功能："+command.featureCode());
        if(command.targetStatus()==null) throw new IllegalArgumentException("目标功能状态不能为空");
        requirePublishableTarget(command.featureCode(), command.targetStatus());
        toggle=toggleMapper.findGlobalForUpdate(command.featureCode());
        SystemTask task=taskService.createDraft(new CreateSystemTaskCommand(command.submitterId(), SystemTaskType.GLOBAL_FEATURE_TOGGLE, command.title(), command.description(), ImpactScope.GLOBAL));
        FeatureToggleChange change=new FeatureToggleChange(idGenerator.nextId(), task.id(), command.featureCode(), command.targetStatus(), toggle.status(), toggle.versionNo());
        changeMapper.insert(change); return change;
    }
    @Transactional public void submit(Long taskId, Long submitterId) { taskService.submit(taskId, submitterId); }
    @Transactional
    public SystemTask approveAndApply(Long taskId, Long auditorId, String comment) {
        FeatureToggleChange change = changeMapper.findByTaskId(taskId);
        if (change == null) throw new IllegalArgumentException("功能开关变更不存在");
        requirePublishableTarget(change.featureCode(), change.targetStatus());
        if(change.beforeStatus()==null || change.baseVersion()==null) throw new FeatureToggleConflictException("历史申请缺少版本快照，请驳回并重新提交");
        taskService.approve(taskId, auditorId, comment);
        if (toggleMapper.compareAndSetGlobalStatus(change.featureCode(), change.targetStatus(), change.baseVersion()) != 1) {
            throw new FeatureToggleConflictException("开关状态已变化，请重新载入并提交");
        }
        if (ORGANIZATION_MINIAPP_AUTH.equals(change.featureCode())
                && change.targetStatus() == com.lingdong.learning.feature.domain.FeatureStatus.DISABLED) {
            deviceSessionMapper.revokeAllActiveOrganizationMiniappSessions(LocalDateTime.now());
        }
        return taskService.markEffective(taskId);
    }

    /** 当前发布范围不具备定位启用条件，历史待审申请也不能越过此边界。 */
    private void requirePublishableTarget(String code, com.lingdong.learning.feature.domain.FeatureStatus target) {
        if (target == com.lingdong.learning.feature.domain.FeatureStatus.ENABLED
                && ("GEO_ATTENDANCE".equals(code) || "STUDENT_LOCATION_TRACK".equals(code))) {
            throw new IllegalArgumentException("当前发布范围内定位能力保持关闭");
        }
    }
}
