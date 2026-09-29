package com.lingdong.learning.exportjob.infrastructure.persistence;
public record StudentTaskExportRow(Long id, String studentName, String title, String sourceType, String status, Long points, String reviewStatus) {}
