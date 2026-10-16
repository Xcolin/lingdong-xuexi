package com.lingdong.learning.organization.web;

import com.lingdong.learning.dictionary.application.DictionaryQueryService;
import com.lingdong.learning.dictionary.domain.DictionaryItem;
import com.lingdong.learning.feature.application.FeatureAccessService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrganizationDivisionControllerTest {
    @Test
    void usesOnlySelectableAdministrativeDivisionItems() {
        var dictionaries = mock(DictionaryQueryService.class);
        var features = mock(FeatureAccessService.class);
        when(dictionaries.findEnabledItems("ADMIN_DIVISION")).thenReturn(List.of(
                DictionaryItem.enabled(1L, 2L, "500000", "重庆市", 1, false)));
        var result = new OrganizationDivisionController(dictionaries, features).options();
        assertEquals(List.of(new OrganizationDivisionController.DivisionOption("500000", "重庆市")), result);
        verify(features).requireEnabled("ORGANIZATION_MANAGEMENT", null);
        verify(dictionaries).findEnabledItems("ADMIN_DIVISION");
        verifyNoMoreInteractions(dictionaries);
    }

    @Test
    void disabledOrganizationFeatureDoesNotReadDictionary() {
        var dictionaries = mock(DictionaryQueryService.class);
        var features = mock(FeatureAccessService.class);
        doThrow(new IllegalStateException("disabled")).when(features).requireEnabled("ORGANIZATION_MANAGEMENT", null);
        assertThrows(IllegalStateException.class, () -> new OrganizationDivisionController(dictionaries, features).options());
        verifyNoInteractions(dictionaries);
    }
}
