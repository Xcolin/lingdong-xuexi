package com.lingdong.learning.templateconfig.web;

import java.util.List;

/** 携带乐观版本号整体替换导入模板字段映射。 */
public record ReplaceImportTemplateFieldsRequest(
        Long versionNo,
        List<ImportTemplateFieldRequest> fields
) { }
