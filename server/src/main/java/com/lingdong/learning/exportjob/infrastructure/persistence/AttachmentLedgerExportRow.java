package com.lingdong.learning.exportjob.infrastructure.persistence;
import java.time.LocalDateTime;
/** 附件安全元数据投影，禁止增加存储字段。 */
public record AttachmentLedgerExportRow(Long id, String originalName, String moduleCode, String uploaderName,
        LocalDateTime createdAt, String fileCategory, Long sizeBytes, String status) { }
