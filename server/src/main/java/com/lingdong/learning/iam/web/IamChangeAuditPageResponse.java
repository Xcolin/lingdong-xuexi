package com.lingdong.learning.iam.web;

import com.lingdong.learning.iam.audit.application.IamChangeAuditPage;

import java.util.List;

/** 身份权限审计分页 HTTP 响应。 */
public record IamChangeAuditPageResponse(List<IamChangeAuditResponse> items, int page, int pageSize, long total) {
    static IamChangeAuditPageResponse from(IamChangeAuditPage page) {
        return new IamChangeAuditPageResponse(
                page.items().stream().map(IamChangeAuditResponse::from).toList(),
                page.page(), page.pageSize(), page.total()
        );
    }
}
