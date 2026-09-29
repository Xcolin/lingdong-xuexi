package com.lingdong.learning.exportjob.domain;

/** 只记录关键状态变化，不按数据批次追加事件。 */
public enum ExportJobEventType {
    REQUESTED,
    REVIEW_SUBMITTED,
    APPROVED,
    REJECTED,
    CLAIMED,
    SUCCEEDED,
    FAILED
}
