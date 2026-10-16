package com.lingdong.learning.organization.application;

import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.iam.audit.application.*;
import com.lingdong.learning.organization.domain.*;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationTypeMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrganizationTypeLifecycleApplicationServiceTest {
    private final OrganizationTypeMapper mapper=mock(OrganizationTypeMapper.class);
    private final com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper userRoles=mock(com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper.class);
    private final PermissionDecisionService decisions=mock(PermissionDecisionService.class);
    private final OrganizationDataScopeService scopes=mock(OrganizationDataScopeService.class);
    private final IamChangeAuditService audit=mock(IamChangeAuditService.class);
    private final OrganizationTypeLifecycleApplicationService service=new OrganizationTypeLifecycleApplicationService(mapper,decisions,scopes,mock(FeatureAccessService.class),audit,userRoles);
    private void allow() {
        when(decisions.isAllowed(eq(1L),eq(PermissionClient.WEB),anyString())).thenReturn(true);
        when(scopes.resolve(1L)).thenReturn(com.lingdong.learning.datascope.application.OrganizationDataScope.all(true));
        when(mapper.lockById(2L)).thenReturn(OrganizationType.custom(2L,"CUSTOM","自定义",0));
    }
    @Test void deniesMissingIndependentPermissionBeforeReadingType() {
        assertThatThrownBy(()->service.delete(1L,2L)).hasMessageContaining("权限");
        verifyNoInteractions(mapper);
    }
    @Test void builtInCannotBeChangedOrDeleted() {
        allow(); when(mapper.lockById(2L)).thenReturn(new OrganizationType(2L,"BASE","内置",true,OrganizationStatus.ENABLED,0,null,null));
        assertThatThrownBy(()->service.update(1L,2L,"新名",1)).hasMessageContaining("内置");
        assertThatThrownBy(()->service.changeStatus(1L,2L,OrganizationStatus.DISABLED)).hasMessageContaining("内置");
        assertThatThrownBy(()->service.delete(1L,2L)).hasMessageContaining("内置");
    }
    @Test void referencedTypeCannotBeDeleted() {
        allow(); when(mapper.hasOrganizations("CUSTOM")).thenReturn(true);
        assertThatThrownBy(()->service.delete(1L,2L)).hasMessageContaining("引用");
        verify(mapper,never()).deleteById(any());
    }
    @Test void validatesNameAndOrderAndUpdatesOnlyDetails() {
        allow(); service.update(1L,2L," 新名 ",10);
        verify(mapper).updateDetails(2L,"新名",10);
        assertThatThrownBy(()->service.update(1L,2L,"a".repeat(33),0)).hasMessageContaining("32");
        assertThatThrownBy(()->service.update(1L,2L,"名称",-1)).hasMessageContaining("排序");
    }
    @Test void deletionAppendsAudit() {
        allow(); service.delete(1L,2L);
        verify(mapper).deleteById(2L);
        verify(audit).record(IamChangeAuditEventType.ORGANIZATION_TYPE_DELETE,1L,IamChangeTargetType.ORGANIZATION_TYPE,2L,null,null,"CUSTOM",null);
    }

    @Test void administratorCanEditBuiltInDetails() {
        when(userRoles.findEnabledRoleCodesByUserId(1L)).thenReturn(java.util.List.of("SYS_ADMIN"));
        allow(); when(mapper.lockById(2L)).thenReturn(new OrganizationType(2L,"SCHOOL","学校",true,OrganizationStatus.ENABLED,0,null,null));
        service.update(1L,2L,"新学校",10);
        verify(mapper).updateDetails(2L,"新学校",10);
    }
}
