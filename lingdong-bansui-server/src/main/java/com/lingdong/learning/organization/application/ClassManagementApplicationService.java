package com.lingdong.learning.organization.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.application.ClassTaskInvalidationService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationChangeAudit;
import com.lingdong.learning.organization.domain.OrganizationEffectiveStatus;
import com.lingdong.learning.organization.domain.OrganizationStatus;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationChangeAuditMapper;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import org.springframework.dao.DuplicateKeyException;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.auth.domain.AuthClientType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 在机构管理员授权学校范围内维护班级基础信息。 */
@Service
public class ClassManagementApplicationService {
    private final PermissionDecisionService operationPermissions;
    private static final String FEATURE_CODE = "CLASS_MANAGEMENT";
    private static final String ORGANIZATION_ADMIN_ROLE = "ORG_ADMIN";
    private static final String SCHOOL_TYPE = "SCHOOL";
    private static final String CLASS_TYPE = "CLASS";

    private final OrganizationMapper organizationMapper;
    private final OrganizationChangeAuditMapper auditMapper;
    private final OrganizationDataScopeService dataScopeService;
    private final OrganizationApplicationService organizationApplicationService;
    private final FeatureAccessService featureAccessService;
    private final ClassTaskInvalidationService classTaskInvalidationService;
    private final IdGenerator idGenerator;

    public ClassManagementApplicationService(
            OrganizationMapper organizationMapper,
            OrganizationChangeAuditMapper auditMapper,
            OrganizationDataScopeService dataScopeService,
            OrganizationApplicationService organizationApplicationService,
            FeatureAccessService featureAccessService,
            ClassTaskInvalidationService classTaskInvalidationService,
            IdGenerator idGenerator, PermissionDecisionService operationPermissions
    ) {
        this.operationPermissions = operationPermissions;
        this.organizationMapper = organizationMapper;
        this.auditMapper = auditMapper;
        this.dataScopeService = dataScopeService;
        this.organizationApplicationService = organizationApplicationService;
        this.featureAccessService = featureAccessService;
        this.classTaskInvalidationService = classTaskInvalidationService;
        this.idGenerator = idGenerator;
    }

    /** 返回当前机构管理员可创建班级的有效学校。 */
    public List<Organization> listManageableSchools(AuthenticatedUser currentUser) {
        requireAccess(currentUser, "CLASS_READ");
        if (currentUser.clientType() != AuthClientType.WEB)
            return organizationMapper.findOperationalSchoolsByOrganizationAdministrator(currentUser.userId());
        return dataScopeService.findAccessibleOrganizations(currentUser.userId()).stream()
                .filter(org -> SCHOOL_TYPE.equals(org.typeCode()))
                .filter(OrganizationOperationalStatusService::isOperational).toList();
    }

    /** 返回授权范围内全部班级，包含自身或上级已停用的历史班级。 */
    public List<Organization> listClasses(AuthenticatedUser currentUser) {
        requireAccess(currentUser, "CLASS_READ");
        if (currentUser.clientType() != AuthClientType.WEB)
            return organizationMapper.findClassesByOrganizationAdministrator(currentUser.userId());
        return dataScopeService.findAccessibleOrganizations(currentUser.userId()).stream()
                .filter(org -> CLASS_TYPE.equals(org.typeCode())).toList();
    }

    /** 直接在学校下创建班级，编码由服务端雪花标识生成。 */
    @Transactional
    public Organization createClass(AuthenticatedUser currentUser, CreateClassCommand command) {
        requireAccess(currentUser, "CLASS_CREATE");
        Objects.requireNonNull(command, "新增班级请求不能为空");
        Long schoolId = requiredId(command.schoolOrganizationId(), "学校组织标识");
        Organization school = requireManageableOrganization(currentUser.userId(), schoolId);
        if (!SCHOOL_TYPE.equals(school.typeCode()) || !OrganizationOperationalStatusService.isOperational(school)) {
            throw notFound();
        }

        String name = requiredName(command.name());
        Integer sortOrder = normalizeSortOrder(command.sortOrder());
        String parentScopeKey = "PARENT:" + school.id();
        if (organizationMapper.existsByParentScopeAndName(parentScopeKey, name)) {
            throw new DuplicateOrganizationNameException(name);
        }

        Long id = idGenerator.nextId();
        String code = "CLS_" + id;
        Organization organization = Organization.create(
                id, school.id(), parentScopeKey, code, name, CLASS_TYPE,
                school.path() + code + "/", sortOrder, null);
        try {
            organizationMapper.insert(organization);
            Organization created = organizationMapper.findById(id);
            auditMapper.insert(OrganizationChangeAudit.create(
                    idGenerator.nextId(), created, currentUser.userId(), LocalDateTime.now()));
            return created;
        } catch (DuplicateKeyException exception) {
            if (organizationMapper.existsByParentScopeAndName(parentScopeKey, name)) {
                throw new DuplicateOrganizationNameException(name);
            }
            throw exception;
        }
    }

