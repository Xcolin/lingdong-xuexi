package com.lingdong.learning.templateconfig.infrastructure.persistence;

import com.lingdong.learning.templateconfig.domain.ImportExportTemplateFieldRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 导入模板字段映射的 MyBatis 持久化边界。 */
@Mapper
public interface ImportExportTemplateFieldMapper {
    int insertBatch(@Param("fields") List<ImportExportTemplateFieldRecord> fields);

    int deleteByTemplateId(@Param("templateId") Long templateId);

    List<ImportExportTemplateFieldRecord> findByTemplateId(@Param("templateId") Long templateId);
}
