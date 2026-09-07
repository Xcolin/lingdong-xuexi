package com.lingdong.learning.importjob.infrastructure.persistence;

import com.lingdong.learning.importjob.domain.ImportJobRecord;
import com.lingdong.learning.importjob.application.ImportJobQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 导入校验作业的 MyBatis 持久化边界。 */
@Mapper
public interface ImportJobMapper {
    int insert(@Param("job") ImportJobRecord job);

    ImportJobRecord findById(@Param("id") Long id);

    List<ImportJobRecord> findQueued(@Param("limit") int limit);

    List<ImportJobRecord> findPage(
            @Param("query") ImportJobQuery query,
            @Param("ownerId") Long ownerId,
            @Param("accessibleOrganizationIds") List<Long> accessibleOrganizationIds,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long count(
            @Param("query") ImportJobQuery query,
            @Param("ownerId") Long ownerId,
            @Param("accessibleOrganizationIds") List<Long> accessibleOrganizationIds
    );

    int claim(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion);

    int finish(
            @Param("id") Long id,
            @Param("expectedVersion") Long expectedVersion,
            @Param("status") com.lingdong.learning.importjob.domain.ImportJobStatus status,
            @Param("errorFileId") Long errorFileId,
            @Param("totalRows") int totalRows,
            @Param("processedRows") int processedRows,
            @Param("validRows") int validRows,
            @Param("invalidRows") int invalidRows,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage
    );

    int markSystemFailed(
            @Param("id") Long id,
            @Param("expectedVersion") Long expectedVersion,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage
    );
}
