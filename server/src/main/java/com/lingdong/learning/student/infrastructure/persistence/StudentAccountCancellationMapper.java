package com.lingdong.learning.student.infrastructure.persistence;

import com.lingdong.learning.student.application.StudentAccountCancellationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 学生注销候选范围与不可变审计的持久化边界。 */
@Mapper
public interface StudentAccountCancellationMapper {
    List<StudentAccountCancellationCandidateRow> findCandidatesByOrganizationAdministrator(
            @Param("operatorUserId") Long operatorUserId
    );

    StudentAccountCancellationCandidateRow findAccessibleCandidate(
            @Param("operatorUserId") Long operatorUserId,
            @Param("studentId") Long studentId
    );

    Long findAccessibleLatestInactiveEnrollmentOrganizationId(
            @Param("operatorUserId") Long operatorUserId,
            @Param("studentId") Long studentId
    );

    int insert(@Param("record") StudentAccountCancellationRecord record);
}

