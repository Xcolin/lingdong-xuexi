package com.lingdong.learning.organization.application;
import org.junit.jupiter.api.Test;
import com.lingdong.learning.dictionary.application.DictionaryQueryService;
import com.lingdong.learning.dictionary.domain.DictionaryItem;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
class CourseCategoryDictionaryTest {
 @Test void unconfiguredDictionariesBlockSaveWithoutInventedValues(){var query=mock(DictionaryQueryService.class);var service=new CourseCategoryDictionary(query,"","");assertFalse(service.options().configured());assertThrows(IllegalArgumentException.class,()->service.validate(List.of("PRIMARY"),"CHINESE",""));verifyNoInteractions(query);}
 @Test void configuredEnabledItemsAreTheOnlyAcceptedValues(){var query=mock(DictionaryQueryService.class);when(query.findEnabledItems("FIXTURE_STAGE")).thenReturn(List.of(DictionaryItem.enabled(1L,11L,"S1","fixture stage",0,false)));when(query.findEnabledItems("FIXTURE_SUBJECT")).thenReturn(List.of(DictionaryItem.enabled(2L,12L,"A1","fixture subject",0,false)));var service=new CourseCategoryDictionary(query,"FIXTURE_STAGE","FIXTURE_SUBJECT");assertTrue(service.options().configured());assertDoesNotThrow(()->service.validate(List.of("S1"),"A1","description"));assertThrows(IllegalArgumentException.class,()->service.validate(List.of("OTHER"),"A1",""));assertThrows(IllegalArgumentException.class,()->service.validate(List.of("S1","S1"),"A1",""));assertThrows(IllegalArgumentException.class,()->service.validate(List.of("S1"),"OTHER",""));assertThrows(IllegalArgumentException.class,()->service.validate(List.of("S1"),"A1","x".repeat(101)));}
}
