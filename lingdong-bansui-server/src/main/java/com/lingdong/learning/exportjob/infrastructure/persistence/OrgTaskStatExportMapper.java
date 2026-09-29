package com.lingdong.learning.exportjob.infrastructure.persistence;

import com.lingdong.learning.exceptionreport.application.ExceptionReportClassOption;
import com.lingdong.learning.exportjob.application.StudentTaskVisibility;
import com.lingdong.learning.exportjob.application.adapter.ExportRequestDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/** 机构任务统计的可见范围冻结与聚合计数查询。 */
@Mapper
public interface OrgTaskStatExportMapper {
    List<ExceptionReportClassOption> findOrgOptions(@Param("scope") StudentTaskVisibility scope);

    List<Long> findVisibleOrgIds(@Param("scope") StudentTaskVisibility scope, @Param("start") LocalDate start,
        @Param("end") LocalDate end, @Param("limit") int limit);

    Long maxVisibleAssignmentId(@Param("request") ExportRequestDefinition request);

    long count(@Param("request") ExportRequestDefinition request, @Param("upperBound") long upperBound);

    List<OrgTaskStatExportRow> findAfter(@Param("request") ExportRequestDefinition request,
        @Param("upperBound") long upperBound, @Param("cursor") long cursor, @Param("limit") int limit);
}
