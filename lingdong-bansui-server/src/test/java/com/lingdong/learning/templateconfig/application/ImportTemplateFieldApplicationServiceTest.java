package com.lingdong.learning.templateconfig.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.dictionary.infrastructure.persistence.DictionaryItemMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateRecord;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateStatus;
import com.lingdong.learning.templateconfig.domain.ImportTemplateFieldDataType;
import com.lingdong.learning.templateconfig.domain.TemplateType;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateFieldMapper;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportTemplateFieldApplicationServiceTest {
    private static final long OPERATOR_ID = 1874244142494646701L;
    private static final long TEMPLATE_ID = 1874244142494646702L;

    private ImportExportTemplateMapper templateMapper;
    private ImportExportTemplateFieldMapper fieldMapper;
    private ImportTemplateUsageQuery usageQuery;
    private PermissionDecisionService permissionDecisionService;
    private DictionaryItemMapper dictionaryItemMapper;
    private IdGenerator idGenerator;
    private ImportTemplateFieldApplicationService service;

    @BeforeEach
    void setUp() {
        templateMapper = mock(ImportExportTemplateMapper.class);
        fieldMapper = mock(ImportExportTemplateFieldMapper.class);
        usageQuery = mock(ImportTemplateUsageQuery.class);
        permissionDecisionService = mock(PermissionDecisionService.class);
        dictionaryItemMapper = mock(DictionaryItemMapper.class);
        idGenerator = mock(IdGenerator.class);
        service = new ImportTemplateFieldApplicationService(
                templateMapper, fieldMapper, usageQuery, permissionDecisionService,
                dictionaryItemMapper, idGenerator
        );
        when(permissionDecisionService.isAllowed(
                OPERATOR_ID, PermissionClient.WEB, "IMPORT_EXPORT_TEMPLATE_MANAGE"))
                .thenReturn(true);
    }

    @Test
    void replacesFieldsOnlyForUnusedDisabledImportTemplateWithExpectedVersion() {
        when(templateMapper.findById(TEMPLATE_ID)).thenReturn(template(
                TemplateType.IMPORT, ImportExportTemplateStatus.DISABLED, 2L));
        when(usageQuery.hasAnyJob(TEMPLATE_ID)).thenReturn(false);
        when(templateMapper.incrementVersionIfDisabled(TEMPLATE_ID, 2L)).thenReturn(1);
        when(idGenerator.nextId()).thenReturn(
                1874244142494646703L, 1874244142494646704L);
        when(fieldMapper.insertBatch(anyList())).thenReturn(2);

        List<ImportTemplateFieldView> result = service.replace(new ReplaceImportTemplateFieldsCommand(
                OPERATOR_ID, TEMPLATE_ID, 2L, List.of(
                new ImportTemplateFieldInput(
                        "STUDENT_CODE", "学生账号", ImportTemplateFieldDataType.TEXT,
                        true, 8, null, 10),
                new ImportTemplateFieldInput(
                        "BIRTH_DATE", "出生日期", ImportTemplateFieldDataType.DATE,
                        false, null, null, 20)
        )));

        assertThat(result).extracting(ImportTemplateFieldView::fieldCode)
                .containsExactly("STUDENT_CODE", "BIRTH_DATE");
        assertThat(result).allSatisfy(field ->
                assertThat(Long.toString(field.id())).hasSize(19));
        verify(fieldMapper).deleteByTemplateId(TEMPLATE_ID);
        verify(templateMapper).incrementVersionIfDisabled(TEMPLATE_ID, 2L);
    }

    @Test
    void rejectsInvalidFieldDefinitionsBeforeChangingPersistence() {
        when(templateMapper.findById(TEMPLATE_ID)).thenReturn(template(
                TemplateType.IMPORT, ImportExportTemplateStatus.DISABLED, 0L));

        assertThatThrownBy(() -> service.replace(new ReplaceImportTemplateFieldsCommand(
                OPERATOR_ID, TEMPLATE_ID, 0L, List.of()
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少配置一个字段");

        assertThatThrownBy(() -> service.replace(new ReplaceImportTemplateFieldsCommand(
                OPERATOR_ID, TEMPLATE_ID, 0L, List.of(
                new ImportTemplateFieldInput(
                        "student-code", "学生账号", ImportTemplateFieldDataType.TEXT,
                        true, 8, null, 10)
        )))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("字段编码");

        assertThatThrownBy(() -> service.replace(new ReplaceImportTemplateFieldsCommand(
                OPERATOR_ID, TEMPLATE_ID, 0L, List.of(
                new ImportTemplateFieldInput(
                        "ACTIVE", "是否启用", ImportTemplateFieldDataType.BOOLEAN,
                        false, null, "YES_NO", 10)
        )))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("布尔字段不能配置数据字典");

        verify(fieldMapper, never()).deleteByTemplateId(TEMPLATE_ID);
    }

    @Test
    void rejectsEnabledExportReferencedAndStaleTemplates() {
        when(templateMapper.findById(TEMPLATE_ID)).thenReturn(template(
                TemplateType.EXPORT, ImportExportTemplateStatus.DISABLED, 0L));
        assertThatThrownBy(() -> service.replace(command(0L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("仅导入模板");

        when(templateMapper.findById(TEMPLATE_ID)).thenReturn(template(
                TemplateType.IMPORT, ImportExportTemplateStatus.ENABLED, 0L));
        assertThatThrownBy(() -> service.replace(command(0L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("停用");

        when(templateMapper.findById(TEMPLATE_ID)).thenReturn(template(
                TemplateType.IMPORT, ImportExportTemplateStatus.DISABLED, 0L));
        when(usageQuery.hasAnyJob(TEMPLATE_ID)).thenReturn(true);
        assertThatThrownBy(() -> service.replace(command(0L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已有导入作业引用");

        when(usageQuery.hasAnyJob(TEMPLATE_ID)).thenReturn(false);
        assertThatThrownBy(() -> service.replace(command(3L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("版本已变化");
    }

    private ReplaceImportTemplateFieldsCommand command(long versionNo) {
        return new ReplaceImportTemplateFieldsCommand(OPERATOR_ID, TEMPLATE_ID, versionNo, List.of(
                new ImportTemplateFieldInput(
                        "STUDENT_CODE", "学生账号", ImportTemplateFieldDataType.TEXT,
                        true, 8, null, 10)
        ));
    }

    private ImportExportTemplateRecord template(
            TemplateType type,
            ImportExportTemplateStatus status,
            long versionNo
    ) {
        return new ImportExportTemplateRecord(
                TEMPLATE_ID, "测试模板", type, "STUDENT", "V1",
                1874244142494646799L, false, "ID:" + TEMPLATE_ID, status,
                versionNo, null, null, "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 128L
        );
    }
}
