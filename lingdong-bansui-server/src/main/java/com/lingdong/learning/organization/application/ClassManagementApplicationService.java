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
    private static final String GRADE_TYPE = "GRADE";

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

    public static final java.util.Set<String> LOOKUP_ACTIONS=java.util.Set.of(
        "IAM_USER_CREATE","IAM_USER_UPDATE","CLASS_READ","CLASS_CREATE","CLASS_UPDATE","CLASS_STATUS_CHANGE",
        "STUDENT_CREATE","STUDENT_CLASS_ASSIGN","STUDENT_IMPORT_EXECUTE","TEACHER_READ","TEACHER_CREATE","TEACHER_UPDATE",
        "TEACHER_STATUS_CHANGE","TEACHER_PASSWORD_RESET","TEACHER_BATCH_MANAGE","TEACHER_CLASS_ASSIGN",
        "LEARNING_TASK_CREATE","ATTENDANCE_READ","ATTENDANCE_RECORD");
    /** Returns scoped choices plus ancestor metadata only; contextual ancestors confer no access to sibling objects. */
    public List<Organization> lookup(AuthenticatedUser user,String permission,String kind,Long schoolId,Long gradeId,Long campusId){
        if(user==null||user.clientType()!=AuthClientType.WEB||!LOOKUP_ACTIONS.contains(permission))throw new SystemOperationAccessDeniedException("不支持该选择器动作");
        if(!operationPermissions.isAllowed(user.userId(),PermissionClient.WEB,permission))throw new SystemOperationAccessDeniedException("缺少对应对象选择动作权限");
        if(!java.util.Set.of("SCHOOL","GRADE","CAMPUS","CLASS").contains(kind))throw new IllegalArgumentException("选择器对象类型无效");
        java.util.Map<Long,Organization> choices=new java.util.LinkedHashMap<>();
        for(Organization scoped:dataScopeService.findAccessibleOrganizations(user.userId())){
            if(!OrganizationOperationalStatusService.isOperational(scoped))continue;
            Organization choice="SCHOOL".equals(kind)?OrganizationHierarchy.schoolOf(scoped,organizationMapper):
                kind.equals(scoped.typeCode())?scoped:!"CLASS".equals(kind)?OrganizationHierarchy.ancestor(scoped,kind,organizationMapper):null;
            if(choice==null||!OrganizationOperationalStatusService.isOperational(choice))continue;
            Organization school=OrganizationHierarchy.schoolOf(choice,organizationMapper);
            Organization grade=OrganizationHierarchy.ancestor(choice,"GRADE",organizationMapper);
            Organization campus=OrganizationHierarchy.ancestor(choice,"CAMPUS",organizationMapper);
            if(schoolId!=null&&(school==null||!schoolId.equals(school.id())))continue;
            if(gradeId!=null&&(grade==null||!gradeId.equals(grade.id())))continue;
            if(campusId!=null&&(campus==null||!campusId.equals(campus.id())))continue;
            choices.put(choice.id(),choice);
        }
        return choices.values().stream().sorted(OrganizationTreeOrder.comparator(organizationMapper.findAll())).toList();
    }

    /** 返回当前机构管理员可创建班级的有效学校。 */
    public List<Organization> listManageableSchools(AuthenticatedUser currentUser) {
        requireAccess(currentUser, "CLASS_READ");
        if (currentUser.clientType() != AuthClientType.WEB)
            return selectedMiniappOrganizations(currentUser,organizationMapper.findOperationalSchoolsByOrganizationAdministrator(currentUser.userId()),true);
        return dataScopeService.findAccessibleOrganizations(currentUser.userId()).stream()
                .filter(org -> OrganizationHierarchy.isInstitutionRoot(org))
                .filter(OrganizationOperationalStatusService::isOperational).sorted(OrganizationTreeOrder.comparator(organizationMapper.findAll())).toList();
    }

    /** 返回授权范围内全部班级，包含自身或上级已停用的历史班级。 */
    public List<Organization> listClasses(AuthenticatedUser currentUser) {
        requireAccess(currentUser, "CLASS_READ");
        if (currentUser.clientType() != AuthClientType.WEB)
            return selectedMiniappOrganizations(currentUser,organizationMapper.findClassesByOrganizationAdministrator(currentUser.userId()),false);
        return dataScopeService.findAccessibleOrganizations(currentUser.userId()).stream()
                .filter(org -> CLASS_TYPE.equals(org.typeCode())).sorted(OrganizationTreeOrder.comparator(organizationMapper.findAll())).toList();
    }

    private List<Organization> selectedMiniappOrganizations(AuthenticatedUser user,List<Organization> candidates,boolean ancestorMetadata) {
        var selected=com.lingdong.learning.common.security.SelectedOrganizationContext.organization();
        if(selected==null)return candidates;
        var scope=dataScopeService.resolve(user.userId());
        if(!scope.allOrganizations()&&scope.rootPaths().isEmpty())return List.of();
        return candidates.stream().filter(org->scope.allows(org)
                || ancestorMetadata&&OrganizationHierarchy.isInstitutionRoot(org)&&selected.path().startsWith(org.path())).toList();
    }

    /** 直接在年级下创建班级，编码由服务端雪花标识生成。 */
    @Transactional
    public Organization createClass(AuthenticatedUser currentUser, CreateClassCommand command) {
        requireAccess(currentUser, "CLASS_CREATE");
        Objects.requireNonNull(command, "新增班级请求不能为空");
        Long gradeId = requiredId(command.gradeOrganizationId(), "年级组织标识");
        Organization grade = requireManageableOrganization(currentUser.userId(), gradeId);
        Organization school = OrganizationHierarchy.schoolOf(grade, organizationMapper);
        if (!GRADE_TYPE.equals(grade.typeCode()) || !OrganizationOperationalStatusService.isOperational(grade)
                || school == null || !OrganizationOperationalStatusService.isOperational(school)) {
            throw notFound();
        }

        if (command.schoolOrganizationId() != null && !command.schoolOrganizationId().equals(school.id())) {
            throw new IllegalArgumentException("班级所属年级不在所选学校内");
        }
        String name = requiredName(command.name());
        Integer sortOrder = normalizeSortOrder(command.sortOrder());
        String parentScopeKey = "PARENT:" + grade.id();
        if (organizationMapper.existsByParentScopeAndName(parentScopeKey, name)) {
            throw new DuplicateOrganizationNameException(name);
        }

        Long id = idGenerator.nextId();
        String code = "CLS_" + id;
        String path = grade.path() + code + "/";
        if (path.length() > 1024) {
            throw new IllegalArgumentException("组织层级过深，组织路径不能超过1024个字符");
        }
        Organization organization = Organization.create(
                id, grade.id(), parentScopeKey, code, name, CLASS_TYPE,
                path, sortOrder, null);
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


    public List<Organization> listGrades(AuthenticatedUser user, Long schoolId) {
        requireAccess(user, "CLASS_READ");
        return dataScopeService.findAccessibleOrganizations(user.userId()).stream()
                .filter(org -> GRADE_TYPE.equals(org.typeCode()))
                .filter(org -> schoolId == null || OrganizationHierarchy.schoolOf(org,organizationMapper) != null && schoolId.equals(OrganizationHierarchy.schoolOf(org,organizationMapper).id())).sorted(OrganizationTreeOrder.comparator(organizationMapper.findAll())).toList();
    }

    public List<Organization> listClasses(AuthenticatedUser user, Long schoolId, Long gradeId) {
        return listClasses(user).stream().filter(org -> {
            Organization school = OrganizationHierarchy.schoolOf(org, organizationMapper);
            Organization grade = OrganizationHierarchy.ancestor(org, GRADE_TYPE, organizationMapper);
            return (schoolId == null || (school != null && schoolId.equals(school.id())))
                    && (gradeId == null || (grade != null && gradeId.equals(grade.id())));
        }).toList();
    }

    /** Ancestor metadata is returned only for an already scoped organization. */
    public Organization ancestor(Organization organization, String type) {
        return OrganizationHierarchy.ancestor(organization, type, organizationMapper);
    }

    @Transactional
    public Organization createGrade(AuthenticatedUser user, Long schoolId, String name, Integer sortOrder) {
        requireAccess(user, "CLASS_CREATE");
        Organization school = requireManageableOrganization(user.userId(), requiredId(schoolId, "学校组织标识"));
        if (!OrganizationHierarchy.isInstitutionRoot(school) || !OrganizationOperationalStatusService.isOperational(school)) throw notFound();
        Organization created = organizationApplicationService.createOrganization(new CreateOrganizationCommand(
                "GRD_" + idGenerator.nextId(), requiredName(name), GRADE_TYPE, school.id(), normalizeSortOrder(sortOrder)));
        auditMapper.insert(OrganizationChangeAudit.create(idGenerator.nextId(),created,user.userId(),LocalDateTime.now()));
        return created;
    }

    @Transactional
    public Organization updateGrade(AuthenticatedUser user, UpdateClassCommand command) {
        requireAccess(user, "CLASS_UPDATE");
        Organization grade = requireManageableOrganization(user.userId(), requiredId(command.classOrganizationId(), "年级组织标识"));
        if (!GRADE_TYPE.equals(grade.typeCode())) throw notFound();
        return organizationApplicationService.updateOrganization(user.userId(),new UpdateOrganizationCommand(
                grade.id(),requiredName(command.name()),normalizeSortOrder(command.sortOrder()),command.versionNo()));
    }

    @Transactional
    public Organization enableGrade(AuthenticatedUser user, Long gradeId, Integer version) {
        requireAccess(user, "CLASS_STATUS_CHANGE");
        Organization grade = requireManageableOrganization(user.userId(), requiredId(gradeId,"年级组织标识"));
        if (!GRADE_TYPE.equals(grade.typeCode())) throw notFound();
        return organizationApplicationService.enableOrganization(user.userId(),gradeId,version);
    }

    @Transactional
    public Organization disableGrade(AuthenticatedUser user,Long gradeId,Integer version) { return disableBranch(user,gradeId,version,GRADE_TYPE); }
    private Organization disableBranch(AuthenticatedUser user,Long gradeId,Integer version,String type) {
        requireAccess(user, "CLASS_STATUS_CHANGE");
        if (version == null || version < 1) throw new IllegalArgumentException("年级版本号必须大于0");
        Organization grade = organizationMapper.findByIdForUpdate(requiredId(gradeId,"年级组织标识"));
        if (grade == null || !type.equals(grade.typeCode()) || !dataScopeService.canAccess(user.userId(),gradeId)) throw notFound();
        if (!grade.versionNo().equals(version)) throw new OrganizationVersionConflictException();
        if (grade.status() == OrganizationStatus.DISABLED) return grade;
        List<Organization> subtree = organizationMapper.findSubtreeByPathForUpdate(grade.path());
        if (organizationMapper.updateStatusAndEffectiveStatus(gradeId,OrganizationStatus.DISABLED,OrganizationEffectiveStatus.DISABLED,version)!=1)
            throw new OrganizationVersionConflictException();
        organizationMapper.disableDescendantEffectiveStatus(gradeId,grade.path());
        subtree.stream().filter(org -> CLASS_TYPE.equals(org.typeCode())).forEach(org ->
                classTaskInvalidationService.invalidateUnfinishedAssignments(org.id(),user.userId()));
        Organization after=organizationMapper.findById(gradeId);
        auditMapper.insert(OrganizationChangeAudit.disable(idGenerator.nextId(),grade,after,user.userId(),LocalDateTime.now()));
        return after;
    }

    /** Assign a legacy school-direct class to a grade in the same school, preserving its identity. */
    @Transactional
    public Organization assignGrade(AuthenticatedUser user,Long classId,Long gradeId,Integer version) {
        requireAccess(user,"CLASS_UPDATE");
        if(version==null || version<1) throw new IllegalArgumentException("班级版本号必须大于0");
        Organization current=organizationMapper.findByIdForUpdate(requiredId(classId,"班级组织标识"));
        Organization grade=organizationMapper.findByIdForUpdate(requiredId(gradeId,"年级组织标识"));
        if(current==null || grade==null || !CLASS_TYPE.equals(current.typeCode()) || !GRADE_TYPE.equals(grade.typeCode())
                || !dataScopeService.canAccess(user.userId(),classId) || !dataScopeService.canAccess(user.userId(),gradeId)) throw notFound();
        if(!current.versionNo().equals(version)) throw new OrganizationVersionConflictException();
        Organization oldParent=organizationMapper.findById(current.parentId());
        Organization school=OrganizationHierarchy.schoolOf(grade,organizationMapper);
        if(oldParent==null || !SCHOOL_TYPE.equals(oldParent.typeCode())) throw new IllegalArgumentException("仅待归属年级的存量班级可归并");
        if(school==null || !oldParent.id().equals(school.id())) throw new IllegalArgumentException("班级只能归并到同校年级");
        if(!OrganizationOperationalStatusService.isOperational(grade)) throw new OrganizationNotOperationalException(grade.id());
        String scope="PARENT:"+grade.id();
        if(organizationMapper.existsByParentScopeAndNameExcludingId(scope,current.name(),classId)) throw new DuplicateOrganizationNameException(current.name());
        String path=grade.path()+current.code()+"/";
        List<Organization> subtree=organizationMapper.findSubtreeByPathForUpdate(current.path());
        if(path.length()>1024 || subtree.stream().anyMatch(org -> path.length()+org.path().length()-current.path().length()>1024))
            throw new IllegalArgumentException("移动后组织路径不能超过1024个字符");
        if(organizationMapper.updateLocationAndPath(classId,grade.id(),scope,path,current.effectiveStatus(),version)!=1) throw new OrganizationVersionConflictException();
        for(Organization child:subtree) if(!child.id().equals(classId)) {
            if(organizationMapper.updatePathAndEffectiveStatus(child.id(),path+child.path().substring(current.path().length()),child.effectiveStatus(),child.versionNo())!=1)
                throw new OrganizationVersionConflictException();
        }
        Organization after=organizationMapper.findById(classId);
        auditMapper.insert(OrganizationChangeAudit.directMove(idGenerator.nextId(),current,after,user.userId(),LocalDateTime.now()));
        return after;
    }


    public List<Organization> listCampuses(AuthenticatedUser user,Long schoolId) {
      requireAccess(user,"CLASS_READ"); return dataScopeService.findAccessibleOrganizations(user.userId()).stream().filter(o -> "CAMPUS".equals(o.typeCode())).filter(o -> schoolId==null || schoolId.equals(o.parentId())).sorted(OrganizationTreeOrder.comparator(organizationMapper.findAll())).toList();
    }
    public List<Organization> listGrades(AuthenticatedUser user,Long schoolId,Long campusId) { return listGrades(user,schoolId).stream().filter(o -> campusId==null || campusId.equals(o.parentId())).toList(); }
    public List<Organization> listClasses(AuthenticatedUser user,Long schoolId,Long gradeId,Long campusId) { return listClasses(user,schoolId,gradeId).stream().filter(o -> campusId==null || ancestor(o,"CAMPUS")!=null && campusId.equals(ancestor(o,"CAMPUS").id())).toList(); }
    @Transactional
    public Organization createGrade(AuthenticatedUser user,Long schoolId,Long campusId,String name,Integer sortOrder) {
      if(campusId==null) return createGrade(user,schoolId,name,sortOrder);
      return createEducationNode(user,campusId,schoolId,"GRADE",name,sortOrder);
    }
    @Transactional
    public Organization createCampus(AuthenticatedUser user,Long schoolId,String name,Integer sortOrder) { return createEducationNode(user,schoolId,schoolId,"CAMPUS",name,sortOrder); }
    private Organization createEducationNode(AuthenticatedUser user,Long parentId,Long schoolId,String type,String name,Integer sortOrder) {
      requireAccess(user,"CLASS_CREATE"); Organization parent=requireManageableOrganization(user.userId(),requiredId(parentId,"父组织标识"));
      Organization school=ancestor(parent,"SCHOOL");
      if(school==null || !Objects.equals(schoolId,school.id()) || !OrganizationOperationalStatusService.isOperational(parent)) throw notFound();
      OrganizationHierarchy.requireParent(type,parent,organizationMapper);
      Organization created=organizationApplicationService.createOrganization(new CreateOrganizationCommand(("CAMPUS".equals(type)?"CMP_":"GRD_")+idGenerator.nextId(),requiredName(name),type,parent.id(),normalizeSortOrder(sortOrder)));
      auditMapper.insert(OrganizationChangeAudit.create(idGenerator.nextId(),created,user.userId(),LocalDateTime.now())); return created;
    }
    @Transactional
    public Organization updateCampus(AuthenticatedUser user,UpdateClassCommand command) {
      requireAccess(user,"CLASS_UPDATE"); Organization campus=requireManageableOrganization(user.userId(),requiredId(command.classOrganizationId(),"校区标识"));
      if(!"CAMPUS".equals(campus.typeCode())) throw notFound();
      return organizationApplicationService.updateOrganization(user.userId(),new UpdateOrganizationCommand(campus.id(),requiredName(command.name()),normalizeSortOrder(command.sortOrder()),command.versionNo()));
    }
    @Transactional
    public Organization enableCampus(AuthenticatedUser user,Long campusId,Integer version) {
      requireAccess(user,"CLASS_STATUS_CHANGE"); Organization campus=requireManageableOrganization(user.userId(),requiredId(campusId,"校区标识"));
      if(!"CAMPUS".equals(campus.typeCode())) throw notFound(); return organizationApplicationService.enableOrganization(user.userId(),campusId,version);
    }
    @Transactional
    public Organization disableCampus(AuthenticatedUser user,Long campusId,Integer version) { return disableBranch(user,campusId,version,"CAMPUS"); }

    public String namePath(Organization node) {
      java.util.LinkedList<String> names=new java.util.LinkedList<>(); java.util.Set<Long> seen=new java.util.HashSet<>();
      while(node!=null && seen.add(node.id())) { names.addFirst(node.name()); node=node.parentId()==null?null:organizationMapper.findById(node.parentId()); }
      return String.join("·",names);
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
