package com.lingdong.learning.exportjob.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 作业内容只允许创建时写入一次，不能通过执行或重试覆盖。 */
@Mapper
public interface ExportJobPayloadMapper {
    record Payload(Long id, Long jobId, String payloadType, String payloadJson, String contentSha256) { }

    int insertForQueuedJob(@Param("payload") Payload payload, @Param("requesterId") Long requesterId,
                          @Param("studentId") Long studentId);

    Payload findByJobId(@Param("jobId") Long jobId);
}
