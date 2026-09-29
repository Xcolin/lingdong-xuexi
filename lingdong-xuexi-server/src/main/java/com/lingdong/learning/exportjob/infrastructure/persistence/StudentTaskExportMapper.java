package com.lingdong.learning.exportjob.infrastructure.persistence;
import com.lingdong.learning.exportjob.application.StudentTaskVisibility;
import com.lingdong.learning.exportjob.application.adapter.ExportRequestDefinition;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointStudentOptionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;
@Mapper
public interface StudentTaskExportMapper {
 List<GrowthPointStudentOptionRow> findStudents(@Param("scope") StudentTaskVisibility scope);
 List<Long> findVisibleIds(@Param("scope") StudentTaskVisibility scope, @Param("studentId") Long studentId,
   @Param("source") String source, @Param("status") String status, @Param("start") LocalDate start,
   @Param("end") LocalDate end, @Param("limit") int limit);
 long countVisibleIds(@Param("scope") StudentTaskVisibility scope, @Param("ids") List<Long> ids);
 List<StudentTaskExportRow> findAfter(@Param("request") ExportRequestDefinition request, @Param("upperBound") long upperBound,
    @Param("cursor") long cursor, @Param("limit") int limit);
 long count(@Param("request") ExportRequestDefinition request, @Param("upperBound") long upperBound);
}
