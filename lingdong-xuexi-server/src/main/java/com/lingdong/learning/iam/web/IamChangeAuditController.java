package com.lingdong.learning.iam.web;

import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;
import com.lingdong.learning.iam.audit.application.IamChangeAuditService;
import com.lingdong.learning.iam.audit.application.IamChangeTargetType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** 提供 Web 端身份权限变更审计分页查询。 */
@RestController
@RequestMapping("/api/v1/iam/audits")
public class IamChangeAuditController {
    private final IamChangeAuditService auditService;

    public IamChangeAuditController(IamChangeAuditService auditService) {
        this.auditService = auditService;
    }

    @RequirePermission("IAM_AUDIT_READ")
    @GetMapping
    public IamChangeAuditPageResponse query(
            @RequestParam(required = false) IamChangeAuditEventType eventType,
            @RequestParam(required = false) IamChangeTargetType targetType,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startedAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endedAt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return IamChangeAuditPageResponse.from(auditService.query(
                eventType, targetType, operatorId, targetId, startedAt, endedAt, page, pageSize
        ));
    }
}
