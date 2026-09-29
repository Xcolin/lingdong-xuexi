package com.lingdong.learning.iam.application;

import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;

/** 用户目录持久化查询参数，仅承载已校验的筛选和分页条件。 */
public record UserDirectoryQuery(
        String keyword,
        UserType type,
        UserStatus status,
        int offset,
        int limit
) { }
