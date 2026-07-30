package com.lingdong.learning.audit.infrastructure.persistence;

import com.lingdong.learning.audit.application.SystemTask;
import com.lingdong.learning.audit.application.SystemTaskStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence boundary for high-risk system task workflow state. */
@Mapper
public interface SystemTaskMapper {
    int insert(@Param("task") SystemTask task);
    SystemTask findById(@Param("id") Long id);
    SystemTask findByCode(@Param("code") String code);
    int updateSubmission(@Param("id") Long id, @Param("status") SystemTaskStatus status);
    int updateReview(@Param("id") Long id, @Param("status") SystemTaskStatus status,
                     @Param("reviewerId") Long reviewerId, @Param("comment") String comment);
    int markEffective(@Param("id") Long id);
}
