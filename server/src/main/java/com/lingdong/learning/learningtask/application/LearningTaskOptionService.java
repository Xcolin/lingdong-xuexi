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
import com.lingdong.learning.organization.domain.OrganizationStatus;
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

    private final LearningTaskOptionMapper optionMapper;
    private final TeacherClassMapper teacherClassMapper;
    private final OrganizationMapper organizationMapper;
    private final OrganizationDataScopeService organizationDataScopeService;
    private final FeatureAccessService featureAccessService;

    public LearningTaskOptionService(
            LearningTaskOptionMapper optionMapper,
            TeacherClassMapper teacherClassMapper,
            OrganizationMapper organizationMapper,
            OrganizationDataScopeService organizationDataScopeService,
            FeatureAccessService featureAccessService
    ) {
        this.optionMapper = optionMapper;
        this.teacherClassMapper = teacherClassMapper;
        this.organizationMapper = organizationMapper;
        this.organizationDataScopeService = organizationDataScopeService;
        this.featureAccessService = featureAccessService;
    }

    public List<OrganizationOption> organizations(
            AuthenticatedUser currentUser,
            LearningTaskSourceType sourceType,
            String organizationType
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireSourceRole(currentUser, sourceType);
        String normalizedType = optionalText(organizationType, "组织类型", 32);
        return switch (sourceType) {
            case FAMILY -> List.of();
            case ORGANIZATION -> optionMapper.findOrganizationOptionsForAdministrator(
                    currentUser.userId(), normalizedType);
            case TEACHER -> optionMapper.findOrganizationOptionsForTeacher(
                    currentUser.userId(), normalizedType);
        };
    }

    public List<StudentOption> students(
            AuthenticatedUser currentUser,
            LearningTaskSourceType sourceType,
            Long organizationId,
            String keyword
    ) {
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
                yield optionMapper.findOrganizationStudentOptions(
                        currentUser.userId(), organizationId, normalizedKeyword);
            }
            case TEACHER -> {
                validateTeacherClass(currentUser, organizationId);
                yield optionMapper.findTeacherStudentOptions(
                        currentUser.userId(), organizationId, normalizedKeyword);
            }
        };
        return rows.stream().map(this::toStudentOption).toList();
    }

    public List<TeacherOption> teachers(
            AuthenticatedUser currentUser, Long classId, String keyword
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireRole(currentUser, "ORG_ADMIN", "仅机构管理员可查询教师候选项");
        validateOrganizationScope(currentUser, classId, true);
        List<TeacherOptionRow> rows = optionMapper.findTeacherOptionsForAdministrator(
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
        grouped.forEach((userId, value) -> result.add(new TeacherOption(
                userId, value.displayName(), List.copyOf(value.classIds()))));
        return List.copyOf(result);
    }

    private StudentOption toStudentOption(StudentOptionRow row) {
        return new StudentOption(
                row.id(), row.studentName(), maskStudentAccount(row.studentAccount()),
                row.currentClassId(), row.currentClassName());
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
        if (organization == null
                || !organizationDataScopeService.canAccess(currentUser.userId(), organizationId)) {
            throw notFound();
        }
        if (organization.status() != OrganizationStatus.ENABLED) {
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

    private void requireRole(AuthenticatedUser currentUser, String roleCode, String message) {
        if (currentUser == null || !currentUser.roleCodes().contains(roleCode)) {
            throw new SystemOperationAccessDeniedException(message);
        }
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
