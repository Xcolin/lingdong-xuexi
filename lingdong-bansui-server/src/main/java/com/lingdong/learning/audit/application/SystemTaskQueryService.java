package com.lingdong.learning.audit.application;

import com.lingdong.learning.audit.infrastructure.persistence.SystemTaskMapper;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import java.util.List;
import java.util.ArrayList;
import com.lingdong.learning.feature.application.FeatureAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 仅查询系统审计元数据；审批与执行仍由原领域服务负责。 */
@Service
public class SystemTaskQueryService {
    private final com.lingdong.learning.datascope.application.OrganizationDataScopeService scopes;
    private final SystemTaskMapper mapper;
    private final PermissionDecisionService permissions;
    private final UserRoleMapper roles;
    private final FeatureAccessService features;
    public SystemTaskQueryService(SystemTaskMapper mapper, PermissionDecisionService permissions, UserRoleMapper roles,FeatureAccessService features, com.lingdong.learning.datascope.application.OrganizationDataScopeService scopes) {
        this.scopes = scopes;
        this.mapper=mapper;this.permissions=permissions;this.roles=roles;this.features=features;
    }
    @Transactional(readOnly=true)
    public Page findPage(AuthenticatedUser user,SystemTaskStatus status,int page,int pageSize) {
        boolean auditor=requireAccess(user);
        if(page<1||page>1_000_000||pageSize<1||pageSize>100)throw new IllegalArgumentException("系统任务分页参数不合法");
        var reviewableTypes=visibleTypes(user.userId(),true);
        var types=queryTypes(user.userId(),reviewableTypes);
        if(types.isEmpty())return new Page(List.of(),page,pageSize,0);
        return new Page(mapper.findVisiblePage(user.userId(),auditor,types,reviewableTypes,status,pageSize,(page-1)*pageSize),
                page,pageSize,mapper.countVisible(user.userId(),auditor,types,reviewableTypes,status));
    }
    @Transactional(readOnly=true)
    public SystemTask findDetails(AuthenticatedUser user,Long id) {
        boolean auditor=requireAccess(user);
        var reviewableTypes=visibleTypes(user.userId(),true);
        var types=queryTypes(user.userId(),reviewableTypes);
        SystemTask task=id==null||types.isEmpty()?null:mapper.findVisibleById(id,user.userId(),auditor,types,reviewableTypes);
        if(task==null)throw new ResourceNotFoundException("系统任务不存在或不可访问");
        return task;
    }
    public VisibilityScope resolveScope(long userId) {
        requireIdentity(userId);
        var reviewableTypes = visibleTypes(userId, true);
        return new VisibilityScope(!reviewableTypes.isEmpty(), queryTypes(userId, reviewableTypes), reviewableTypes);
    }
    public record VisibilityScope(boolean auditor, List<SystemTaskType> types, List<SystemTaskType> reviewableTypes) {
        public VisibilityScope(boolean auditor, List<SystemTaskType> types) {
            this(auditor, types, auditor ? types : List.of());
        }
        public VisibilityScope { types = List.copyOf(types); reviewableTypes = List.copyOf(reviewableTypes); }
    }
    private boolean requireAccess(AuthenticatedUser user) {
        if(user==null||user.clientType()!=AuthClientType.WEB)throw denied();
        return requireIdentity(user.userId());
    }
    private boolean requireIdentity(long userId) {
        if(!permissions.isAllowed(userId,PermissionClient.WEB,"SYSTEM_TASK_READ"))throw denied();
        return allowed(userId,"FEATURE_TOGGLE_REVIEW") || allowed(userId,"ORG_NODE_CHANGE_REVIEW")
                || allowed(userId,"CACHE_REVIEW") || allowed(userId,"INTERFACE_SERVICE_REVIEW")
                || allowed(userId,"EXPORT_SENSITIVE_REVIEW");
    }
    private SystemOperationAccessDeniedException denied(){return new SystemOperationAccessDeniedException("当前身份无系统任务读取权限");}
    /** 汇总权限不能绕过领域权限和开关；未落地处理器不列入查询。 */
    private List<SystemTaskType> visibleTypes(Long userId,boolean auditor) {
        var types=new ArrayList<SystemTaskType>();
        if(allowed(userId,"FEATURE_TOGGLE_READ")&&(!auditor||allowed(userId,"FEATURE_TOGGLE_REVIEW")))types.add(SystemTaskType.GLOBAL_FEATURE_TOGGLE);
        if(scopes.resolve(userId).allOrganizations()&&features.isEnabled("ORGANIZATION_MANAGEMENT",null)&&allowed(userId,auditor?"ORG_NODE_CHANGE_REVIEW":"ORG_NODE_CHANGE_SUBMIT"))
            types.addAll(List.of(SystemTaskType.ORGANIZATION_DISABLE,SystemTaskType.ORGANIZATION_MOVE,SystemTaskType.ORGANIZATION_DELETE));
        if(allowed(userId,"CACHE_READ")&&features.isEnabled("CACHE_MANAGEMENT",null)&&allowed(userId,auditor?"CACHE_REVIEW":"CACHE_READ"))types.add(SystemTaskType.CACHE_CLEAR);
        if(allowed(userId,"INTERFACE_SERVICE_READ")&&features.isEnabled("INTERFACE_SERVICE_MANAGEMENT",null)&&allowed(userId,auditor?"INTERFACE_SERVICE_REVIEW":"INTERFACE_SERVICE_READ"))types.add(SystemTaskType.INTERFACE_SERVICE_CHANGE);
        if(features.isEnabled("DATA_EXPORT",null)&&features.isEnabled("IMPORT_EXPORT_TEMPLATE_MANAGEMENT",null)&&features.isEnabled("ATTACHMENT_SERVICE",null)
                &&allowed(userId,auditor?"EXPORT_SENSITIVE_REVIEW":"EXPORT_JOB_READ")
                &&(auditor||allowed(userId,"EXPORT_SENSITIVE_SUBMIT")&&allowed(userId,"IAM_AUDIT_READ")))types.add(SystemTaskType.SENSITIVE_DATA_EXPORT);
        return types;
    }
    private List<SystemTaskType> queryTypes(Long userId, List<SystemTaskType> reviewableTypes) {
        var result = new java.util.LinkedHashSet<>(visibleTypes(userId, false));
        result.addAll(reviewableTypes);
        return List.copyOf(result);
    }
    private boolean allowed(Long userId,String code){return permissions.isAllowed(userId,PermissionClient.WEB,code);}
    public record Page(List<SystemTask> items,int page,int pageSize,long total) { }
}
