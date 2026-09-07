package com.lingdong.learning.exportjob.infrastructure.persistence;

import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** IAM 审计导出专用只读查询，固定按雪花主键游标分页。 */
@Mapper
public interface IamAuditExportMapper {
    Long findUpperBound(
            @Param("startedAt") LocalDateTime startedAt,
            @Param("endedAt") LocalDateTime endedAt,
            @Param("eventType") IamChangeAuditEventType eventType
    );

    long count(
            @Param("startedAt") LocalDateTime startedAt,
            @Param("endedAt") LocalDateTime endedAt,
            @Param("eventType") IamChangeAuditEventType eventType,
            @Param("upperBound") Long upperBound
    );

    List<IamAuditExportRow> findAfter(
            @Param("startedAt") LocalDateTime startedAt,
            @Param("endedAt") LocalDateTime endedAt,
            @Param("eventType") IamChangeAuditEventType eventType,
            @Param("upperBound") Long upperBound,
            @Param("cursor") Long cursor,
            @Param("limit") int limit
    );
}
