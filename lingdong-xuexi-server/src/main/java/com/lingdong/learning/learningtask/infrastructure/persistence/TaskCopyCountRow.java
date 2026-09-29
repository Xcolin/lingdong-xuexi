package com.lingdong.learning.learningtask.infrastructure.persistence;

/** 复制批次当前条目汇总。 */
public record TaskCopyCountRow(Integer totalCount, Integer successCount, Integer failureCount) {
}
