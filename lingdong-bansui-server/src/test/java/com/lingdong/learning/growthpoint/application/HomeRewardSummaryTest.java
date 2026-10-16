package com.lingdong.learning.growthpoint.application;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.infrastructure.persistence.*;
import com.lingdong.learning.learningtask.application.CurrentStudentAccessService;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import org.junit.jupiter.api.Test;
import java.time.Clock;import java.util.List;
import static org.mockito.Mockito.*;import static org.assertj.core.api.Assertions.*;
class HomeRewardSummaryTest {
@Test void retainsFeatureAndActorGuardsAndSeparateStatuses(){
var mapper=mock(GrowthRewardExchangeMapper.class);var features=mock(FeatureAccessService.class);var parents=mock(ParentActorAccessService.class);
var service=new GrowthRewardExchangeService(mapper,mock(GrowthRewardMapper.class),mock(GrowthPointAccountMapper.class),mock(GrowthPointLedgerMapper.class),mock(ParentStudentMapper.class),mock(CurrentStudentAccessService.class),features,mock(IdGenerator.class),Clock.systemUTC(),parents);
var user=new AuthenticatedUser(1L,2L,"p","p",AuthClientType.MINIAPP,List.of("CUSTOM"));
when(mapper.countPendingByParent(1L,"PENDING_APPROVAL")).thenReturn(2L);when(mapper.countPendingByParent(1L,"PENDING_VERIFICATION")).thenReturn(3L);
assertThat(service.pendingSummary(user)).isEqualTo(new GrowthRewardExchangeService.PendingSummary(2,3));verify(features).requireEnabled("REWARD_EXCHANGE",null);verify(parents).require(user);
reset(mapper);doThrow(new IllegalStateException("disabled")).when(features).requireEnabled("REWARD_EXCHANGE",null);assertThatThrownBy(()->service.pendingSummary(user)).isInstanceOf(IllegalStateException.class);verifyNoInteractions(mapper);
reset(features);doThrow(new IllegalStateException("profile denied")).when(parents).require(user);assertThatThrownBy(()->service.pendingSummary(user)).isInstanceOf(IllegalStateException.class);verifyNoInteractions(mapper);
}
}
