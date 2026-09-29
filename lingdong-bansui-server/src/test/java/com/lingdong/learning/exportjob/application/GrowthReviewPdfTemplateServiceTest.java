package com.lingdong.learning.exportjob.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.growthpoint.infrastructure.pdf.GrowthReviewPdfRenderer.Template;
import com.lingdong.learning.templateconfig.domain.*;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateMapper;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 统一模板引用和受限配置解析，不执行模板提供的脚本或资源。 */
class GrowthReviewPdfTemplateServiceTest {
    private final ImportExportTemplateMapper mapper = mock(ImportExportTemplateMapper.class);
    private final ManagedAttachmentContentService content = mock(ManagedAttachmentContentService.class);
    private final GrowthReviewPdfTemplateService service = new GrowthReviewPdfTemplateService(mapper, content, new ObjectMapper());

    @Test void listsOnlyValidEnabledReportTemplatesWithStableModes() {
        configure(valid(), ImportExportTemplateStatus.ENABLED);
        var template = mapper.findById(1L);
        when(mapper.findAvailableReportExports())
                .thenReturn(java.util.List.of(template));
        var options = service.findOptions();
        assertThat(options).hasSize(1);
        assertThat(options.get(0).id()).isEqualTo("1");
        assertThat(options.get(0).modes()).containsExactly(Template.SIMPLE, Template.DETAILED);
        configure("{}", ImportExportTemplateStatus.ENABLED);
        assertThat(service.findOptions()).isEmpty();
    }

    @Test void validatesManagedTemplateVersionAndSelectedMode() {
        configure(valid(), ImportExportTemplateStatus.ENABLED);
        assertThatCode(() -> service.requireForCreation(1L, "复盘模板", "V1", Template.SIMPLE)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireForCreation(1L, "复盘模板", "V1", Template.DETAILED)).doesNotThrowAnyException();
        verify(content, times(2)).read(2L);
    }

    @Test void rejectsDisabledAndMismatchedVersionBeforeReadingFile() {
        configure(valid(), ImportExportTemplateStatus.DISABLED);
        assertThatThrownBy(() -> service.requireForCreation(1L, "复盘模板", "V1", Template.SIMPLE)).isInstanceOf(IllegalArgumentException.class);
        configure(valid(), ImportExportTemplateStatus.ENABLED);
        assertThatThrownBy(() -> service.requireForCreation(1L, "复盘模板", "V2", Template.SIMPLE)).isInstanceOf(IllegalArgumentException.class);
        verify(content, never()).read(any());
    }

    @Test void rejectsUnconfiguredMode() {
        configure(valid().replace("\"SIMPLE\",", ""), ImportExportTemplateStatus.ENABLED);
        assertThatThrownBy(() -> service.requireForCreation(1L, "复盘模板", "V1", Template.SIMPLE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsUnknownFieldsDuplicateKeysAndTrailingDocuments() {
        for (var input : new String[]{valid().replace("{", "{\"url\":\"https://example.invalid\","),
                valid().replace("{", "{\"schemaVersion\":2,"), valid() + " {}"}) {
            configure(input, ImportExportTemplateStatus.ENABLED);
            assertThatThrownBy(() -> service.requireForCreation(1L, "复盘模板", "V1", Template.SIMPLE)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void rejectsUnsupportedSchemaWrongReportAndDuplicateModes() {
        for (var input : new String[]{valid().replace(":1", ":2"), valid().replace("GROWTH_REVIEW", "OTHER"),
                valid().replace("DETAILED", "SIMPLE"), valid().replace(":1", ":18446744073709551617"), "[]", "null"}) {
            configure(input, ImportExportTemplateStatus.ENABLED);
            assertThatThrownBy(() -> service.requireForCreation(1L, "复盘模板", "V1", Template.SIMPLE)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void rejectsOversizedFile() {
        configure(" ".repeat(65537), ImportExportTemplateStatus.ENABLED);
        assertThatThrownBy(() -> service.requireForCreation(1L, "复盘模板", "V1", Template.SIMPLE)).isInstanceOf(IllegalArgumentException.class);
    }

    private String valid() { return "{\"schemaVersion\":1,\"reportType\":\"GROWTH_REVIEW\",\"templates\":[\"SIMPLE\",\"DETAILED\"]}"; }
    private void configure(String text, ImportExportTemplateStatus status) {
        when(mapper.findById(1L)).thenReturn(new ImportExportTemplateRecord(1L, "复盘模板", TemplateType.EXPORT,
                "REPORT", "V1", 2L, false, "TEST", status, 0L, null, null, "review.json", "application/json", 100L));
        when(content.read(2L)).thenReturn(new AttachmentContentView("review.json", "application/json", text.getBytes(StandardCharsets.UTF_8)));
    }
}
