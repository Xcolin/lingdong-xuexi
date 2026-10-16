package com.lingdong.learning.organization.application;

import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.iam.audit.application.*;
import com.lingdong.learning.organization.domain.*;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationTypeMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 自定义组织类型的编辑、状态和删除事务边界。 */
@Service
public class OrganizationTypeLifecycleApplicationService {
    private final com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper userRoles;
    private final OrganizationTypeMapper types;
    private final PermissionDecisionService decisions;
    private final OrganizationDataScopeService scopes;
    private final FeatureAccessService features;
    private final IamChangeAuditService audit;

    public OrganizationTypeLifecycleApplicationService(OrganizationTypeMapper types, PermissionDecisionService decisions,
            OrganizationDataScopeService scopes, FeatureAccessService features, IamChangeAuditService audit, com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper userRoles) {
        this.userRoles=userRoles;
        this.types=types; this.decisions=decisions; this.scopes=scopes; this.features=features; this.audit=audit;
    }

    @Transactional
    public OrganizationType update(Long operatorId, Long id, String name, Integer sortOrder) {
        requirePermission(operatorId, "ORG_TYPE_UPDATE");
        OrganizationType current = types.lockById(id);
        if (current == null) throw new IllegalArgumentException("组织类型不存在");
        if (current.builtIn() && !userRoles.findEnabledRoleCodesByUserId(operatorId).contains("SYS_ADMIN"))
            throw new SystemOperationAccessDeniedException("内置组织类型仅系统管理员可编辑");
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("组织类型名称不能为空");
        String normalized = name.trim();
        if (normalized.length() > 32) throw new IllegalArgumentException("组织类型名称不能超过32个字符");
        if (sortOrder == null || sortOrder < 0) throw new IllegalArgumentException("排序值不能为空或小于0");
        if (types.existsByNameExceptId(normalized,id)) throw new IllegalArgumentException("组织类型名称已存在");
        try { types.updateDetails(id,normalized,sortOrder); }
        catch (DuplicateKeyException exception) { throw new IllegalArgumentException("组织类型名称已存在"); }
        audit.record(IamChangeAuditEventType.ORGANIZATION_TYPE_UPDATE,operatorId,IamChangeTargetType.ORGANIZATION_TYPE,
                id,null,null,current.name()+":"+current.sortOrder(),normalized+":"+sortOrder);
        return types.findById(id);
    }

    @Transactional
    public OrganizationType changeStatus(Long operatorId, Long id, OrganizationStatus status) {
        requirePermission(operatorId,"ORG_TYPE_STATUS_CHANGE");
        OrganizationType current=requireCustom(id);
        if (status == null) throw new IllegalArgumentException("组织类型状态不能为空");
        if (current.status()!=status) {
            types.updateStatus(id,status);
            audit.record(IamChangeAuditEventType.ORGANIZATION_TYPE_STATUS_CHANGE,operatorId,IamChangeTargetType.ORGANIZATION_TYPE,
                    id,null,null,current.status().name(),status.name());
        }
        return types.findById(id);
    }

    @Transactional
    public void delete(Long operatorId,Long id) {
        requirePermission(operatorId,"ORG_TYPE_DELETE");
        OrganizationType current=requireCustom(id);
        if (types.hasOrganizations(current.code())) throw new IllegalArgumentException("组织类型已被组织引用，请先调整组织类型后再删除");
        types.deleteById(id);
        audit.record(IamChangeAuditEventType.ORGANIZATION_TYPE_DELETE,operatorId,IamChangeTargetType.ORGANIZATION_TYPE,
                id,null,null,current.code(),null);
    }

    private OrganizationType requireCustom(Long id) {
        OrganizationType current=types.lockById(id);
        if (current==null) throw new IllegalArgumentException("组织类型不存在");
        if (current.builtIn()) throw new IllegalArgumentException("内置组织类型不允许编辑、删除或启停");
        return current;
    }

    private void requirePermission(Long operatorId,String code) {
        if (operatorId==null || !decisions.isAllowed(operatorId,PermissionClient.WEB,code))
            throw new SystemOperationAccessDeniedException("当前账号无此组织类型管理权限");
        features.requireEnabled("ORGANIZATION_MANAGEMENT",null);
        if (!scopes.resolve(operatorId).allOrganizations()) throw new SystemOperationAccessDeniedException("组织类型管理需要全局数据范围");
    }
}
