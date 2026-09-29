package com.lingdong.learning.exportjob.infrastructure.persistence;

import java.time.LocalDateTime;

/** 字典台账只读取对外允许的配置字段。 */
public record DictionaryLedgerExportRow(Long id, String typeCode, String typeName,
        String itemCode, String itemName, Integer sortOrder, String status, LocalDateTime updatedAt) { }
