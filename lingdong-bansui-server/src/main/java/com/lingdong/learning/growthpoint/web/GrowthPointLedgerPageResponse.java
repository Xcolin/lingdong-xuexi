package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.growthpoint.application.GrowthPointLedgerPage;

import java.util.List;

/** 积分台账分页响应。 */
public record GrowthPointLedgerPageResponse(
        List<GrowthPointLedgerResponse> items,
        int page,
        int pageSize,
        long total
) {
    static GrowthPointLedgerPageResponse from(GrowthPointLedgerPage page) {
        return new GrowthPointLedgerPageResponse(
                page.items().stream().map(GrowthPointLedgerResponse::from).toList(),
                page.page(), page.pageSize(), page.total());
    }
}
