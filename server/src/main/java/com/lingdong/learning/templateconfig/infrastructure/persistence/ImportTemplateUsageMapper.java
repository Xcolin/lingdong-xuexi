package com.lingdong.learning.templateconfig.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 导入作业对模板引用关系的最小查询边界。 */
@Mapper
public interface ImportTemplateUsageMapper {
    int countByTemplateId(@Param("templateId") Long templateId);
}
