package com.lingdong.learning.organization.onboarding;

import com.lingdong.learning.organization.onboarding.application.OrganizationOnboardingService;
import com.lingdong.learning.organization.onboarding.infrastructure.persistence.*;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.audit.infrastructure.persistence.SystemTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnboardingEmailContactTest {
    @Test void emailOnlyAccountCanSubmitInstitutionApplicationWithoutSms() {
        UserMapper users=mock(UserMapper.class);
        when(users.findByIdForUpdate(10L)).thenReturn(User.create(10L,"new-user","新用户",null,UserType.PERSONAL));
        OnboardingMapper mapper=mock(OnboardingMapper.class);
        OrganizationMapper organizations=mock(OrganizationMapper.class);
        when(organizations.findByIdForUpdate(30L)).thenReturn(com.lingdong.learning.organization.domain.Organization.create(30L,null,"ROOT","REGION","区域","REGION","/REGION/",1,null));
        IdGenerator ids=mock(IdGenerator.class);when(ids.nextId()).thenReturn(100L,101L,102L);
        SystemTaskMapper tasks=mock(SystemTaskMapper.class);
        when(tasks.updateSubmission(anyLong(),any())).thenReturn(1);
        when(mapper.lock(100L)).thenReturn(java.util.Map.of("id",100L,"applicantId",10L,"status","PENDING","taskId",101L));
        var service=new OrganizationOnboardingService(mapper,users,organizations,mock(UserOrganizationMapper.class),
            mock(UserRoleMapper.class),mock(RoleMapper.class),mock(OrganizationAdminMapper.class),mock(ParentAuthenticationMapper.class),ids,tasks);
        var email=mock(EmailVerificationMapper.class);when(email.verifiedEmail(10L)).thenReturn("new@example.com");
        var mobile=mock(MobileVerificationMapper.class);
        ReflectionTestUtils.setField(service,"emailVerification",email);
        ReflectionTestUtils.setField(service,"mobileVerification",mobile);
        ReflectionTestUtils.setField(service,"features",mock(com.lingdong.learning.feature.application.FeatureAccessService.class));
        assertThat(service.submit(10L,new OrganizationOnboardingService.Form("SCHOOL","新学校",30L,"联系人",null,null))).containsEntry("status","PENDING");
        var row=org.mockito.ArgumentCaptor.forClass(java.util.Map.class);verify(mapper).insert(row.capture());
        assertThat(row.getValue()).containsEntry("email","new@example.com").containsEntry("mobile",null);
        verifyNoInteractions(mobile);
    }
    @Test void contactContextDoesNotRequireMobileAndExplicitlyReportsEmailVerification() {
        UserMapper users = mock(UserMapper.class);
        when(users.findByIdForUpdate(10L)).thenReturn(User.create(10L,"new-user","新用户",null,UserType.PERSONAL));
        var service = new OrganizationOnboardingService(mock(OnboardingMapper.class),users,mock(OrganizationMapper.class),
            mock(UserOrganizationMapper.class),mock(UserRoleMapper.class),mock(RoleMapper.class),
            mock(OrganizationAdminMapper.class),mock(ParentAuthenticationMapper.class),mock(IdGenerator.class),mock(SystemTaskMapper.class));
        ReflectionTestUtils.setField(service,"mobileVerification",mock(MobileVerificationMapper.class));
        assertThat(service.context(10L)).containsEntry("verifiedMobile",false).containsEntry("verifiedEmail",false);
    }
}
