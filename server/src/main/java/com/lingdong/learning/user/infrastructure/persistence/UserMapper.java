package com.lingdong.learning.user.infrastructure.persistence;

import com.lingdong.learning.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Persistence boundary for managed user accounts.
 */
@Mapper
public interface UserMapper {
    User findById(@Param("id") Long id);

    User findByUsername(@Param("username") String username);

    boolean existsByUsername(@Param("username") String username);

    boolean existsByMobile(@Param("mobile") String mobile);

    int insert(@Param("user") User user);
}
