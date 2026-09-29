package com.lingdong.learning.exportjob.domain;

/** 导出作业从申请到终态的有限状态。 */
public enum ExportJobStatus {
    PENDING_REVIEW,
    QUEUED,
    EXPORTING,
    SUCCEEDED,
    FAILED,
    REJECTED
}
