package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewMapper;
import com.lingdong.learning.learningtask.application.CurrentStudentAccessService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GrowthReviewMiniappFamilyReadTest {
 private final GrowthReviewMapper reviews=mock(GrowthReviewMapper.class);
 private final ParentStudentMapper children=mock(ParentStudentMapper.class);
 private final PermissionDecisionService grants=mock(PermissionDecisionService.class);
 private final ParentActorAccessService actors=mock(ParentActorAccessService.class);
 private final GrowthReviewQueryService service=new GrowthReviewQueryService(reviews,
   mock(CurrentStudentAccessService.class),children,mock(FeatureAccessService.class),
   mock(IdGenerator.class),Clock.systemUTC(),grants,mock(UserRoleMapper.class),actors,mock(GrowthReviewGenerationService.class));
 private final AuthenticatedUser mini=new AuthenticatedUser(1L,2L,"parent","parent",AuthClientType.MINIAPP,List.of("PARENT"));
 @Test void grantAndActorAreRequiredBeforeFamilyScopeQuery() {
  assertThatThrownBy(()->service.findMiniappChildren(mini)).isInstanceOf(SystemOperationAccessDeniedException.class);
  verifyNoInteractions(children);
  when(grants.isAllowed(1L,PermissionClient.MINIAPP,"MINIAPP_GROWTH_REVIEW_READ_CHILD")).thenReturn(true);
  doThrow(new SystemOperationAccessDeniedException("actor")).when(actors).require(mini);
  assertThatThrownBy(()->service.findMiniappChildren(mini)).isInstanceOf(SystemOperationAccessDeniedException.class);
  verifyNoInteractions(children);
 }
 @Test void optionsListAndDetailUseSameMiniappReadScope() {
  when(grants.isAllowed(1L,PermissionClient.MINIAPP,"MINIAPP_GROWTH_REVIEW_READ_CHILD")).thenReturn(true);
  when(children.findMiniappReviewChildren(1L)).thenReturn(List.of(new GrowthReviewQueryService.ChildOption("10","A")));
  assertThat(service.findMiniappChildren(mini)).extracting(GrowthReviewQueryService.ChildOption::studentId).containsExactly("10");
  when(children.existsMiniappReviewChild(1L,10L)).thenReturn(true);
  when(reviews.findCurrentByStudent(10L,com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType.WEEK,0,20)).thenReturn(List.of());
  assertThat(service.findMiniappChildWeeklyReviews(mini,10L,1,20).total()).isZero();
  assertThatThrownBy(()->service.findMiniappChildWeeklyReview(mini,10L,99L)).isInstanceOf(ResourceNotFoundException.class);
  verify(reviews).findCurrentDetail(10L,99L);
  assertThatThrownBy(()->service.findMiniappChildWeeklyReviews(mini,20L,1,20)).isInstanceOf(ResourceNotFoundException.class);
  assertThatThrownBy(()->service.findMiniappChildWeeklyReview(mini,20L,99L)).isInstanceOf(ResourceNotFoundException.class);
  verify(reviews,never()).findCurrentDetail(20L,99L);
  verify(children,never()).existsActiveByParentAndStudent(anyLong(),anyLong());
 }
 @Test void webReadStillRequiresGuardianAndNeverUsesFamilyFallback() {
  var web=new AuthenticatedUser(1L,2L,"parent","parent",AuthClientType.WEB,List.of("PARENT"));
  assertThatThrownBy(()->service.findChildReview(web,10L,99L)).isInstanceOf(ResourceNotFoundException.class);
  verify(children).existsActiveByParentAndStudent(1L,10L);
  verify(children,never()).existsMiniappReviewChild(anyLong(),anyLong());
 }
}
