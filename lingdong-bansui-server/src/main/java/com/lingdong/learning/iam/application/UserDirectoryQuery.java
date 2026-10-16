package com.lingdong.learning.iam.application;

import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;

/** 用户目录持久化查询参数，仅承载已校验的筛选和分页条件。 */
public record UserDirectoryQuery(
        String keyword,
        UserType type,
        UserStatus status,
        Long organizationId,
        int offset,
        int limit,
        java.util.List<Long> visibleOrganizationIds,
        String identityRoleCode
) {
    public UserDirectoryQuery(String keyword, UserType type, UserStatus status, Long organizationId, int offset, int limit, java.util.List<Long> visibleOrganizationIds) {
        this(keyword,type,status,organizationId,offset,limit,visibleOrganizationIds,null);
    }
    public UserDirectoryQuery(String keyword, UserType type, UserStatus status, Long organizationId, int offset, int limit) {
        this(keyword, type, status, organizationId, offset, limit, null);
    }
    public UserDirectoryQuery(String keyword, UserType type, UserStatus status, int offset, int limit) {
        this(keyword, type, status, null, offset, limit);
    }
}
