package com.lingdong.learning.learningtask.infrastructure.persistence;

/** 到期处理锁定后使用的最小任务实例快照。 */
public record TaskOverdueStateRow(Long id, Long studentId, Integer versionNo) {
}
