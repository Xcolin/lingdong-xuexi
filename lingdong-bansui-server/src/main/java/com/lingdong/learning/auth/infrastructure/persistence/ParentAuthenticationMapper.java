package com.lingdong.learning.auth.infrastructure.persistence;

import com.lingdong.learning.auth.application.ParentAgreementAcceptance;
import com.lingdong.learning.auth.application.ParentProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 家长协议接受和首次引导档案的持久化边界。 */
@Mapper
public interface ParentAuthenticationMapper {
    String findCurrentAgreementVersion();

    ParentProfile findProfileByUserId(@Param("userId") Long userId);

    boolean hasAgreementAcceptance(@Param("userId") Long userId, @Param("agreementVersion") String agreementVersion);

    int insertProfile(@Param("profile") ParentProfile profile);

    int insertAgreementAcceptance(@Param("acceptance") ParentAgreementAcceptance acceptance);

    int completeOnboarding(
            @Param("userId") Long userId,
            @Param("completedAt") LocalDateTime completedAt
    );
}
