package com.lingdong.learning.feature.web;

import com.lingdong.learning.feature.application.FeatureAccessService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicCapabilityControllerTest {
    @Test
    void exposesTeacherManagementCapabilityToBothIndependentClients() {
        FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
        when(featureAccessService.isEnabled("TEACHER_MANAGEMENT", null)).thenReturn(true);
        PublicCapabilityController controller = new PublicCapabilityController(featureAccessService);

        assertThat(controller.capabilities("WEB").teacherManagementEnabled()).isTrue();
        assertThat(controller.capabilities("MINIAPP").teacherManagementEnabled()).isTrue();
    }

    @Test
    void exposesAttachmentServiceCapabilityToBothIndependentClients() {
        FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
        when(featureAccessService.isEnabled("ATTACHMENT_SERVICE", null)).thenReturn(true);
        PublicCapabilityController controller = new PublicCapabilityController(featureAccessService);

        assertThat(controller.capabilities("WEB").attachmentServiceEnabled()).isTrue();
        assertThat(controller.capabilities("MINIAPP").attachmentServiceEnabled()).isTrue();
    }

    @Test
    void exposesTemplateManagementOnlyToWebWhenBothRequiredFeaturesAreEnabled() {
        FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
        when(featureAccessService.isEnabled("IMPORT_EXPORT_TEMPLATE_MANAGEMENT", null)).thenReturn(true);
        when(featureAccessService.isEnabled("ATTACHMENT_SERVICE", null)).thenReturn(true);
        PublicCapabilityController controller = new PublicCapabilityController(featureAccessService);

        assertThat(controller.capabilities("WEB").importExportTemplateManagementEnabled()).isTrue();
        assertThat(controller.capabilities("MINIAPP").importExportTemplateManagementEnabled()).isFalse();

        when(featureAccessService.isEnabled("ATTACHMENT_SERVICE", null)).thenReturn(false);
        assertThat(controller.capabilities("WEB").importExportTemplateManagementEnabled()).isFalse();
    }

    @Test
    void exposesImportValidationOnlyToWebWhenAllThreeDependenciesAreEnabled() {
        FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
        when(featureAccessService.isEnabled("DATA_IMPORT_VALIDATION", null)).thenReturn(true);
        when(featureAccessService.isEnabled("IMPORT_EXPORT_TEMPLATE_MANAGEMENT", null)).thenReturn(true);
        when(featureAccessService.isEnabled("ATTACHMENT_SERVICE", null)).thenReturn(true);
        PublicCapabilityController controller = new PublicCapabilityController(featureAccessService);

        assertThat(controller.capabilities("WEB").dataImportValidationEnabled()).isTrue();
        assertThat(controller.capabilities("MINIAPP").dataImportValidationEnabled()).isFalse();

        when(featureAccessService.isEnabled("DATA_IMPORT_VALIDATION", null)).thenReturn(false);
        assertThat(controller.capabilities("WEB").dataImportValidationEnabled()).isFalse();
    }

    @Test
    void exposesDataExportOnlyToWebWhenAllThreeDependenciesAreEnabled() {
        FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
        when(featureAccessService.isEnabled("DATA_EXPORT", null)).thenReturn(true);
        when(featureAccessService.isEnabled("IMPORT_EXPORT_TEMPLATE_MANAGEMENT", null)).thenReturn(true);
        when(featureAccessService.isEnabled("ATTACHMENT_SERVICE", null)).thenReturn(true);
        PublicCapabilityController controller = new PublicCapabilityController(featureAccessService);

        assertThat(controller.capabilities("WEB").dataExportEnabled()).isTrue();
        assertThat(controller.capabilities("MINIAPP").dataExportEnabled()).isFalse();

        when(featureAccessService.isEnabled("ATTACHMENT_SERVICE", null)).thenReturn(false);
        assertThat(controller.capabilities("WEB").dataExportEnabled()).isFalse();
    }

    @Test
    void exposesStudentBatchImportOnlyToWebWhenAllFourDependenciesAreEnabled() {
        FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
        when(featureAccessService.isEnabled("STUDENT_BATCH_IMPORT", null)).thenReturn(true);
        when(featureAccessService.isEnabled("DATA_IMPORT_VALIDATION", null)).thenReturn(true);
        when(featureAccessService.isEnabled("IMPORT_EXPORT_TEMPLATE_MANAGEMENT", null)).thenReturn(true);
        when(featureAccessService.isEnabled("ATTACHMENT_SERVICE", null)).thenReturn(true);
        PublicCapabilityController controller = new PublicCapabilityController(featureAccessService);

        assertThat(controller.capabilities("WEB").studentBatchImportEnabled()).isTrue();
        assertThat(controller.capabilities("MINIAPP").studentBatchImportEnabled()).isFalse();

        when(featureAccessService.isEnabled("DATA_IMPORT_VALIDATION", null)).thenReturn(false);
        assertThat(controller.capabilities("WEB").studentBatchImportEnabled()).isFalse();
    }
}
