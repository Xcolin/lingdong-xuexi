package com.lingdong.learning.teacher.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lingdong.learning.dictionary.application.DictionaryQueryService;
import com.lingdong.learning.dictionary.domain.DictionaryItem;
import java.util.List;
class TeacherSubjectDictionaryTest {
 @Test void missingConfigurationDoesNotInventSubjectsOrBlockClear(){var query=mock(DictionaryQueryService.class);var dictionary=new TeacherSubjectDictionary(query,"");assertThat(dictionary.options()).isEmpty();assertThat(dictionary.validate(null)).isNull();assertThatThrownBy(()->dictionary.validate("MATH")).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(query);}
 @Test void onlyRealEnabledDictionaryCodeIsAccepted(){var query=mock(DictionaryQueryService.class);when(query.findEnabledItems("TEACHER_SUBJECT")).thenReturn(List.of(DictionaryItem.enabled(1L,2L,"MATH","数学",1,false)));var dictionary=new TeacherSubjectDictionary(query,"TEACHER_SUBJECT");assertThat(dictionary.options()).hasSize(1);assertThat(dictionary.validate(" MATH ")).isEqualTo("MATH");assertThatThrownBy(()->dictionary.validate("ART")).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->dictionary.validate("  ")).isInstanceOf(IllegalArgumentException.class);assertThat(dictionary.validate(null)).isNull();}
}
