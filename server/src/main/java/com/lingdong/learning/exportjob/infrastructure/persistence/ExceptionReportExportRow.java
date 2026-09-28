package com.lingdong.learning.exportjob.infrastructure.persistence;
import java.time.LocalDateTime;
public record ExceptionReportExportRow(Long id, String studentName, String exceptionType,
        String reporterName, String status, LocalDateTime reportedAt) { }
