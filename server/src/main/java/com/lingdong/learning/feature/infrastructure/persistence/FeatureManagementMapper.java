package com.lingdong.learning.feature.infrastructure.persistence;

import com.lingdong.learning.feature.application.FeatureManagementService.ChangeView;
import com.lingdong.learning.feature.application.FeatureManagementService.ToggleView;
import com.lingdong.learning.audit.application.SystemTaskStatus;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 全局开关及领域审批记录查询，SQL 保留在 MyBatis XML。 */
@Mapper
public interface FeatureManagementMapper {
    List<ToggleView> findToggles();
    List<ChangeView> findPage(@Param("userId") Long userId, @Param("auditor") boolean auditor,
            @Param("status") SystemTaskStatus status, @Param("limit") int limit, @Param("offset") int offset);
    long count(@Param("userId") Long userId, @Param("auditor") boolean auditor, @Param("status") SystemTaskStatus status);
    ChangeView findByTaskId(@Param("taskId") Long taskId);
}
