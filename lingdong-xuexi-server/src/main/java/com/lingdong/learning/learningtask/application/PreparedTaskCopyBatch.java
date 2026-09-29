package com.lingdong.learning.learningtask.application;

import java.util.List;

/** 已持久化、可逐条执行的复制批次。 */
public record PreparedTaskCopyBatch(Long batchId, boolean existing, List<Long> itemIds) {
    public PreparedTaskCopyBatch {
        itemIds = List.copyOf(itemIds);
    }
}
