package com.lingdong.learning.iam.audit.application;

import java.util.List;

/** 身份权限变更审计分页结果。 */
public record IamChangeAuditPage(List<IamChangeAudit> items, int page, int pageSize, long total) { }
