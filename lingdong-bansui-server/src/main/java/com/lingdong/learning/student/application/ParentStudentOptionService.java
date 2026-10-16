package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.application.ParentActorAccessService;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.domain.ParentRelationshipRole;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Minimum directory for growth actions; every returned child is the actor's active relationship. */
@Service
public class ParentStudentOptionService {
    private static final List<String> ACTIONS = List.of("GROWTH_POINT_READ_CHILD", "GROWTH_POINT_CORRECT_CHILD",
            "GROWTH_REVIEW_READ_CHILD", "GROWTH_REVIEW_SUPPLEMENT_CHILD", "GROWTH_REVIEW_PDF_EXPORT",
            "EXPORT_JOB_CREATE", "GROWTH_REVIEW_SUBSCRIBE_CHILD", "REWARD_READ", "REWARD_CREATE",
            "REWARD_UPDATE", "REWARD_DELETE", "REWARD_EXCHANGE_READ", "REWARD_EXCHANGE_REVIEW");
    private final ParentActorAccessService actors;
    private final PermissionDecisionService permissions;
    private final FeatureAccessService features;
    private final ParentStudentMapper relations;
    private final StudentMapper students;
    private final StudentOrganizationMapper enrollments;
    public ParentStudentOptionService(ParentActorAccessService actors, PermissionDecisionService permissions,
            FeatureAccessService features, ParentStudentMapper relations, StudentMapper students,
            StudentOrganizationMapper enrollments) {
        this.actors=actors; this.permissions=permissions; this.features=features;
        this.relations=relations; this.students=students; this.enrollments=enrollments;
    }
    @Transactional(readOnly=true)
    public List<Option> find(AuthenticatedUser actor) {
        actors.require(actor);
        if (actor.clientType()!=AuthClientType.WEB || ACTIONS.stream().noneMatch(code ->
                permissions.isAllowed(actor.userId(), PermissionClient.WEB, code) && featureEnabled(code))) {
            throw new SystemOperationAccessDeniedException("当前账号没有已开启的成长或家庭奖励动作权限");
        }
        return relations.findActiveStudentsByParent(actor.userId()).stream().map(relation -> {
            var student=students.findById(relation.studentId());
            if (student==null || student.status()!=StudentStatus.ENABLED) return null;
            var organizations=enrollments.findActiveEnrollmentOrganizationIds(student.id());
            return new Option(student.id().toString(),student.studentName(),student.gradeCode(),
                    organizations.isEmpty()?null:organizations.get(0).toString(),student.status(),relation.relationshipRole());
        }).filter(java.util.Objects::nonNull).toList();
    }
    private boolean featureEnabled(String code) {
        if (code.startsWith("REWARD_")) return features.isEnabled("REWARD_EXCHANGE",null);
        if (code.equals("GROWTH_POINT_CORRECT_CHILD")) return features.isEnabled("GROWTH_POINT_CORRECTION",null);
        if (code.equals("GROWTH_POINT_READ_CHILD")) return features.isEnabled("GROWTH_POINT_QUERY",null);
        if (code.equals("GROWTH_REVIEW_SUBSCRIBE_CHILD")) return features.isEnabled("GROWTH_REVIEW_WEEKLY_SUBSCRIPTION",null)
                && features.isEnabled("PERIODIC_GROWTH_REPORT",null);
        if (code.equals("GROWTH_REVIEW_SUPPLEMENT_CHILD")) return features.isEnabled("DAILY_GROWTH_REVIEW",null);
        if (code.equals("GROWTH_REVIEW_READ_CHILD")) return features.isEnabled("DAILY_GROWTH_REVIEW",null)
                || features.isEnabled("PERIODIC_GROWTH_REPORT",null);
        return features.isEnabled("DATA_EXPORT",null) && features.isEnabled("GROWTH_REVIEW_PDF_EXPORT",null);
    }
    public record Option(String id,String studentName,String gradeCode,String schoolOrganizationId,
            StudentStatus status,ParentRelationshipRole relationshipRole) { }
}
