package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.TeacherClassRelation;
import com.lingdong.learning.learningtask.domain.TeacherClassStatus;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskOptionMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.StudentOptionRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.TeacherClassMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TeacherOptionRow;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/** 为任务表单提供按来源裁剪、按最小字段返回的候选项。 */
@Service
public class LearningTaskOptionService {
    private static final String FEATURE_CODE = "LEARNING_TASK_MANAGEMENT";

    @org.springframework.beans.factory.annotation.Autowired
    private com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper userOrganizationMapper;
    private final LearningTaskOptionMapper optionMapper;
    private final TeacherClassMapper teacherClassMapper;
    private final OrganizationMapper organizationMapper;
    private final OrganizationDataScopeService organizationDataScopeService;
    private final FeatureAccessService featureAccessService;
    private final LearningTaskScopeService scopes;
    private final com.lingdong.learning.organization.application.OrganizationNamePathService organizationNamePathService;

    public LearningTaskOptionService(
            LearningTaskOptionMapper optionMapper,
            TeacherClassMapper teacherClassMapper,
            OrganizationMapper organizationMapper,
            OrganizationDataScopeService organizationDataScopeService,
            FeatureAccessService featureAccessService,
            LearningTaskScopeService scopes,
            com.lingdong.learning.organization.application.OrganizationNamePathService organizationNamePathService
    ) {
        this.optionMapper = optionMapper;
        this.teacherClassMapper = teacherClassMapper;
        this.organizationMapper = organizationMapper;
        this.organizationDataScopeService = organizationDataScopeService;
        this.featureAccessService = featureAccessService;
        this.scopes = scopes;
        this.organizationNamePathService = organizationNamePathService;
    }

    public List<OrganizationOption> organizations(
            AuthenticatedUser currentUser,
            LearningTaskSourceType sourceType,
            String organizationType
    ) {
        scopes.requireWebPermission(currentUser, "LEARNING_TASK_CREATE");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireSourceRole(currentUser, sourceType);
        String normalizedType = optionalText(organizationType, "组织类型", 32);
        if (sourceType == LearningTaskSourceType.ORGANIZATION && isWeb(currentUser)) {
            return organizationDataScopeService.findAccessibleOrganizations(currentUser.userId()).stream()
                    .filter(OrganizationOperationalStatusService::isOperational)
                    .filter(node -> SelectedTaskInstitutionScope.allows(organizationMapper,node))
                    .filter(node -> normalizedType == null || normalizedType.equals(node.typeCode()) || "SCHOOL".equals(normalizedType) && com.lingdong.learning.organization.application.OrganizationHierarchy.isInstitutionRoot(node))
                    .sorted(com.lingdong.learning.organization.application.OrganizationTreeOrder.comparator(organizationMapper.findAll()))
                    .map(node -> {
                java.util.Map<Long, String> namePaths = organizationNamePathService.namePathsById();
                return new OrganizationOption(node.id(), namePaths.getOrDefault(node.id(), node.name()), node.typeCode(), node.parentId(), node.path());
            }).toList();
        }
        List<OrganizationOption> options = switch (sourceType) {
            case FAMILY -> List.of();
            case ORGANIZATION -> optionMapper.findOrganizationOptionsForAdministrator(
                    currentUser.userId(), normalizedType);
            case TEACHER -> optionMapper.findOrganizationOptionsForTeacher(
                    currentUser.userId(), normalizedType);
        };
        var all=organizationMapper.findAll(); var byId=new java.util.HashMap<Long,Organization>(); all.forEach(o -> byId.put(o.id(),o));
        var order=com.lingdong.learning.organization.application.OrganizationTreeOrder.comparator(all);
        return options.stream().filter(option -> SelectedTaskInstitutionScope.allows(organizationMapper,byId.get(option.id()))).sorted((a,b) -> byId.get(a.id())==null || byId.get(b.id())==null ? a.id().compareTo(b.id()) : order.compare(byId.get(a.id()),byId.get(b.id()))).toList();
    }

