package com.lingdong.learning.organization.onboarding.infrastructure.persistence;
import org.apache.ibatis.annotations.*;
@Mapper
public interface EmailVerificationMapper {
    @Select("SELECT email FROM auth_verified_email WHERE user_id=#{userId}")
    String verifiedEmail(@Param("userId") Long userId);
}
