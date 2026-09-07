package com.lingdong.learning.studentimport.domain;

/** 学员批量导入执行的确定状态。 */
public enum StudentImportExecutionStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    PARTIAL_SUCCEEDED,
    FAILED
}
