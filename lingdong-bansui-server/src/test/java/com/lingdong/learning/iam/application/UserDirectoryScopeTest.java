package com.lingdong.learning.iam.application;

import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserDirectoryScopeTest {
    @Test void membershipsAreServerDerivedAndSharedByRowsAndCount() {
        UserMapper users=mock(UserMapper.class);
        UserOrganizationMapper memberships=mock(UserOrganizationMapper.class);
        PermissionDecisionService decisions=mock(PermissionDecisionService.class);
        when(decisions.isAllowed(1L, PermissionClient.WEB,"IAM_USER_LIST")).thenReturn(true);
        when(memberships.findDirectoryOrganizationIds(1L)).thenReturn(List.of(10L,11L,20L));
        when(users.findPage(any())).thenReturn(List.of());
        var service=new IamQueryApplicationService(users,mock(RoleMapper.class),mock(PermissionMapper.class),memberships,decisions);
        service.listUsers(1L,null,null,null,99L,1,20);
        var rows=ArgumentCaptor.forClass(UserDirectoryQuery.class);
        var count=ArgumentCaptor.forClass(UserDirectoryQuery.class);
        verify(users).findPage(rows.capture()); verify(users).count(count.capture());
        assertEquals(List.of(10L,11L,20L),rows.getValue().visibleOrganizationIds());
        assertEquals(99L,rows.getValue().organizationId());
        assertSame(rows.getValue(),count.getValue());
    }
    @Test void emptyMembershipAndOutsideDetailFailClosed() {
        UserMapper users=mock(UserMapper.class);
        UserOrganizationMapper memberships=mock(UserOrganizationMapper.class);
        PermissionDecisionService decisions=mock(PermissionDecisionService.class);
        when(decisions.isAllowed(1L, PermissionClient.WEB,"IAM_USER_READ")).thenReturn(true);
        when(memberships.findDirectoryOrganizationIds(1L)).thenReturn(List.of());
        when(memberships.findOrganizationIds(2L)).thenReturn(List.of(99L));
        var service=new IamQueryApplicationService(users,mock(RoleMapper.class),mock(PermissionMapper.class),memberships,decisions);
        assertEquals(List.of(),service.directoryScope(1L));
        assertThrows(ResourceNotFoundException.class,()->service.findUser(1L,2L));
        verify(users,never()).findById(any());
    }
    @Test void onlyEnabledSystemAdminUsesAllScope() {
        UserOrganizationMapper memberships=mock(UserOrganizationMapper.class);
        when(memberships.isEnabledSystemAdmin(1L)).thenReturn(true);
        var service=new IamQueryApplicationService(mock(UserMapper.class),mock(RoleMapper.class),mock(PermissionMapper.class),memberships,mock(PermissionDecisionService.class));
        assertNull(service.directoryScope(1L));
        verify(memberships,never()).findDirectoryOrganizationIds(any());
    }
}
