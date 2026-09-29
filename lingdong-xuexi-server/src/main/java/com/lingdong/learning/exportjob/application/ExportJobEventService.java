package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.exportjob.domain.ExportJobEventRecord;
import com.lingdong.learning.exportjob.domain.ExportJobEventType;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobEventMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/** 统一追加导出作业关键状态事件。 */
@Service
public class ExportJobEventService {
    private final ExportJobEventMapper eventMapper;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public ExportJobEventService(ExportJobEventMapper eventMapper, IdGenerator idGenerator, Clock clock) {
        this.eventMapper = eventMapper;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public void record(
            Long jobId,
            ExportJobEventType eventType,
            Long operatorId,
            String summary
    ) {
        if (jobId == null || eventType == null || summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("导出作业事件参数不完整");
        }
        String normalized = summary.trim();
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("导出作业事件摘要长度不能超过500个字符");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (eventMapper.insert(new ExportJobEventRecord(
                idGenerator.nextId(), jobId, eventType, operatorId, normalized, now, now)) != 1) {
            throw new IllegalStateException("导出作业事件保存失败");
        }
    }
}
