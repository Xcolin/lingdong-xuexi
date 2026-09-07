package com.lingdong.learning.auth.infrastructure.persistence;

import com.lingdong.learning.student.domain.ParentRelationshipRole;

/** 人工换绑候选查询的内部持久化行，手机号仅供服务端脱敏和比对。 */
public record ParentMobileManualRecoveryCandidateRow(
        Long studentId,
        String studentName,
        Long organizationId,
        String organizationName,
        Long parentUserId,
        String parentDisplayName,
        String mobile,
        ParentRelationshipRole relationshipRole
) {
}
