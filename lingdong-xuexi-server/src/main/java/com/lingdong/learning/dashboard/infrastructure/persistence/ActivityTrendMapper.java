package com.lingdong.learning.dashboard.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/** 学员活跃度按行为事实聚合查询（R-002，docs/design/09 第 2.7 节）。 */
@Mapper
public interface ActivityTrendMapper {
    List<ActivityTrendRow> findDailyActive(@Param("allOrganizations") boolean allOrganizations,
            @Param("rootPaths") List<String> rootPaths,
            @Param("start") LocalDate start, @Param("end") LocalDate end);
}
