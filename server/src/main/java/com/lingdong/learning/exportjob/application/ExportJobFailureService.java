package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.exportjob.domain.ExportJobEventType;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** 以受限错误码和中性中文消息提交失败终态。 */
@Service
public class ExportJobFailureService {
    private final ExportJobMapper jobMapper;
    private final ExportJobEventService eventService;
    private final Clock clock;

    public ExportJobFailureService(
            ExportJobMapper jobMapper,
            ExportJobEventService eventService,
            Clock clock
    ) {
        this.jobMapper = jobMapper;
        this.eventService = eventService;
        this.clock = clock;
    }

    @Transactional
    public void fail(Long jobId, long expectedVersion, String failureCode, String failureMessage) {
        String code = limited(failureCode, 64, "EXPORT_GENERATION_FAILED");
        String message = limited(failureMessage, 500, "导出文件生成失败");
        if (jobMapper.fail(jobId, expectedVersion, code, message, LocalDateTime.now(clock)) != 1) {
            throw new IllegalStateException("导出作业失败终态更新冲突：" + jobId);
        }
        eventService.record(jobId, ExportJobEventType.FAILED, null, message);
    }

    private String limited(String value, int maxLength, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
