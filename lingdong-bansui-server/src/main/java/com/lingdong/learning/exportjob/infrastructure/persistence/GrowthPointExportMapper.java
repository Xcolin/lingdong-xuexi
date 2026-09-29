package com.lingdong.learning.exportjob.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 积分导出专用只读查询，固定按雪花主键游标分页。 */
@Mapper
public interface GrowthPointExportMapper {
    Long findUpperBound(
            @Param("studentId") Long studentId,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("endedAt") LocalDateTime endedAt
    );

    long count(
            @Param("studentId") Long studentId,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("endedAt") LocalDateTime endedAt,
            @Param("upperBound") Long upperBound
    );

    List<GrowthPointExportRow> findAfter(
            @Param("studentId") Long studentId,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("endedAt") LocalDateTime endedAt,
            @Param("upperBound") Long upperBound,
            @Param("cursor") Long cursor,
            @Param("limit") int limit
    );
}
