package com.lingdong.learning.templateconfig.application;

/** 隔离模板配置模块与导入作业持久化细节的引用查询端口。 */
public interface ImportTemplateUsageQuery {
    boolean hasAnyJob(Long templateId);
}
