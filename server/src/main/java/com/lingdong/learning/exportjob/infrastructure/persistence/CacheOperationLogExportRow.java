package com.lingdong.learning.exportjob.infrastructure.persistence;
import java.time.LocalDateTime;
/** 缓存日志导出白名单投影，不加载自由文本。 */
public record CacheOperationLogExportRow(Long id, String cacheDomain, String operationType, String status, Long executedBy, LocalDateTime createdAt) {}