    /** 编辑班级名称和排序，所属学校和组织编码保持不变。 */
    @Transactional
    public Organization updateClass(AuthenticatedUser currentUser, UpdateClassCommand command) {
        requireAccess(currentUser, "CLASS_UPDATE");
        Objects.requireNonNull(command, "编辑班级请求不能为空");
        Long classId = requiredId(command.classOrganizationId(), "班级组织标识");
        Organization current = requireManageableOrganization(currentUser.userId(), classId);
        if (!CLASS_TYPE.equals(current.typeCode())) {
            throw notFound();
        }
        return organizationApplicationService.updateOrganization(
                currentUser.userId(), new UpdateOrganizationCommand(
                        classId, requiredName(command.name()),
                        normalizeSortOrder(command.sortOrder()), command.versionNo()));
    }

    /** 停用班级并在同一事务使班内未完成机构和教师任务失效。 */
    @Transactional
    public Organization disableClass(
            AuthenticatedUser currentUser,
            Long classOrganizationId,
            Integer expectedVersion
    ) {
        requireAccess(currentUser, "CLASS_STATUS_CHANGE");
        Long classId = requiredId(classOrganizationId, "班级组织标识");
        if (expectedVersion == null || expectedVersion < 1) {
            throw new IllegalArgumentException("班级版本号必须大于0");
        }
        Organization current = organizationMapper.findByIdForUpdate(classId);
        if (current == null || !CLASS_TYPE.equals(current.typeCode())
                || !dataScopeService.canAccess(currentUser.userId(), classId)) {
            throw notFound();
        }
        if (!current.versionNo().equals(expectedVersion)) {
            throw new OrganizationVersionConflictException();
        }
        if (current.status() == OrganizationStatus.DISABLED) {
            return current;
        }
        if (organizationMapper.updateStatusAndEffectiveStatus(
                classId, OrganizationStatus.DISABLED,
                OrganizationEffectiveStatus.DISABLED, expectedVersion) != 1) {
            throw new OrganizationVersionConflictException();
        }
        organizationMapper.disableDescendantEffectiveStatus(classId, current.path());
        classTaskInvalidationService.invalidateUnfinishedAssignments(classId, currentUser.userId());
        Organization disabled = organizationMapper.findById(classId);
        auditMapper.insert(OrganizationChangeAudit.disable(
                idGenerator.nextId(), current, disabled, currentUser.userId(), LocalDateTime.now()));
        return disabled;
    }

    /** 重新启用班级，但不恢复停用期间已经失效的任务。 */
    @Transactional
    public Organization enableClass(
            AuthenticatedUser currentUser,
            Long classOrganizationId,
            Integer expectedVersion
    ) {
        requireAccess(currentUser, "CLASS_STATUS_CHANGE");
        Long classId = requiredId(classOrganizationId, "班级组织标识");
        Organization current = requireManageableOrganization(currentUser.userId(), classId);
        if (!CLASS_TYPE.equals(current.typeCode())) {
            throw notFound();
        }
        return organizationApplicationService.enableOrganization(
                currentUser.userId(), classId, expectedVersion);
    }

    private void requireAccess(AuthenticatedUser currentUser, String permission) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        if (currentUser == null || currentUser.clientType() == null
                || !operationPermissions.isAllowed(currentUser.userId(), PermissionClient.valueOf(currentUser.clientType().name()), permission)
                || (currentUser.clientType() != AuthClientType.WEB && !currentUser.roleCodes().contains(ORGANIZATION_ADMIN_ROLE))) {
            throw new SystemOperationAccessDeniedException("当前账号缺少班级操作权限");
        }
    }

    private Organization requireManageableOrganization(Long userId, Long organizationId) {
        Organization organization = organizationMapper.findById(organizationId);
        if (organization == null || !dataScopeService.canAccess(userId, organizationId)) {
            throw notFound();
        }
        return organization;
    }

    private Long requiredId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private String requiredName(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("班级名称不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > 50) {
            throw new IllegalArgumentException("班级名称长度不能超过50个字符");
        }
        return normalized;
    }

    private Integer normalizeSortOrder(Integer value) {
        if (value == null) {
            return 0;
        }
        if (value < 0) {
            throw new IllegalArgumentException("排序值不能小于0");
        }
        return value;
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("学校或班级不存在或不可访问");
    }
}
