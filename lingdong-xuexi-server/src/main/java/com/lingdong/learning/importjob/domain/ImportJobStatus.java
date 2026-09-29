package com.lingdong.learning.importjob.domain;

/** 导入校验作业的受控状态。 */
public enum ImportJobStatus {
    QUEUED,
    VALIDATING,
    VALIDATED,
    VALIDATION_FAILED,
    SYSTEM_FAILED
}
