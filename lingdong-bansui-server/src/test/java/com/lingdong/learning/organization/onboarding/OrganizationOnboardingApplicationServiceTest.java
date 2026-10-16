package com.lingdong.learning.organization.onboarding;
import com.lingdong.learning.organization.onboarding.domain.OnboardingState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OrganizationOnboardingApplicationServiceTest {
 @Test void staleReviewCannotOverwriteResubmission(){assertThrows(IllegalStateException.class,()->OnboardingState.PENDING.requirePending(3,2,1,1));}
 @Test void withdrawalCannotBeReviewed(){assertThrows(IllegalStateException.class,()->OnboardingState.WITHDRAWN.requirePending(2,1,2,1));}
 @Test void onlyRejectedOrWithdrawnCanResubmit(){for(var state:new OnboardingState[]{OnboardingState.PENDING,OnboardingState.APPROVED})assertThrows(IllegalStateException.class,()->state.requireResubmittable(1,1,1,1));}
 @Test void currentPendingBatchIsAccepted(){assertDoesNotThrow(()->OnboardingState.PENDING.requirePending(1,1,1,1));}
}
