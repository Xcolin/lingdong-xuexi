package com.lingdong.learning.datascope.infrastructure.persistence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper public interface OrganizationAdminMapper { List<Long> findOrganizationIds(@Param("userId") Long userId); boolean exists(@Param("userId") Long userId,@Param("organizationId") Long organizationId); int insert(@Param("id") Long id,@Param("userId") Long userId,@Param("organizationId") Long organizationId); }
