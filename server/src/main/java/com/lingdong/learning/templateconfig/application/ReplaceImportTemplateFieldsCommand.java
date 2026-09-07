package com.lingdong.learning.templateconfig.application;

import java.util.List;

/** 携带乐观版本锁整体替换导入模板字段映射的请求。 */
public record ReplaceImportTemplateFieldsCommand(
        Long operatorId,
        Long templateId,
        Long versionNo,
        List<ImportTemplateFieldInput> fields
) { }
