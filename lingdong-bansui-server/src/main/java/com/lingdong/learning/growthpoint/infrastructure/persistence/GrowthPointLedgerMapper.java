package com.lingdong.learning.growthpoint.infrastructure.persistence;

import com.lingdong.learning.growthpoint.domain.GrowthPointLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 不可变积分台账写入边界。 */
@Mapper
public interface GrowthPointLedgerMapper {
    int insert(@Param("ledger") GrowthPointLedger ledger);

    GrowthPointLedger findByIdForUpdate(@Param("id") Long id);

    Long findCorrectionIdByOriginalId(@Param("originalLedgerId") Long originalLedgerId);
}
