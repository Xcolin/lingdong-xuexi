package com.lingdong.learning.organization.onboarding.application;
import org.junit.jupiter.api.Test;import static org.mockito.Mockito.*;import static org.assertj.core.api.Assertions.*;
class OrganizationOwnerGuardTest {
 @Test void ownerCannotBeRemovedEvenWithAnotherAdministrator(){var mapper=mock(OrganizationOwnerMapper.class);when(mapper.lockOwner(2L)).thenReturn(1L);var guard=new OrganizationOwnerGuard(mapper);assertThatThrownBy(()->guard.requireRemovable(1L,2L)).hasMessageContaining("转交");}
 @Test void lastAdministratorCannotBeRemoved(){var mapper=mock(OrganizationOwnerMapper.class);when(mapper.isAdministrator(1L,2L)).thenReturn(true);when(mapper.activeAdministrators(2L)).thenReturn(1);assertThatThrownBy(()->new OrganizationOwnerGuard(mapper).requireRemovable(1L,2L)).hasMessageContaining("最后");}
 @Test void ordinaryMemberRemovalIsUnchanged(){var mapper=mock(OrganizationOwnerMapper.class);new OrganizationOwnerGuard(mapper).requireRemovable(1L,2L);verify(mapper,never()).activeAdministrators(any());}
}