    public List<StudentOption> students(
            AuthenticatedUser currentUser,
            LearningTaskSourceType sourceType,
            Long organizationId,
            String keyword
    ) {
        scopes.requireWebPermission(currentUser, "LEARNING_TASK_CREATE");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireSourceRole(currentUser, sourceType);
        String normalizedKeyword = optionalText(keyword, "学生关键字", 64);
        List<StudentOptionRow> rows = switch (sourceType) {
            case FAMILY -> {
                if (organizationId != null) {
                    throw new IllegalArgumentException("家庭任务学生候选不接受组织筛选");
                }
                yield optionMapper.findFamilyStudentOptions(currentUser.userId(), normalizedKeyword);
            }
            case ORGANIZATION -> {
                validateOrganizationScope(currentUser, organizationId, false);
                if (isWeb(currentUser)) {
                    var ids = accessibleIds(currentUser);
                    yield ids.isEmpty() ? List.<StudentOptionRow>of() : optionMapper.findScopedStudentOptions(ids, organizationId, normalizedKeyword);
                }
                yield optionMapper.findOrganizationStudentOptions(
                        currentUser.userId(), organizationId, normalizedKeyword);
            }
            case TEACHER -> {
                validateTeacherClass(currentUser, organizationId);
                yield optionMapper.findTeacherStudentOptions(
                        currentUser.userId(), organizationId, normalizedKeyword);
            }
        };
        var all=organizationMapper.findAll();
        var byId=new java.util.HashMap<Long,Organization>(); all.forEach(o -> byId.put(o.id(),o));
        var order=com.lingdong.learning.organization.application.OrganizationTreeOrder.comparator(all);
        var namePaths=organizationNamePathService.namePathsById();
        return rows.stream().sorted((a,b) -> {
            Organization first=byId.get(a.currentClassId()),second=byId.get(b.currentClassId());
            int c=first==null ? (second==null?0:1) : second==null?-1:order.compare(first,second);
            if(c==0)c=a.studentName().compareTo(b.studentName());
            return c==0?a.id().compareTo(b.id()):c;
        }).map(row -> new StudentOption(row.id(),row.studentName(),maskStudentAccount(row.studentAccount()),row.currentClassId(),namePaths.getOrDefault(row.currentClassId(),row.currentClassName()),row.relationshipRole())).toList();
    }

    public List<TeacherOption> teachers(
            AuthenticatedUser currentUser, Long classId, String keyword
    ) {
        scopes.requireAnyPermission(currentUser, java.util.List.of("TEACHER_CLASS_ASSIGN","LEARNING_TASK_CREATE"));
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireRole(currentUser, "ORG_ADMIN", "仅机构管理员可查询教师候选项");
        validateOrganizationScope(currentUser, classId, true);
        List<TeacherOptionRow> rows = isWeb(currentUser)
                ? (accessibleIds(currentUser).isEmpty() ? List.of() : optionMapper.findScopedTeacherOptions(
                    accessibleIds(currentUser), classId, optionalText(keyword, "教师关键字", 64)))
                : optionMapper.findTeacherOptionsForAdministrator(
                currentUser.userId(), classId, optionalText(keyword, "教师关键字", 64));

        Map<Long, TeacherOptionAccumulator> grouped = new LinkedHashMap<>();
        for (TeacherOptionRow row : rows) {
            TeacherOptionAccumulator accumulator = grouped.computeIfAbsent(
                    row.userId(), ignored -> new TeacherOptionAccumulator(row.displayName()));
            if (row.classOrganizationId() != null) {
                accumulator.classIds().add(row.classOrganizationId());
            }
        }
        List<TeacherOption> result = new ArrayList<>(grouped.size());
        var all = organizationMapper.findAll();
        var byId = new java.util.HashMap<Long,Organization>(); all.forEach(org -> byId.put(org.id(),org));
        var order = com.lingdong.learning.organization.application.OrganizationTreeOrder.comparator(all);
        var scope = organizationDataScopeService.resolve(currentUser.userId());
        var names = organizationNamePathService.namePathsById();
        Organization targetSchool = classId == null ? null : com.lingdong.learning.organization.application.OrganizationHierarchy.schoolOf(byId.get(classId),organizationMapper);
        Map<Long,List<Organization>> teacherOrganizations = new java.util.HashMap<>();
        grouped.forEach((userId, value) -> {
            List<Organization> memberships = userOrganizationMapper.findOrganizationIds(userId).stream()
                    .map(byId::get).filter(Objects::nonNull).filter(OrganizationOperationalStatusService::isOperational)
                    .filter(scope::allows)
                    .filter(org -> java.util.Set.of("SCHOOL","TRAINING_INSTITUTION","CAMPUS","GRADE","CLASS").contains(org.typeCode()))
                    .filter(org -> targetSchool == null || org.path().startsWith(targetSchool.path()))
                    .sorted(order).toList();
            teacherOrganizations.put(userId,memberships);
            List<Long> classIds=value.classIds().stream().sorted((a,b) -> byId.containsKey(a)&&byId.containsKey(b)?order.compare(byId.get(a),byId.get(b)):a.compareTo(b)).toList();
            result.add(new TeacherOption(userId,value.displayName(),classIds,memberships.stream().map(org -> names.getOrDefault(org.id(),org.name())).toList()));
        });
        result.sort((a,b) -> {
            List<Organization> first=teacherOrganizations.get(a.userId()),second=teacherOrganizations.get(b.userId());
            int c=first.isEmpty()?(second.isEmpty()?0:1):second.isEmpty()?-1:order.compare(first.get(0),second.get(0));
            if(c==0)c=a.displayName().compareTo(b.displayName());
            return c==0?a.userId().compareTo(b.userId()):c;
        });
        return List.copyOf(result);
    }

