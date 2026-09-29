package com.lingdong.learning.studentimport.application;

import com.lingdong.learning.studentimport.infrastructure.persistence.StudentImportRowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 在开户事务回滚后，以独立事务记录受限的逐行失败事实。 */
@Service
public class StudentImportRowFailureService {
    private final StudentImportRowMapper rowMapper;

    public StudentImportRowFailureService(StudentImportRowMapper rowMapper) {
        this.rowMapper = rowMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(long rowId, String failureCode, String failureMessage) {
        if (rowMapper.markFailed(rowId, limited(failureCode, 64), limited(failureMessage, 500)) != 1) {
            throw new IllegalStateException("学员导入行失败状态更新冲突");
        }
    }

    private String limited(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
