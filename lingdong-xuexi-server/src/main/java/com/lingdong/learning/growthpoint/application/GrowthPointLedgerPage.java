package com.lingdong.learning.growthpoint.application;

import java.util.List;

/** 积分台账的稳定分页结果。 */
public record GrowthPointLedgerPage(
        List<GrowthPointLedgerView> items,
        int page,
        int pageSize,
        long total
) {
    public GrowthPointLedgerPage {
        items = List.copyOf(items);
    }
}