    private StudentOption toStudentOption(StudentOptionRow row) {
        return new StudentOption(
                row.id(), row.studentName(), maskStudentAccount(row.studentAccount()),
                row.currentClassId(), row.currentClassName(), row.relationshipRole());
    }

    private String maskStudentAccount(String account) {
        if (account == null || account.isBlank()) {
            return null;
        }
        if (account.length() <= 4) {
            return "****";
        }
        return account.substring(0, 2) + "****" + account.substring(account.length() - 2);
    }

    private void validateOrganizationScope(
            AuthenticatedUser currentUser, Long organizationId, boolean requireClass
    ) {
        if (organizationId == null) {
            return;
        }
        Organization organization = organizationMapper.findById(organizationId);
        if (!SelectedTaskInstitutionScope.allows(organizationMapper,organization) || organization == null
                || !organizationDataScopeService.canAccess(currentUser.userId(), organizationId)) {
            throw notFound();
        }
        if (!OrganizationOperationalStatusService.isOperational(organization)) {
            throw new IllegalStateException("筛选组织已停用");
        }
        if (requireClass && !"CLASS".equals(organization.typeCode())) {
            throw new IllegalArgumentException("筛选组织不是班级");
        }
    }

    private void validateTeacherClass(AuthenticatedUser currentUser, Long organizationId) {
        if (organizationId == null) {
            return;
        }
        if (!SelectedTaskInstitutionScope.allows(organizationMapper,organizationMapper.findById(organizationId))) throw notFound();
        TeacherClassRelation relation = teacherClassMapper.findByTeacherAndClass(
                currentUser.userId(), organizationId);
        if (relation == null || relation.status() != TeacherClassStatus.ACTIVE) {
            throw notFound();
        }
    }

    private void requireSourceRole(AuthenticatedUser currentUser, LearningTaskSourceType sourceType) {
        Objects.requireNonNull(sourceType, "任务来源不能为空");
        String roleCode = switch (sourceType) {
            case FAMILY -> "PARENT";
            case ORGANIZATION -> "ORG_ADMIN";
            case TEACHER -> "TEACHER";
        };
        requireRole(currentUser, roleCode, "当前角色不能使用该任务来源");
    }

    private void requireRole(AuthenticatedUser currentUser, String identity, String message) {
        boolean valid = switch(identity) {
            case "PARENT" -> scopes.isParent(currentUser);
            case "TEACHER" -> scopes.isTeacher(currentUser);
            case "ORG_ADMIN" -> scopes.isOrganizationOperator(currentUser);
            default -> false;
        };
        if (!valid) throw new SystemOperationAccessDeniedException(message);
    }

    private boolean isWeb(AuthenticatedUser user) { return user != null && user.clientType() == com.lingdong.learning.auth.domain.AuthClientType.WEB; }
    private List<Long> accessibleIds(AuthenticatedUser user) {
        return organizationDataScopeService.findAccessibleOrganizations(user.userId()).stream()
                .filter(OrganizationOperationalStatusService::isOperational).map(Organization::id).toList();
    }

    private String optionalText(String value, String fieldName, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 " + maxLength + " 个字符");
        }
        return "组织类型".equals(fieldName) ? normalized.toUpperCase(Locale.ROOT) : normalized;
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("候选资源不存在或不可访问");
    }

    private record TeacherOptionAccumulator(String displayName, TreeSet<Long> classIds) {
        private TeacherOptionAccumulator(String displayName) {
            this(displayName, new TreeSet<>());
        }
    }
}
