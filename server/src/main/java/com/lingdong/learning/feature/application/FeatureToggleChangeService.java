package com.lingdong.learning.feature.application;

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

/** Applies a global toggle only after the linked high-risk system task has been approved. */
@Service
public class FeatureToggleChangeService {
    private final FeatureToggleMapper toggleMapper;
    private final FeatureToggleChangeMapper changeMapper;
    private final SystemTaskApplicationService taskService;
    public FeatureToggleChangeService(FeatureToggleMapper toggleMapper, FeatureToggleChangeMapper changeMapper, SystemTaskApplicationService taskService) { this.toggleMapper=toggleMapper; this.changeMapper=changeMapper; this.taskService=taskService; }

    @Transactional
    public FeatureToggleChange createDraft(CreateGlobalFeatureToggleChangeCommand command) {
        FeatureToggle toggle=toggleMapper.findGlobal(command.featureCode());
        if(toggle==null) throw new IllegalArgumentException("未配置的全局功能："+command.featureCode());
        if(command.targetStatus()==null) throw new IllegalArgumentException("目标功能状态不能为空");
        SystemTask task=taskService.createDraft(new CreateSystemTaskCommand(command.submitterId(), SystemTaskType.GLOBAL_FEATURE_TOGGLE, command.title(), command.description(), ImpactScope.GLOBAL));
        FeatureToggleChange change=new FeatureToggleChange(task.id(), command.featureCode(), command.targetStatus());
        changeMapper.insert(change); return change;
    }
    @Transactional public void submit(Long taskId, Long submitterId) { taskService.submit(taskId, submitterId); }
    @Transactional
    public SystemTask approveAndApply(Long taskId, Long auditorId, String comment) {
        taskService.approve(taskId, auditorId, comment);
        FeatureToggleChange change=changeMapper.findByTaskId(taskId);
        if(change==null || toggleMapper.updateGlobalStatus(change.featureCode(), change.targetStatus())!=1) throw new IllegalStateException("功能开关变更执行失败");
        return taskService.markEffective(taskId);
    }
}
