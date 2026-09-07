package com.lingdong.learning.user.infrastructure.persistence;

import com.lingdong.learning.iam.application.UserDirectoryQuery;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Persistence boundary for managed user accounts.
 */
@Mapper
public interface UserMapper {
    User findById(@Param("id") Long id);

    User findByIdForUpdate(@Param("id") Long id);

    User findByUsername(@Param("username") String username);

    User findByMobileForUpdate(@Param("mobile") String mobile);

    List<User> findPage(@Param("query") UserDirectoryQuery query);

    long count(@Param("query") UserDirectoryQuery query);

    boolean existsByUsername(@Param("username") String username);

    boolean existsByMobile(@Param("mobile") String mobile);

    int insert(@Param("user") User user);

    int updatePasswordHash(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    int updateMobileIfExpected(
            @Param("id") Long id,
            @Param("expectedMobile") String expectedMobile,
            @Param("newMobile") String newMobile
    );

    int updateStatus(@Param("id") Long id, @Param("status") UserStatus status);

    int anonymizeCancelledParent(
            @Param("id") Long id,
            @Param("anonymousUsername") String anonymousUsername
    );

    int anonymizeCancelledStudent(
            @Param("id") Long id,
            @Param("anonymousUsername") String anonymousUsername
    );
}
