package com.lingdong.learning.growthpoint.infrastructure.persistence;
import com.lingdong.learning.growthpoint.domain.GrowthReviewSubscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GrowthReviewSubscriptionMapper {
    java.util.List<GrowthReviewSubscription> findAfter(@Param("afterId") long afterId, @Param("limit") int limit);
    GrowthReviewSubscription find(@Param("parentId") Long parentId, @Param("studentId") Long studentId);
    int insert(@Param("row") GrowthReviewSubscription row);
    int update(@Param("row") GrowthReviewSubscription row, @Param("expectedVersion") long expectedVersion);
}
