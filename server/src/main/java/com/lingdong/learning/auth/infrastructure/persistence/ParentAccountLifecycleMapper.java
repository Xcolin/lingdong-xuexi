package com.lingdong.learning.auth.infrastructure.persistence;

import com.lingdong.learning.auth.application.ParentMobileChangeRecord;
import com.lingdong.learning.auth.application.ParentAccountCancellationRecord;
import com.lingdong.learning.auth.application.ParentAccountFinalizationCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 家长账号生命周期审计与注销申请持久化边界。 */
@Mapper
public interface ParentAccountLifecycleMapper {
    int insertMobileChange(@Param("record") ParentMobileChangeRecord record);

    ParentAccountCancellationRecord findActiveCancellation(@Param("userId") Long userId);

    ParentAccountCancellationRecord findActiveCancellationForUpdate(@Param("userId") Long userId);

    int insertCancellation(@Param("record") ParentAccountCancellationRecord record);

    int revokeCancellation(
            @Param("id") Long id,
            @Param("closedScopeKey") String closedScopeKey,
            @Param("revokedAt") java.time.LocalDateTime revokedAt
    );

    ParentAccountFinalizationCandidate findFinalizationCandidateForUpdate(
            @Param("cancellationId") Long cancellationId
    );

    int deferFinalization(
            @Param("cancellationId") Long cancellationId,
            @Param("nextFinalizeAt") java.time.LocalDateTime nextFinalizeAt,
            @Param("errorCode") String errorCode
    );

    int finalizeCancellation(
            @Param("cancellationId") Long cancellationId,
            @Param("closedScopeKey") String closedScopeKey,
            @Param("finalizedAt") java.time.LocalDateTime finalizedAt
    );

    List<Long> findDueFinalizationIds(
            @Param("now") java.time.LocalDateTime now,
            @Param("limit") int limit
    );
}
