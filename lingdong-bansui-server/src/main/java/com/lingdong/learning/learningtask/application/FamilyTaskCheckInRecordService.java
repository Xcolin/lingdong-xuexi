package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.attachment.application.TaskAttachmentApplicationService;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskCheckInMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 家长任务详情打卡记录查询（02-06）。 */
@Service
public class FamilyTaskCheckInRecordService {

    private static final int MAX_RECORDS = 50;

    private final TaskCheckInMapper checkInMapper;
    private final TaskAttachmentApplicationService attachmentService;

    public FamilyTaskCheckInRecordService(
            TaskCheckInMapper checkInMapper,
            TaskAttachmentApplicationService attachmentService
    ) {
        this.checkInMapper = checkInMapper;
        this.attachmentService = attachmentService;
    }

    @Transactional(readOnly = true)
    public List<FamilyTaskCheckInRecord> findRecords(Long taskId) {
        return checkInMapper.findFamilyRecords(taskId, MAX_RECORDS).stream()
                .map(row -> new FamilyTaskCheckInRecord(
                        row, attachmentService.findByCheckInId(row.checkInId())))
                .toList();
    }
}
