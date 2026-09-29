package com.lingdong.learning.auth.application;

import com.lingdong.learning.student.domain.ParentRelationshipRole;

/** 机构管理员组织范围内可执行人工换号核验的家长候选。 */
public record ParentMobileManualRecoveryCandidate(
        Long studentId,
        String studentName,
        Long organizationId,
        String organizationName,
        Long parentUserId,
        String parentDisplayName,
        String maskedMobile,
        ParentRelationshipRole relationshipRole
) {
}
