package com.lingdong.learning.templateconfig.web;

import java.util.List;

/** 返回字段映射替换后的新版本号。 */
public record ReplaceImportTemplateFieldsResponse(
        long versionNo,
        List<ImportTemplateFieldResponse> fields
) { }
