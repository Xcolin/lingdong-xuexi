package com.lingdong.learning.feature.web;
import com.lingdong.learning.feature.application.FeatureAccessService;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class GrowthReviewPdfCapabilityTest {
    @Test void exposesGenerationOnlyForWebWithAllDependenciesEnabled() {
        var features = mock(FeatureAccessService.class);
        when(features.isEnabled(anyString(), isNull())).thenReturn(true);
        var controller = new PublicCapabilityController(features);
        assertThat(controller.capabilities("WEB").growthReviewPdfExportEnabled()).isTrue();
        assertThat(controller.capabilities("MINIAPP").growthReviewPdfExportEnabled()).isFalse();
        when(features.isEnabled("GROWTH_REVIEW_PDF_EXPORT", null)).thenReturn(false);
        assertThat(controller.capabilities("WEB").growthReviewPdfExportEnabled()).isFalse();
    }
}
