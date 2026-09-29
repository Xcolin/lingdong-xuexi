package com.lingdong.learning.importjob.infrastructure.persistence;

import com.lingdong.learning.importjob.domain.ImportJobRowResultRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 导入校验作业逐行结果的 MyBatis 持久化边界。 */
@Mapper
public interface ImportJobRowResultMapper {
    int insertBatch(@Param("rows") List<ImportJobRowResultRecord> rows);

    List<ImportJobRowResultRecord> findByJobId(
            @Param("jobId") Long jobId,
            @Param("invalidOnly") boolean invalidOnly,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countByJobId(@Param("jobId") Long jobId, @Param("invalidOnly") boolean invalidOnly);
}
