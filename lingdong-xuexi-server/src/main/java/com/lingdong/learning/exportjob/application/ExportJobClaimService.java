package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.exportjob.domain.ExportJobEventType;
import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 使用数据库状态和版本条件领取一个排队导出作业。 */
@Service
public class ExportJobClaimService {
    private final ExportJobMapper jobMapper;
    private final ExportJobEventService eventService;

    public ExportJobClaimService(ExportJobMapper jobMapper, ExportJobEventService eventService) {
        this.jobMapper = jobMapper;
        this.eventService = eventService;
    }

    @Transactional
    public ExportJobRecord claim(Long jobId, Long expectedVersion) {
        if (jobId == null || expectedVersion == null || expectedVersion < 0) {
            throw new IllegalArgumentException("导出作业标识和期望版本不能为空");
        }
        ExportJobRecord current = jobMapper.findById(jobId);
        if (current == null || current.status() != ExportJobStatus.QUEUED
                || !expectedVersion.equals(current.versionNo())) {
            return null;
        }
        if (jobMapper.claim(jobId, expectedVersion) != 1) {
            return null;
        }
        ExportJobRecord claimed = jobMapper.findById(jobId);
        if (claimed == null || claimed.status() != ExportJobStatus.EXPORTING
                || claimed.versionNo() != expectedVersion + 1) {
            throw new IllegalStateException("导出作业领取后状态不一致：" + jobId);
        }
        eventService.record(jobId, ExportJobEventType.CLAIMED, null, "系统已领取导出作业");
        return claimed;
    }
}
