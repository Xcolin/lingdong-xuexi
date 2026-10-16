package com.lingdong.learning.organization.onboarding;
import com.lingdong.learning.organization.onboarding.application.MobileVerificationService;
import com.lingdong.learning.organization.onboarding.infrastructure.persistence.MobileVerificationMapper;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class MobileVerificationServiceTest {
 @Test void storesProofOnlyAfterSuccessfulCurrentMobileConsumption(){var users=mock(UserMapper.class);var proofs=mock(MobileVerificationMapper.class);var sms=mock(ParentSmsVerificationService.class);when(users.findByIdForUpdate(1L)).thenReturn(User.create(1L,"org","机构账号","13800000000",UserType.ORGANIZATION));var service=new MobileVerificationService(users,proofs,sms);service.verify(1L,AuthClientType.WEB,"123456");var order=inOrder(sms,proofs);order.verify(sms).verifyAndConsume("13800000000",ParentSmsPurpose.ORGANIZATION_ONBOARDING,AuthClientType.WEB,"123456");order.verify(proofs).save(1L,"13800000000");}
 @Test void invalidCodeDoesNotPersistProof(){var users=mock(UserMapper.class);var proofs=mock(MobileVerificationMapper.class);var sms=mock(ParentSmsVerificationService.class);when(users.findByIdForUpdate(1L)).thenReturn(User.create(1L,"org","机构账号","13800000000",UserType.ORGANIZATION));doThrow(new ParentSmsVerificationFailedException()).when(sms).verifyAndConsume(any(),any(),any(),any());assertThrows(ParentSmsVerificationFailedException.class,()->new MobileVerificationService(users,proofs,sms).verify(1L,AuthClientType.MINIAPP,"123456"));verifyNoInteractions(proofs);}
 @Test void studentCannotRequestCode(){var users=mock(UserMapper.class);var proofs=mock(MobileVerificationMapper.class);var sms=mock(ParentSmsVerificationService.class);when(users.findByIdForUpdate(1L)).thenReturn(User.create(1L,"student","学生","13800000000",UserType.STUDENT));assertThrows(IllegalArgumentException.class,()->new MobileVerificationService(users,proofs,sms).issue(1L,AuthClientType.WEB));verifyNoInteractions(sms,proofs);}
}
