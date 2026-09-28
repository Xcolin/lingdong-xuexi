package com.lingdong.learning.exportjob.infrastructure.persistence;

import com.lingdong.learning.exportjob.application.adapter.ExportRequestDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InterfaceServiceLedgerExportMapper {
    Long findUpperBound(@Param("request") ExportRequestDefinition request);
    long count(@Param("request") ExportRequestDefinition request, @Param("upperBound") long upperBound);
    List<InterfaceServiceLedgerExportRow> findAfter(@Param("request") ExportRequestDefinition request,
            @Param("upperBound") long upperBound, @Param("cursor") long cursor, @Param("limit") int limit);
}
