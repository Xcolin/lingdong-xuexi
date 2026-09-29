package com.lingdong.learning.iam.infrastructure.persistence;

import com.lingdong.learning.iam.domain.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Persistence boundary for the role aggregate. SQL stays in the XML mapper.
 */
@Mapper
public interface RoleMapper {
    Role findById(@Param("id") Long id);

    boolean existsByCode(@Param("code") String code);

    int insert(@Param("role") Role role);

    Role findByCode(@Param("code") String code);

    List<Role> findAll();
}
