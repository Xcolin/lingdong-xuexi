package com.lingdong.learning.auth.infrastructure.persistence;

import com.lingdong.learning.auth.application.ParentMobileManualRecoveryRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 家长手机号人工核验候选和不可变审计的持久化边界。 */
@Mapper
public interface ParentMobileManualRecoveryMapper {
    List<ParentMobileManualRecoveryCandidateRow> findCandidatesByOrganizationAdministrator(
            @Param("operatorUserId") Long operatorUserId
    );

    ParentMobileManualRecoveryCandidateRow findAccessibleCandidate(
            @Param("operatorUserId") Long operatorUserId,
            @Param("studentId") Long studentId,
            @Param("parentUserId") Long parentUserId
    );

    Long findAccessibleOrganizationId(
            @Param("operatorUserId") Long operatorUserId,
            @Param("studentId") Long studentId
    );

    int insert(@Param("record") ParentMobileManualRecoveryRecord record);
}
