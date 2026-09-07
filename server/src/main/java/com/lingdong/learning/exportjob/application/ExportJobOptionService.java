package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.exportjob.application.adapter.ExportAdapterRegistry;
import com.lingdong.learning.exportjob.application.adapter.ExportDatasetAdapter;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointQueryMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointStudentOptionRow;
import com.lingdong.learning.templateconfig.domain.TemplateType;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 返回当前账号可创建导出所需的受控选项。 */
@Service
public class ExportJobOptionService {
    private final ExportJobAccessService accessService;
    private final ExportAdapterRegistry registry;
    private final ImportExportTemplateMapper templateMapper;
    private final GrowthPointQueryMapper growthPointMapper;

    public ExportJobOptionService(
            ExportJobAccessService accessService,
            ExportAdapterRegistry registry,
            ImportExportTemplateMapper templateMapper,
            GrowthPointQueryMapper growthPointMapper
    ) {
        this.accessService = accessService;
        this.registry = registry;
        this.templateMapper = templateMapper;
        this.growthPointMapper = growthPointMapper;
    }

    public ExportJobOptions findOptions(Long userId, ExportJobType type) {
        if (userId == null || type == null) {
            throw new IllegalArgumentException("操作人和导出类型不能为空");
        }
        accessService.requireFeatures();
        ExportDatasetAdapter adapter = registry.require(type);
        List<GrowthPointStudentOptionRow> students;
        if (adapter.sensitive()) {
            accessService.requireSensitiveSubmit(userId);
            students = List.of();
        } else {
            students = growthPointMapper.findPrimaryStudentsByParentUserId(userId);
            if (!students.isEmpty()) {
                accessService.requireOrdinaryCreate(userId, students.get(0).studentId());
            } else {
                accessService.requireListRead(userId);
            }
        }
        var template = templateMapper.findCurrentDefault("REPORT", TemplateType.EXPORT);
        if (template == null) {
            throw new IllegalStateException("导出模板未配置");
        }
        return new ExportJobOptions(type, template.templateName(), template.version(),
                adapter.columns(), students, adapter.sensitive());
    }
}
