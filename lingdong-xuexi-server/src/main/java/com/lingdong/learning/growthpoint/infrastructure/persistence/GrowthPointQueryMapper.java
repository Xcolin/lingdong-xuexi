package com.lingdong.learning.growthpoint.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 积分账户与不可变台账的只读查询边界。 */
@Mapper
public interface GrowthPointQueryMapper {
    GrowthPointAccountViewRow findAccountByStudentId(@Param("studentId") Long studentId);

    List<GrowthPointLedgerViewRow> findLedgersByStudentId(
            @Param("studentId") Long studentId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countLedgersByStudentId(@Param("studentId") Long studentId);

    List<GrowthPointStudentOptionRow> findPrimaryStudentsByParentUserId(
            @Param("parentUserId") Long parentUserId
    );
}
