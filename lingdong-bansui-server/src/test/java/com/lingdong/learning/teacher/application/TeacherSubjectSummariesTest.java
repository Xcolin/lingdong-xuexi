package com.lingdong.learning.teacher.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import com.lingdong.learning.teacher.infrastructure.persistence.TeacherSubjectStore;
class TeacherSubjectSummariesTest {
 @Test void everyPairIsAuthorizedBeforeAnyMetadataRead() throws Exception {
  var f=new TeacherSubjectRulesTest();var method=TeacherSubjectService.class.getMethod("summaries",com.lingdong.learning.auth.application.AuthenticatedUser.class,List.class);
  when(f.members.exists(4L,2L)).thenReturn(false);
  assertThatThrownBy(()->method.invoke(f.service,f.actor,List.of(new TeacherSubjectStore.Key(4,2)))).hasCauseInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);verifyNoInteractions(f.store,f.dictionary);
 }
 @Test void oneBatchReturnsOnlyRealDictionaryNamesAndKeepsMissingMetadataEmpty() throws Exception {
  var f=new TeacherSubjectRulesTest();var method=TeacherSubjectService.class.getMethod("summaries",com.lingdong.learning.auth.application.AuthenticatedUser.class,List.class);var keys=List.of(new TeacherSubjectStore.Key(4,2));
  when(f.store.readBatch(keys)).thenReturn(List.of(new TeacherSubjectStore.Metadata(4,2,"MATH",1)));when(f.dictionary.options()).thenReturn(List.of(new TeacherSubjectDictionary.Option("MATH","数学")));
  var result=(List<?>)method.invoke(f.service,f.actor,keys);assertThat(result).hasSize(1);assertThat(result.toString()).contains("数学","MATH");verify(f.store).readBatch(keys);verify(f.store,never()).read(anyLong(),anyLong());
  when(f.store.readBatch(keys)).thenReturn(List.of());when(f.store.readClassCounts(keys)).thenReturn(Map.of(keys.get(0),2));
  var missing=(List<TeacherSubjectService.Summary>)method.invoke(f.service,f.actor,keys);assertThat(missing).hasSize(1);assertThat(missing.get(0).subjectCode()).isNull();assertThat(missing.get(0).subjectName()).isNull();assertThat(missing.get(0).classCount()).isEqualTo(2);
 }
}
