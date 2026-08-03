package com.lingdong.learning.organization.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationStatus;
import com.lingdong.learning.organization.domain.OrganizationType;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationTypeMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Application service that protects organization-tree invariants before nodes are persisted.
 */
@Service
public class OrganizationApplicationService {
    private static final String ROOT_SCOPE_KEY = "ROOT";
    private static final Pattern ORGANIZATION_CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");
    private static final Pattern ORGANIZATION_TYPE_CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{2,31}");

    private final OrganizationMapper organizationMapper;
    private final OrganizationTypeMapper organizationTypeMapper;
    private final IdGenerator idGenerator;

    public OrganizationApplicationService(
            OrganizationMapper organizationMapper,
            OrganizationTypeMapper organizationTypeMapper,
            IdGenerator idGenerator
    ) {
        this.organizationMapper = organizationMapper;
        this.organizationTypeMapper = organizationTypeMapper;
        this.idGenerator = idGenerator;
    }

    /**
     * Creates a reusable classification before it can be selected by organization tree nodes.
     */
    @Transactional
    public OrganizationType createOrganizationType(CreateOrganizationTypeCommand command) {
        Objects.requireNonNull(command, "创建组织类型请求不能为空");

        String code = requiredText(command.code(), "组织类型编码", 32);
        String name = requiredText(command.name(), "组织类型名称", 32);
        Integer sortOrder = normalizeSortOrder(command.sortOrder());
        validateTypeCode(code);

        if (organizationTypeMapper.existsByCode(code) || organizationTypeMapper.existsByName(name)) {
            throw new DuplicateOrganizationTypeException(code);
        }

        OrganizationType organizationType = OrganizationType.custom(idGenerator.nextId(), code, name, sortOrder);
        try {
            organizationTypeMapper.insert(organizationType);
            return organizationTypeMapper.findByCode(code);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateOrganizationTypeException(code);
        }
    }

    /**
     * Creates one node while preserving type validity, parent availability, and sibling-name uniqueness.
     * The unique database constraint remains the final guard for concurrent administrators.
     */
    @Transactional
    public Organization createOrganization(CreateOrganizationCommand command) {
        Objects.requireNonNull(command, "创建组织请求不能为空");

        String code = requiredText(command.code(), "组织编码", 64);
        String typeCode = requiredText(command.typeCode(), "组织类型编码", 32);
        validateOrganizationCode(code);
        validateTypeCode(typeCode);

        OrganizationType organizationType = organizationTypeMapper.findByCode(typeCode);
        if (organizationType == null) {
            throw new ResourceNotFoundException("组织类型不存在：" + typeCode);
        }
        if (organizationType.status() != OrganizationStatus.ENABLED) {
            throw new IllegalStateException("组织类型已停用：" + typeCode);
        }

        String name = requiredText(command.name(), "组织名称", maximumNameLength(typeCode));
        Integer sortOrder = normalizeSortOrder(command.sortOrder());
        ParentContext parentContext = resolveParent(command.parentId());

        if (organizationMapper.existsByCode(code)) {
            throw new DuplicateOrganizationCodeException(code);
        }
        if (organizationMapper.existsByParentScopeAndName(parentContext.scopeKey(), name)) {
            throw new DuplicateOrganizationNameException(name);
        }

        String path = parentContext.path() + code + "/";
        if (path.length() > 1024) {
            throw new IllegalArgumentException("组织层级过深，组织路径不能超过1024个字符");
        }

        Organization organization = Organization.create(
                idGenerator.nextId(),
                command.parentId(),
                parentContext.scopeKey(),
                code,
                name,
                typeCode,
                path,
                sortOrder
        );
        try {
            organizationMapper.insert(organization);
            return organizationMapper.findByCode(code);
        } catch (DuplicateKeyException exception) {
            if (organizationMapper.existsByCode(code)) {
                throw new DuplicateOrganizationCodeException(code);
            }
            throw new DuplicateOrganizationNameException(name);
        }
    }

    private ParentContext resolveParent(Long parentId) {
        if (parentId == null) {
            return new ParentContext(ROOT_SCOPE_KEY, "/");
        }

        Organization parent = organizationMapper.findById(parentId);
        if (parent == null) {
            throw new ResourceNotFoundException("父级组织不存在：" + parentId);
        }
        if (parent.status() != OrganizationStatus.ENABLED) {
            throw new IllegalStateException("父级组织已停用，不能新增下级组织：" + parentId);
        }
        return new ParentContext("PARENT:" + parent.id(), parent.path());
    }

    private void validateOrganizationCode(String code) {
        if (!ORGANIZATION_CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("组织编码仅允许3至64位大写字母、数字和下划线，且必须以字母开头");
        }
    }

    private void validateTypeCode(String code) {
        if (!ORGANIZATION_TYPE_CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("组织类型编码仅允许3至32位大写字母、数字和下划线，且必须以字母开头");
        }
    }

    private int maximumNameLength(String typeCode) {
        return switch (typeCode) {
            case "REGION", "CLASS" -> 50;
            default -> 100;
        };
    }

    private Integer normalizeSortOrder(Integer sortOrder) {
        if (sortOrder == null) {
            return 0;
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("排序值不能小于0");
        }
        return sortOrder;
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private record ParentContext(String scopeKey, String path) {
    }
}
