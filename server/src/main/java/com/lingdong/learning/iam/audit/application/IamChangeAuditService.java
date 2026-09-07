package com.lingdong.learning.iam.audit.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.iam.audit.infrastructure.persistence.IamChangeAuditMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

/** 统一追加和查询身份权限审计，确保各模块使用同一数据口径。 */
@Service
public class IamChangeAuditService {
    private final IamChangeAuditMapper auditMapper;
    private final IdGenerator idGenerator;

    public IamChangeAuditService(IamChangeAuditMapper auditMapper, IdGenerator idGenerator) {
        this.auditMapper = auditMapper;
        this.idGenerator = idGenerator;
    }

    public void record(IamChangeAuditEventType eventType, Long operatorId, IamChangeTargetType targetType,
                       Long targetId, Long relatedId, Long organizationId, String beforeValue, String afterValue) {
        Objects.requireNonNull(eventType, "审计事件类型不能为空");
        Objects.requireNonNull(targetType, "审计对象类型不能为空");
        Objects.requireNonNull(targetId, "审计对象标识不能为空");
        auditMapper.insert(new IamChangeAudit(
                idGenerator.nextId(), eventType, operatorId, targetType, targetId, relatedId,
                organizationId, normalizeValue(beforeValue), normalizeValue(afterValue), LocalDateTime.now()
        ));
    }

    public IamChangeAuditPage query(IamChangeAuditEventType eventType, IamChangeTargetType targetType,
                                    Long operatorId, Long targetId, LocalDateTime startedAt,
                                    LocalDateTime endedAt, int page, int pageSize) {
        if (page < 1 || page > 1_000_000) {
            throw new IllegalArgumentException("页码必须在 1 至 1000000 之间");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("每页数量必须在 1 至 100 之间");
        }
        if (startedAt != null && endedAt != null && startedAt.isAfter(endedAt)) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        IamChangeAuditQuery query = new IamChangeAuditQuery(
                eventType, targetType, operatorId, targetId, startedAt, endedAt,
                Math.multiplyExact(page - 1, pageSize), pageSize
        );
        return new IamChangeAuditPage(auditMapper.findPage(query), page, pageSize, auditMapper.count(query));
    }

    private String normalizeValue(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("审计状态值长度不能超过 128 个字符");
        }
        return normalized;
    }
}
