package com.lingdong.learning.feature.infrastructure.persistence;
import com.lingdong.learning.feature.domain.FeatureToggle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
/** Persistence boundary for feature-toggle resolution. */
@Mapper public interface FeatureToggleMapper {
    FeatureToggle findGlobal(@Param("code") String code);
    FeatureToggle findOrganization(@Param("code") String code, @Param("scopeKey") String scopeKey);
    int insert(@Param("toggle") FeatureToggle toggle);
    int updateGlobalStatus(@Param("code") String code, @Param("status") com.lingdong.learning.feature.domain.FeatureStatus status);
}
