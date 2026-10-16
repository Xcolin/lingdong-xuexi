package com.lingdong.learning.iam.application;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FamilyAdministratorEligibilityTest {
 @Test void createdParentAccountDoesNotRequireOnboardingProfile(){assertTrue(OrganizationMembershipService.familyAdministratorEligible(UserType.FAMILY,false));}
 @Test void personalAccountNeedsRealParentIdentity(){assertFalse(OrganizationMembershipService.familyAdministratorEligible(UserType.PERSONAL,false));assertTrue(OrganizationMembershipService.familyAdministratorEligible(UserType.PERSONAL,true));}
 @Test void studentCannotBecomeFamilyAdministrator(){assertFalse(OrganizationMembershipService.familyAdministratorEligible(UserType.STUDENT,false));assertFalse(OrganizationMembershipService.familyAdministratorEligible(UserType.STUDENT,true));}
}
