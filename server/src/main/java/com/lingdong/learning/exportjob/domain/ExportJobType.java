package com.lingdong.learning.exportjob.domain;

/** V57 首批支持的数据集类型。 */
public enum ExportJobType {
    GROWTH_POINT_LEDGER,
    IAM_CHANGE_AUDIT,
    GROWTH_REVIEW_PDF,
    DICTIONARY_LEDGER,
    TEMPLATE_LEDGER,
    INTERFACE_SERVICE_LEDGER,
    CACHE_OPERATION_LOG,
    SYSTEM_TASK_LEDGER,
    REWARD_EXCHANGE_LEDGER,
    EXCEPTION_REPORT_LEDGER,
    ATTACHMENT_LEDGER;

    public String templateModule() {
        return switch (this) {
            case ATTACHMENT_LEDGER -> "ATTACHMENT_LEDGER_REPORT";
            case EXCEPTION_REPORT_LEDGER -> "EXCEPTION_REPORT_EXPORT";
            case REWARD_EXCHANGE_LEDGER -> "REWARD_EXCHANGE_REPORT";
            case DICTIONARY_LEDGER -> "DICTIONARY_REPORT";
            case TEMPLATE_LEDGER -> "TEMPLATE_REPORT";
            case SYSTEM_TASK_LEDGER -> "SYSTEM_TASK_REPORT";
            case CACHE_OPERATION_LOG -> "CACHE_REPORT";
            case INTERFACE_SERVICE_LEDGER -> "INTERFACE_REPORT";
            default -> "REPORT";
        };
    }
}
