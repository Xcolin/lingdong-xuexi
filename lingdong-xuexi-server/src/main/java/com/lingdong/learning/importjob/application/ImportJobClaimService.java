package com.lingdong.learning.importjob.application;

import com.lingdong.learning.importjob.domain.ImportJobRecord;
import com.lingdong.learning.importjob.domain.ImportJobStatus;
import com.lingdong.learning.importjob.infrastructure.persistence.ImportJobMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 通过数据库状态和版本条件更新领取单个排队作业。 */
@Service
public class ImportJobClaimService {
    private final ImportJobMapper jobMapper;

    public ImportJobClaimService(ImportJobMapper jobMapper) {
        this.jobMapper = jobMapper;
    }

    @Transactional
    public ImportJobRecord claim(Long jobId, Long expectedVersion) {
        if (jobId == null || expectedVersion == null || expectedVersion < 0) {
            throw new IllegalArgumentException("作业标识和期望版本不能为空");
        }
        ImportJobRecord current = jobMapper.findById(jobId);
        if (current == null
                || current.status() != ImportJobStatus.QUEUED
                || !expectedVersion.equals(current.versionNo())) {
            return null;
        }
        if (jobMapper.claim(jobId, expectedVersion) != 1) {
            return null;
        }
        ImportJobRecord claimed = jobMapper.findById(jobId);
        if (claimed == null || claimed.status() != ImportJobStatus.VALIDATING) {
            throw new IllegalStateException("导入校验作业领取后状态不一致：" + jobId);
        }
        return claimed;
    }
}
