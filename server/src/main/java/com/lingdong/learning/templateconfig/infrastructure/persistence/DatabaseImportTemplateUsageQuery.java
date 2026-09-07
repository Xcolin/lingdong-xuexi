package com.lingdong.learning.templateconfig.infrastructure.persistence;

import com.lingdong.learning.templateconfig.application.ImportTemplateUsageQuery;
import org.springframework.stereotype.Component;

/** 使用导入作业事实判断模板字段是否已经锁定。 */
@Component
public class DatabaseImportTemplateUsageQuery implements ImportTemplateUsageQuery {
    private final ImportTemplateUsageMapper usageMapper;

    public DatabaseImportTemplateUsageQuery(ImportTemplateUsageMapper usageMapper) {
        this.usageMapper = usageMapper;
    }

    @Override
    public boolean hasAnyJob(Long templateId) {
        return usageMapper.countByTemplateId(templateId) > 0;
    }
}
