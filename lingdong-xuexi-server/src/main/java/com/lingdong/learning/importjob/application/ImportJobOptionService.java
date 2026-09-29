package com.lingdong.learning.importjob.application;

import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateStatus;
import com.lingdong.learning.templateconfig.domain.TemplateType;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateMapper;
import org.springframework.stereotype.Service;

/** 查询创建导入校验作业所需的受控选项。 */
@Service
public class ImportJobOptionService {
    private final ImportJobAccessService accessService;
    private final ImportExportTemplateMapper templateMapper;
    private final OrganizationDataScopeService dataScopeService;

    public ImportJobOptionService(
            ImportJobAccessService accessService,
            ImportExportTemplateMapper templateMapper,
            OrganizationDataScopeService dataScopeService
    ) {
        this.accessService = accessService;
        this.templateMapper = templateMapper;
        this.dataScopeService = dataScopeService;
    }

    public ImportJobOptionsView findOptions(Long operatorId) {
        accessService.requireReadPermission(operatorId);
        return new ImportJobOptionsView(
                templateMapper.findAll(null, TemplateType.IMPORT, null,
                                ImportExportTemplateStatus.ENABLED).stream()
                        .map(template -> new ImportJobOptionView(
                                template.id().toString(),
                                template.templateName() + "（" + template.version() + "）"))
                        .toList(),
                dataScopeService.findAccessibleOrganizations(operatorId).stream()
                        .map(organization -> new ImportJobOptionView(
                                organization.id().toString(), organization.name()))
                        .toList()
        );
    }
}
