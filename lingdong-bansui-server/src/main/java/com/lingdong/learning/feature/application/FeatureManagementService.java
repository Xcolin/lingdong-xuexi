package com.lingdong.learning.feature.application;

import com.lingdong.learning.audit.application.SystemTaskApplicationService;
import com.lingdong.learning.audit.application.SystemTaskStatus;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.domain.FeatureStatus;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;



import java.util.List;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureManagementMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 全局开关管理门面，实时校验 Web 权限和提交版本。 */
@Service
public class FeatureManagementService {
    private final FeatureManagementMapper mapper;
    private final FeatureToggleMapper toggles;
    private final FeatureToggleChangeService changes;
    private final SystemTaskApplicationService tasks;
    private final PermissionDecisionService permissions;
    private final UserRoleMapper roles;
    public FeatureManagementService(FeatureManagementMapper mapper,FeatureToggleMapper toggles,FeatureToggleChangeService changes,
            SystemTaskApplicationService tasks,PermissionDecisionService permissions,UserRoleMapper roles) {
        this.mapper=mapper;this.toggles=toggles;this.changes=changes;this.tasks=tasks;this.permissions=permissions;this.roles=roles;
    }
    public List<ToggleView> listToggles(AuthenticatedUser user) {
        access(user,"FEATURE_TOGGLE_READ");
        return mapper.findToggles();
    }
    @Transactional(readOnly=true)
    public Page listChanges(AuthenticatedUser user,SystemTaskStatus status,int page,int pageSize,boolean queue) {
        boolean auditor=access(user,"FEATURE_TOGGLE_READ");
        if(queue) { access(user,"FEATURE_TOGGLE_REVIEW"); auditor=true; status=SystemTaskStatus.PENDING_REVIEW; }
        if(page<1||page>1_000_000||pageSize<1||pageSize>100)throw new IllegalArgumentException("分页参数不合法");
        return new Page(mapper.findPage(user.userId(),auditor,status,pageSize,(page-1)*pageSize),page,pageSize,mapper.count(user.userId(),auditor,status));
    }
    @Transactional
    public ChangeView submit(AuthenticatedUser user,String code,FeatureStatus target,Long expectedVersion,String title,String description,boolean confirmed) {
        access(user,"FEATURE_TOGGLE_READ"); access(user,"FEATURE_TOGGLE_MANAGE");
        if(!confirmed||target==null||expectedVersion==null||expectedVersion<0)throw new IllegalArgumentException("请确认变更及当前版本");
        var toggle=toggles.findGlobalForUpdate(code);
        if(toggle==null)throw new IllegalArgumentException("全局开关不存在");
        if(!toggle.versionNo().equals(expectedVersion))throw new FeatureToggleConflictException("开关状态已变化，请重新载入并提交");
        if(toggle.status()==target)throw new IllegalArgumentException("目标状态与当前状态相同");
        var change=changes.createDraft(new CreateGlobalFeatureToggleChangeCommand(user.userId(),code,target,title,description));
        changes.submit(change.taskId(),user.userId());
        return details(change.taskId());
    }
    @Transactional
    public ChangeView review(AuthenticatedUser user,Long taskId,String comment,boolean approve) {
        access(user,"FEATURE_TOGGLE_READ");
        access(user,"FEATURE_TOGGLE_REVIEW");
        details(taskId); // 先限定领域，不能审核其他类型系统任务。
        if(comment!=null&&comment.trim().length()>500)throw new IllegalArgumentException("审批意见最长500字");
        if(approve)changes.approveAndApply(taskId,user.userId(),comment);
        else tasks.reject(taskId,user.userId(),comment);
        return details(taskId);
    }
    private ChangeView details(Long taskId) {
        var result=mapper.findByTaskId(taskId);
        if(result==null)throw new ResourceNotFoundException("功能开关任务不存在");
        return result;
    }
    private boolean access(AuthenticatedUser user,String permission) {
        if(user==null||user.clientType()!=AuthClientType.WEB||!permissions.isAllowed(user.userId(),PermissionClient.WEB,permission))throw denied();
        return permissions.isAllowed(user.userId(),PermissionClient.WEB,"FEATURE_TOGGLE_REVIEW");
    }
    private SystemOperationAccessDeniedException denied(){return new SystemOperationAccessDeniedException("无权执行功能开关管理操作");}
    public record ToggleView(String id,String featureCode,String featureName,String status,String versionNo,String description,boolean enableAllowed){}
    public record ChangeView(String id,String taskId,String featureCode,String featureName,String beforeStatus,String targetStatus,String currentStatus,String baseVersion,String currentVersion,String taskStatus,String title,String description,String submittedBy,String submittedByUserId,String submittedAt,String reviewedBy,String reviewedAt,String reviewComment,String createdAt,boolean enableAllowed){}
    public record Page(List<ChangeView> items,int page,int pageSize,long total){}
}
