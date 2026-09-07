package com.lingdong.learning.organization.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationChangeAudit;
import com.lingdong.learning.organization.domain.OrganizationEffectiveStatus;
import com.lingdong.learning.organization.domain.OrganizationStatus;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationChangeAuditMapper;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 统一判断组织业务可用性，并维护组织子树的有效状态。 */
@Service
public class OrganizationOperationalStatusService {
    private final OrganizationMapper organizationMapper;
    private final OrganizationChangeAuditMapper organizationChangeAuditMapper;
    private final IdGenerator idGenerator;

    public OrganizationOperationalStatusService(
            OrganizationMapper organizationMapper,
            OrganizationChangeAuditMapper organizationChangeAuditMapper,
            IdGenerator idGenerator
    ) {
        this.organizationMapper = organizationMapper;
        this.organizationChangeAuditMapper = organizationChangeAuditMapper;
        this.idGenerator = idGenerator;
    }

    /** 要求组织自身及全部祖先均为启用状态。 */
    @Transactional(readOnly = true)
    public Organization requireOperational(Long organizationId) {
        Objects.requireNonNull(organizationId, "组织ID不能为空");
        Organization organization = requireOrganization(organizationId);
        if (!isOperational(organization)) {
            throw new OrganizationNotOperationalException(organizationId);
        }
        return organization;
    }

    /** 供已完成组织加载或数据范围校验的业务服务复用统一有效性判定。 */
    public static boolean isOperational(Organization organization) {
        return organization != null
                && organization.status() == OrganizationStatus.ENABLED
                && organization.effectiveStatus() == OrganizationEffectiveStatus.ENABLED;
    }

    /** 启用目标节点，并按每个节点自身状态重新计算整棵子树。 */
    @Transactional
    public Organization enableOrganization(Long operatorUserId, Long organizationId, Integer expectedVersion) {
        Objects.requireNonNull(operatorUserId, "操作人不能为空");
        Objects.requireNonNull(organizationId, "组织ID不能为空");
        Objects.requireNonNull(expectedVersion, "组织版本号不能为空");
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("组织版本号必须大于0");
        }

        Organization current = organizationMapper.findByIdForUpdate(organizationId);
        if (current == null) {
            throw new ResourceNotFoundException("组织不存在：" + organizationId);
        }
        OrganizationEffectiveStatus parentEffectiveStatus = resolveParentEffectiveStatus(current.parentId());
        OrganizationEffectiveStatus targetEffectiveStatus = parentEffectiveStatus;
        int affectedRows = organizationMapper.updateStatusAndEffectiveStatus(
                current.id(), OrganizationStatus.ENABLED, targetEffectiveStatus, expectedVersion);
        if (affectedRows != 1) {
            throw new OrganizationVersionConflictException();
        }

        recomputeDescendants(current.path(), current.id(), targetEffectiveStatus);
        Organization enabled = organizationMapper.findById(current.id());
        organizationChangeAuditMapper.insert(OrganizationChangeAudit.enable(
                idGenerator.nextId(), current, enabled, operatorUserId, LocalDateTime.now()));
        return enabled;
    }

    private void recomputeDescendants(
            String rootPath,
            Long rootId,
            OrganizationEffectiveStatus rootEffectiveStatus
    ) {
        List<Organization> subtree = organizationMapper.findSubtreeByPathForUpdate(rootPath);
        Map<Long, OrganizationEffectiveStatus> effectiveStatusById = new HashMap<>();
        effectiveStatusById.put(rootId, rootEffectiveStatus);
        for (Organization organization : subtree) {
            if (organization.id().equals(rootId)) {
                continue;
            }
            OrganizationEffectiveStatus parentEffectiveStatus = effectiveStatusById.get(organization.parentId());
            if (parentEffectiveStatus == null) {
                throw new IllegalStateException("组织树路径与父子关系不一致：" + organization.id());
            }
            OrganizationEffectiveStatus calculated = calculateEffectiveStatus(
                    organization.status(), parentEffectiveStatus);
            if (organization.effectiveStatus() != calculated) {
                int affectedRows = organizationMapper.updateEffectiveStatus(
                        organization.id(), calculated, organization.versionNo());
                if (affectedRows != 1) {
                    throw new OrganizationVersionConflictException();
                }
            }
            effectiveStatusById.put(organization.id(), calculated);
        }
    }

    private OrganizationEffectiveStatus resolveParentEffectiveStatus(Long parentId) {
        if (parentId == null) {
            return OrganizationEffectiveStatus.ENABLED;
        }
        return requireOrganization(parentId).effectiveStatus();
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

    private Organization requireOrganization(Long organizationId) {
        Organization organization = organizationMapper.findById(organizationId);
        if (organization == null) {
            throw new ResourceNotFoundException("组织不存在：" + organizationId);
        }
        return organization;
    }
}
