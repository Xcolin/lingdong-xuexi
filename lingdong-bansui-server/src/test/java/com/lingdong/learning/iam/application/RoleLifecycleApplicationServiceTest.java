package com.lingdong.learning.iam.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.iam.audit.application.*;
import com.lingdong.learning.iam.domain.*;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoleLifecycleApplicationServiceTest {
    private final RoleMapper mapper=mock(RoleMapper.class);
    private final IamChangeAuditService audit=mock(IamChangeAuditService.class);
    private final com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper userRoles=mock(com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper.class);
    private final PermissionDecisionService decisions=mock(PermissionDecisionService.class);
    private final RoleApplicationService service=new RoleApplicationService(mapper,mock(IdGenerator.class),audit,decisions,userRoles);
    @BeforeEach void setup() {
        when(decisions.isAllowed(eq(1L),eq(PermissionClient.WEB),anyString())).thenReturn(true);
        when(mapper.lockById(2L)).thenReturn(Role.custom(2L,"CUSTOM_ROLE","自定义",null,RoleDataScope.SELF));
    }
    @Test void permissionIsCheckedBeforeAnyRoleRead() {
        when(decisions.isAllowed(1L,PermissionClient.WEB,"IAM_ROLE_UPDATE")).thenReturn(false);
        assertThatThrownBy(()->service.updateRole(1L,2L,"新名",null)).isInstanceOf(SystemOperationAccessDeniedException.class);
        verifyNoInteractions(mapper);
    }
    @Test void rejectsBuiltInFlagAndTypeIndependently() {
        for (Role role: new Role[]{new Role(2L,"BASE","内置",RoleType.CUSTOM,RoleDataScope.ALL,true,RoleStatus.ENABLED,null,null,null),
                new Role(2L,"BASE","内置",RoleType.BUILT_IN,RoleDataScope.ALL,false,RoleStatus.ENABLED,null,null,null)}) {
            when(mapper.lockById(2L)).thenReturn(role);
            assertThatThrownBy(()->service.updateRole(1L,2L,"新名",null)).hasMessageContaining("内置");
            assertThatThrownBy(()->service.changeRoleStatus(1L,2L,RoleStatus.DISABLED)).hasMessageContaining("内置");
            assertThatThrownBy(()->service.deleteRole(1L,2L)).hasMessageContaining("内置");
        }
        verify(mapper,never()).deleteById(any());
    }
    @Test void assignedRoleCannotBeDeleted() {
        when(mapper.hasUserAssignments(2L)).thenReturn(true);
        assertThatThrownBy(()->service.deleteRole(1L,2L)).hasMessageContaining("关联用户");
        verify(mapper,never()).deletePermissions(any());
    }
    @Test void deletionCleansDependenciesAndPreservesAudit() {
        service.deleteRole(1L,2L);
        var order=inOrder(mapper,audit);
        order.verify(mapper).deletePermissions(2L);
        order.verify(mapper).deleteDataScopes(2L);
        order.verify(mapper).deleteById(2L);
        order.verify(audit).record(IamChangeAuditEventType.ROLE_DELETE,1L,IamChangeTargetType.ROLE,2L,null,null,"CUSTOM_ROLE",null);
    }
    @Test void changingStatusRevokesSessionsInSameTransaction() {
        service.changeRoleStatus(1L,2L,RoleStatus.DISABLED);
        verify(mapper).updateStatus(2L,RoleStatus.DISABLED);
        verify(mapper).revokeAssociatedSessions(2L);
        verify(audit).record(IamChangeAuditEventType.ROLE_STATUS_CHANGE,1L,IamChangeTargetType.ROLE,2L,null,null,"ENABLED","DISABLED");
    }
    @Test void updateTrimsAndRestrictsEditableFields() {
        service.updateRole(1L,2L," 新名称 "," 描述 ");
        verify(mapper).updateDetails(2L,"新名称","描述");
        assertThatThrownBy(()->service.updateRole(1L,2L,"a".repeat(65),null)).hasMessageContaining("64");
    }

    @Test void administratorCanEditBuiltInDetails() {
        when(userRoles.findEnabledRoleCodesByUserId(1L)).thenReturn(java.util.List.of("SYS_ADMIN"));
        when(mapper.lockById(2L)).thenReturn(new Role(2L,"BASE","内置",RoleType.BUILT_IN,RoleDataScope.ALL,true,RoleStatus.ENABLED,null,null,null));
        service.updateRole(1L,2L,"新名称","说明");
        verify(mapper).updateDetails(2L,"新名称","说明");
    }
}
