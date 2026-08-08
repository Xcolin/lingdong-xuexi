package com.lingdong.learning.learningtask.infrastructure.persistence;

/** 昨日可复制任务的最小来源快照。 */
public record TaskCopySourceRow(Long taskId, String title) {
}
