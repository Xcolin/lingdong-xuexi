package com.lingdong.learning.feature.infrastructure.persistence;
import com.lingdong.learning.feature.application.FeatureToggleChange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper public interface FeatureToggleChangeMapper { int insert(@Param("change") FeatureToggleChange change); FeatureToggleChange findByTaskId(@Param("taskId") Long taskId); }
