package com.lingdong.learning.datascope.infrastructure.persistence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper public interface RoleDataScopeMapper { List<Long> findOrganizationIds(@Param("roleId") Long roleId); boolean exists(@Param("roleId") Long roleId,@Param("organizationId") Long organizationId); int insert(@Param("roleId") Long roleId,@Param("organizationId") Long organizationId); }
