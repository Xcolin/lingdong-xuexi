package com.lingdong.learning.studentimport.application;

import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionRecord;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionStatus;
import com.lingdong.learning.studentimport.domain.StudentImportRowStatus;
import com.lingdong.learning.studentimport.infrastructure.persistence.StudentImportExecutionMapper;
import com.lingdong.learning.studentimport.infrastructure.persistence.StudentImportRowMapper;
import org.springframework.stereotype.Service;

/** 查询本人且仍位于当前组织范围内的学员导入执行和逐行结果。 */
@Service
public class StudentImportQueryService {
    private final StudentImportExecutionMapper executionMapper;
    private final StudentImportRowMapper rowMapper;
    private final StudentImportAccessService accessService;

    public StudentImportQueryService(
            StudentImportExecutionMapper executionMapper,
            StudentImportRowMapper rowMapper,
            StudentImportAccessService accessService
    ) {
        this.executionMapper = executionMapper;
        this.rowMapper = rowMapper;
        this.accessService = accessService;
    }

    public StudentImportPage findPage(
            Long operatorId,
            StudentImportExecutionStatus status,
            int page,
            int pageSize
    ) {
        validatePage(page, pageSize);
        requireId(operatorId, "操作人");
        accessService.requireListRead(operatorId);
        int offset = Math.multiplyExact(page - 1, pageSize);
        return new StudentImportPage(
                executionMapper.findPageByRequester(operatorId, status, offset, pageSize)
                        .stream().map(StudentImportView::from).toList(),
                page, pageSize, executionMapper.countByRequester(operatorId, status));
    }

    public StudentImportView findDetail(Long operatorId, Long executionId) {
        return StudentImportView.from(requireReadable(operatorId, executionId));
    }

    public StudentImportRowPage findRows(
            Long operatorId,
            Long executionId,
            StudentImportRowStatus status,
            int page,
            int pageSize
    ) {
        StudentImportExecutionRecord execution = requireReadable(operatorId, executionId);
        validatePage(page, pageSize);
        int offset = Math.multiplyExact(page - 1, pageSize);
        return new StudentImportRowPage(
                rowMapper.findByExecutionIdAndStatus(
                        execution.id(), status, offset, pageSize).stream()
                        .map(StudentImportRowView::from).toList(),
                page, pageSize,
                rowMapper.countByExecutionIdAndStatus(execution.id(), status));
    }

    private StudentImportExecutionRecord requireReadable(Long operatorId, Long executionId) {
        requireId(operatorId, "操作人");
        requireId(executionId, "学员导入执行");
        StudentImportExecutionRecord execution = executionMapper.findById(executionId);
        if (execution == null) {
            throw new ResourceNotFoundException("学员导入执行不存在");
        }
        accessService.requireOwnerRead(operatorId, execution);
        return execution;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("分页参数不合法，单页数量范围为1至100");
        }
    }

    private void requireId(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + "标识必须为正整数");
        }
    }
}
