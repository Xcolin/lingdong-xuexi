package com.lingdong.learning.iam.web;

import com.lingdong.learning.iam.application.UserDirectoryPage;

import java.util.List;

/** 用户目录接口响应，统一复用安全用户资料。 */
public record UserDirectoryPageResponse(List<UserResponse> items, int page, int pageSize, long total) {
    static UserDirectoryPageResponse from(UserDirectoryPage page) {
        return new UserDirectoryPageResponse(page.items().stream().map(UserResponse::from).toList(),
                page.page(), page.pageSize(), page.total());
    }
}
