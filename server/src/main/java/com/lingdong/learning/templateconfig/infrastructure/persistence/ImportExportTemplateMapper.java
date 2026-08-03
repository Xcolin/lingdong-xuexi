package com.lingdong.learning.templateconfig.infrastructure.persistence;

import com.lingdong.learning.templateconfig.domain.ImportExportTemplateRecord;
import com.lingdong.learning.templateconfig.domain.TemplateType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 导入导出模板的 MyBatis 持久化边界，SQL 保持在 XML 文件中。 */
@Mapper
public interface ImportExportTemplateMapper {
    int insert(@Param("template") ImportExportTemplateRecord template);

    ImportExportTemplateRecord findById(@Param("id") Long id);

    ImportExportTemplateRecord findByModuleTypeAndVersion(
            @Param("moduleCode") String moduleCode,
            @Param("templateType") TemplateType templateType,
            @Param("version") String version
    );

    ImportExportTemplateRecord findCurrentDefault(
            @Param("moduleCode") String moduleCode,
            @Param("templateType") TemplateType templateType
    );

    int clearCurrentDefault(@Param("moduleCode") String moduleCode, @Param("templateType") TemplateType templateType);

    int markAsDefault(@Param("id") Long id);

    int disable(@Param("id") Long id);
}
