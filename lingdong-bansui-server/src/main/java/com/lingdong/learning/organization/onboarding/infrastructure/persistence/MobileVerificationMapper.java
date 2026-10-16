package com.lingdong.learning.organization.onboarding.infrastructure.persistence;
import org.apache.ibatis.annotations.*;
@Mapper public interface MobileVerificationMapper {
 @Select("SELECT EXISTS(SELECT 1 FROM auth_verified_mobile WHERE user_id=#{userId} AND mobile=#{mobile})") boolean verified(@Param("userId") Long userId,@Param("mobile") String mobile);
 @Insert("INSERT INTO auth_verified_mobile(user_id,mobile,verified_at) VALUES(#{userId},#{mobile},CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE mobile=VALUES(mobile),verified_at=CURRENT_TIMESTAMP") int save(@Param("userId") Long userId,@Param("mobile") String mobile);
}
