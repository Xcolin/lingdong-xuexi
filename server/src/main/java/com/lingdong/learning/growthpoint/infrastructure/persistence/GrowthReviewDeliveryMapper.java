package com.lingdong.learning.growthpoint.infrastructure.persistence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 内部队列持久化，不提供越过业务授权的下载或发送接口。 */
@Mapper
public interface GrowthReviewDeliveryMapper {
    record Report(Long reviewId, Long snapshotId) { }
    Report findWeeklyReport(@Param("studentId") Long studentId, @Param("start") LocalDate start, @Param("end") LocalDate end);
    Long findId(@Param("subscriptionId") Long subscriptionId, @Param("start") LocalDate start);
    int insert(@Param("id") Long id, @Param("subscriptionId") Long subscriptionId, @Param("version") long version,
            @Param("start") LocalDate start, @Param("report") Report report, @Param("expires") LocalDateTime expires, @Param("now") LocalDateTime now);
    int reactivate(@Param("id") Long id, @Param("version") long version, @Param("report") Report report, @Param("now") LocalDateTime now);
    int cancelPending(@Param("subscriptionId") Long subscriptionId);
    int cancelExpired(@Param("now") LocalDateTime now);
}
