package com.lingdong.learning.exportjob.infrastructure.persistence;

import com.lingdong.learning.exportjob.domain.ExportJobEventRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 导出事件只允许追加和按作业读取。 */
@Mapper
public interface ExportJobEventMapper {
    int insert(@Param("event") ExportJobEventRecord event);

    List<ExportJobEventRecord> findByJobId(@Param("jobId") Long jobId);
}
