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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 全局开关管理门面，实时校验 Web 权限、固定职责和提交版本。 */
@Service
public class FeatureManagementService {
    private final JdbcTemplate jdbc;
    private final FeatureToggleMapper toggles;
    private final FeatureToggleChangeService changes;
    private final SystemTaskApplicationService tasks;
    private final PermissionDecisionService permissions;
    private final UserRoleMapper roles;
    private static final String JOIN = " FROM sys_feature_toggle_change c JOIN sys_system_task t ON t.id=c.task_id JOIN sys_feature_toggle f ON f.feature_code=c.feature_code AND f.scope_key='GLOBAL' ";
    private static final String COLUMNS = "c.id,c.task_id,c.feature_code,c.before_status,c.target_status,c.base_version,f.feature_name,f.status current_status,f.version_no,t.status task_status,t.task_title,t.task_description,t.submitted_by,t.submitted_at,t.reviewed_by,t.reviewed_at,t.review_comment,c.created_at";
    public FeatureManagementService(JdbcTemplate jdbc,FeatureToggleMapper toggles,FeatureToggleChangeService changes,
            SystemTaskApplicationService tasks,PermissionDecisionService permissions,UserRoleMapper roles) {
        this.jdbc=jdbc;this.toggles=toggles;this.changes=changes;this.tasks=tasks;this.permissions=permissions;this.roles=roles;
    }
    public List<ToggleView> listToggles(AuthenticatedUser user) {
        access(user,"FEATURE_TOGGLE_READ");
        return jdbc.query("SELECT id,feature_code,feature_name,status,version_no,description FROM sys_feature_toggle WHERE scope_key='GLOBAL' ORDER BY id",
                (r,n)->new ToggleView(r.getString("id"),r.getString("feature_code"),r.getString("feature_name"),r.getString("status"),r.getString("version_no"),r.getString("description"),enableAllowed(r.getString("feature_code"))));
    }
    @Transactional(readOnly=true)
    public Page listChanges(AuthenticatedUser user,SystemTaskStatus status,int page,int pageSize,boolean queue) {
        boolean auditor=access(user,"FEATURE_TOGGLE_READ");
        if(queue) { access(user,"FEATURE_TOGGLE_REVIEW"); if(!auditor)throw denied(); status=SystemTaskStatus.PENDING_REVIEW; }
        if(page<1||page>1_000_000||pageSize<1||pageSize>100)throw new IllegalArgumentException("分页参数不合法");
        String where=" WHERE t.task_type='GLOBAL_FEATURE_TOGGLE'";
        List<Object> args=new ArrayList<>();
        if(auditor) where+=" AND t.status<>'DRAFT' AND t.submitted_at IS NOT NULL";
        else { where+=" AND t.submitted_by=?";args.add(user.userId()); }
        if(status!=null) {where+=" AND t.status=?";args.add(status.name());}
        Long total=jdbc.queryForObject("SELECT COUNT(*)"+JOIN+where,Long.class,args.toArray());
        args.add(pageSize);args.add((page-1)*pageSize);
        return new Page(jdbc.query("SELECT "+COLUMNS+JOIN+where+" ORDER BY c.created_at DESC,c.id DESC LIMIT ? OFFSET ?",this::map,args.toArray()),page,pageSize,total==null?0:total);
    }
    @Transactional
    public ChangeView submit(AuthenticatedUser user,String code,FeatureStatus target,Long expectedVersion,String title,String description,boolean confirmed) {
        boolean auditor=access(user,"FEATURE_TOGGLE_MANAGE");
        if(auditor||!roles.hasRoleCode(user.userId(),"SYS_ADMIN"))throw denied();
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
        if(!access(user,"FEATURE_TOGGLE_REVIEW"))throw denied();
        details(taskId); // 先限定领域，不能审核其他类型系统任务。
        if(comment!=null&&comment.trim().length()>500)throw new IllegalArgumentException("审批意见最长500字");
        if(approve)changes.approveAndApply(taskId,user.userId(),comment);
        else tasks.reject(taskId,user.userId(),comment);
        return details(taskId);
    }
    private ChangeView details(Long taskId) {
        var result=jdbc.query("SELECT "+COLUMNS+JOIN+" WHERE t.id=? AND t.task_type='GLOBAL_FEATURE_TOGGLE'",this::map,taskId);
        if(result.isEmpty())throw new ResourceNotFoundException("功能开关任务不存在");
        return result.get(0);
    }
    private boolean access(AuthenticatedUser user,String permission) {
        if(user==null||user.clientType()!=AuthClientType.WEB||!permissions.isAllowed(user.userId(),PermissionClient.WEB,permission))throw denied();
        boolean auditor=roles.hasRoleCode(user.userId(),"SYS_AUDITOR");
        if(!auditor&&!roles.hasRoleCode(user.userId(),"SYS_ADMIN"))throw denied();
        return auditor;
    }
    private SystemOperationAccessDeniedException denied(){return new SystemOperationAccessDeniedException("无权执行功能开关管理操作");}
    private boolean enableAllowed(String code){return !"GEO_ATTENDANCE".equals(code)&&!"STUDENT_LOCATION_TRACK".equals(code);}
    private String time(ResultSet r,String name)throws SQLException {var value=r.getTimestamp(name);return value==null?null:value.toLocalDateTime().toString();}
    private ChangeView map(ResultSet r,int row)throws SQLException {
        return new ChangeView(r.getString("id"),r.getString("task_id"),r.getString("feature_code"),r.getString("feature_name"),r.getString("before_status"),r.getString("target_status"),r.getString("current_status"),r.getString("base_version"),r.getString("version_no"),r.getString("task_status"),r.getString("task_title"),r.getString("task_description"),r.getString("submitted_by"),time(r,"submitted_at"),r.getString("reviewed_by"),time(r,"reviewed_at"),r.getString("review_comment"),time(r,"created_at"),enableAllowed(r.getString("feature_code")));
    }
    public record ToggleView(String id,String featureCode,String featureName,String status,String versionNo,String description,boolean enableAllowed){}
    public record ChangeView(String id,String taskId,String featureCode,String featureName,String beforeStatus,String targetStatus,String currentStatus,String baseVersion,String currentVersion,String taskStatus,String title,String description,String submittedBy,String submittedAt,String reviewedBy,String reviewedAt,String reviewComment,String createdAt,boolean enableAllowed){}
    public record Page(List<ChangeView> items,int page,int pageSize,long total){}
}
