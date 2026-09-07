package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.application.ParentMobileManualRecoveryCandidate;
import com.lingdong.learning.student.domain.ParentRelationshipRole;

/** 机构人工换绑候选响应，所有内部雪花标识均以字符串传输。 */
public record ParentMobileManualRecoveryCandidateResponse(
        String studentId,
        String studentName,
        String organizationId,
        String organizationName,
        String parentUserId,
        String parentDisplayName,
        String maskedMobile,
        ParentRelationshipRole relationshipRole
) {
    public static ParentMobileManualRecoveryCandidateResponse from(
            ParentMobileManualRecoveryCandidate candidate
    ) {
        return new ParentMobileManualRecoveryCandidateResponse(
                candidate.studentId().toString(), candidate.studentName(),
                candidate.organizationId().toString(), candidate.organizationName(),
                candidate.parentUserId().toString(), candidate.parentDisplayName(),
                candidate.maskedMobile(), candidate.relationshipRole());
    }
}
