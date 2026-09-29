package com.lingdong.learning.exportjob.infrastructure.persistence;

import com.lingdong.learning.attendance.infrastructure.persistence.AttendanceScope;
import com.lingdong.learning.exceptionreport.application.ExceptionReportClassOption;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointStudentOptionRow;
import com.lingdong.learning.exportjob.application.adapter.ExportRequestDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 考勤台账导出读模型；范围仅来自服务端冻结身份，空集合失败关闭。 */
@Mapper
public interface AttendanceLedgerExportMapper {
    List<ExceptionReportClassOption> findClassOptions(@Param("scope") AttendanceScope scope);
    List<Long> findVisibleClassIds(@Param("scope") AttendanceScope scope, @Param("limit") int limit);
    List<GrowthPointStudentOptionRow> findFamilyStudents(@Param("userId") long userId);
    Long findUpperBound(@Param("request") ExportRequestDefinition request);
    long count(@Param("request") ExportRequestDefinition request, @Param("upperBound") long upperBound);
    List<AttendanceLedgerExportRow> findAfter(@Param("request") ExportRequestDefinition request,
            @Param("upperBound") long upperBound, @Param("cursor") long cursor, @Param("limit") int limit);
}
