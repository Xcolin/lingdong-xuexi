package com.lingdong.learning.growthpoint.application;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.growthpoint.infrastructure.persistence.*;
import com.lingdong.learning.growthpoint.domain.*;
import com.lingdong.learning.learningtask.application.CurrentStudentAccessService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.growthpoint.web.SaveDailyReflectionRequest;
class GrowthReviewDailyReflectionTest {
 @Test void validatesEntireBatchBeforeAnyInsertAndKeepsDailyOnlyGuard() {
  var mapper=mock(GrowthReviewMapper.class);var access=mock(CurrentStudentAccessService.class);
  var actor=new AuthenticatedUser(1L,1L,"student","student",AuthClientType.MINIAPP,List.of("STUDENT"));
  when(access.require(actor)).thenReturn(Student.create(2L,"child","G1",1L));
  var service=new GrowthReviewQueryService(mapper,access,mock(com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper.class),mock(FeatureAccessService.class),mock(com.lingdong.learning.common.id.IdGenerator.class),Clock.fixed(Instant.parse("2026-10-14T01:00:00Z"),ZoneOffset.UTC),mock(com.lingdong.learning.permission.application.PermissionDecisionService.class),mock(com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper.class),mock(ParentActorAccessService.class));
  assertThatThrownBy(()->service.saveMyDailyReflection(actor,3L,new SaveDailyReflectionRequest("insight","difficulty"," ").toCommands())).isInstanceOf(IllegalArgumentException.class);
  verifyNoInteractions(mapper);
  var date=LocalDate.of(2026,10,14);var time=date.atStartOfDay();
  when(mapper.findCurrentDetail(2L,3L)).thenReturn(new GrowthReviewDetailRow(3L,2L,"child",GrowthReviewPeriodType.WEEK,date,date,4L,1,0,0,0,0,0,BigDecimal.ZERO,0L,0,time,time));
  assertThatThrownBy(()->service.saveMyDailyReflection(actor,3L,new SaveDailyReflectionRequest("insight","difficulty","plan").toCommands())).isInstanceOf(IllegalStateException.class);
  verify(mapper,never()).insertSupplement(any(),any(),any(),any(),any(),any(),any());
  when(mapper.findCurrentDetail(2L,3L)).thenReturn(new GrowthReviewDetailRow(3L,2L,"child",GrowthReviewPeriodType.DAY,date,date,4L,1,0,0,0,0,0,BigDecimal.ZERO,0L,0,time,time));
  when(mapper.insertSupplement(any(),any(),any(),any(),any(),any(),any())).thenReturn(1);
  var saved=service.saveMyDailyReflection(actor,3L,new SaveDailyReflectionRequest("insight","difficulty","plan").toCommands());
  assertThat(saved).extracting(GrowthReviewSupplementView::supplementType).containsExactly(GrowthReviewSupplementType.INSIGHT,GrowthReviewSupplementType.STRENGTH_WEAKNESS,GrowthReviewSupplementType.NEXT_PLAN);
  verify(mapper,times(3)).insertSupplement(any(),eq(3L),eq(1L),eq("STUDENT"),any(),any(),any());
 }
}
